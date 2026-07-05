#!/usr/bin/env bash
#
# setup.sh — EcoPulse backend installer.
#
# Goal: running this once on a fresh clone should leave you with a fully
# working, verified backend — not just "dependencies installed," but an
# actual proof that the server boots and answers requests. Specifically:
#
#   1. System prerequisites (curl, python3-venv-equivalent) — detected and,
#      with confirmation, installed via whichever package manager exists
#      (dnf/apt/pacman/brew)
#   2. Python version check — this codebase uses `str | None` union syntax
#      directly (no `from __future__ import annotations`), which needs
#      Python >= 3.10 at runtime, not just under a type checker
#   3. Virtual environment + pip install -r requirements.txt
#   4. .env generation from env.example, with a REAL random admin token
#      generated fresh (not the placeholder committed in env.example)
#   5. SQLite database initialization — creates ecopulse.db with all
#      tables and seeded quiz questions, by calling the server's own
#      _init_db() directly, so the DB exists and is verified *before* you
#      ever run `python server.py` for the first time
#   6. Ollama install (if missing) + daemon start + model pull + build the
#      custom `ecopulse-guide` model from Modelfile.ecopulse
#   7. A REAL live-boot smoke test: starts the actual server as a
#      background process, polls /health and /dashboard until they
#      respond, then shuts it down cleanly — proves the whole stack
#      actually works end to end, not just that imports succeed
#
# Safe to re-run at any point: every step checks "is this already done?"
# before doing it. A partial failure halfway through won't redo finished
# work, and re-running won't regenerate your admin token or wipe your
# database (use --force-env / --reset-db to force those specifically).
#
# USAGE:
#   ./setup.sh                          # ghana region, interactive, full install
#   ./setup.sh --region kenya
#   ./setup.sh --skip-ollama            # Gemini-only setup, no local model
#   ./setup.sh --gemini-key sk-...      # also wire up Gemini fallback
#   ./setup.sh --reset-db               # wipe and recreate ecopulse.db
#   ./setup.sh -y                       # non-interactive (assume yes everywhere)
#   ./setup.sh --with-benchmark-models  # also pull the models benchmark.py compares
#
# Run from anywhere — the script locates bot/ as the directory it lives
# in and operates relative to that, so cd'ing into bot/ first isn't required.

set -uo pipefail   # deliberately NOT -e — see note above main(), errors are
                    # checked explicitly so one failed optional step (e.g. an
                    # apt-get install) doesn't nuke the whole run via a stray
                    # nonzero exit somewhere unrelated. Every step that MUST
                    # succeed calls fail() itself on error.

# ---------------------------------------------------------------------------
# Paths, flags, colors
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BOT_DIR="$SCRIPT_DIR"
VENV_DIR="$BOT_DIR/venv"
ENV_PATH="$BOT_DIR/.env"
DB_PATH="$BOT_DIR/ecopulse.db"
OLLAMA_MIN_WAIT_SECS=30
SERVER_BOOT_WAIT_SECS=15

REGION="ghana"
GEMINI_KEY=""
ADMIN_TOKEN=""
SKIP_OLLAMA=false
SKIP_MODEL_PULL=false
NO_VENV=false
FORCE_ENV=false
RESET_DB=false
NON_INTERACTIVE=false
WITH_BENCHMARK_MODELS=false
SKIP_BOOT_TEST=false

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_BLUE=$'\033[34m'
else
  C_RESET=""; C_BOLD=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_BLUE=""
fi

log()   { echo "${C_BLUE}==>${C_RESET} ${C_BOLD}$*${C_RESET}"; }
ok()    { echo "${C_GREEN}  ✓${C_RESET} $*"; }
warn()  { echo "${C_YELLOW}  ! $*${C_RESET}"; }
fail()  { echo "${C_RED}  ✗ $*${C_RESET}" >&2; cleanup_test_server; exit 1; }

confirm() {
  $NON_INTERACTIVE && return 0
  read -r -p "$1 [Y/n] " reply
  [[ -z "$reply" || "$reply" =~ ^[Yy] ]]
}

# ---------------------------------------------------------------------------
# Arg parsing
# ---------------------------------------------------------------------------

print_help() {
  sed -n '2,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) REGION="$2"; shift 2 ;;
    --gemini-key) GEMINI_KEY="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
    --skip-ollama) SKIP_OLLAMA=true; shift ;;
    --skip-model-pull) SKIP_MODEL_PULL=true; shift ;;
    --no-venv) NO_VENV=true; shift ;;
    --force-env) FORCE_ENV=true; shift ;;
    --reset-db) RESET_DB=true; shift ;;
    --skip-boot-test) SKIP_BOOT_TEST=true; shift ;;
    --with-benchmark-models) WITH_BENCHMARK_MODELS=true; shift ;;
    -y|--yes) NON_INTERACTIVE=true; shift ;;
    -h|--help) print_help; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 1 ;;
  esac
done

echo "${C_BOLD}"
echo "  ______      _____      _          "
echo " |  ____|    |  __ \\    | |         "
echo " | |__ ___   | |__) |   | |___  ___ "
echo " |  __/ __|  |  ___/ /\\ | | __|/ _ \\"
echo " | | | (__   | |  | /  \\| | |_|  __/"
echo " |_|  \\___|  |_|  |_|  |_|_|\\__|\\___|"
echo "${C_RESET}"
echo "EcoPulse backend setup — region: $REGION"
echo "bot/ directory: $BOT_DIR"
echo

# ---------------------------------------------------------------------------
# Helpers used across steps
# ---------------------------------------------------------------------------

detect_pkg_mgr() {
  command -v dnf >/dev/null 2>&1 && { echo dnf; return; }
  command -v apt-get >/dev/null 2>&1 && { echo apt-get; return; }
  command -v pacman >/dev/null 2>&1 && { echo pacman; return; }
  command -v brew >/dev/null 2>&1 && { echo brew; return; }
  echo ""
}

install_pkgs() {
  local mgr="$1"; shift
  case "$mgr" in
    dnf) sudo dnf install -y "$@" ;;
    apt-get) sudo apt-get update -y && sudo apt-get install -y "$@" ;;
    pacman) sudo pacman -Sy --noconfirm "$@" ;;
    brew) brew install "$@" ;;
  esac
}

# Portable in-place sed — GNU sed (-i) vs BSD/macOS sed (-i '') differ.
sedi() {
  if sed --version >/dev/null 2>&1; then sed -i "$@"; else sed -i '' "$@"; fi
}

get_pid_on_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$port" 2>/dev/null | head -n1
  elif command -v fuser >/dev/null 2>&1; then
    fuser "$port"/tcp 2>/dev/null | awk '{print $1}'
  else
    echo ""
  fi
}

port_in_use() { [[ -n "$(get_pid_on_port "$1")" ]]; }

TEST_SERVER_PID=""
cleanup_test_server() {
  if [[ -n "$TEST_SERVER_PID" ]]; then
    kill "$TEST_SERVER_PID" 2>/dev/null || true
    wait "$TEST_SERVER_PID" 2>/dev/null || true
    TEST_SERVER_PID=""
  fi
}
trap cleanup_test_server EXIT

# ---------------------------------------------------------------------------
# Step 1 — sanity: are we actually in the right directory?
# ---------------------------------------------------------------------------

log "Checking bot/ directory contents"
missing=()
for required in server.py climate_agent.py climate_llm.py weather.py requirements.txt env.example Modelfile.ecopulse region_config.py; do
  [[ -f "$BOT_DIR/$required" ]] || missing+=("$required")
done
if [[ ${#missing[@]} -gt 0 ]]; then
  fail "Missing files: ${missing[*]}. Run this script from inside (or pointed at) the ecopulse bot/ directory."
fi
if [[ ! -f "$BOT_DIR/configs/$REGION.json" ]]; then
  fail "No region config at configs/$REGION.json. Valid options are the files in bot/configs/ (e.g. ghana, kenya)."
fi
ok "Found server.py, requirements.txt, Modelfile.ecopulse, configs/$REGION.json"

if [[ ! -f "$BOT_DIR/static/index.html" ]]; then
  warn "bot/static/index.html not found — the org dashboard (GET /dashboard) will 404 until it's added. Not blocking the rest of setup."
fi

# ---------------------------------------------------------------------------
# Step 2 — system prerequisites
# ---------------------------------------------------------------------------

log "Checking system prerequisites"
sys_missing=()
command -v curl >/dev/null 2>&1 || sys_missing+=(curl)

if [[ ${#sys_missing[@]} -gt 0 ]]; then
  warn "Missing: ${sys_missing[*]}"
  mgr="$(detect_pkg_mgr)"
  if [[ -n "$mgr" ]] && confirm "Install ${sys_missing[*]} now via 'sudo $mgr'?"; then
    install_pkgs "$mgr" "${sys_missing[@]}" || fail "Failed to install ${sys_missing[*]} — install manually and re-run."
    ok "Installed ${sys_missing[*]}"
  else
    fail "${sys_missing[*]} required. Install manually and re-run (or answer yes to the prompt)."
  fi
else
  ok "curl present"
fi

# ---------------------------------------------------------------------------
# Step 3 — Python version check
# ---------------------------------------------------------------------------

log "Checking Python version"
PYTHON_BIN=""
for candidate in python3.13 python3.12 python3.11 python3.10 python3; do
  if command -v "$candidate" >/dev/null 2>&1; then
    ver="$("$candidate" -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")' 2>/dev/null)" || continue
    major="${ver%%.*}"; minor="${ver##*.}"
    if [[ "$major" -eq 3 && "$minor" -ge 10 ]]; then
      PYTHON_BIN="$candidate"
      ok "Using $candidate (Python $ver)"
      break
    fi
  fi
done
if [[ -z "$PYTHON_BIN" ]]; then
  fail "Need Python 3.10+ (server.py/climate_agent.py use 'str | None' union syntax, which needs 3.10+ at runtime). Install a newer Python and re-run."
fi

# ---------------------------------------------------------------------------
# Step 4 — virtualenv + pip deps
# ---------------------------------------------------------------------------

if $NO_VENV; then
  warn "Skipping virtualenv (--no-venv) — installing into whatever Python environment is currently active"
  RUN_PYTHON="$PYTHON_BIN"
  RUN_PIP=("$PYTHON_BIN" -m pip)
else
  log "Setting up virtual environment at $VENV_DIR"
  if [[ -d "$VENV_DIR" && -x "$VENV_DIR/bin/python" ]]; then
    ok "venv already exists, reusing it"
  else
    venv_err="$(mktemp)"
    if ! "$PYTHON_BIN" -m venv "$VENV_DIR" 2>"$venv_err"; then
      cat "$venv_err" >&2
      if grep -qi "ensurepip\|No module named venv" "$venv_err"; then
        mgr="$(detect_pkg_mgr)"
        case "$mgr" in
          apt-get) hint="sudo apt-get install python3-venv" ;;
          dnf)     hint="sudo dnf install python3-virtualenv" ;;
          *)       hint="install your distro's python3-venv equivalent" ;;
        esac
        fail "Python's venv module isn't fully installed. Try: $hint — then re-run this script."
      fi
      fail "Could not create virtualenv (see error above)."
    fi
    rm -f "$venv_err"
    ok "Created venv"
  fi
  RUN_PYTHON="$VENV_DIR/bin/python"
  RUN_PIP=("$VENV_DIR/bin/pip")
  ok "Will use $RUN_PYTHON for every step below"
fi

log "Installing Python dependencies from requirements.txt"
"${RUN_PIP[@]}" install --upgrade pip --quiet || fail "pip upgrade failed"
"${RUN_PIP[@]}" install -r "$BOT_DIR/requirements.txt" --quiet || fail "Dependency install failed — check network access and requirements.txt"
ok "Base requirements installed"

if [[ -n "$GEMINI_KEY" ]]; then
  log "Installing google-genai (Gemini fallback requested via --gemini-key)"
  "${RUN_PIP[@]}" install --quiet google-genai || fail "google-genai install failed"
  ok "google-genai installed"
fi

"$RUN_PYTHON" -c "import fastapi, uvicorn, pydantic, requests" \
  || fail "A required package failed to import after install — check the pip output above."
ok "Core packages import cleanly"

# ---------------------------------------------------------------------------
# Step 5 — .env generation
# ---------------------------------------------------------------------------

log "Setting up .env"
if [[ -f "$ENV_PATH" ]] && ! $FORCE_ENV; then
  ok ".env already exists, leaving it alone (use --force-env to regenerate)"
else
  cp "$BOT_DIR/env.example" "$ENV_PATH"

  # Generate a real random admin token instead of shipping env.example's
  # placeholder ("my_secure_demo_secret_token_123") — that placeholder
  # being live in a real deployment was a flagged backlog item; generating
  # a fresh one on every install closes it by construction.
  if [[ -z "$ADMIN_TOKEN" ]]; then
    if command -v openssl >/dev/null 2>&1; then
      ADMIN_TOKEN="$(openssl rand -hex 16)"
    else
      ADMIN_TOKEN="$("$RUN_PYTHON" -c 'import secrets; print(secrets.token_hex(16))')"
    fi
  fi

  sedi "s|^ECOPULSE_REGION=.*|ECOPULSE_REGION=$REGION|" "$ENV_PATH"
  sedi "s|^ECOPULSE_ADMIN_TOKEN=.*|ECOPULSE_ADMIN_TOKEN=$ADMIN_TOKEN|" "$ENV_PATH"
  [[ -n "$GEMINI_KEY" ]] && sedi "s|^GEMINI_API_KEY=.*|GEMINI_API_KEY=$GEMINI_KEY|" "$ENV_PATH"

  ok "Created .env (region=$REGION)"
  echo "    ${C_YELLOW}Admin token: $ADMIN_TOKEN${C_RESET}"
  echo "    Save that — it's what the org dashboard's login modal expects."
fi

# Read back whatever's actually in .env now (handles both freshly generated
# and pre-existing/hand-edited .env files) for use in later steps.
SERVER_PORT="$(grep '^SERVER_PORT=' "$ENV_PATH" | cut -d= -f2)"
SERVER_PORT="${SERVER_PORT:-8000}"

# ---------------------------------------------------------------------------
# Step 6 — database initialization
# ---------------------------------------------------------------------------

log "Setting up the SQLite database"
if $RESET_DB; then
  if confirm "Delete existing ecopulse.db and recreate it from scratch?"; then
    rm -f "$DB_PATH" "$DB_PATH-wal" "$DB_PATH-shm"
    warn "Deleted existing database files"
  fi
fi

db_result="$(cd "$BOT_DIR" && ECOPULSE_REGION="$REGION" "$RUN_PYTHON" - <<'PYEOF'
import os, sys, sqlite3
sys.path.insert(0, os.getcwd())
try:
    import server
    server._init_db()
    conn = sqlite3.connect(server.DB_PATH)
    tables = sorted(r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'"))
    quiz_count = conn.execute("SELECT COUNT(*) FROM quiz_questions").fetchone()[0]
    incident_count = conn.execute("SELECT COUNT(*) FROM incidents").fetchone()[0]
    profile_count = conn.execute("SELECT COUNT(*) FROM profiles").fetchone()[0]
    print(f"OK|{','.join(tables)}|{quiz_count}|{incident_count}|{profile_count}")
except Exception as e:
    print(f"FAIL|{e}")
PYEOF
)"

if [[ "$db_result" == FAIL* ]]; then
  fail "Database init failed: ${db_result#FAIL|}"
fi

IFS='|' read -r _ tables quiz_count incident_count profile_count <<< "$db_result"
ok "Database ready at $DB_PATH"
echo "    Tables: $tables"
echo "    Quiz questions seeded: $quiz_count | Incidents on file: $incident_count | Profiles on file: $profile_count"
echo "    Note: this is the SERVER-side DB (org dashboard / API). The Android"
echo "    app has its own separate on-device Room DB — they sync over the"
echo "    network when both are running, they are not the same file."

# ---------------------------------------------------------------------------
# Step 7 — Ollama install + model pull + custom model build
# ---------------------------------------------------------------------------

if $SKIP_OLLAMA; then
  warn "Skipping Ollama setup (--skip-ollama) — the app will fall back to Gemini if GEMINI_API_KEY is set, or show a setup message otherwise"
else
  log "Checking for Ollama"
  if ! command -v ollama >/dev/null 2>&1; then
    warn "Ollama not found."
    if confirm "Install Ollama now via the official install script (curl https://ollama.com/install.sh | sh)?"; then
      curl -fsSL https://ollama.com/install.sh | sh || fail "Ollama install script failed"
      ok "Ollama installed"
    else
      fail "Ollama is required unless you pass --skip-ollama. Install manually from https://ollama.com/download and re-run."
    fi
  else
    ok "Ollama already installed ($(ollama --version 2>&1 | head -n1))"
  fi

  log "Making sure the Ollama daemon is running"
  if curl -s -m 2 http://localhost:11434/api/tags >/dev/null 2>&1; then
    ok "Ollama daemon already reachable"
  else
    if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files 2>/dev/null | grep -q '^ollama\.service'; then
      sudo systemctl start ollama && ok "Started ollama via systemd" \
        || warn "systemctl start ollama failed — trying 'ollama serve' directly"
    fi
    if ! curl -s -m 2 http://localhost:11434/api/tags >/dev/null 2>&1; then
      nohup ollama serve >"$BOT_DIR/.ollama-serve.log" 2>&1 &
      disown
      ok "Launched 'ollama serve' in the background (logs: $BOT_DIR/.ollama-serve.log)"
    fi

    log "Waiting up to ${OLLAMA_MIN_WAIT_SECS}s for the daemon to respond"
    waited=0
    until curl -s -m 2 http://localhost:11434/api/tags >/dev/null 2>&1; do
      sleep 2; waited=$((waited + 2))
      if [[ $waited -ge $OLLAMA_MIN_WAIT_SECS ]]; then
        fail "Ollama daemon didn't come up after ${OLLAMA_MIN_WAIT_SECS}s — check $BOT_DIR/.ollama-serve.log"
      fi
    done
    ok "Ollama daemon is up"
  fi

  if $SKIP_MODEL_PULL; then
    warn "Skipping model pull (--skip-model-pull) — assuming phi4-mini is already available"
  else
    log "Pulling phi4-mini (used by both the Modelfile and climate_llm.py's extraction helper)"
    echo "    This downloads several GB the first time — grab a coffee if your connection is slow."
    ollama pull phi4-mini || fail "Failed to pull phi4-mini"
    ok "phi4-mini ready"

    if $WITH_BENCHMARK_MODELS; then
      log "Also pulling phi4-mini:q4_K_M and gemma3:2b for benchmark.py comparisons"
      ollama pull phi4-mini:q4_K_M || warn "phi4-mini:q4_K_M pull failed, skipping"
      ollama pull gemma3:2b || warn "gemma3:2b pull failed, skipping"
      ok "Benchmark models ready — run 'python benchmark.py' to compare them on this hardware"
    fi
  fi

  log "Building the ecopulse-guide custom model from Modelfile.ecopulse"
  (cd "$BOT_DIR" && ollama create ecopulse-guide -f Modelfile.ecopulse) \
    || fail "ollama create failed — check Modelfile.ecopulse syntax"
  ok "ecopulse-guide model built"
fi

# ---------------------------------------------------------------------------
# Step 8 — live boot smoke test
# ---------------------------------------------------------------------------

if $SKIP_BOOT_TEST; then
  warn "Skipping live boot test (--skip-boot-test)"
else
  log "Starting the real server briefly to verify it actually boots (not just imports)"

  if port_in_use "$SERVER_PORT"; then
    warn "Port $SERVER_PORT is already in use — skipping live boot test. (Something's already listening there; if that's a previous EcoPulse instance, this is probably fine.)"
  else
    ( cd "$BOT_DIR" && ECOPULSE_REGION="$REGION" nohup "$RUN_PYTHON" server.py >"$BOT_DIR/.setup-smoketest.log" 2>&1 & )

    waited=0
    until curl -s -m 2 "http://localhost:$SERVER_PORT/health" >/dev/null 2>&1; do
      sleep 1; waited=$((waited + 1))
      if [[ $waited -ge $SERVER_BOOT_WAIT_SECS ]]; then
        echo "--- last 30 lines of $BOT_DIR/.setup-smoketest.log ---"
        tail -n 30 "$BOT_DIR/.setup-smoketest.log" 2>/dev/null || true
        fail "Server didn't respond on port $SERVER_PORT within ${SERVER_BOOT_WAIT_SECS}s — see log above."
      fi
    done

    TEST_SERVER_PID="$(get_pid_on_port "$SERVER_PORT")"

    HEALTH="$(curl -s "http://localhost:$SERVER_PORT/health")"
    ok "Server booted — /health: $HEALTH"

    DASH_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$SERVER_PORT/dashboard")"
    if [[ "$DASH_CODE" == "200" ]]; then
      ok "/dashboard responded 200"
    else
      warn "/dashboard returned HTTP $DASH_CODE (expected 200) — check bot/static/index.html is present"
    fi

    INCIDENTS_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$SERVER_PORT/incidents")"
    [[ "$INCIDENTS_CODE" == "200" ]] && ok "/incidents responded 200" || warn "/incidents returned HTTP $INCIDENTS_CODE"

    cleanup_test_server
    ok "Test server stopped cleanly"
  fi
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------

echo
echo "${C_GREEN}${C_BOLD}Setup complete.${C_RESET}"
echo
echo "Next steps:"
if $NO_VENV; then
  echo "  1. cd $BOT_DIR && python server.py"
  echo "  2. Org dashboard: http://localhost:$SERVER_PORT/dashboard"
else
  echo "  1. source $VENV_DIR/bin/activate   (needed in every new terminal)"
  echo "  2. cd $BOT_DIR && python server.py"
  echo "  3. Org dashboard: http://localhost:$SERVER_PORT/dashboard"
fi
echo "  Android emulator points at http://10.0.2.2:$SERVER_PORT automatically"
echo
echo "Your .env is at $ENV_PATH — review ECOPULSE_ADMIN_TOKEN and GEMINI_API_KEY before sharing this box with anyone."
echo "Your database is at $DB_PATH — safe to delete and re-run with --reset-db any time you want a clean slate."