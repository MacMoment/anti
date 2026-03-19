"""
Tier-3 AI analysis service — uses Claude for behavioural analysis.
Results are cached in Redis for CACHE_TTL_SECONDS.
"""
import hashlib
import json
import logging
from typing import Any

import anthropic
import redis.asyncio as aioredis

from app.ai.aim_analysis import analyze_aim
from app.ai.click_analysis import analyze_clicks
from app.ai.movement_analysis import analyze_movement
from app.config import settings
from app.models.schemas import AnalyzeRequest

logger = logging.getLogger(__name__)


def _cache_key(player_uuid: str, data_hash: str) -> str:
    return f"ai_analysis:{player_uuid}:{data_hash}"


def _hash_request(request: AnalyzeRequest) -> str:
    payload = json.dumps(
        {
            "clicks": sorted(request.click_timestamps),
            "movement": len(request.movement_samples),
            "combat": len(request.combat_samples),
        },
        sort_keys=True,
    )
    return hashlib.sha256(payload.encode()).hexdigest()[:16]


def _merge_results(results: list[dict[str, Any]]) -> dict[str, Any]:
    """Weighted merge of multiple AI sub-analysis results into a single verdict."""
    if not results:
        return {
            "cheat_probability": 0.0,
            "detected_cheats": [],
            "confidence": "low",
            "recommended_action": "none",
            "reasoning": "No analysis data.",
        }

    probs = [r.get("cheat_probability", 0.0) for r in results]
    # Take the 75th percentile rather than the mean — one strong signal matters
    probs_sorted = sorted(probs)
    p75_idx = max(0, int(len(probs_sorted) * 0.75) - 1)
    combined_prob = probs_sorted[p75_idx]

    detected: list[str] = []
    for r in results:
        detected.extend(r.get("detected_cheats", []))
    detected = list(dict.fromkeys(detected))  # dedup, preserve order

    confidences = [r.get("confidence", "low") for r in results]
    conf_rank = {"low": 0, "medium": 1, "high": 2}
    best_conf = max(confidences, key=lambda c: conf_rank.get(c, 0))

    reasonings = [r.get("reasoning", "") for r in results if r.get("reasoning")]
    reasoning = " | ".join(reasonings[:3])

    # Map probability to action
    if combined_prob >= 0.8:
        action = "ban"
    elif combined_prob >= 0.6:
        action = "mitigate"
    elif combined_prob >= 0.4:
        action = "monitor"
    else:
        action = "none"

    return {
        "cheat_probability": round(combined_prob, 4),
        "detected_cheats": detected,
        "confidence": best_conf,
        "recommended_action": action,
        "reasoning": reasoning,
    }


class AIAnalyzer:
    def __init__(
        self,
        client: anthropic.AsyncAnthropic,
        redis_client: aioredis.Redis | None = None,
    ) -> None:
        self._client = client
        self._redis = redis_client

    async def analyze(
        self,
        request: AnalyzeRequest,
        tier: str = "sonnet",
    ) -> dict[str, Any]:
        """
        Run all AI sub-analyses (clicks, aim, movement) in sequence,
        merge results, cache and return.

        `tier` can be "sonnet" (fast, default) or "opus" (higher quality for ambiguous cases).
        """
        model = (
            settings.CLAUDE_OPUS_MODEL
            if tier == "opus"
            else settings.CLAUDE_SONNET_MODEL
        )

        # Try cache
        cache_key = _cache_key(request.player_uuid, _hash_request(request))
        if self._redis:
            try:
                cached = await self._redis.get(cache_key)
                if cached:
                    result = json.loads(cached)
                    result["cached"] = True
                    logger.debug("Cache hit for %s", request.player_uuid)
                    return result
            except Exception as exc:
                logger.warning("Redis cache read failed: %s", exc)

        sub_results: list[dict[str, Any]] = []

        # Click analysis
        if request.click_timestamps:
            try:
                click_result = await analyze_clicks(
                    self._client, request.click_timestamps, model
                )
                sub_results.append(click_result)
            except anthropic.APIError as exc:
                logger.error("Click analysis API error: %s", exc)

        # Aim analysis
        if request.combat_samples:
            try:
                aim_result = await analyze_aim(
                    self._client, request.combat_samples, model
                )
                sub_results.append(aim_result)
            except anthropic.APIError as exc:
                logger.error("Aim analysis API error: %s", exc)

        # Movement analysis
        if request.movement_samples:
            try:
                movement_result = await analyze_movement(
                    self._client, request.movement_samples, model
                )
                sub_results.append(movement_result)
            except anthropic.APIError as exc:
                logger.error("Movement analysis API error: %s", exc)

        merged = _merge_results(sub_results)
        merged["cached"] = False

        # Write to cache
        if self._redis:
            try:
                await self._redis.setex(
                    cache_key,
                    settings.CACHE_TTL_SECONDS,
                    json.dumps(merged),
                )
            except Exception as exc:
                logger.warning("Redis cache write failed: %s", exc)

        logger.info(
            "AI analysis complete: player=%s prob=%.3f action=%s model=%s",
            request.player_uuid,
            merged["cheat_probability"],
            merged["recommended_action"],
            model,
        )
        return merged
