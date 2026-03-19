"""
Pattern-based (Tier 2) anticheat analysis — no AI, no API cost.
Uses numpy/scipy for statistical calculations.
"""
import logging
import math
from typing import Any

import numpy as np
from scipy import stats as sp_stats

from app.models.schemas import AnalyzeRequest

logger = logging.getLogger(__name__)

# Thresholds
MAX_LEGIT_SPEED = 8.0          # blocks/s including speed effects
REACH_VIOLATION_DIST = 3.5     # blocks
MIN_AUTOCLICKER_CPS = 16.0
AUTOCLICKER_STDDEV_MAX = 5.0   # ms — suspiciously consistent
LOW_ENTROPY_THRESHOLD = 2.0    # bits

VIOLATION_WEIGHTS: dict[str, float] = {
    "SPEED": 0.15,
    "FLY": 0.25,
    "REACH": 0.20,
    "KILLAURA": 0.30,
    "AUTOCLICKER": 0.20,
}


class PatternDetector:
    """Tier-2 rule-based + statistical detector."""

    def analyze(self, request: AnalyzeRequest) -> dict[str, Any]:
        flags: list[str] = []
        details: dict[str, Any] = {}
        partial_scores: list[float] = []

        # --- Click analysis ---
        if request.click_timestamps and len(request.click_timestamps) >= 5:
            click_result = self._analyze_clicks(request.click_timestamps)
            details["clicks"] = click_result
            partial_scores.append(click_result["score"])
            flags.extend(click_result["flags"])

        # --- Movement analysis ---
        if len(request.movement_samples) >= 3:
            move_result = self._analyze_movement(request.movement_samples)
            details["movement"] = move_result
            partial_scores.append(move_result["score"])
            flags.extend(move_result["flags"])

        # --- Combat analysis ---
        if request.combat_samples:
            combat_result = self._analyze_combat(request.combat_samples)
            details["combat"] = combat_result
            partial_scores.append(combat_result["score"])
            flags.extend(combat_result["flags"])

        # --- Violation history ---
        if request.violation_history:
            hist_result = self._analyze_violation_history(request.violation_history)
            details["history"] = hist_result
            partial_scores.append(hist_result["score"])
            flags.extend(hist_result["flags"])

        suspicion_score = float(np.clip(np.mean(partial_scores) if partial_scores else 0.0, 0.0, 1.0))

        logger.debug(
            "PatternDetector: player=%s score=%.3f flags=%s",
            request.player_uuid,
            suspicion_score,
            flags,
        )

        return {
            "suspicion_score": round(suspicion_score, 4),
            "flags": list(set(flags)),
            "details": details,
        }

    # ------------------------------------------------------------------
    # Click analysis
    # ------------------------------------------------------------------
    def _analyze_clicks(self, timestamps: list[int]) -> dict[str, Any]:
        ts = np.array(sorted(timestamps), dtype=np.float64)
        intervals = np.diff(ts)  # ms

        if len(intervals) < 4:
            return {"score": 0.0, "flags": [], "mean_cps": 0.0}

        mean_iv = float(np.mean(intervals))
        stddev_iv = float(np.std(intervals))
        mean_cps = 1000.0 / mean_iv if mean_iv > 0 else 0.0

        duration_sec = (ts[-1] - ts[0]) / 1000.0
        overall_cps = len(ts) / duration_sec if duration_sec > 0 else 0.0

        # Shannon entropy of 10ms-binned intervals
        bins = np.floor(intervals / 10).astype(int)
        _, counts = np.unique(bins, return_counts=True)
        probs = counts / counts.sum()
        entropy = float(-np.sum(probs * np.log2(probs + 1e-12)))

        raw_kurtosis = sp_stats.kurtosis(intervals)
        raw_skewness = sp_stats.skew(intervals)
        kurtosis = float(raw_kurtosis) if raw_kurtosis == raw_kurtosis else 0.0  # NaN guard
        skewness = float(raw_skewness) if raw_skewness == raw_skewness else 0.0
        cv = stddev_iv / mean_iv if mean_iv > 0 else 0.0

        score = 0.0
        flags: list[str] = []

        if overall_cps > MIN_AUTOCLICKER_CPS:
            score += 0.4
            flags.append("AUTOCLICKER_HIGH_CPS")
        if stddev_iv < AUTOCLICKER_STDDEV_MAX and overall_cps > 10:
            score += 0.4
            flags.append("AUTOCLICKER_LOW_VARIANCE")
        if entropy < LOW_ENTROPY_THRESHOLD and len(intervals) > 20:
            score += 0.2
            flags.append("AUTOCLICKER_LOW_ENTROPY")

        return {
            "score": min(score, 1.0),
            "flags": flags,
            "mean_cps": round(mean_cps, 3),
            "overall_cps": round(overall_cps, 3),
            "stddev_ms": round(stddev_iv, 3),
            "entropy_bits": round(entropy, 4),
            "kurtosis": round(kurtosis, 4),
            "skewness": round(skewness, 4),
            "cv": round(cv, 4),
        }

    # ------------------------------------------------------------------
    # Movement analysis
    # ------------------------------------------------------------------
    def _analyze_movement(self, samples: list) -> dict[str, Any]:
        sorted_s = sorted(samples, key=lambda s: s.timestamp)
        speeds: list[float] = []
        y_deltas: list[float] = []
        fly_ticks = 0

        for i in range(1, len(sorted_s)):
            s1, s2 = sorted_s[i - 1], sorted_s[i]
            dt = (s2.timestamp - s1.timestamp) / 1000.0
            if dt <= 0:
                continue
            dx, dz = s2.x - s1.x, s2.z - s1.z
            h_speed = math.sqrt(dx * dx + dz * dz) / dt
            y_delta = s2.y - s1.y
            speeds.append(h_speed)
            y_deltas.append(y_delta)
            if not s2.on_ground and y_delta > 0.05:
                fly_ticks += 1

        if not speeds:
            return {"score": 0.0, "flags": []}

        sp_arr = np.array(speeds)
        speed_violations = int(np.sum(sp_arr > MAX_LEGIT_SPEED))
        violation_ratio = speed_violations / len(speeds)
        fly_ratio = fly_ticks / len(speeds)

        score = 0.0
        flags: list[str] = []

        if violation_ratio > 0.05:
            score += min(violation_ratio * 2, 0.7)
            flags.append("SPEED")
        if fly_ratio > 0.1:
            score += min(fly_ratio * 2, 0.8)
            flags.append("FLY")

        return {
            "score": min(score, 1.0),
            "flags": flags,
            "mean_speed": round(float(np.mean(sp_arr)), 4),
            "max_speed": round(float(np.max(sp_arr)), 4),
            "speed_violations": speed_violations,
            "violation_ratio": round(violation_ratio, 4),
            "fly_ticks": fly_ticks,
            "fly_ratio": round(fly_ratio, 4),
        }

    # ------------------------------------------------------------------
    # Combat analysis
    # ------------------------------------------------------------------
    def _analyze_combat(self, samples: list) -> dict[str, Any]:
        distances = np.array([s.distance for s in samples], dtype=np.float64)
        hits = np.array([1 if s.hit else 0 for s in samples], dtype=np.float64)

        hit_rate = float(np.mean(hits)) if len(hits) else 0.0
        reach_violations = int(np.sum(distances > REACH_VIOLATION_DIST))
        reach_ratio = reach_violations / len(distances) if len(distances) else 0.0

        # Absolute pitch offset to target — low values indicate pitch-lock (aimbot)
        abs_pitch_to_target = np.array([abs(s.pitch_to_target) for s in samples], dtype=np.float64)
        pitch_near_zero_ratio = float(np.mean(abs_pitch_to_target < 5.0)) if len(abs_pitch_to_target) else 0.0

        score = 0.0
        flags: list[str] = []

        if reach_ratio > 0.15:
            score += min(reach_ratio * 2, 0.7)
            flags.append("REACH")

        if hit_rate > 0.85 and len(hits) >= 10:
            score += 0.3
            flags.append("KILLAURA_HIGH_HIT_RATE")

        if pitch_near_zero_ratio > 0.7 and len(pitches) >= 10:
            score += 0.2
            flags.append("AIMBOT_PITCH_LOCK")

        return {
            "score": min(score, 1.0),
            "flags": flags,
            "hit_rate": round(hit_rate, 4),
            "reach_violations": reach_violations,
            "reach_ratio": round(reach_ratio, 4),
            "mean_distance": round(float(np.mean(distances)), 4),
            "pitch_near_zero_ratio": round(pitch_near_zero_ratio, 4),
        }

    # ------------------------------------------------------------------
    # Violation history analysis
    # ------------------------------------------------------------------
    def _analyze_violation_history(self, history: list[str]) -> dict[str, Any]:
        severity_map = {
            "SPEED": 0.3,
            "FLY": 0.5,
            "KILLAURA": 0.5,
            "REACH": 0.3,
            "AUTOCLICKER": 0.25,
            "AIMBOT": 0.5,
            "TIMER": 0.4,
            "NOFALL": 0.2,
            "BAN_EVADE": 0.7,
        }
        total = len(history)
        if total == 0:
            return {"score": 0.0, "flags": [], "total_violations": 0}

        weighted_sum = sum(severity_map.get(v.upper(), 0.1) for v in history)
        # Decay with count: more violations = higher score, capped
        score = min(weighted_sum / max(total, 1) * math.log1p(total), 1.0)

        high_severity_flags = [
            v for v in history if severity_map.get(v.upper(), 0.0) >= 0.4
        ]
        flags = ["REPEATED_VIOLATIONS"] if total >= 5 else []

        return {
            "score": round(score, 4),
            "flags": flags,
            "total_violations": total,
            "unique_types": len(set(v.upper() for v in history)),
            "high_severity_count": len(high_severity_flags),
        }
