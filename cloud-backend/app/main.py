import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

import anthropic
import redis.asyncio as aioredis
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

from app.config import settings
from app.db.database import create_all_tables
from app.routes import analyze, health, players

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Shared clients (populated during lifespan)
# ---------------------------------------------------------------------------
redis_client: aioredis.Redis | None = None
anthropic_client: anthropic.AsyncAnthropic | None = None

limiter = Limiter(key_func=get_remote_address)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    global redis_client, anthropic_client

    # Initialise DB
    try:
        await create_all_tables()
        logger.info("Database ready")
    except Exception as exc:
        logger.error("Database init failed: %s", exc)

    # Initialise Redis
    try:
        redis_client = aioredis.from_url(
            settings.REDIS_URL,
            encoding="utf-8",
            decode_responses=True,
        )
        await redis_client.ping()
        logger.info("Redis connected: %s", settings.REDIS_URL)
    except Exception as exc:
        logger.warning("Redis unavailable (non-fatal): %s", exc)
        redis_client = None

    # Initialise Anthropic client
    if settings.ANTHROPIC_API_KEY:
        anthropic_client = anthropic.AsyncAnthropic(api_key=settings.ANTHROPIC_API_KEY)
        logger.info("Anthropic client ready")
    else:
        logger.warning("ANTHROPIC_API_KEY not set — AI Tier 3 analysis disabled")

    yield

    # Cleanup
    if redis_client:
        await redis_client.aclose()
        logger.info("Redis connection closed")


# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Minecraft Anticheat Cloud Backend",
    description="AI-powered anticheat analysis using Claude",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url="/redoc" if settings.DEBUG else None,
)

# Rate limiting
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(health.router)
app.include_router(analyze.router)
app.include_router(players.router)


# ---------------------------------------------------------------------------
# Global exception handler
# ---------------------------------------------------------------------------
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.error("Unhandled exception on %s %s: %s", request.method, request.url, exc, exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error"},
    )
