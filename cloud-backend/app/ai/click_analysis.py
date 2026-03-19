import json
import logging
import math
from typing import Any

import anthropic

from app.ai.prompts import CLICK_ANALYSIS_PROMPT, SYSTEM_PROMPT

logger = logging.getLogger(__name__)


def _calc_entropy(intervals: list[float]) -> float:
    """Shannon entropy of discretised inter-click intervals (10 ms bins)."""
    if len(intervals) < 2:
        return 0.0
    binned: dict[int, int] = {}
    for iv in intervals:
        key = int(iv / 10)
        binned[key] = binned.get(key, 0) + 1
    total = len(intervals)
    return -sum((c / total) * math.log2(c / total) for c in binned.values() if c > 0)


def _calc_autocorrelation(intervals: list[float], lag: int = 1) -> float:
    """Pearson autocorrelation at given lag."""
    n = len(intervals)
    if n <= lag + 1:
        return 0.0
    mean = sum(intervals) / n
    numerator = sum(
        (intervals[i] - mean) * (intervals[i + lag] - mean) for i in range(n - lag)
    )
    denominator = sum((x - mean) ** 2 for x in intervals)
    return numerator / denominator if denominator > 0 else 0.0


def _build_stats(click_timestamps: list[int]) -> dict[str, Any]:
    if len(click_timestamps) < 2:
        return {"error": "insufficient data", "count": len(click_timestamps)}

    sorted_ts = sorted(click_timestamps)
    intervals = [sorted_ts[i + 1] - sorted_ts[i] for i in range(len(sorted_ts) - 1)]
    intervals_sec = [iv / 1000.0 for iv in intervals]

    n = len(intervals)
    mean_iv = sum(intervals) / n
    variance = sum((x - mean_iv) ** 2 for x in intervals) / n
    stddev = math.sqrt(variance)

    cps_values: list[float] = [1000.0 / iv for iv in intervals if iv > 0]
    mean_cps = sum(cps_values) / len(cps_values) if cps_values else 0.0

    # Skewness and kurtosis
    if stddev > 0:
        skewness = sum(((x - mean_iv) / stddev) ** 3 for x in intervals) / n
        kurtosis = sum(((x - mean_iv) / stddev) ** 4 for x in intervals) / n - 3.0
    else:
        skewness = 0.0
        kurtosis = 0.0

    entropy = _calc_entropy(intervals)
    autocorr_lag1 = _calc_autocorrelation(intervals)

    duration_sec = (sorted_ts[-1] - sorted_ts[0]) / 1000.0
    total_clicks = len(sorted_ts)
    overall_cps = total_clicks / duration_sec if duration_sec > 0 else 0.0

    return {
        "count": total_clicks,
        "duration_sec": round(duration_sec, 2),
        "overall_cps": round(overall_cps, 3),
        "mean_interval_ms": round(mean_iv, 3),
        "stddev_interval_ms": round(stddev, 3),
        "min_interval_ms": min(intervals),
        "max_interval_ms": max(intervals),
        "mean_cps": round(mean_cps, 3),
        "skewness": round(skewness, 4),
        "kurtosis": round(kurtosis, 4),
        "entropy_bits": round(entropy, 4),
        "autocorr_lag1": round(autocorr_lag1, 4),
        "cv": round(stddev / mean_iv, 4) if mean_iv > 0 else 0.0,
    }


async def analyze_clicks(
    client: anthropic.AsyncAnthropic,
    click_timestamps: list[int],
    model: str,
) -> dict[str, Any]:
    """Send click timing data to Claude for autoclicker detection."""
    if not click_timestamps:
        return {
            "cheat_probability": 0.0,
            "detected_cheats": [],
            "confidence": "low",
            "recommended_action": "none",
            "reasoning": "No click data provided.",
        }

    stats = _build_stats(click_timestamps)
    sample_ts = click_timestamps[:200]

    prompt = CLICK_ANALYSIS_PROMPT.format(
        stats=json.dumps(stats, indent=2),
        count=len(sample_ts),
        timestamps=json.dumps(sample_ts),
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
        logger.debug("click_analysis result: %s", result)
        return result
    except json.JSONDecodeError as exc:
        logger.warning("Claude returned non-JSON click analysis: %s", exc)
        return {
            "cheat_probability": stats.get("overall_cps", 0) / 30.0,
            "detected_cheats": ["autoclicker"] if stats.get("overall_cps", 0) > 18 else [],
            "confidence": "low",
            "recommended_action": "monitor",
            "reasoning": f"AI parse error; statistical fallback: CPS={stats.get('overall_cps')}",
        }
    except anthropic.APIError as exc:
        logger.error("Anthropic API error in click_analysis: %s", exc)
        raise
