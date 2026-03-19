# anti

> A Polar-style Minecraft anticheat with Claude AI integration — silent, smart, blue.

## Overview

**anti** is a production-grade Minecraft anticheat system that combines real-time local checks with a cloud-based AI backend powered by Anthropic Claude. It uses a **mitigation-first approach**: silently nerfing cheaters rather than immediately banning them, reducing false positives and keeping the gameplay experience smooth.

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Minecraft Server                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │              anticheat-plugin (Java)              │   │
│  │  ProtocolLib ──► PacketListener ──► CheckManager  │   │
│  │  17 checks: movement, combat, player, packet      │   │
│  │  MitigationEngine ──► silent nerfs               │   │
│  │  CloudConnector ──► async batch sync every 30s   │   │
│  └─────────────────────┬────────────────────────────┘   │
└────────────────────────┼────────────────────────────────┘
                         │ HTTPS + HMAC-SHA256
                         ▼
┌─────────────────────────────────────────────────────────┐
│               cloud-backend (Python/FastAPI)             │
│  Tier 1: Plugin local checks (no API cost)               │
│  Tier 2: Statistical analysis — numpy/scipy              │
│  Tier 3: Claude AI — only for ambiguous cases (<5%)      │
│  PostgreSQL ──► audit log + player profiles              │
│  Redis ──► caching + rate limiting + cross-server        │
└─────────────────────────────────────────────────────────┘
```

---

## Components

### 1. Minecraft Plugin (`anticheat-plugin/`)

**Java 17+ | Paper 1.20.4 | ProtocolLib | Gradle**

#### Checks Implemented

| Category | Check | Description |
|---|---|---|
| Movement | SpeedCheck | Horizontal speed vs physics-predicted max |
| Movement | FlyCheck | Sustained upward movement without ground contact |
| Movement | NoFallCheck | Missing fall damage when Y-delta demands it |
| Movement | StepCheck | Illegal step height (> 0.6 blocks) |
| Movement | PhaseCheck | Positions inside solid blocks |
| Movement | VelocityCheck | Knockback/velocity cancellation |
| Combat | ReachCheck | Attack distance > 3.1 blocks (with lag comp) |
| Combat | KillAuraCheck | Multi-target aura / attacking behind |
| Combat | AimbotCheck | GCD rotation analysis, snap detection |
| Combat | AutoClickerCheck | CPS stddev, entropy, kurtosis analysis |
| Combat | CriticalCheck | Forced critical hits without proper jump state |
| Player | NoSlowCheck | Missing slowdown while eating/blocking |
| Player | ScaffoldCheck | Impossible block placement patterns |
| Player | TimerCheck | Too many movement packets per second |
| Player | FastUseCheck | Eating/placing faster than vanilla allows |
| Packet | BadPacketsCheck | Invalid / impossible packet states |
| Packet | InvalidRotationCheck | Pitch outside ±90°, NaN/Infinity rotations |

#### Mitigation Actions

| Action | Behavior |
|---|---|
| `MONITOR` | Increase data sampling, flag for cloud analysis |
| `RUBBERBAND` | Teleport back to last valid position |
| `LIMIT_REACH` | Cancel server-side attacks beyond vanilla reach |
| `LIMIT_CPS` | Rate-limit attack packets server-side |
| `SLOW_MOVEMENT` | Cap movement speed silently |
| `FLAG_FOR_BAN` | Queue for manual review or auto-ban |

#### Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/ac alerts` | `anticheat.alerts` | Toggle alert display |
| `/ac status <player>` | `anticheat.staff` | View violation levels |
| `/ac profile <player>` | `anticheat.admin` | Request AI analysis |
| `/ac mitigate <player> <action>` | `anticheat.admin` | Apply mitigation manually |
| `/ac reload` | `anticheat.admin` | Reload configuration |
| `/alerts` | `anticheat.alerts` | Toggle alerts (shorthand) |

#### Building the Plugin

**Prerequisites:** Java 17+, Gradle (or use `./gradlew`)

```bash
cd anticheat-plugin
./gradlew shadowJar
# Output: build/libs/anticheat-plugin-1.0.0.jar
```

#### Installation

1. Copy `anticheat-plugin-1.0.0.jar` to your server's `plugins/` folder.
2. Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (required dependency).
3. Start the server — `plugins/anti/config.yml` is generated automatically.
4. Edit `config.yml` to set your cloud API URL and API key.
5. Restart or `/ac reload`.

---

### 2. Cloud Backend (`cloud-backend/`)

**Python 3.11+ | FastAPI | PostgreSQL | Redis | Claude AI**

#### Tiered Analysis System

```
Player data arrives ──► Tier 2: Statistical analysis (free)
                             │
                    suspicious? ──► Tier 3: Claude AI analysis
                             │
                    result ──► cache in Redis ──► store in DB
                             │
                    ◄── AnalyzeResponse (cheat_probability, action)
```

- **Tier 2** runs numpy/scipy statistical checks on all players — no API cost.
- **Tier 3 (Claude)** only triggers when Tier 2 score exceeds the threshold (default 0.6), targeting **< 5% of players**.
- Results are cached in Redis for 5 minutes — repeated requests for the same player reuse the cached result.
- `claude-sonnet` is used for routine analysis; `claude-opus` is reserved for complex/ambiguous cases.

#### API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/analyze` | Submit player data for analysis |
| `GET` | `/health` | Health check with DB + Redis status |
| `GET` | `/players/{uuid}` | Player profile |
| `GET` | `/players/{uuid}/analyses` | Analysis history (paginated) |
| `GET` | `/players/search?q=` | Search by username |

#### Security

- **API Key** authentication via `X-API-Key` header.
- **HMAC-SHA256** request signing: `HMAC(request_body + X-Timestamp, API_SECRET_KEY)` — prevents replay attacks.
- Rate limiting: 100 requests/minute per IP (configurable).
- All data encrypted in transit (HTTPS).
- No PII beyond Minecraft UUIDs stored.

#### Deploying with Docker

```bash
cd cloud-backend

# 1. Create .env file
cat > .env << EOF
ANTHROPIC_API_KEY=sk-ant-...
API_SECRET_KEY=your-random-256-bit-secret
EOF

# 2. Start all services
docker-compose up -d

# API available at: http://localhost:8000
# Docs at: http://localhost:8000/docs
```

**Services started:**
- `api` — FastAPI on port 8000
- `db` — PostgreSQL 16
- `redis` — Redis 7

#### Local Development

```bash
cd cloud-backend
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Set environment variables
export DATABASE_URL="postgresql+asyncpg://anti:anti@localhost:5432/anticheat"
export REDIS_URL="redis://localhost:6379"
export ANTHROPIC_API_KEY="sk-ant-..."
export API_SECRET_KEY="dev-secret"

uvicorn app.main:app --reload
```

---

## Configuration Reference

### Plugin (`config.yml`)

```yaml
cloud:
  enabled: true
  api-url: "https://your-anticheat-api.com"
  api-key: "your-api-key-here"
  sync-interval-seconds: 30

checks:
  speed:
    enabled: true
    max-violations: 20
    decay-rate: 0.95
  fly:
    enabled: true
    max-violations: 10
    decay-rate: 0.90
  # ... (see full config.yml in plugin resources)

mitigation:
  enabled: true
  silent-mode: true

alerts:
  enabled: true
  min-violation-level: 5
  staff-permission: "anticheat.alerts"
  discord-webhook: ""
```

### Backend (`.env`)

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | — | PostgreSQL async connection string |
| `REDIS_URL` | — | Redis connection string |
| `ANTHROPIC_API_KEY` | — | Anthropic API key |
| `API_SECRET_KEY` | — | HMAC signing secret (shared with plugin) |
| `DEBUG` | `false` | Enable debug logging |
| `RATE_LIMIT_PER_MINUTE` | `100` | API rate limit |
| `AI_TIER3_THRESHOLD` | `0.6` | Suspicion score to escalate to AI |
| `CACHE_TTL_SECONDS` | `300` | Redis cache TTL for AI results |

---

## Performance

- Plugin overhead: **< 1ms per tick** — all cloud calls are async.
- Supports **500+ concurrent players** with local checks only.
- Cloud backend: **100+ requests/second** (horizontal scaling via Docker).
- AI costs controlled by tiered analysis — Claude only called for **< 5% of players**.

---

## License

MIT