import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Integer, Text, Float, JSON, ForeignKey
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class Player(Base):
    __tablename__ = "players"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    uuid: Mapped[str] = mapped_column(String(36), unique=True, nullable=False, index=True)
    username: Mapped[str] = mapped_column(String(16), nullable=False, index=True)
    first_seen: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    last_seen: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
    total_flags: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    ban_status: Mapped[str] = mapped_column(String(20), default="clean", nullable=False)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
