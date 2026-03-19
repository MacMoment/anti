import logging
import math

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.models.analysis import Analysis
from app.models.player import Player
from app.models.schemas import (
    AnalysisSummary,
    PaginatedAnalyses,
    PlayerProfile,
    PlayerWithHistory,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/players", tags=["players"])


@router.get("/{uuid}", response_model=PlayerWithHistory)
async def get_player(uuid: str, db: AsyncSession = Depends(get_db)):
    """Return a player's profile and their 10 most recent analyses."""
    player = await _get_player_or_404(db, uuid)

    recent_result = await db.execute(
        select(Analysis)
        .where(Analysis.player_id == player.id)
        .order_by(Analysis.created_at.desc())
        .limit(10)
    )
    recent = recent_result.scalars().all()

    count_result = await db.execute(
        select(func.count()).select_from(Analysis).where(Analysis.player_id == player.id)
    )
    total = count_result.scalar_one()

    return PlayerWithHistory(
        profile=PlayerProfile.model_validate(player),
        recent_analyses=[_to_summary(a) for a in recent],
        analysis_count=total,
    )


@router.get("/{uuid}/analyses", response_model=PaginatedAnalyses)
async def list_player_analyses(
    uuid: str,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
):
    """Paginated list of analyses for a player."""
    player = await _get_player_or_404(db, uuid)

    count_result = await db.execute(
        select(func.count()).select_from(Analysis).where(Analysis.player_id == player.id)
    )
    total = count_result.scalar_one()
    pages = max(1, math.ceil(total / page_size))

    offset = (page - 1) * page_size
    result = await db.execute(
        select(Analysis)
        .where(Analysis.player_id == player.id)
        .order_by(Analysis.created_at.desc())
        .limit(page_size)
        .offset(offset)
    )
    analyses = result.scalars().all()

    return PaginatedAnalyses(
        items=[_to_summary(a) for a in analyses],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages,
    )


@router.get("/search", response_model=list[PlayerProfile])
async def search_players(
    q: str = Query(min_length=2, max_length=32),
    limit: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
):
    """Search players by username (case-insensitive prefix match)."""
    result = await db.execute(
        select(Player)
        .where(Player.username.ilike(f"%{q}%"))
        .order_by(Player.last_seen.desc())
        .limit(limit)
    )
    players = result.scalars().all()
    return [PlayerProfile.model_validate(p) for p in players]


# ------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------

async def _get_player_or_404(db: AsyncSession, uuid: str) -> Player:
    result = await db.execute(select(Player).where(Player.uuid == uuid))
    player = result.scalar_one_or_none()
    if player is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Player not found")
    return player


def _to_summary(a: Analysis) -> AnalysisSummary:
    return AnalysisSummary(
        id=a.id,
        server_id=a.server_id,
        created_at=a.created_at,
        cheat_probability=a.cheat_probability,
        detected_cheats=a.detected_cheats or [],
        confidence=a.confidence,
        recommended_action=a.recommended_action,
        tier_used=a.tier_used,
    )
