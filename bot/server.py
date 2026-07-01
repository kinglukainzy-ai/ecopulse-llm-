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
    OLLAMA_HOST        - Ollama server (default: http://localhost:11434)
    OLLAMA_MODEL       - Custom model name (default: ecopulse-guide)
    OLLAMA_BASE_MODEL  - Base model for extraction (default: phi4-mini)
    GEMINI_API_KEY     - Optional Gemini fallback API key
    SERVER_PORT        - Port to listen on (default: 8000)
    SERVER_HOST        - Host to bind to (default: 0.0.0.0)
"""

import logging
import os
import sys

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
    from fastapi import FastAPI, HTTPException
    from fastapi.middleware.cors import CORSMiddleware
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
app = FastAPI(
    title="EcoPulse Climate Guide API",
    description=(
        "REST wrapper around the EcoPulse climate_agent bot. "
        "Connects to a local Ollama model (ecopulse-guide) and exposes "
        "a single /ask endpoint for the Android app."
    ),
    version="1.0.0",
)

# In production, set ALLOWED_ORIGINS to your specific domains/app identifiers.
# Defaults to "*" for easy local development.
allowed_origins_env = os.getenv("ALLOWED_ORIGINS", "*")
allowed_origins = [origin.strip() for origin in allowed_origins_env.split(",")] if allowed_origins_env else ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

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


# ---------------------------------------------------------------------------
# Endpoints
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
        reply = climate_agent.ask(text, user_id=req.user_id)
    except Exception as e:
        logger.exception(f"Unexpected error from climate_agent.ask: {e}")
        raise HTTPException(status_code=500, detail=f"Agent error: {e}")

    # Determine which backend was actually used (best-effort)
    source = "ollama" if climate_llm.is_configured() else (
        "gemini" if os.getenv("GEMINI_API_KEY") else "fallback"
    )

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
    Get a real-time weather and flood hazard alert for a Ghana city or lat,lon.
    Calls Open-Meteo APIs directly (no API key needed).
    """
    from weather import get_active_alert
    alert = get_active_alert(location)
    return {"location": location, "alert": alert}


# ---------------------------------------------------------------------------
# Startup log
# ---------------------------------------------------------------------------

@app.on_event("startup")
def on_startup():
    reachable = climate_llm.is_configured()
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    model = os.getenv("OLLAMA_MODEL", "ecopulse-guide")
    gemini_key = os.getenv("GEMINI_API_KEY", "")

    logger.info("=" * 60)
    logger.info("EcoPulse Climate Guide API server started")
    logger.info(f"  Ollama host  : {host}")
    logger.info(f"  Ollama model : {model}")
    logger.info(f"  Ollama ready : {'YES ✓' if reachable else 'NO - will use Gemini fallback'}")
    logger.info(f"  Gemini key   : {'SET ✓' if gemini_key else 'not set'}")
    logger.info("  Android emulator endpoint : http://10.0.2.2:8000")
    logger.info("=" * 60)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    port = int(os.getenv("SERVER_PORT", 8000))
    host = os.getenv("SERVER_HOST", "0.0.0.0")
    logger.info(f"Starting server on {host}:{port}")
    uvicorn.run("server:app", host=host, port=port, reload=False, log_level="info")
