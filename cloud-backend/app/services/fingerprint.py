"""
Behavioral fingerprinting service.

A fingerprint captures the statistical signature of a player's behavioral
patterns: click rhythm, combat preferences, and movement style. Fingerprints
are stored in Redis (fast lookup) and optionally persisted to DB via a JSON
column on the Player model.
"""
import hashlib
import json
import logging
import math
from typing import Any

import redis.asyncio as aioredis

from app.config import settings
from app.models.schemas import AnalyzeRequest

logger = logging.getLogger(__name__)

_FINGERPRINT_TTL = 60 * 60 * 24 * 7  # 7 days
_SIMILARITY_THRESHOLD = 0.80


def _redis_key(player_uuid: str) -> str:
    return f"fingerprint:{player_uuid}"


def _safe_mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def _safe_stddev(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    m = _safe_mean(values)
    return math.sqrt(sum((v - m) ** 2 for v in values) / len(values))


class FingerprintService:
    def __init__(self, redis_client: aioredis.Redis | None = None) -> None:
        self._redis = redis_client

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def build_fingerprint(self, request: AnalyzeRequest) -> dict[str, Any]:
        """
        Derive a compact behavioural fingerprint from an AnalyzeRequest.
        Returns a dict that can be JSON-serialised and stored.
        """
        fp: dict[str, Any] = {
            "player_uuid": request.player_uuid,
            "server_id": request.server_id,
            "click_profile": self._click_profile(request.click_timestamps),
            "combat_profile": self._combat_profile(request.combat_samples),
            "movement_profile": self._movement_profile(request.movement_samples),
        }
        # Stable hash of the fingerprint vector (used for comparison indexing)
        vector_str = json.dumps(
            {k: v for k, v in fp.items() if k not in ("player_uuid", "server_id")},
            sort_keys=True,
        )
        fp["vector_hash"] = hashlib.sha256(vector_str.encode()).hexdigest()[:12]
        return fp

    async def store_fingerprint(self, player_uuid: str, fingerprint: dict[str, Any]) -> None:
        if not self._redis:
            return
        try:
            key = _redis_key(player_uuid)
            await self._redis.setex(key, _FINGERPRINT_TTL, json.dumps(fingerprint))
            logger.debug("Stored fingerprint for %s", player_uuid)
        except Exception as exc:
            logger.warning("Failed to store fingerprint: %s", exc)

    async def get_fingerprint(self, player_uuid: str) -> dict[str, Any] | None:
        if not self._redis:
            return None
        try:
            key = _redis_key(player_uuid)
            raw = await self._redis.get(key)
            return json.loads(raw) if raw else None
        except Exception as exc:
            logger.warning("Failed to retrieve fingerprint: %s", exc)
            return None

    def compare(self, fp1: dict[str, Any], fp2: dict[str, Any]) -> dict[str, Any]:
        """
        Compare two fingerprints and return a similarity score in [0, 1].
        A score ≥ SIMILARITY_THRESHOLD suggests the same player.
        """
        scores: list[float] = []

        scores.append(self._compare_click_profiles(
            fp1.get("click_profile", {}), fp2.get("click_profile", {})
        ))
        scores.append(self._compare_combat_profiles(
            fp1.get("combat_profile", {}), fp2.get("combat_profile", {})
        ))
        scores.append(self._compare_movement_profiles(
            fp1.get("movement_profile", {}), fp2.get("movement_profile", {})
        ))

        overall = _safe_mean(scores)
        return {
            "similarity_score": round(overall, 4),
            "likely_same_player": overall >= _SIMILARITY_THRESHOLD,
            "component_scores": {
                "click": round(scores[0], 4),
                "combat": round(scores[1], 4),
                "movement": round(scores[2], 4),
            },
        }

    # ------------------------------------------------------------------
    # Profile builders
    # ------------------------------------------------------------------

    def _click_profile(self, timestamps: list[int]) -> dict[str, Any]:
        if len(timestamps) < 5:
            return {}
        ts = sorted(timestamps)
        intervals = [ts[i + 1] - ts[i] for i in range(len(ts) - 1)]
        mean_iv = _safe_mean(intervals)
        duration = (ts[-1] - ts[0]) / 1000.0
        return {
            "mean_cps": round(1000.0 / mean_iv, 3) if mean_iv > 0 else 0.0,
            "stddev_ms": round(_safe_stddev(intervals), 3),
            "sample_size": len(timestamps),
            "duration_sec": round(duration, 2),
        }

    def _combat_profile(self, samples: list) -> dict[str, Any]:
        if not samples:
            return {}
        distances = [s.distance for s in samples]
        hits = [1 if s.hit else 0 for s in samples]
        return {
            "mean_distance": round(_safe_mean(distances), 3),
            "stddev_distance": round(_safe_stddev(distances), 3),
            "hit_rate": round(_safe_mean(hits), 4),
            "sample_size": len(samples),
        }

    def _movement_profile(self, samples: list) -> dict[str, Any]:
        if len(samples) < 3:
            return {}
        sorted_s = sorted(samples, key=lambda s: s.timestamp)
        speeds: list[float] = []
        for i in range(1, len(sorted_s)):
            s1, s2 = sorted_s[i - 1], sorted_s[i]
            dt = (s2.timestamp - s1.timestamp) / 1000.0
            if dt <= 0:
                continue
            dx, dz = s2.x - s1.x, s2.z - s1.z
            speeds.append(math.sqrt(dx * dx + dz * dz) / dt)
        ground_ratio = sum(1 for s in sorted_s if s.on_ground) / len(sorted_s)
        return {
            "mean_speed": round(_safe_mean(speeds), 4),
            "stddev_speed": round(_safe_stddev(speeds), 4),
            "ground_ratio": round(ground_ratio, 4),
            "sample_size": len(samples),
        }

    # ------------------------------------------------------------------
    # Profile comparators (return 0.0–1.0 similarity)
    # ------------------------------------------------------------------

    def _compare_click_profiles(self, p1: dict, p2: dict) -> float:
        if not p1 or not p2:
            return 0.5  # unknown
        cps_sim = self._norm_similarity(p1.get("mean_cps", 0), p2.get("mean_cps", 0), scale=5.0)
        std_sim = self._norm_similarity(p1.get("stddev_ms", 0), p2.get("stddev_ms", 0), scale=20.0)
        return (cps_sim + std_sim) / 2

    def _compare_combat_profiles(self, p1: dict, p2: dict) -> float:
        if not p1 or not p2:
            return 0.5
        dist_sim = self._norm_similarity(p1.get("mean_distance", 0), p2.get("mean_distance", 0), scale=1.0)
        hr_sim = self._norm_similarity(p1.get("hit_rate", 0), p2.get("hit_rate", 0), scale=0.2)
        return (dist_sim + hr_sim) / 2

    def _compare_movement_profiles(self, p1: dict, p2: dict) -> float:
        if not p1 or not p2:
            return 0.5
        speed_sim = self._norm_similarity(p1.get("mean_speed", 0), p2.get("mean_speed", 0), scale=1.0)
        gnd_sim = self._norm_similarity(p1.get("ground_ratio", 0), p2.get("ground_ratio", 0), scale=0.2)
        return (speed_sim + gnd_sim) / 2

    @staticmethod
    def _norm_similarity(v1: float, v2: float, scale: float = 1.0) -> float:
        """Gaussian similarity: 1.0 when equal, decays with distance / scale."""
        diff = abs(v1 - v2)
        return math.exp(-0.5 * (diff / scale) ** 2)
