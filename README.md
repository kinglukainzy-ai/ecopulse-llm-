# ecopulse-llm

Self-hosted LLM component for EcoPulse's "Climate Guide" chatbot.
Forked from [novelbot/Thoth](https://github.com/kinglukainzy-ai/novelbot).

## Files

| File | What it is |
|---|---|
| `Modelfile.ecopulse` | Ollama custom model definition - bakes the Climate Guide persona + hard safety rules into the model once so no API call ever needs to resend them |
| `climate_agent.py` | Main agent: trigger-word tool routing, Ollama tool-calling loop, plain-text tool-call fallback parser, post-generation safety filter, Gemini fallback |
| `climate_llm.py` | Ollama connectivity check + `summarize_hazard_lookup()` for grounded web-result summarization |
| `weather.py` | **Live** Open-Meteo Forecast + Flood API integration. No key needed. Geocodes Ghana city names, returns plain-language hazard alerts with action guidance |
| `cache.py` | Static FAQ cache - bypasses the model entirely for common questions |
| `benchmark.py` | Empirical latency comparison between phi4-mini:q4_K_M and gemma3:2b on your actual hardware |
| `env.example` | Environment variables template |

## Setup

```bash
# 1. Pull the base model at the right quantization
ollama pull phi4-mini:q4_K_M

# 2. Build the custom Climate Guide model from the Modelfile
ollama create ecopulse-guide -f Modelfile.ecopulse

# 3. Copy and fill in env
cp env.example .env

# 4. (Optional but recommended) Run the benchmark on your actual box
#    before locking in phi4-mini - see benchmark.py
python benchmark.py
```

## How to call it

```python
from climate_agent import ask

reply = ask("What should I do if there's flooding near me?", user_id=123)
print(reply)
```

`user_id` maintains per-user conversation history across turns.
Pass `0` if you don't have a real per-user ID yet (shared history).
Pass `"clear"` as the text to wipe history for a user.

Cache check can be added before `ask()`:

```python
from cache import get_cached_response
from climate_agent import ask

reply = get_cached_response(text) or ask(text, user_id=user_id)
```

## What still needs wiring up (TODOs for the next session)

- ~~**Real alert backend**~~ ✅ **DONE** — `weather.py` calls Open-Meteo Forecast + Flood APIs. No API key. Just works.
- **Real reports backend** in `get_investigation_evidence()` - still stub data. Wire to EcoPulse's reports DB.
- **Real quiz bank** in `get_quiz_question()` - still returns a hardcoded pretend question.
- ✅ **Cache wired into the agent** — `cache.py` now answers common FAQ queries before the Ollama call.
- **Safety filter hardening** - `_safety_check()` in `climate_agent.py` uses keyword/regex matching. A second cheap classification pass ("does this reply suggest physical risk: yes/no") would be more robust but costs an extra inference call. Current version will have false positives and false negatives.

## Architecture notes

The routing hierarchy is:
```
User message
  → cache.py (static FAQ lookup, instant)
  → climate_agent.ask()
      → climate_llm.is_configured()?
          YES → _run_ollama_agent() (local phi4-mini via Ollama, no API cost)
                  → trigger-word check → attach only relevant tools
                  → Ollama /api/chat loop (up to 6 tool iterations)
                  → _parse_text_tool_calls() fallback if model emits JSON text
                  → _safety_check() before returning
          NO  → _ask_gemini() (Gemini with retry/backoff, API key required)
                  → _safety_check() before returning
```

This is the same tiered pattern as novelbot/Thoth. Read `novelbot/bot/ai_agent.py`
for the full reasoning behind the trigger-word gating and tool-count overhead
numbers - that file has more detailed comments than this one.
