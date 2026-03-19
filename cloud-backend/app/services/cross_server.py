"""
Cross-server tracking service.

Tracks player activity across multiple game servers, aggregates violations,
and detects ban evasion (same player signing on under a different UUID after
a ban, identified via behavioral fingerprint similarity).
"""
import json
import logging
from datetime import datetime, timezone
from typing import Any

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

_SERVER_ACTIVITY_TTL = 60 * 60 * 24 * 30   # 30 days
_BAN_EVADE_SIMILARITY = 0.78                 # fingerprint similarity to flag as same player


def _activity_key(player_uuid: str) -> str:
    return f"cross_server:activity:{player_uuid}"


def _violation_key(player_uuid: str) -> str:
    return f"cross_server:violations:{player_uuid}"


def _banned_fingerprints_key() -> str:
    return "cross_server:banned_fps"


class CrossServerService:
    def __init__(self, redis_client: aioredis.Redis | None = None) -> None:
        self._redis = redis_client

    # ------------------------------------------------------------------
    # Activity tracking
    # ------------------------------------------------------------------

    async def record_activity(
        self,
        player_uuid: str,
        server_id: str,
        cheat_probability: float,
        detected_cheats: list[str],
        recommended_action: str,
    ) -> None:
        """Append a server activity record for a player."""
        if not self._redis:
            return

        entry = {
            "server_id": server_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "cheat_probability": cheat_probability,
            "detected_cheats": detected_cheats,
            "recommended_action": recommended_action,
        }

        try:
            key = _activity_key(player_uuid)
            await self._redis.rpush(key, json.dumps(entry))
            await self._redis.expire(key, _SERVER_ACTIVITY_TTL)
            await self._redis.ltrim(key, -200, -1)  # keep last 200 records
        except Exception as exc:
            logger.warning("Failed to record cross-server activity: %s", exc)

    async def get_activity(self, player_uuid: str) -> list[dict[str, Any]]:
        """Return all recorded cross-server activity for a player."""
        if not self._redis:
            return []
        try:
            raw_list = await self._redis.lrange(_activity_key(player_uuid), 0, -1)
            return [json.loads(r) for r in raw_list]
        except Exception as exc:
            logger.warning("Failed to get cross-server activity: %s", exc)
            return []

    # ------------------------------------------------------------------
    # Aggregated violation history
    # ------------------------------------------------------------------

    async def record_violation(
        self,
        player_uuid: str,
        server_id: str,
        violation_type: str,
    ) -> None:
        """Append a violation record for cross-server analysis."""
        if not self._redis:
            return
        entry = {
            "server_id": server_id,
            "type": violation_type.upper(),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        try:
            key = _violation_key(player_uuid)
            await self._redis.rpush(key, json.dumps(entry))
            await self._redis.expire(key, _SERVER_ACTIVITY_TTL)
            await self._redis.ltrim(key, -500, -1)
        except Exception as exc:
            logger.warning("Failed to record violation: %s", exc)

    async def get_aggregated_violations(self, player_uuid: str) -> dict[str, Any]:
        """Aggregate all violations across all servers for a player."""
        records = []
        if self._redis:
            try:
                raw_list = await self._redis.lrange(_violation_key(player_uuid), 0, -1)
                records = [json.loads(r) for r in raw_list]
            except Exception as exc:
                logger.warning("Failed to retrieve violations: %s", exc)

        server_counts: dict[str, int] = {}
        type_counts: dict[str, int] = {}

        for rec in records:
            server_counts[rec["server_id"]] = server_counts.get(rec["server_id"], 0) + 1
            type_counts[rec["type"]] = type_counts.get(rec["type"], 0) + 1

        return {
            "total_violations": len(records),
            "servers_with_violations": len(server_counts),
            "per_server_counts": server_counts,
            "per_type_counts": type_counts,
            "recent": records[-10:],
        }

    # ------------------------------------------------------------------
    # Ban evasion detection
    # ------------------------------------------------------------------

    async def register_banned_fingerprint(
        self, player_uuid: str, fingerprint: dict[str, Any]
    ) -> None:
        """Store a banned player's fingerprint for future evasion detection."""
        if not self._redis:
            return
        try:
            entry = json.dumps({"uuid": player_uuid, "fingerprint": fingerprint})
            await self._redis.hset(_banned_fingerprints_key(), player_uuid, entry)
        except Exception as exc:
            logger.warning("Failed to register banned fingerprint: %s", exc)

    async def check_ban_evasion(
        self,
        candidate_uuid: str,
        candidate_fingerprint: dict[str, Any],
        fingerprint_service: Any,  # FingerprintService, avoid circular import
    ) -> dict[str, Any]:
        """
        Compare a new player's fingerprint against all banned fingerprints.
        Returns the best match if similarity exceeds the threshold.
        """
        if not self._redis:
            return {"evading": False}

        try:
            all_entries = await self._redis.hgetall(_banned_fingerprints_key())
        except Exception as exc:
            logger.warning("Failed to fetch banned fingerprints: %s", exc)
            return {"evading": False}

        best_match: dict[str, Any] | None = None
        best_score = 0.0

        for raw_entry in all_entries.values():
            try:
                entry = json.loads(raw_entry)
                banned_uuid = entry["uuid"]
                if banned_uuid == candidate_uuid:
                    continue
                comparison = fingerprint_service.compare(
                    candidate_fingerprint, entry["fingerprint"]
                )
                sim = comparison["similarity_score"]
                if sim > best_score:
                    best_score = sim
                    best_match = {
                        "banned_uuid": banned_uuid,
                        "similarity_score": sim,
                        "component_scores": comparison.get("component_scores", {}),
                    }
            except (json.JSONDecodeError, KeyError):
                continue

        if best_match and best_score >= _BAN_EVADE_SIMILARITY:
            logger.warning(
                "Ban evasion detected: %s resembles banned %s (sim=%.3f)",
                candidate_uuid,
                best_match["banned_uuid"],
                best_score,
            )
            return {
                "evading": True,
                "match": best_match,
            }

        return {"evading": False}

    async def get_servers_for_player(self, player_uuid: str) -> list[str]:
        """Return a deduplicated list of servers where this player has been seen."""
        activity = await self.get_activity(player_uuid)
        seen: list[str] = []
        for entry in activity:
            sid = entry.get("server_id", "")
            if sid and sid not in seen:
                seen.append(sid)
        return seen
