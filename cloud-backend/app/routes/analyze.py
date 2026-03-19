import hashlib
import hmac
import logging
import uuid
from datetime import datetime, timezone

import anthropic
from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.db.database import get_db
from app.models.analysis import Analysis
from app.models.player import Player
from app.models.schemas import AnalyzeRequest, AnalyzeResponse
from app.services.ai_analyzer import AIAnalyzer
from app.services.cross_server import CrossServerService
from app.services.fingerprint import FingerprintService
from app.services.pattern_detector import PatternDetector

logger = logging.getLogger(__name__)
router = APIRouter(tags=["analyze"])

_pattern_detector = PatternDetector()


def _verify_api_key(x_api_key: str = Header(..., alias="X-API-Key")) -> str:
    if x_api_key != settings.API_SECRET_KEY:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API key")
    return x_api_key


@router.post("/analyze", response_model=AnalyzeResponse, status_code=status.HTTP_200_OK)
async def analyze_player(
    request: Request,
    payload: AnalyzeRequest,
    db: AsyncSession = Depends(get_db),
    _api_key: str = Depends(_verify_api_key),
):
    """
    Analyse a player's behaviour data.

    Tier 2: statistical pattern detection (always runs, no cost).
    Tier 3: Claude AI analysis (only if Tier 2 suspicion ≥ threshold).
    """
    from app.main import anthropic_client, redis_client  # lazy to avoid circular

    # Verify HMAC signature — the Java plugin signs (body_json + timestamp_millis)
    body = await request.body()
    sig_header = request.headers.get("X-Signature", "")
    if sig_header:
        ts_header = request.headers.get("X-Timestamp", "")
        signed_data = body.decode("utf-8") + ts_header
        expected_sig = hmac.new(
            settings.API_SECRET_KEY.encode(),
            signed_data.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        if not hmac.compare_digest(expected_sig, sig_header):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid signature")

    # --- Tier 2: Pattern detection ---
    tier2_result = _pattern_detector.analyze(payload)
    suspicion_score: float = tier2_result["suspicion_score"]
    tier_used = 2

    final_result: dict = {
        "cheat_probability": suspicion_score,
        "detected_cheats": tier2_result["flags"],
        "confidence": "medium" if suspicion_score > 0.3 else "low",
        "recommended_action": _score_to_action(suspicion_score),
        "reasoning": f"Pattern analysis: suspicion_score={suspicion_score:.3f}. Flags: {', '.join(tier2_result['flags']) or 'none'}.",
        "cached": False,
    }

    # --- Tier 3: AI analysis (escalate if suspicious) ---
    if suspicion_score >= settings.AI_TIER3_THRESHOLD and anthropic_client:
        tier_used = 3
        try:
            ai_analyzer = AIAnalyzer(client=anthropic_client, redis_client=redis_client)
            # Use opus for mid-range scores where the decision is most ambiguous;
            # clearly high or clearly low scores use the faster/cheaper sonnet model.
            tier = "opus" if 0.65 <= suspicion_score <= 0.85 else "sonnet"
            ai_result = await ai_analyzer.analyze(payload, tier=tier)

            # Blend: AI takes precedence, but we note the Tier 2 flags
            final_result = ai_result
            if tier2_result["flags"]:
                extra_flags = [f for f in tier2_result["flags"] if f not in ai_result.get("detected_cheats", [])]
                if extra_flags:
                    final_result["detected_cheats"] = list(ai_result.get("detected_cheats", [])) + extra_flags
        except anthropic.APIError as exc:
            logger.error("AI analysis failed, falling back to Tier 2: %s", exc)

    # --- Persist player and analysis to DB ---
    try:
        player = await _upsert_player(db, payload)
        await _save_analysis(db, player, payload, final_result, tier2_result, tier_used)
    except Exception as exc:
        logger.error("DB persistence failed: %s", exc)

    # --- Cross-server tracking and fingerprinting (fire-and-forget) ---
    try:
        cs_service = CrossServerService(redis_client=redis_client)
        fp_service = FingerprintService(redis_client=redis_client)
        fingerprint = fp_service.build_fingerprint(payload)

        await cs_service.record_activity(
            player_uuid=payload.player_uuid,
            server_id=payload.server_id,
            cheat_probability=final_result["cheat_probability"],
            detected_cheats=final_result.get("detected_cheats", []),
            recommended_action=final_result.get("recommended_action", "none"),
        )
        await fp_service.store_fingerprint(payload.player_uuid, fingerprint)

        if final_result.get("recommended_action") == "ban":
            await cs_service.register_banned_fingerprint(payload.player_uuid, fingerprint)
            for vtype in final_result.get("detected_cheats", []):
                await cs_service.record_violation(payload.player_uuid, payload.server_id, vtype)
    except Exception as exc:
        logger.warning("Cross-server/fingerprint update failed: %s", exc)

    return AnalyzeResponse(
        cheat_probability=final_result.get("cheat_probability", suspicion_score),
        detected_cheats=final_result.get("detected_cheats", tier2_result["flags"]),
        confidence=final_result.get("confidence", "low"),
        recommended_action=final_result.get("recommended_action", _score_to_action(suspicion_score)),
        reasoning=final_result.get("reasoning", ""),
        tier_used=tier_used,
        cached=final_result.get("cached", False),
    )


# ------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------

def _score_to_action(score: float) -> str:
    if score >= 0.8:
        return "ban"
    if score >= 0.6:
        return "mitigate"
    if score >= 0.4:
        return "monitor"
    return "none"


async def _upsert_player(db: AsyncSession, payload: AnalyzeRequest) -> Player:
    from sqlalchemy import select

    result = await db.execute(select(Player).where(Player.uuid == payload.player_uuid))
    player = result.scalar_one_or_none()
    now = datetime.now(timezone.utc)

    if player is None:
        player = Player(
            id=str(uuid.uuid4()),
            uuid=payload.player_uuid,
            username=payload.username or "Unknown",
            first_seen=now,
            last_seen=now,
        )
        db.add(player)
    else:
        player.last_seen = now
        if payload.username:
            player.username = payload.username
    await db.flush()
    return player


async def _save_analysis(
    db: AsyncSession,
    player: Player,
    payload: AnalyzeRequest,
    result: dict,
    tier2_result: dict,
    tier_used: int,
) -> None:
    analysis = Analysis(
        id=str(uuid.uuid4()),
        player_id=player.id,
        server_id=payload.server_id,
        cheat_probability=result.get("cheat_probability", 0.0),
        detected_cheats=result.get("detected_cheats", []),
        confidence=result.get("confidence", "low"),
        recommended_action=result.get("recommended_action", "none"),
        reasoning=result.get("reasoning", ""),
        tier_used=tier_used,
        raw_data={
            "tier2": tier2_result.get("details", {}),
            "movement_count": len(payload.movement_samples),
            "combat_count": len(payload.combat_samples),
            "click_count": len(payload.click_timestamps),
        },
    )
    db.add(analysis)

    # Bump total_flags counter when action is monitor/mitigate/ban
    if result.get("recommended_action", "none") != "none":
        player.total_flags = (player.total_flags or 0) + 1
        if result.get("recommended_action") == "ban":
            player.ban_status = "banned"
        elif result.get("recommended_action") == "mitigate":
            player.ban_status = "flagged"

    await db.flush()
