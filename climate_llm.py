"""
climate_llm.py - Ollama connectivity check + standalone extraction-style
helpers for the EcoPulse Climate Guide.

FORKED FROM: novelbot/bot/local_llm.py. is_configured() is copied as-is
(generic Ollama reachability check, nothing novel-specific in it).
extract_chapter_marker() was novel-tracker-specific and is NOT carried
over. answer_from_search_results() IS carried over (renamed
summarize_hazard_lookup) because the shape - "answer a question using
ONLY the snippets you're given, say so plainly if they don't answer it" -
is exactly what's needed if/when EcoPulse adds a web_lookup tool for
things the local model can't know (e.g. "is there a flood warning today").

WHAT'S STILL A STUB (left for the next AI/IDE session to wire up):
- There is no real hazard-data source wired in yet. summarize_hazard_lookup
  expects to be handed snippets by a caller the same way novelbot's
  websearch.py module hands snippets to answer_from_search_results - that
  caller doesn't exist for EcoPulse yet. See climate_agent.py's
  get_active_alert()/lookup_investigation_evidence() for where real
  alert/report DB calls need to replace the placeholder data.
"""
import os
import requests

TIMEOUT = 60  # local CPU inference can take a while


def _ollama_host():
    return os.getenv("OLLAMA_HOST", "http://localhost:11434").rstrip("/")


def is_configured() -> bool:
    """Whether a local Ollama host is actually reachable right now.
    Identical to novelbot's version - this is a generic Ollama health
    check, nothing climate-specific about it."""
    try:
        resp = requests.get(f"{_ollama_host()}/api/tags", timeout=3)
        return resp.status_code == 200
    except requests.RequestException:
        return False


def summarize_hazard_lookup(query: str, snippet_block: str) -> str | None:
    """Turns raw search-result snippets into a direct, grounded answer to
    a hazard/climate question - the local-model equivalent of what a
    Gemini web-search tool call would otherwise do. FORKED FROM
    answer_from_search_results() in novelbot/bot/local_llm.py - same
    shape, same anti-hallucination instruction ("ONLY use what's in the
    snippets, say so plainly if they don't answer it"), renamed for
    clarity in this codebase.

    Deliberately does NOT use the "ecopulse-guide" custom model here -
    uses the bare base model with its own explicit system message instead,
    same reasoning as novelbot: this is a narrow extraction task, not a
    chat turn, and the baked Climate Guide persona/safety SYSTEM block
    would just add irrelevant tokens (cost on a CPU-only box) without
    changing this task's behavior, since this prompt already says exactly
    what to do.

    Returns None - never raises - on any failure, so callers can fall back
    to showing the user the raw snippets instead.
    """
    model = os.getenv("OLLAMA_BASE_MODEL", "phi4-mini")
    system_msg = (
        "You answer questions using ONLY the search result snippets you "
        "are given. You never invent facts beyond what's in the snippets. "
        "If the snippets don't actually answer the question, say so plainly. "
        "Never suggest that anyone physically go somewhere to check on a "
        "hazard or suspected violation - if the snippets don't have a remote "
        "way to confirm something, say that plainly instead of suggesting "
        "an in-person check."
    )
    user_msg = (
        f"Question: {query}\n\n"
        f"Search result snippets:\n---\n{snippet_block[:6000]}\n---\n\n"
        "Answer the question in 2-4 sentences using only the information "
        "above. Mention which source it came from if relevant."
    )
    try:
        resp = requests.post(
            f"{_ollama_host()}/api/chat",
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": system_msg},
                    {"role": "user", "content": user_msg},
                ],
                "options": {"temperature": 0.3, "num_predict": 150},
                "stream": False,
            },
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        out = (resp.json().get("message", {}).get("content") or "").strip()
    except (requests.RequestException, ValueError, KeyError):
        return None

    return out or None
