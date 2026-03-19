SYSTEM_PROMPT = """You are an expert Minecraft anticheat analyst with deep knowledge of:
- Vanilla Minecraft physics and movement mechanics (1.8 through 1.20+)
- Common cheat clients: Wurst, Future, Rise, Vape, Meteor, Aristois, LiquidBounce, Sigma
- Statistical analysis of player behavior data
- False positive scenarios: high ping (>150ms), lag spikes, rubber-banding, legitimate PvP techniques
  (butterfly clicking, jitter clicking, drag clicking), speedbridging, parkour

Analyze the provided player data and respond ONLY with valid JSON in this exact format:
{
  "cheat_probability": 0.0,
  "detected_cheats": [],
  "confidence": "low|medium|high",
  "recommended_action": "none|monitor|mitigate|ban",
  "reasoning": "detailed explanation"
}

Rules:
- cheat_probability: float 0.0-1.0 (0=clean, 1=definitely cheating)
- detected_cheats: list of strings from [killaura, aimbot, reach, speed, fly, autoclicker, timer, scaffold, nofall, bhop]
- confidence: based on data quality and consistency of signals
- recommended_action: none (<0.4), monitor (0.4-0.6), mitigate (0.6-0.8), ban (>0.8)
- reasoning: 2-4 sentences explaining the key signals and your conclusion
Do NOT include markdown, code fences, or any text outside the JSON object."""

CLICK_ANALYSIS_PROMPT = """Analyze these click timestamps (Unix milliseconds) for autoclicker detection.

Statistical summary provided:
{stats}

Raw timestamps (last {count} clicks):
{timestamps}

Consider:
- CPS distribution: vanilla players typically click 4-14 CPS; autoclickers often show 16-25+ CPS
- Interval stddev: legitimate clicking has stddev >15ms; perfect autoclickers <5ms
- Kurtosis/skewness: human clicking is right-skewed with positive kurtosis
- Shannon entropy of inter-click intervals: low entropy (<2.0 bits) suggests automation
- Butterfly clicking: two fingers alternating, produces ~15-25 CPS with bimodal interval distribution
- Jitter clicking: rapid vibrations, intervals cluster near 50ms with high variance
- Drag clicking: long sustained high-CPS streaks (20-30+ CPS), unique friction signature
- Autocorrelation at lag-1: strong positive correlation suggests timer-based automation

Respond with the standard JSON format."""

AIM_ANALYSIS_PROMPT = """Analyze these rotation time series for aimbot/killaura detection.

Combat samples and rotation analysis:
{combat_data}

GCD Analysis:
{gcd_data}

Consider:
- GCD (Greatest Common Divisor) of rotation deltas: mouse sensitivity produces consistent GCD patterns;
  aimbots often have GCD near 0 or anomalously large values
- Snap aiming: yaw/pitch changes >45 degrees in a single tick (50ms) without proportional mouse movement
- Smooth tracking: impossibly consistent angular velocity toward moving targets
- Rotation during attacks: legitimate players often have slight deviation; aimbots lock precisely
- Hit rate at distance: >85% hit rate beyond 3.5 blocks is highly suspicious
- Pitch manipulation: aimbot clients often show pitch stuck near 0 degrees during combat
- Yaw oscillation: killaura rotates through multiple targets, creating distinctive oscillation patterns
- Lock-on behavior: target switching with instant lock in <1 tick

Respond with the standard JSON format."""

MOVEMENT_ANALYSIS_PROMPT = """Analyze these movement positions for movement hacks (speed, fly, bhop, timer).

Movement samples and velocity analysis:
{movement_data}

Statistical summary:
{stats}

Consider:
- Vanilla speed: walking 4.317 m/s, sprinting 5.612 m/s, sprinting+jump 7.127 m/s (brief)
- Speed violations: horizontal velocity consistently >6.0 m/s indicates speed hacks
- Flight detection: Y-axis velocity that defies gravity (9.8 m/s² downward acceleration),
  sustained elevation without jump/elytra
- Bunny hop: perfect sprint-jump timing every 12-13 ticks, speed maintained through jumps
- Timer hack: packet rate manipulation creating more movement ticks per second than server expects
- No-clip/phase: position changes that pass through blocks (requires block data correlation)
- Step height: vertical jumps >0.6 blocks without legitimate mechanics
- Ground detection: on_ground=true while Y-velocity indicates falling

Respond with the standard JSON format."""

PROFILE_MATCHING_PROMPT = """Compare these two behavioral fingerprints to determine if they are likely the same player.

Profile A:
{profile_a}

Profile B:
{profile_b}

Analyze these behavioral dimensions:
- Click rhythm: average CPS, interval distribution shape, clicking style (butterfly/jitter/normal)
- Combat behavior: preferred fight distance, hit rate, target switching speed
- Movement style: average sprint speed, jump frequency, strafe patterns
- Session patterns: typical session duration, active hours, server preferences
- Violation patterns: types of flags, frequency, progression over time

Respond ONLY with valid JSON:
{
  "similarity_score": 0.0,
  "likely_same_player": false,
  "confidence": "low|medium|high",
  "matching_features": [],
  "differing_features": [],
  "reasoning": "explanation"
}"""
