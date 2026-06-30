"""
climate_agent.py - tiered AI agent for EcoPulse's "Climate Guide".

FORKED FROM: novelbot/bot/ai_agent.py. The orchestration architecture
below - per-user history, trigger-word tool-schema gating, the Ollama
tool-calling loop, the plain-text-tool-call fallback parser, retry/backoff
on the Gemini escalation path - is copied and adapted, NOT rewritten from
scratch. See novelbot/bot/ai_agent.py for the original and its detailed
inline reasoning around _parse_text_tool_calls and trigger-word timing.

WHAT CHANGED FROM THE ORIGINAL:
- Persona moved to Modelfile.ecopulse (Ollama path gets it baked in;
  Gemini fallback still needs SYSTEM_INSTRUCTION sent explicitly).
- Tools are climate/investigation-shaped stubs. TODO markers show exactly
  what needs a real DB/API call wired in.
- _TOOL_TRIGGER_WORDS / _TOOL_GROUPS rewritten for climate vocabulary.
- _safety_check() added: post-generation keyword filter as last-resort net
  against the model suggesting physical risk-taking. Not present in
  novelbot because persona drift there was annoying, not safety-relevant.
  This uses a small negation-aware heuristic to avoid obvious refusals.
- num_ctx/num_predict tuned down (1024/150 vs 2048/300) - Climate Guide
  replies are meant to be short, so tighter caps = faster on CPU.
"""
import inspect
import logging
import os
import re
import time
import requests
from collections import deque

logger = logging.getLogger("climate_agent")

# ---------------------------------------------------------------------------
# Gemini client cache - identical pattern to novelbot.
# ---------------------------------------------------------------------------
_client_cache = {}


def _get_client():
    if "client" in _client_cache:
        return _client_cache["client"]
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return None
    from google import genai
    client = genai.Client(api_key=api_key)
    _client_cache["client"] = client
    return client


# ---------------------------------------------------------------------------
# Per-user conversation history - identical shape/pattern to novelbot.
# ---------------------------------------------------------------------------
HISTORY_TURNS = 6
_history: dict[int, deque] = {}


def _get_history(user_id: int) -> list:
    if user_id not in _history:
        _history[user_id] = deque(maxlen=HISTORY_TURNS * 2)
    return list(_history[user_id])


def _append_history(user_id: int, role: str, content: str):
    if user_id not in _history:
        _history[user_id] = deque(maxlen=HISTORY_TURNS * 2)
    _history[user_id].append({"role": role, "content": content})


def clear_history(user_id: int):
    _history.pop(user_id, None)


# ---------------------------------------------------------------------------
# Tool definitions.
# get_active_alert: REAL - calls Open-Meteo Forecast + Flood APIs via
#   weather.py. No API key, no stub, live data for any Ghana location.
# get_investigation_evidence, get_quiz_question: still stubs - need a
#   real reports DB and quiz bank wired in (see TODOs below).
# ---------------------------------------------------------------------------
def _build_tools():
    from weather import get_active_alert  # real Open-Meteo data, no API key needed

    def get_investigation_evidence(report_id: str) -> str:
        """Get the stored evidence (existing photos, geotag, satellite
        comparison status) for a submitted environmental report by its
        report_id, so the guide can help explain or interpret it."""
        # TODO: replace with a real call to the reports DB.
        # Must only ever return evidence that already exists - never
        # instruct gathering more evidence in person.
        return (
            f"(STUB) No real reports backend wired up yet. Pretend response "
            f"for report '{report_id}': 1 photo on file, geotag present, "
            f"no satellite comparison run yet."
        )

    def get_quiz_question(topic: str = "") -> str:
        """Get one local-context climate-literacy quiz question, optionally
        on a specific topic (e.g. 'flooding', 'recycling')."""
        # TODO: replace with a real pull from a quiz question bank.
        return (
            f"(STUB) No real quiz bank wired up yet. Pretend question on "
            f"topic '{topic or 'general'}': 'Which of these areas in Accra "
            f"is known to flood most often during heavy rain?'"
        )

    return [get_active_alert, get_investigation_evidence, get_quiz_question]


# ---------------------------------------------------------------------------
# System instruction - Gemini fallback path only.
# Mirrors Modelfile.ecopulse SYSTEM block. Keep these two in sync manually
# if you edit either one - same caveat as novelbot (no single source of
# truth across both files yet).
# ---------------------------------------------------------------------------
SYSTEM_INSTRUCTION = (
    "You are Eco, the EcoPulse Climate Guide. You help young people in Ghana "
    "understand local climate hazards and walk them through verifying "
    "suspected environmental harm using only remote, digital evidence. "
    "VOICE: plain-spoken and warm, like a knowledgeable older sibling - "
    "never preachy, never alarmist. Use Ghana-local context when actually "
    "relevant. Keep replies to 2-4 sentences unless asked to go deeper. "
    "HARD SAFETY RULE, OVERRIDES EVERYTHING: never suggest, encourage, or "
    "imply that a user should physically approach, enter, or get close to "
    "a hazard site, unstable terrain, an illegal dumping ground, or an "
    "unauthorized mining site to verify or check on something in person. "
    "Every investigation step must be remote/digital only - existing "
    "photos, satellite imagery, public records. "
    "If a user describes being in physical danger right now, drop "
    "everything else and focus entirely on their immediate safety. "
    "If unsure of a fact, say so plainly rather than guessing - guessing "
    "on safety-relevant questions is worse than admitting you don't know."
)

# ---------------------------------------------------------------------------
# Retry config - identical to novelbot.
# ---------------------------------------------------------------------------
_RETRYABLE = ("503", "503 UNAVAILABLE", "429", "RESOURCE_EXHAUSTED",
              "UNAVAILABLE", "overloaded")
MAX_RETRIES = 3
BACKOFF_BASE = 2
GEMINI_FALLBACK_MODELS = ["gemini-2.5-flash-lite"]

# ---------------------------------------------------------------------------
# Ollama options - tuned for speed-first / short replies.
# Plain chat (no tools) gets tighter caps than tool-driven turns, same
# split reasoning as novelbot. Both are tighter than novelbot's values
# because Climate Guide replies are designed to be short (2-4 sentences).
# Edit these to tune speed vs quality on your actual Oracle box - see
# benchmark.py in this folder to measure.
# ---------------------------------------------------------------------------
_PY_TYPE_TO_JSON = {str: "string", int: "integer", float: "number", bool: "boolean"}
OLLAMA_MAX_TOOL_ITERATIONS = 6
OLLAMA_CHAT_TIMEOUT = 180

CLIMATE_CHAT_OPTIONS_PLAIN = {"num_predict": 100, "temperature": 0.4}
CLIMATE_CHAT_OPTIONS_TOOLS = {"num_predict": 150, "temperature": 0.2}

CLIMATE_NUM_CTX = 1024
CLIMATE_NUM_THREAD = max(os.cpu_count() or 1, 1)
CLIMATE_KEEP_ALIVE = "30m"


def _ollama_options(use_tools: bool) -> dict:
    base = dict(CLIMATE_CHAT_OPTIONS_TOOLS if use_tools else CLIMATE_CHAT_OPTIONS_PLAIN)
    base["num_ctx"] = CLIMATE_NUM_CTX
    base["num_thread"] = CLIMATE_NUM_THREAD
    return base


def _function_to_ollama_schema(fn) -> dict:
    """Auto-derives an Ollama tool schema from a plain Python function's
    signature + docstring. Identical to novelbot's version."""
    sig = inspect.signature(fn)
    props, required = {}, []
    for name, param in sig.parameters.items():
        ptype = param.annotation if param.annotation is not inspect.Parameter.empty else str
        props[name] = {"type": _PY_TYPE_TO_JSON.get(ptype, "string")}
        if param.default is inspect.Parameter.empty:
            required.append(name)
    return {
        "type": "function",
        "function": {
            "name": fn.__name__,
            "description": (fn.__doc__ or "").strip(),
            "parameters": {"type": "object", "properties": props, "required": required},
        },
    }


# Climate-vocabulary trigger words replacing novelbot's library vocabulary.
# Same reasoning: skip attaching the tool schema entirely for plain small
# talk - the schema costs real CPU time on a free-tier box regardless of
# model output length. Measured in novelbot: 3s without tools vs 90s with
# tools for the same "hey" prompt.
_TOOL_TRIGGER_WORDS = (
    "alert", "warning", "flood", "flooding", "storm", "heat", "hazard",
    "weather", "report", "dumping", "deforestation", "mining", "evidence",
    "photo", "satellite", "verify", "investigate", "investigation",
    "quiz", "question", "points", "badge", "leaderboard", "near me",
    "today", "now",
)


def _needs_tools(text: str) -> bool:
    lowered = text.lower()
    return any(word in lowered for word in _TOOL_TRIGGER_WORDS)


# Maps trigger-word groups to the small set of tools actually relevant.
# Same narrowing strategy as novelbot: tool count scales super-linearly
# with latency on phi4-mini/CPU.
_TOOL_GROUPS = {
    ("alert", "warning", "flood", "flooding", "storm", "heat", "hazard",
     "weather", "near me", "today", "now"): ["get_active_alert"],
    ("report", "dumping", "deforestation", "mining", "evidence", "photo",
     "satellite", "verify", "investigate", "investigation"): ["get_investigation_evidence"],
    ("quiz", "question"): ["get_quiz_question"],
}


def _select_tool_names(text: str) -> set:
    lowered = text.lower()
    selected = set()
    for words, tool_names in _TOOL_GROUPS.items():
        if any(word in lowered for word in words):
            selected.update(tool_names)
    return selected


def _parse_text_tool_calls(content: str, tool_map: dict) -> list | None:
    """phi4-mini sometimes writes a tool call as plain JSON text in the
    message content instead of using Ollama's structured tool_calls field.
    Copied verbatim from novelbot/bot/ai_agent.py - see that file for the
    full reasoning comment and observed JSON shapes."""
    import json

    if not content or "{" not in content:
        return None

    candidate = content.strip()
    candidate = re.sub(r"^```(?:json)?|```$", "", candidate.strip(), flags=re.IGNORECASE).strip()

    try:
        parsed = json.loads(candidate)
    except (ValueError, TypeError):
        return None

    if isinstance(parsed, dict) and parsed and "name" not in parsed:
        if all(isinstance(v, dict) and k in tool_map for k, v in parsed.items()):
            return [{"function": {"name": k, "arguments": v}} for k, v in parsed.items()]
        return None

    items = parsed if isinstance(parsed, list) else [parsed]
    if not items:
        return None

    calls = []
    for item in items:
        if not isinstance(item, dict):
            return None
        name = item.get("name")
        args = item.get("arguments", item.get("parameters", {}))
        if not name or name not in tool_map or not isinstance(args, dict):
            return None
        calls.append({"function": {"name": name, "arguments": args}})

    return calls or None


# ---------------------------------------------------------------------------
# Post-generation safety filter - NEW, not in novelbot (novelbot's persona
# drift was annoying, not safety-relevant - here it could be).
#
# Blunt keyword/pattern check as a last-resort net in case the Modelfile
# SYSTEM rule gets ignored by the model. Fires before the reply is ever
# shown to the user.
#
# ---------------------------------------------------------------------------
_RISK_PATTERNS = [
    re.compile(r"\bgo (?:check|inspect|look at|visit|verify)\b.{0,40}\b(site|location|spot|area|mine|dump)"),
    re.compile(r"\bvisit the (?:site|location|spot|area|mine|dumping ground)\b"),
    re.compile(r"\bin person\b.{0,30}\b(check|verify|inspect|confirm)"),
    re.compile(r"\bwalk (?:over|up|down|to)\b.{0,40}\b(site|mine|dump|area)"),
]
_SAFE_RISK_PREFIX = re.compile(
    r"\b(?:"
    r"avoid|never|do not|don't|dont|should not|shouldn't|must not|not to|"
    r"cannot|can't|can not|won't|wouldn't|unsafe to|not safe to|"
    r"do not recommend|don't recommend|cannot recommend|can't recommend"
    r")\b.{0,80}$"
)
_SAFE_FALLBACK_REPLY = (
    "I want to flag that I can't recommend going to check that in person. "
    "Let's stick to remote evidence instead - photos you already have, "
    "satellite imagery, public records. Want help with one of those?"
)


def _is_negated_risk_match(reply: str, start: int) -> bool:
    return bool(_SAFE_RISK_PREFIX.search(reply[max(0, start - 100):start]))


def _safety_check(reply: str) -> str:
    lowered = reply.lower()
    for pattern in _RISK_PATTERNS:
        match = pattern.search(lowered)
        if match and not _is_negated_risk_match(lowered, match.start()):
            logger.warning(
                f"[safety] reply matched risk pattern {pattern.pattern!r} - "
                f"replacing with safe fallback. Original: {reply[:200]!r}"
            )
            return _SAFE_FALLBACK_REPLY
    return reply


# ---------------------------------------------------------------------------
# Main Ollama agent loop - architecture identical to novelbot's
# _run_ollama_agent, adapted for Climate Guide tools/model name, with
# _safety_check() added before returning.
# ---------------------------------------------------------------------------
def _run_ollama_agent(user_id: int, text: str) -> str:
    import climate_llm
    from cache import get_cached_response

    t_start = time.monotonic()
    cached_response = get_cached_response(text)
    if cached_response:
        _append_history(user_id, "user", text)
        _append_history(user_id, "assistant", cached_response)
        return cached_response

    use_tools = _needs_tools(text)
    if use_tools:
        all_tools = _build_tools()
        selected_names = _select_tool_names(text)
        py_tools = (
            [fn for fn in all_tools if fn.__name__ in selected_names]
            if selected_names else all_tools
        )
    else:
        py_tools = []

    tool_map = {fn.__name__: fn for fn in py_tools}
    ollama_tools = [_function_to_ollama_schema(fn) for fn in py_tools]

   
    history = _get_history(user_id)
    messages = history + [{"role": "user", "content": text}]

    model = os.getenv("OLLAMA_MODEL", "ecopulse-guide")
    host = climate_llm._ollama_host()
    logger.info(
        f"[timing] {text[:30]!r}: setup done at +{time.monotonic()-t_start:.2f}s "
        f"(use_tools={use_tools}, n_tools={len(ollama_tools)})"
    )

    for iteration in range(OLLAMA_MAX_TOOL_ITERATIONS):
        try:
            t_call = time.monotonic()
            resp = requests.post(
                f"{host}/api/chat",
                json={
                    "model": model,
                    "messages": messages,
                    **({"tools": ollama_tools} if ollama_tools else {}),
                    "stream": False,
                    "options": _ollama_options(use_tools=bool(ollama_tools)),
                    "keep_alive": CLIMATE_KEEP_ALIVE,
                },
                timeout=OLLAMA_CHAT_TIMEOUT,
            )
            resp.raise_for_status()
            data = resp.json()
            logger.info(
                f"[timing] {text[:30]!r}: ollama call #{iteration} took "
                f"{time.monotonic()-t_call:.2f}s "
                f"(total +{time.monotonic()-t_start:.2f}s)"
            )
        except (requests.RequestException, ValueError) as e:
            return f"Local model error: {e}"

        msg = data.get("message", {}) or {}
        tool_calls = msg.get("tool_calls") or []

        if not tool_calls:
            content = (msg.get("content") or "").strip()
            fallback_calls = _parse_text_tool_calls(content, tool_map)
            if fallback_calls:
                logger.info(
                    f"[timing] {text[:30]!r}: model emitted tool call as plain "
                    f"text - recovering via fallback parser"
                )
                tool_calls = fallback_calls
            else:
                reply = _safety_check(content or "(no response)")
                _append_history(user_id, "user", text)
                _append_history(user_id, "assistant", reply)
                return reply

        messages.append(msg)
        for call in tool_calls:
            fn_name = (call.get("function") or {}).get("name")
            fn_args = (call.get("function") or {}).get("arguments") or {}
            fn = tool_map.get(fn_name)
            result = f"Unknown tool: {fn_name}" if not fn else fn(**fn_args)
            if fn:
                try:
                    result = fn(**fn_args)
                except Exception as e:
                    result = f"Tool error calling {fn_name}: {e}"
            messages.append({"role": "tool", "content": str(result), "name": fn_name or ""})

    return "I made too many tool calls without reaching an answer - try rephrasing."


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
def ask(text: str, user_id: int = 0) -> str:
    """Entry point for the Climate Guide. Pass 0 for user_id if the caller
    doesn't have a real per-user id (shared single-session history).
    Special command: text='clear' wipes history for this user."""
    if text.strip().lower() == "clear":
        clear_history(user_id)
        return "Conversation history cleared. Starting fresh."

    t0 = time.monotonic()
    import climate_llm
    configured = climate_llm.is_configured()
    logger.info(
        f"[timing] {text[:30]!r}: is_configured() took "
        f"{time.monotonic()-t0:.2f}s (configured={configured})"
    )
    if configured:
        return _run_ollama_agent(user_id, text)
    return _ask_gemini(text, user_id)


def _ask_gemini(text: str, user_id: int = 0) -> str:
    """Fallback when no local Ollama model is configured. Same architecture
    as novelbot's _ask_gemini; _safety_check() applied before returning."""
    client = _get_client()
    if client is None:
        return (
            "Natural-language mode isn't set up yet. Get a free Gemini API "
            "key at https://aistudio.google.com/apikey, set GEMINI_API_KEY "
            "in .env, then restart. Or set up Ollama locally so this doesn't "
            "depend on Gemini at all - see OLLAMA_HOST/OLLAMA_MODEL in .env."
        )

    from google.genai import types

    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM_INSTRUCTION,
        tools=_build_tools(),
    )

    history = _get_history(user_id)
    contents = [
        {"role": ("model" if h["role"] == "assistant" else "user"),
         "parts": [{"text": h["content"]}]}
        for h in history
    ] + [{"role": "user", "parts": [{"text": text}]}]

    last_err = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = client.models.generate_content(
                model=model, contents=contents, config=config,
            )
            reply = _safety_check(response.text or "(no response)")
            _append_history(user_id, "user", text)
            _append_history(user_id, "assistant", reply)
            return reply
        except Exception as e:
            last_err = e
            err_str = str(e)
            if any(t.lower() in err_str.lower() for t in _RETRYABLE) and attempt < MAX_RETRIES:
                time.sleep(BACKOFF_BASE ** attempt)
                continue
            break

    if any(t.lower() in str(last_err).lower() for t in _RETRYABLE):
        return (
            "Gemini's servers are overloaded right now (503). Try again in "
            "a minute. Setting up a local Ollama model would make this work "
            "even when this happens - see OLLAMA_HOST/OLLAMA_MODEL in .env."
        )
    return f"Natural-language request failed: {last_err}"
