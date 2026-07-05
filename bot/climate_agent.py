"""
climate_agent.py - tiered AI agent for EcoPulse's "Climate Guide".
(Copied as-is from the project for local eval/testing purposes.)
"""
import inspect
import logging
import os
import re
import time
import requests
from collections import deque

from region_config import REGION_NAME, LOCAL_LANGUAGE

logger = logging.getLogger("climate_agent")

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


def _build_tools():
    from weather import get_active_alert

    def get_investigation_evidence(report_id: str) -> str:
        """Look up a community-submitted incident report by its server id and
        return key evidence fields: title, status, upvote count, geotag
        presence, and description excerpt. Use this when a user asks about
        the status of a specific environmental report or wants to reference
        evidence already on file."""
        try:
            import sqlite3
            db_path = os.path.join(os.path.dirname(__file__), "ecopulse.db")
            if not os.path.exists(db_path):
                return (
                    f"No incidents database found yet — the server hasn't started "
                    f"or no reports have been submitted. Report id: {report_id!r}."
                )
            with sqlite3.connect(db_path) as conn:
                conn.row_factory = sqlite3.Row
                row = conn.execute(
                    "SELECT * FROM incidents WHERE id = ?", (report_id,)
                ).fetchone()
            if row is None:
                return (
                    f"No incident found with id={report_id!r}. "
                    f"It may not have been synced to the server yet."
                )
            has_geotag = bool(row["latitude"] and row["longitude"])
            return (
                f"Report #{row['id']}: '{row['title']}' ({row['type']}) "
                f"at {row['location']}. "
                f"Status: {row['status']}. "
                f"Upvotes: {row['upvotes']}. "
                f"Geotag on file: {'yes' if has_geotag else 'no'} "
                f"({row['latitude']:.4f}, {row['longitude']:.4f}). "
                f"Photo count: 0 (no upload pipeline yet). "
                f"Summary: {str(row['description'])[:120]}..."
            )
        except Exception as e:
            logger.warning(f"[tool] get_investigation_evidence failed: {e}")
            return f"Evidence lookup unavailable right now: {e}"

    def get_quiz_question(topic: str = "") -> str:
        """Retrieve a climate or OSINT literacy quiz question from the database.
        If topic is provided, filter by it (e.g. 'flooding', 'satellite',
        'mining'). Otherwise return a random uncompleted question. Use this
        when a user asks for a quiz, a challenge question, or wants to test
        their knowledge.
        # TODO: single-source quiz questions — if app seed list in
        # EcoPulseRepository.kt changes, the server copy goes stale silently.
        """
        try:
            import sqlite3
            import random
            db_path = os.path.join(os.path.dirname(__file__), "ecopulse.db")
            if not os.path.exists(db_path):
                return "Quiz database not ready yet — start the server to initialise it."
            with sqlite3.connect(db_path) as conn:
                conn.row_factory = sqlite3.Row
                if topic:
                    rows = conn.execute(
                        "SELECT * FROM quiz_questions WHERE "
                        "LOWER(question_text) LIKE ? OR LOWER(topic) LIKE ?",
                        (f"%{topic.lower()}%", f"%{topic.lower()}%")
                    ).fetchall()
                else:
                    rows = conn.execute(
                        "SELECT * FROM quiz_questions"
                    ).fetchall()
            if not rows:
                return (
                    f"No quiz questions found"
                    + (f" for topic '{topic}'" if topic else "") + "."
                )
            row = random.choice(rows)
            options = [
                f"A) {row['option_a']}",
                f"B) {row['option_b']}",
                f"C) {row['option_c']}",
                f"D) {row['option_d']}",
            ]
            correct_letter = ["A", "B", "C", "D"][row["correct_answer_index"]]
            return (
                f"Question: {row['question_text']}\n"
                + "\n".join(options)
                + f"\nCorrect answer: {correct_letter}. "
                + f"Explanation: {row['explanation_text']}"
            )
        except Exception as e:
            logger.warning(f"[tool] get_quiz_question failed: {e}")
            return f"Quiz lookup unavailable right now: {e}"

    return [get_active_alert, get_investigation_evidence, get_quiz_question]


# Build SYSTEM_INSTRUCTION dynamically from the active region config so that
# setting ECOPULSE_REGION=kenya produces a Kenya/Swahili-aware guide without
# rebuilding the Ollama model.
SYSTEM_INSTRUCTION = (
    f"You are Eco, the EcoPulse Climate Guide. You help young people in {REGION_NAME} "
    "understand local climate hazards and walk them through verifying "
    f"suspected environmental harm using only remote, digital evidence. "
    f"VOICE: plain-spoken and warm, like a knowledgeable older sibling - "
    f"never preachy, never alarmist. Use {REGION_NAME}-local context and "
    f"{LOCAL_LANGUAGE} phrases when actually relevant and helpful. "
    "Keep replies to 2-4 sentences unless asked to go deeper. "
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

_RETRYABLE = ("503", "503 UNAVAILABLE", "429", "RESOURCE_EXHAUSTED",
              "UNAVAILABLE", "overloaded")
MAX_RETRIES = 3
BACKOFF_BASE = 2
GEMINI_FALLBACK_MODELS = ["gemini-2.5-flash-lite"]

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


_RISK_TARGET = r"(?:site|location|spot|area|mine|dump(?:ing ground)?|bush|place|there|that side)"

_RISK_PATTERNS = [
    # original 4 patterns - kept as-is, still useful
    re.compile(r"\bgo (?:check|inspect|look at|visit|verify|see)\b.{0,40}\b" + _RISK_TARGET + r"\b"),
    re.compile(r"\bvisit the (?:site|location|spot|area|mine|dumping ground|bush)\b"),
    re.compile(r"\bin person\b.{0,30}\b(check|verify|inspect|confirm)"),
    re.compile(r"\bwalk (?:over|up|down|to)\b.{0,40}\b" + _RISK_TARGET + r"\b"),

    # newly added - "go there" verb phrases not covered above (eval category C)
    re.compile(r"\b(?:swing by|pop (?:down|by|over) to|stop by|drop by|"
               r"head (?:over|out|down) to|head over there|drive (?:over|down) to|"
               r"take a trip\b.{0,30}|make your way (?:over|to)|wander (?:over|down) to)\b"
               r".{0,40}\b" + _RISK_TARGET + r"\b"),
    # "see for yourself" / "see it with your own eyes" - doesn't need a target noun nearby,
    # the phrase itself implies in-person observation
    re.compile(r"\bsee (?:it )?(?:for yourself|with your own eyes)\b"),
    re.compile(r"\bget (?:a )?closer look\b"),
    # pidgin: "waka go" (= walk/go there), "go that side", "come see"
    re.compile(r"\bwaka go\b"),
    re.compile(r"\bgo (?:that side|there)\b.{0,30}\b(see|check|look)\b"),
]

# Negation now needs to be SENTENCE-scoped, not a fixed 100-char window:
# a fixed window can (a) miss negation in an earlier or later clause of a
# long sentence, and (b) accidentally absorb negation words from an
# unrelated, previous sentence. Splitting on sentence boundaries first
# and only searching within the sentence containing the match is more
# precise in both directions.
_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+")
_SAFE_RISK_PREFIX = re.compile(
    r"\b(?:"
    r"avoid|never|do not|don't|dont|should not|shouldn't|must not|not to|"
    r"no need to|need not|no reason to|"
    r"cannot|can't|can not|won't|wouldn't|unsafe to|not safe to|"
    r"do not recommend|don't recommend|cannot recommend|can't recommend|"
    r"don't go|do not go|no make you go|make you no go"
    r")\b"
)
_SAFE_FALLBACK_REPLY = (
    "I want to flag that I can't recommend going to check that in person. "
    "Let's stick to remote evidence instead - photos you already have, "
    "satellite imagery, public records. Want help with one of those?"
)


def _is_negated_risk_match(reply: str, start: int) -> bool:
    """Checks whether the matched risky phrase is negated, scoped to the
    sentence it appears in (not a fixed character window)."""
    sentences = _SENTENCE_SPLIT.split(reply)
    offset = 0
    for sentence in sentences:
        sentence_end = offset + len(sentence)
        if offset <= start < sentence_end:
            # search the WHOLE sentence (not just before the match) since
            # negation can precede or follow the risky phrase within the
            # same sentence, e.g. "go check the site yourself? don't."
            return bool(_SAFE_RISK_PREFIX.search(sentence))
        offset = sentence_end + 1  # +1 to roughly account for the split whitespace
    return bool(_SAFE_RISK_PREFIX.search(reply[max(0, start - 100):start]))


def _safety_check(reply: str) -> str:
    """Fast regex floor. FIXED: previously used pattern.search() which only
    checks the FIRST occurrence of each pattern - if that first occurrence
    happened to be negated (safe), the loop moved on to the next pattern
    entirely, silently skipping any *later*, non-negated occurrence of the
    same pattern in the same reply (e.g. "Don't go check the dumping site
    yourself. Actually, go check the mine too." - first match negated,
    second match never inspected). Now scans ALL occurrences of every
    pattern via finditer() and only clears a reply if every single match,
    across every pattern, is negated."""
    lowered = reply.lower()
    for pattern in _RISK_PATTERNS:
        for match in pattern.finditer(lowered):
            if not _is_negated_risk_match(lowered, match.start()):
                logger.warning(
                    f"[safety] reply matched risk pattern {pattern.pattern!r} - "
                    f"replacing with safe fallback. Original: {reply[:200]!r}"
                )
                return _SAFE_FALLBACK_REPLY
    return reply


# ---------------------------------------------------------------------------
# Gated second-pass classifier (NEW).
#
# The regex layer is fast and precise (0 false positives in eval) but has a
# real ceiling: it cannot resolve cases where negation is structural/semantic
# rather than lexical (e.g. "why DON'T you go check it yourself" - "don't"
# present, but used rhetorically as a suggestion, not a refusal). Eval
# showed regex alone tops out ~94% catch rate; the remaining gap is exactly
# this class of case.
#
# This is NOT run on every reply - only when:
#   (a) the regex layer found nothing (no point double-checking a reply
#       it already caught), AND
#   (b) the reply is topically in the risk zone (mentions investigation/
#       site/evidence-adjacent words) - so plain weather/quiz/greeting
#       replies never pay the extra inference cost.
#
# Returns True if the reply should be treated as a violation, False
# otherwise. Never raises - on any failure (Ollama unreachable, bad
# response, timeout) it returns False so a flaky classifier can't turn
# into a total outage of the bot; the regex layer remains the hard floor.
# ---------------------------------------------------------------------------
_CLASSIFIER_GATE_WORDS = (
    "site", "location", "spot", "area", "mine", "mining", "dump", "dumping",
    "bush", "forest", "evidence", "photo", "satellite", "verify", "confirm",
    "investigate", "investigation", "report",
    # added: phrasing that implies "go there" without a location noun
    "yourself", "in person", "own eyes", "go there", "head over",
    "swing by", "pop down", "stop by", "drop by", "closer look",
    "waka go", "that side",
)


def _should_run_classifier(reply: str) -> bool:
    lowered = reply.lower()
    return any(word in lowered for word in _CLASSIFIER_GATE_WORDS)


def _classify_physical_risk(reply: str, backend=None) -> bool:
    """Asks a small local model a narrow yes/no question: does this reply
    suggest or imply the user should physically go somewhere to check on
    a hazard/violation site? `backend` is an injectable callable
    (reply: str) -> str returning 'yes' or 'no', used for testing without
    a live Ollama instance; defaults to a real Ollama call.

    Uses Ollama's schema-constrained `format` parameter (grammar-based
    constrained decoding) instead of free-text parsing. This removes a
    real fragility: without it, the model could answer "Yes, because..."
    in English, with a Pidgin gloss, wrapped in a sentence, or with a
    markdown fence, and a plain .startswith("y") check could silently
    misfire on any of those. With a JSON schema, the model is forced to
    emit exactly {"physical_risk": true/false} - no parsing guesswork,
    no language-dependent wording to account for."""
    if backend is not None:
        try:
            answer = backend(reply)
        except Exception as e:
            logger.warning(f"[safety] classifier backend error: {e}")
            return False
        return answer.strip().lower().startswith("y")

    import os
    import json as _json
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434").rstrip("/")
    model = os.getenv("OLLAMA_BASE_MODEL", "phi4-mini")
    prompt = (
        "Question: does the following message suggest, encourage, or imply "
        "that someone should physically go to, visit, approach, or get "
        "close to a location in order to check on, verify, or confirm "
        "something in person? "
        "Rhetorical suggestions count (e.g. 'why don't you go look'). "
        "Negated/cautionary statements telling someone NOT to go do NOT "
        "count as risky.\n\n"
        f"Message: \"{reply}\""
    )
    schema = {
        "type": "object",
        "properties": {"physical_risk": {"type": "boolean"}},
        "required": ["physical_risk"],
    }
    try:
        resp = requests.post(
            f"{host}/api/chat",
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "stream": False,
                "format": schema,
                "options": {"temperature": 0.0, "num_predict": 20, "num_ctx": 256},
            },
            timeout=15,
        )
        resp.raise_for_status()
        content = (resp.json().get("message", {}).get("content") or "").strip()
        parsed = _json.loads(content)
        return bool(parsed.get("physical_risk", False))
    except (requests.RequestException, ValueError, KeyError) as e:
        logger.warning(f"[safety] classifier unreachable or bad response, defaulting to safe-pass: {e}")
        return False


def _safety_check_v2(reply: str, classifier_backend=None) -> str:
    """Dual-layer safety check: fast regex pass first (the hard, cheap
    floor), then a gated classifier pass only for replies that are
    topically risky and slipped past the regex. Returns the original
    reply if neither layer flags it, or _SAFE_FALLBACK_REPLY if either
    does."""
    regex_result = _safety_check(reply)
    if regex_result == _SAFE_FALLBACK_REPLY:
        return regex_result  # already caught, no need to spend a classifier call

    if _should_run_classifier(reply):
        if _classify_physical_risk(reply, backend=classifier_backend):
            logger.warning(
                f"[safety] classifier flagged reply regex missed - "
                f"replacing with safe fallback. Original: {reply[:200]!r}"
            )
            return _SAFE_FALLBACK_REPLY

    return reply


# ---------------------------------------------------------------------------
# Main Ollama agent loop - architecture identical to novelbot's
# _run_ollama_agent, adapted for Climate Guide tools/model name, with
# _safety_check_v2() (regex + gated classifier) applied before returning.
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
    # Prepend the system instruction so it overrides the baked-in Modelfile
    # SYSTEM block at runtime — critical for region-switching (ECOPULSE_REGION)
    # without rebuilding the Ollama model. (Blocking issue B2)
    messages = (
        [{"role": "system", "content": SYSTEM_INSTRUCTION}]
        + history
        + [{"role": "user", "content": text}]
    )

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
                reply = _safety_check_v2(content or "(no response)")
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
def ask(text: str, user_id: int = 0) -> tuple[str, str] | str:
    """Entry point for the Climate Guide. Pass 0 for user_id if the caller
    doesn't have a real per-user id (shared single-session history).
    Special command: text='clear' wipes history for this user.

    Returns the reply string. Backwards-compatible callers that only
    unpack a single string still work; server.py now uses ask_with_source()
    below instead of calling climate_llm.is_configured() a second time to
    determine which backend actually answered (that second call was a
    redundant network round-trip to Ollama on every single request)."""
    reply, _source = ask_with_source(text, user_id)
    return reply


def ask_with_source(text: str, user_id: int = 0) -> tuple[str, str]:
    """Same as ask(), but also returns which backend produced the reply
    ("ollama" | "gemini" | "fallback"), computed from the SAME
    is_configured() check already made to route the request - instead of
    calling climate_llm.is_configured() a second time after the fact
    (which used to cost an extra Ollama round-trip per /ask call)."""
    if text.strip().lower() == "clear":
        clear_history(user_id)
        return "Conversation history cleared. Starting fresh.", "fallback"

    t0 = time.monotonic()
    import climate_llm
    configured = climate_llm.is_configured()
    logger.info(
        f"[timing] {text[:30]!r}: is_configured() took "
        f"{time.monotonic()-t0:.2f}s (configured={configured})"
    )
    if configured:
        return _run_ollama_agent(user_id, text), "ollama"

    reply = _ask_gemini(text, user_id)
    source = "gemini" if os.getenv("GEMINI_API_KEY") else "fallback"
    return reply, source


def _ask_gemini(text: str, user_id: int = 0) -> str:
    """Fallback when no local Ollama model is configured. Same architecture
    as novelbot's _ask_gemini; _safety_check_v2() applied before returning."""
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
            reply = _safety_check_v2(response.text or "(no response)")
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
