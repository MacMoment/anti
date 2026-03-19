import json
import logging
from typing import Any

import anthropic

from app.ai.prompts import PROFILE_MATCHING_PROMPT, SYSTEM_PROMPT

logger = logging.getLogger(__name__)

_MATCH_SYSTEM = SYSTEM_PROMPT + """

For profile matching tasks, respond ONLY with valid JSON:
{
  "similarity_score": 0.0,
  "likely_same_player": false,
  "confidence": "low|medium|high",
  "matching_features": [],
  "differing_features": [],
  "reasoning": "explanation"
}"""


async def match_profiles(
    client: anthropic.AsyncAnthropic,
    profile1: dict[str, Any],
    profile2: dict[str, Any],
    model: str,
) -> dict[str, Any]:
    """Compare two behavioral fingerprints to detect alt accounts."""
    prompt = PROFILE_MATCHING_PROMPT.format(
        profile_a=json.dumps(profile1, indent=2),
        profile_b=json.dumps(profile2, indent=2),
    )

    try:
        message = await client.messages.create(
            model=model,
            max_tokens=512,
            system=_MATCH_SYSTEM,
            messages=[{"role": "user", "content": prompt}],
        )
        raw = message.content[0].text.strip()
        result = json.loads(raw)
        logger.debug("profile_matching result: %s", result)
        return result
    except json.JSONDecodeError as exc:
        logger.warning("Claude returned non-JSON profile match result: %s", exc)
        return {
            "similarity_score": 0.0,
            "likely_same_player": False,
            "confidence": "low",
            "matching_features": [],
            "differing_features": [],
            "reasoning": "AI parse error; could not determine similarity.",
        }
    except anthropic.APIError as exc:
        logger.error("Anthropic API error in profile_matching: %s", exc)
        raise
