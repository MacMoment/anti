import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Float, JSON, Integer, Text, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column

from app.models.player import Base


class Analysis(Base):
    __tablename__ = "analyses"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    player_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("players.id", ondelete="CASCADE"), nullable=False, index=True
    )
    server_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    cheat_probability: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    detected_cheats: Mapped[list | None] = mapped_column(JSON, nullable=True)
    confidence: Mapped[str] = mapped_column(String(10), nullable=False, default="low")
    recommended_action: Mapped[str] = mapped_column(String(20), nullable=False, default="none")
    reasoning: Mapped[str | None] = mapped_column(Text, nullable=True)
    tier_used: Mapped[int] = mapped_column(Integer, nullable=False, default=2)
    raw_data: Mapped[dict | None] = mapped_column(JSON, nullable=True)
