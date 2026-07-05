"""
server.py - EcoPulse Climate Guide HTTP API server.

Wraps climate_agent.ask() in a simple REST endpoint that the Android app
calls instead of running Ollama directly (since Android can't easily reach
Ollama on the same machine without a network bridge).

Usage:
    # From the bot/ directory:
    python server.py

    # Or with uvicorn for production:
    uvicorn server:app --host 0.0.0.0 --port 8000

Android emulator accesses this via http://10.0.2.2:8000
Physical device on same Wi-Fi: use host machine's LAN IP (e.g. 192.168.x.x:8000)

Environment variables (same as .env):
    OLLAMA_HOST          - Ollama server (default: http://localhost:11434)
    OLLAMA_MODEL         - Custom model name (default: ecopulse-guide)
    OLLAMA_BASE_MODEL    - Base model for extraction (default: phi4-mini)
    GEMINI_API_KEY       - Optional Gemini fallback API key
    SERVER_PORT          - Port to listen on (default: 8000)
    SERVER_HOST          - Host to bind to (default: 0.0.0.0)
    ECOPULSE_REGION      - Region config to load (default: ghana)
    ECOPULSE_ADMIN_TOKEN - Secret for write-protected admin endpoints (default: unset = disabled)

ORGANIZATION DASHBOARD:
    A read-only-by-default incident dashboard lives at bot/static/index.html
    and is mounted below at GET /dashboard. It's the pull-model handoff for
    the "reports go to organizations" step of the flow: an NGO/assembly
    staffer opens /dashboard, sees submitted reports (same GET /incidents
    data the Android app's map already uses), and can advance a report's
    status using the *existing* PATCH /incidents/{id}/status endpoint —
    gated behind the same ECOPULSE_ADMIN_TOKEN that already protects it.
    This isn't a new auth system, just a UI on top of endpoints that
    already existed.
"""

import logging
import os
import sqlite3
import sys
import time
from contextlib import asynccontextmanager

# Load .env file if present (pip install python-dotenv)
try:
    from dotenv import load_dotenv
    load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
except ImportError:
    pass  # dotenv not installed - rely on shell environment

# Add bot directory to path so we can import climate_agent
sys.path.insert(0, os.path.dirname(__file__))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ecopulse_server")

try:
    from fastapi import FastAPI, HTTPException, Header, Request
    from fastapi.middleware.cors import CORSMiddleware
    from fastapi.staticfiles import StaticFiles
    from fastapi.responses import RedirectResponse
    from pydantic import BaseModel
    import uvicorn
except ImportError:
    logger.error(
        "FastAPI/uvicorn/pydantic not installed. Run:\n"
        "  pip install fastapi uvicorn pydantic\n"
        "Then restart this script."
    )
    sys.exit(1)

import climate_agent
import climate_llm

# ---------------------------------------------------------------------------
# SQLite database setup
# ---------------------------------------------------------------------------

DB_PATH = os.path.join(os.path.dirname(__file__), "ecopulse.db")


def _get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")  # safe concurrent reads
    return conn


def _init_db():
    """Create tables if they don't exist yet and seed default quiz questions."""
    with _get_db() as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS incidents (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                title         TEXT    NOT NULL,
                type          TEXT    NOT NULL,
                location      TEXT    NOT NULL,
                description   TEXT    NOT NULL,
                latitude      REAL    NOT NULL DEFAULT 0.0,
                longitude     REAL    NOT NULL DEFAULT 0.0,
                reporter_name TEXT    NOT NULL DEFAULT 'Anonymous',
                timestamp     INTEGER NOT NULL,
                status        TEXT    NOT NULL DEFAULT 'Submitted',
                upvotes       INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS profiles (
                user_id      TEXT    PRIMARY KEY,
                name         TEXT    NOT NULL DEFAULT 'Eco-Warrior',
                city         TEXT    NOT NULL DEFAULT '',
                points       INTEGER NOT NULL DEFAULT 0,
                level        INTEGER NOT NULL DEFAULT 1,
                last_updated INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS quiz_questions (
                id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                question_text        TEXT NOT NULL,
                option_a             TEXT NOT NULL,
                option_b             TEXT NOT NULL,
                option_c             TEXT NOT NULL,
                option_d             TEXT NOT NULL,
                correct_answer_index INTEGER NOT NULL,
                explanation_text     TEXT NOT NULL,
                points_reward        INTEGER NOT NULL DEFAULT 30,
                topic                TEXT NOT NULL DEFAULT ''
            );
        """)

        # Seed default quiz questions if table is empty.
        # TODO: single-source these with EcoPulseRepository.kt seed data —
        # if the app's quiz list changes, this server copy goes stale silently.
        count = conn.execute("SELECT COUNT(*) FROM quiz_questions").fetchone()[0]
        if count == 0:
            conn.executemany(
                """INSERT INTO quiz_questions
                   (question_text, option_a, option_b, option_c, option_d,
                    correct_answer_index, explanation_text, points_reward, topic)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                [
                    (
                        "Which of the following is a primary indicator of illegal "
                        "river-sand mining when inspecting high-resolution satellite imagery?",
                        "Increased growth of water hyacinth",
                        "Fresh shoreline erosion, heavy-machinery wheel ruts, and unauthorized sand-barges",
                        "Expansion of nearby residential concrete rooftops",
                        "Increased water clarity downstream",
                        1,
                        "Illegal sand mining is visually characterized by active dredging barges, "
                        "artificial sand mounds, heavy wheel scars on beaches, and dramatic "
                        "shoreline degradation over time.",
                        30,
                        "satellite,mining",
                    ),
                    (
                        "What OSINT metadata element can definitively prove a photo of "
                        "dumping was taken at the reported coordinates?",
                        "EXIF GPS metadata embedded in the image file",
                        "The brightness and contrast settings of the camera",
                        "The file format of the photo (JPEG vs PNG)",
                        "The profile name of the person who uploaded the photo",
                        0,
                        "EXIF (Exchangeable Image File Format) metadata contains precise GPS "
                        "coordinates (latitude and longitude) captured by the device's camera "
                        "sensor at the time of exposure.",
                        30,
                        "satellite,osint,dumping",
                    ),
                    (
                        "When reviewing public concessions to verify illegal logging, "
                        "which authority's records should you cross-reference?",
                        "The regional meteorological agency",
                        "The Ministry of Forestry or land registration concessions registry",
                        "The local municipal transit authority",
                        "The agricultural fertilizer distribution records",
                        1,
                        "Concession registries map out which logging companies have valid legal "
                        "permits to cut timber in specific forest compartments, allowing you to "
                        "audit whether a logging site is unauthorized.",
                        30,
                        "logging,deforestation",
                    ),
                ],
            )
            logger.info("[db] Seeded default quiz questions.")

    logger.info(f"[db] Database ready at {DB_PATH}")


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup logic — replaces the deprecated @app.on_event('startup') hook."""
    _init_db()
    reachable = climate_llm.is_configured()
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    model = os.getenv("OLLAMA_MODEL", "ecopulse-guide")
    gemini_key = os.getenv("GEMINI_API_KEY", "")
    region = os.getenv("ECOPULSE_REGION", "ghana")
    admin_token_set = bool(os.getenv("ECOPULSE_ADMIN_TOKEN", "").strip())

    logger.info("=" * 60)
    logger.info("EcoPulse Climate Guide API server started")
    logger.info(f"  Region           : {region}")
    logger.info(f"  Ollama host      : {host}")
    logger.info(f"  Ollama model     : {model}")
    logger.info(f"  Ollama ready     : {'YES \u2713' if reachable else 'NO - will use Gemini fallback'}")
    logger.info(f"  Gemini key       : {'SET \u2713' if gemini_key else 'not set'}")
    logger.info(f"  Admin token      : {'SET \u2713 (PATCH /incidents/*/status enabled)' if admin_token_set else 'NOT SET \u2014 PATCH /status is disabled (safe default)'}")
    logger.info(f"  DB path          : {DB_PATH}")
    logger.info(f"  Org dashboard    : http://localhost:{os.getenv('SERVER_PORT', 8000)}/dashboard")
    logger.info("  Android emulator endpoint : http://10.0.2.2:8000")
    logger.info("=" * 60)
    yield  # server runs


app = FastAPI(
    title="EcoPulse Climate Guide API",
    description=(
        "REST wrapper around the EcoPulse climate_agent bot. "
        "Connects to a local Ollama model (ecopulse-guide) and exposes "
        "/ask, /incidents, /leaderboard, and /profile endpoints."
    ),
    version="2.0.0",
    lifespan=lifespan,
)

# In production, set ALLOWED_ORIGINS to your specific domains/app identifiers.
# Defaults to "*" for easy local development.
allowed_origins_env = os.getenv("ALLOWED_ORIGINS", "*")
allowed_origins = [origin.strip() for origin in allowed_origins_env.split(",")] if allowed_origins_env else ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_methods=["POST", "GET", "PATCH"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Auth helper (Blocking issue B3)
# ---------------------------------------------------------------------------

_ADMIN_TOKEN = os.getenv("ECOPULSE_ADMIN_TOKEN", "").strip()


def _require_admin(x_admin_token: str | None):
    """Raise HTTP 403 if the admin token header is missing or wrong.
    If ECOPULSE_ADMIN_TOKEN env var is not set, the endpoint is disabled
    entirely (returns 403 with a clear message)."""
    if not _ADMIN_TOKEN:
        raise HTTPException(
            status_code=403,
            detail="Status updates are disabled. Set ECOPULSE_ADMIN_TOKEN in .env to enable.",
        )
    if x_admin_token != _ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid or missing X-Admin-Token header.")


# ---------------------------------------------------------------------------
# Request / Response schemas
# ---------------------------------------------------------------------------

class AskRequest(BaseModel):
    text: str
    user_id: int = 0  # per-user history key; 0 = anonymous / single session


class AskResponse(BaseModel):
    reply: str
    source: str  # "ollama" | "gemini" | "fallback"


class HealthResponse(BaseModel):
    status: str
    ollama_reachable: bool
    ollama_host: str
    model: str


class IncidentCreate(BaseModel):
    title: str
    type: str
    location: str
    description: str
    latitude: float = 0.0
    longitude: float = 0.0
    reporter_name: str = "Anonymous"


class StatusUpdate(BaseModel):
    status: str  # "Submitted" | "In Investigation" | "Verified"


class ProfileSync(BaseModel):
    user_id: str  # UUID generated client-side, stable per-install
    name: str
    city: str
    points: int
    level: int


# ---------------------------------------------------------------------------
# Core AI endpoints (unchanged from v1)
# ---------------------------------------------------------------------------

@app.get("/health", response_model=HealthResponse, summary="Health check")
def health():
    """Returns server status and whether Ollama is reachable."""
    reachable = climate_llm.is_configured()
    return HealthResponse(
        status="ok",
        ollama_reachable=reachable,
        ollama_host=os.getenv("OLLAMA_HOST", "http://localhost:11434"),
        model=os.getenv("OLLAMA_MODEL", "ecopulse-guide"),
    )


@app.post("/ask", response_model=AskResponse, summary="Ask the Climate Guide")
def ask(req: AskRequest):
    """
    Send a question to the EcoPulse Climate Guide.

    The bot:
    - Talks to the local Ollama 'ecopulse-guide' model when available
    - Falls back to Gemini API if Ollama is unreachable and GEMINI_API_KEY is set
    - Applies dual-layer safety checks (regex + classifier) to every reply
    - Maintains per-user conversation history (keyed by user_id)

    Special command: send text='clear' to wipe this user's conversation history.
    """
    if not req.text or not req.text.strip():
        raise HTTPException(status_code=400, detail="text field must not be empty")

    text = req.text.strip()
    logger.info(f"[ask] user_id={req.user_id} text={text[:60]!r}")

    try:
        reply, source = climate_agent.ask_with_source(text, user_id=req.user_id)
    except Exception as e:
        logger.exception(f"Unexpected error from climate_agent.ask: {e}")
        raise HTTPException(status_code=500, detail=f"Agent error: {e}")

    logger.info(f"[ask] reply={reply[:80]!r} source={source}")
    return AskResponse(reply=reply, source=source)


@app.post("/clear/{user_id}", summary="Clear conversation history")
def clear_history(user_id: int):
    """Wipes conversation history for the given user_id."""
    climate_agent.clear_history(user_id)
    return {"status": "cleared", "user_id": user_id}


@app.get("/weather/{location}", summary="Get weather alert for a location")
def weather_alert(location: str):
    """
    Get a real-time weather and flood hazard alert for a city in the active region,
    or a lat,lon coordinate string. Calls Open-Meteo APIs (no API key needed).
    """
    from weather import get_active_alert
    alert = get_active_alert(location)
    return {"location": location, "alert": alert}


# ---------------------------------------------------------------------------
# Incident endpoints (Task 1)
# ---------------------------------------------------------------------------

@app.post("/incidents", summary="Submit a community incident report")
def create_incident(incident: IncidentCreate):
    """
    Submit a new environmental incident report. The server assigns id, timestamp,
    and default status='Submitted'. Returns the assigned id so the client can store
    it as remoteId for future upvote/status calls.
    """
    ts = int(time.time() * 1000)  # milliseconds, consistent with Android System.currentTimeMillis()
    try:
        with _get_db() as conn:
            cur = conn.execute(
                """INSERT INTO incidents
                   (title, type, location, description, latitude, longitude,
                    reporter_name, timestamp, status, upvotes)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Submitted', 0)""",
                (
                    incident.title, incident.type, incident.location,
                    incident.description, incident.latitude, incident.longitude,
                    incident.reporter_name, ts,
                ),
            )
            new_id = cur.lastrowid
        logger.info(f"[incidents] created id={new_id} title={incident.title!r}")
        return {"id": new_id, "timestamp": ts, "status": "Submitted"}
    except Exception as e:
        logger.exception(f"[incidents] create failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


@app.get("/admin/verify", summary="Verify an admin token (no side effects)")
def verify_admin_token(x_admin_token: str | None = Header(default=None)):
    """
    Checks whether X-Admin-Token matches ECOPULSE_ADMIN_TOKEN, without
    changing anything. Exists purely so the org dashboard's login modal can
    validate a token immediately (clear success/failure) instead of only
    finding out it's wrong the first time someone clicks a status button.
    """
    _require_admin(x_admin_token)
    return {"valid": True}


@app.get("/incidents", summary="List all incident reports")
def list_incidents(since: int = 0, limit: int = 500):
    """
    Return incidents, newest first. Supports optional ?since=<timestamp_ms>
    for incremental sync — only returns incidents created after that timestamp.
    Use ?limit=N to cap rows returned (default 500, max 5000).

    This is also what the org dashboard (GET /dashboard) reads from — same
    endpoint the Android app's map already uses, no separate org-facing API.
    """
    limit = max(1, min(limit, 5000))  # clamp to [1, 5000]
    try:
        with _get_db() as conn:
            rows = conn.execute(
                "SELECT * FROM incidents WHERE timestamp > ? ORDER BY timestamp DESC LIMIT ?",
                (since, limit),
            ).fetchall()
        return {"incidents": [dict(r) for r in rows]}
    except Exception as e:
        logger.exception(f"[incidents] list failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


@app.post("/incidents/{incident_id}/upvote", summary="Upvote an incident report")
def upvote_incident(incident_id: int):
    """Increment the upvote count for an incident. Open to all app clients."""
    try:
        with _get_db() as conn:
            conn.execute(
                "UPDATE incidents SET upvotes = upvotes + 1 WHERE id = ?",
                (incident_id,),
            )
        return {"id": incident_id, "status": "upvoted"}
    except Exception as e:
        logger.exception(f"[incidents] upvote failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


@app.patch("/incidents/{incident_id}/status", summary="Update incident status (admin only)")
def update_incident_status(
    incident_id: int,
    update: StatusUpdate,
    x_admin_token: str | None = Header(default=None),
):
    """
    Update the status of an incident report (e.g. 'Verified', 'In Investigation').
    Requires X-Admin-Token header matching ECOPULSE_ADMIN_TOKEN env var.
    This endpoint is the hook for local assemblies/NGOs — it is intentionally
    not open to regular app clients to protect data integrity (Blocking issue B3).

    As of the org dashboard addition, this is the SAME endpoint the dashboard's
    "Mark In Investigation" / "Mark Verified" buttons call — no separate
    write path was added, the dashboard is just a UI on top of this.
    """
    _require_admin(x_admin_token)
    valid_statuses = {"Submitted", "In Investigation", "Verified"}
    if update.status not in valid_statuses:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid status. Must be one of: {', '.join(valid_statuses)}",
        )
    try:
        with _get_db() as conn:
            conn.execute(
                "UPDATE incidents SET status = ? WHERE id = ?",
                (update.status, incident_id),
            )
        logger.info(f"[incidents] id={incident_id} status -> {update.status!r}")
        return {"id": incident_id, "status": update.status}
    except Exception as e:
        logger.exception(f"[incidents] status update failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


# ---------------------------------------------------------------------------
# Profile & Leaderboard endpoints (Task 4)
# ---------------------------------------------------------------------------

@app.post("/profile", summary="Sync user profile to the server")
def sync_profile(profile: ProfileSync):
    """
    Upsert a user profile by stable UUID (user_id, generated client-side and
    persisted in SharedPreferences). Called whenever points are awarded in the app.
    """
    ts = int(time.time() * 1000)
    try:
        with _get_db() as conn:
            conn.execute(
                """INSERT INTO profiles (user_id, name, city, points, level, last_updated)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT(user_id) DO UPDATE SET
                       name=excluded.name,
                       city=excluded.city,
                       points=excluded.points,
                       level=excluded.level,
                       last_updated=excluded.last_updated""",
                (profile.user_id, profile.name, profile.city,
                 profile.points, profile.level, ts),
            )
        logger.info(f"[profile] synced user_id={profile.user_id!r} points={profile.points}")
        return {"status": "synced", "user_id": profile.user_id}
    except Exception as e:
        logger.exception(f"[profile] sync failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


@app.get("/leaderboard", summary="Get top users by points")
def get_leaderboard(city: str = "", limit: int = 20):
    """
    Return top N users by points, optionally filtered by city (hyperlocal).
    Supports ?city=Nairobi for city-scoped leaderboard, or no param for global.
    Each entry includes rank, user_id, name, city, points, level.
    """
    try:
        with _get_db() as conn:
            if city:
                rows = conn.execute(
                    """SELECT user_id, name, city, points, level
                       FROM profiles
                       WHERE LOWER(city) = LOWER(?)
                       ORDER BY points DESC LIMIT ?""",
                    (city, limit),
                ).fetchall()
            else:
                rows = conn.execute(
                    """SELECT user_id, name, city, points, level
                       FROM profiles
                       ORDER BY points DESC LIMIT ?""",
                    (limit,),
                ).fetchall()
        leaderboard = [
            {"rank": i + 1, **dict(r)}
            for i, r in enumerate(rows)
        ]
        return {"leaderboard": leaderboard, "city_filter": city or None}
    except Exception as e:
        logger.exception(f"[leaderboard] fetch failed: {e}")
        raise HTTPException(status_code=500, detail=f"Database error: {e}")


# ---------------------------------------------------------------------------
# Organization dashboard — static page + convenience redirect
# ---------------------------------------------------------------------------
# Mounted AFTER all API routes above so the "/incidents", "/health" etc.
# paths are matched first; StaticFiles only handles what falls under the
# /dashboard prefix.

_STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
if os.path.isdir(_STATIC_DIR):
    app.mount("/dashboard", StaticFiles(directory=_STATIC_DIR, html=True), name="dashboard")
else:
    logger.warning(f"[dashboard] static dir not found at {_STATIC_DIR} — /dashboard will 404")


@app.get("/", include_in_schema=False)
def root_redirect():
    """Convenience: opening the bare server URL in a browser goes straight
    to the org dashboard instead of a bare 404/JSON blob."""
    return RedirectResponse(url="/dashboard")




# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    port = int(os.getenv("SERVER_PORT", 8000))
    host = os.getenv("SERVER_HOST", "0.0.0.0")
    logger.info(f"Starting server on {host}:{port}")
    uvicorn.run("server:app", host=host, port=port, reload=False, log_level="info")
