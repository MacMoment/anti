import json
import logging
import math
from typing import Any

import anthropic

from app.ai.prompts import MOVEMENT_ANALYSIS_PROMPT, SYSTEM_PROMPT
from app.models.schemas import MovementSample

logger = logging.getLogger(__name__)

# Vanilla movement constants (blocks per second)
WALK_SPEED = 4.317
SPRINT_SPEED = 5.612
SPRINT_JUMP_SPEED = 7.127
MAX_LEGIT_SPEED = 8.0  # generous upper bound including ice/speed effects


def _horizontal_speed(s1: MovementSample, s2: MovementSample) -> float:
    dt_sec = (s2.timestamp - s1.timestamp) / 1000.0
    if dt_sec <= 0:
        return 0.0
    dx = s2.x - s1.x
    dz = s2.z - s1.z
    return math.sqrt(dx * dx + dz * dz) / dt_sec


def _vertical_delta(s1: MovementSample, s2: MovementSample) -> float:
    return s2.y - s1.y


def _build_movement_data(
    movement_samples: list[MovementSample],
) -> tuple[dict[str, Any], dict[str, Any]]:
    if len(movement_samples) < 2:
        return {}, {}

    sorted_samples = sorted(movement_samples, key=lambda s: s.timestamp)

    speeds: list[float] = []
    y_deltas: list[float] = []
    on_ground_changes = 0
    fly_ticks = 0

    for i in range(1, len(sorted_samples)):
        s1, s2 = sorted_samples[i - 1], sorted_samples[i]
        h_speed = _horizontal_speed(s1, s2)
        y_delta = _vertical_delta(s1, s2)
        speeds.append(h_speed)
        y_deltas.append(y_delta)

        # Fly heuristic: not on ground, not falling, not jumping
        if not s2.on_ground and y_delta > 0.05:
            fly_ticks += 1

        if i > 1 and sorted_samples[i].on_ground != sorted_samples[i - 1].on_ground:
            on_ground_changes += 1

    n = len(speeds)
    mean_speed = sum(speeds) / n if n else 0.0
    max_speed = max(speeds) if speeds else 0.0
    speed_violations = sum(1 for s in speeds if s > MAX_LEGIT_SPEED)

    # Standard deviation
    if n > 1:
        variance = sum((s - mean_speed) ** 2 for s in speeds) / n
        stddev_speed = math.sqrt(variance)
    else:
        stddev_speed = 0.0

    mean_y_delta = sum(y_deltas) / len(y_deltas) if y_deltas else 0.0

    movement_data = {
        "total_samples": len(sorted_samples),
        "duration_sec": (sorted_samples[-1].timestamp - sorted_samples[0].timestamp) / 1000.0,
        "speed_violations_gt8bps": speed_violations,
        "fly_ticks": fly_ticks,
        "on_ground_changes": on_ground_changes,
        "positions_sample": [
            {
                "x": round(s.x, 3),
                "y": round(s.y, 3),
                "z": round(s.z, 3),
                "on_ground": s.on_ground,
                "ts": s.timestamp,
            }
            for s in sorted_samples[:40]
        ],
    }

    stats = {
        "mean_horizontal_speed_bps": round(mean_speed, 4),
        "max_horizontal_speed_bps": round(max_speed, 4),
        "stddev_speed": round(stddev_speed, 4),
        "mean_y_delta": round(mean_y_delta, 4),
        "max_y_delta": round(max(y_deltas), 4) if y_deltas else 0.0,
        "min_y_delta": round(min(y_deltas), 4) if y_deltas else 0.0,
        "speed_violation_ratio": round(speed_violations / n, 4) if n else 0.0,
        "fly_tick_ratio": round(fly_ticks / n, 4) if n else 0.0,
    }
    return movement_data, stats


async def analyze_movement(
    client: anthropic.AsyncAnthropic,
    movement_samples: list[MovementSample],
    model: str,
) -> dict[str, Any]:
    """Send movement position data to Claude for movement hack detection."""
    if not movement_samples:
        return {
            "cheat_probability": 0.0,
            "detected_cheats": [],
            "confidence": "low",
            "recommended_action": "none",
            "reasoning": "No movement data provided.",
        }

    movement_data, stats = _build_movement_data(movement_samples)

    prompt = MOVEMENT_ANALYSIS_PROMPT.format(
        movement_data=json.dumps(movement_data, indent=2),
        stats=json.dumps(stats, indent=2),
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
        logger.debug("movement_analysis result: %s", result)
        return result
    except json.JSONDecodeError as exc:
        logger.warning("Claude returned non-JSON movement analysis: %s", exc)
        violation_ratio = stats.get("speed_violation_ratio", 0.0)
        fly_ratio = stats.get("fly_tick_ratio", 0.0)
        cheats = []
        if violation_ratio > 0.1:
            cheats.append("speed")
        if fly_ratio > 0.1:
            cheats.append("fly")
        prob = min(1.0, violation_ratio * 2 + fly_ratio * 2)
        return {
            "cheat_probability": round(prob, 3),
            "detected_cheats": cheats,
            "confidence": "low",
            "recommended_action": "monitor" if cheats else "none",
            "reasoning": f"AI parse error; statistical fallback: violation_ratio={violation_ratio}, fly_ratio={fly_ratio}",
        }
    except anthropic.APIError as exc:
        logger.error("Anthropic API error in movement_analysis: %s", exc)
        raise
