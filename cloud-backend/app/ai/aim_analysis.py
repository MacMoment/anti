import json
import logging
import math
from typing import Any

import anthropic

from app.ai.prompts import AIM_ANALYSIS_PROMPT, SYSTEM_PROMPT
from app.models.schemas import CombatSample

logger = logging.getLogger(__name__)


def _gcd(a: float, b: float, tolerance: float = 1e-6) -> float:
    """Euclidean GCD for floats."""
    while abs(b) > tolerance:
        a, b = b, a % b
    return abs(a)


def _gcd_of_list(values: list[float]) -> float:
    """GCD of a list of floats, used for sensitivity fingerprinting."""
    if not values:
        return 0.0
    result = abs(values[0])
    for v in values[1:]:
        result = _gcd(result, abs(v))
        if result < 1e-6:
            return 0.0
    return result


def _build_combat_data(combat_samples: list[CombatSample]) -> tuple[dict, dict]:
    if not combat_samples:
        return {}, {}

    sorted_samples = sorted(combat_samples, key=lambda s: s.timestamp)

    yaw_deltas: list[float] = []
    pitch_deltas: list[float] = []
    distances: list[float] = []
    hits: list[bool] = []

    prev_yaw: float | None = None
    prev_pitch: float | None = None

    for s in sorted_samples:
        if prev_yaw is not None:
            raw_yaw_delta = abs(s.yaw_to_target - prev_yaw)
            # Wrap-around correction
            if raw_yaw_delta > 180:
                raw_yaw_delta = 360 - raw_yaw_delta
            yaw_deltas.append(raw_yaw_delta)
            pitch_deltas.append(abs(s.pitch_to_target - prev_pitch))  # type: ignore[operator]
        prev_yaw = s.yaw_to_target
        prev_pitch = s.pitch_to_target
        distances.append(s.distance)
        hits.append(s.hit)

    hit_rate = sum(hits) / len(hits) if hits else 0.0

    mean_distance = sum(distances) / len(distances) if distances else 0.0
    mean_yaw_delta = sum(yaw_deltas) / len(yaw_deltas) if yaw_deltas else 0.0
    mean_pitch_delta = sum(pitch_deltas) / len(pitch_deltas) if pitch_deltas else 0.0

    snap_count = sum(1 for d in yaw_deltas if d > 45.0)

    combat_data = {
        "total_interactions": len(sorted_samples),
        "hit_rate": round(hit_rate, 4),
        "mean_distance": round(mean_distance, 3),
        "max_distance": round(max(distances), 3) if distances else 0.0,
        "mean_yaw_delta": round(mean_yaw_delta, 4),
        "mean_pitch_delta": round(mean_pitch_delta, 4),
        "snap_count_yaw_gt45": snap_count,
        "samples": [
            {
                "target": s.target_uuid[:8],
                "hit": s.hit,
                "dist": round(s.distance, 3),
                "yaw": round(s.yaw_to_target, 4),
                "pitch": round(s.pitch_to_target, 4),
            }
            for s in sorted_samples[:50]
        ],
    }

    non_zero_yaw = [d for d in yaw_deltas if d > 1e-4]
    non_zero_pitch = [d for d in pitch_deltas if d > 1e-4]

    gcd_data = {
        "yaw_gcd": round(_gcd_of_list(non_zero_yaw[:50]), 6),
        "pitch_gcd": round(_gcd_of_list(non_zero_pitch[:50]), 6),
        "yaw_deltas_sample": [round(d, 4) for d in yaw_deltas[:30]],
        "pitch_deltas_sample": [round(d, 4) for d in pitch_deltas[:30]],
    }
    return combat_data, gcd_data


async def analyze_aim(
    client: anthropic.AsyncAnthropic,
    combat_samples: list[CombatSample],
    model: str,
) -> dict[str, Any]:
    """Send combat rotation data to Claude for aimbot/killaura detection."""
    if not combat_samples:
        return {
            "cheat_probability": 0.0,
            "detected_cheats": [],
            "confidence": "low",
            "recommended_action": "none",
            "reasoning": "No combat data provided.",
        }

    combat_data, gcd_data = _build_combat_data(combat_samples)

    prompt = AIM_ANALYSIS_PROMPT.format(
        combat_data=json.dumps(combat_data, indent=2),
        gcd_data=json.dumps(gcd_data, indent=2),
    )

    try:
        message = await client.messages.create(
            model=model,
            max_tokens=512,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        raw = message.content[0].text.strip()
        result = json.loads(raw)
        logger.debug("aim_analysis result: %s", result)
        return result
    except json.JSONDecodeError as exc:
        logger.warning("Claude returned non-JSON aim analysis: %s", exc)
        hit_rate = combat_data.get("hit_rate", 0.0)
        max_dist = combat_data.get("max_distance", 0.0)
        suspicious = hit_rate > 0.85 or max_dist > 3.5
        return {
            "cheat_probability": 0.7 if suspicious else 0.1,
            "detected_cheats": ["aimbot", "reach"] if suspicious else [],
            "confidence": "low",
            "recommended_action": "monitor" if suspicious else "none",
            "reasoning": f"AI parse error; statistical fallback: hit_rate={hit_rate}, max_dist={max_dist}",
        }
    except anthropic.APIError as exc:
        logger.error("Anthropic API error in aim_analysis: %s", exc)
        raise
