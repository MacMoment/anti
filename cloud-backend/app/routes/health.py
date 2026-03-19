import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db

logger = logging.getLogger(__name__)
router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check(db: AsyncSession = Depends(get_db)):
    """Return service health including DB and Redis connectivity."""
    from app.main import redis_client  # imported lazily to avoid circular at module load

    db_ok = False
    redis_ok = False

    try:
        await db.execute(text("SELECT 1"))
        db_ok = True
    except Exception as exc:
        logger.warning("Health check — DB unavailable: %s", exc)

    try:
        if redis_client:
            await redis_client.ping()
            redis_ok = True
    except Exception as exc:
        logger.warning("Health check — Redis unavailable: %s", exc)

    status = "healthy" if (db_ok and redis_ok) else "degraded"

    return {
        "status": status,
        "db": "ok" if db_ok else "unavailable",
        "redis": "ok" if redis_ok else "unavailable",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
