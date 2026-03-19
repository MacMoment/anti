from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class MovementSample(BaseModel):
    x: float
    y: float
    z: float
    yaw: float = Field(ge=-180.0, le=180.0)
    pitch: float = Field(ge=-90.0, le=90.0)
    on_ground: bool
    timestamp: int = Field(description="Unix millisecond timestamp")


class CombatSample(BaseModel):
    target_uuid: str
    hit: bool
    distance: float = Field(ge=0.0)
    yaw_to_target: float
    pitch_to_target: float
    timestamp: int = Field(description="Unix millisecond timestamp")


class AnalyzeRequest(BaseModel):
    player_uuid: str = Field(min_length=36, max_length=36)
    server_id: str = Field(min_length=1, max_length=64)
    movement_samples: list[MovementSample] = Field(default_factory=list)
    combat_samples: list[CombatSample] = Field(default_factory=list)
    click_timestamps: list[int] = Field(default_factory=list)
    violation_history: list[str] = Field(default_factory=list)
    session_duration: int = Field(ge=0, description="Session duration in seconds")
    username: Optional[str] = Field(default=None, max_length=16)

    @field_validator("player_uuid")
    @classmethod
    def validate_uuid_format(cls, v: str) -> str:
        import re
        pattern = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        if not re.match(pattern, v.lower()):
            raise ValueError("player_uuid must be a valid UUID v4 string")
        return v.lower()


class AnalyzeResponse(BaseModel):
    cheat_probability: float = Field(ge=0.0, le=1.0)
    detected_cheats: list[str]
    confidence: str = Field(pattern="^(low|medium|high)$")
    recommended_action: str = Field(pattern="^(none|monitor|mitigate|ban)$")
    reasoning: str
    tier_used: int
    cached: bool = False


class PlayerProfile(BaseModel):
    uuid: str
    username: str
    first_seen: datetime
    last_seen: datetime
    total_flags: int
    ban_status: str

    model_config = {"from_attributes": True}


class AnalysisSummary(BaseModel):
    id: str
    server_id: str
    created_at: datetime
    cheat_probability: float
    detected_cheats: list[str] | None
    confidence: str
    recommended_action: str
    tier_used: int

    model_config = {"from_attributes": True}


class PlayerWithHistory(BaseModel):
    profile: PlayerProfile
    recent_analyses: list[AnalysisSummary]
    analysis_count: int


class PaginatedAnalyses(BaseModel):
    items: list[AnalysisSummary]
    total: int
    page: int
    page_size: int
    pages: int
