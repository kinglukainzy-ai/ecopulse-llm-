"""
benchmark.py - compare phi4-mini:q4_K_M vs gemma3:2b on your actual
Oracle Cloud ARM box before committing to a model choice.

Run on your server (after pulling both models):
    ollama pull phi4-mini:q4_K_M
    ollama pull gemma3:2b
    python benchmark.py

Prints per-prompt latency and a summary. Results from general benchmarks
online vary too much across hardware to trust for your specific box -
run this yourself and pick based on what you actually measure.

Prompts are representative of real Climate Guide traffic: a plain greeting
(no tools, short), a hazard question (no tools, medium), and a
verification question (would trigger tools in the real agent, but tested
here as plain chat to isolate raw generation latency from tool overhead).
"""
import os
import time
import requests

OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")
MODELS = ["phi4-mini:q4_K_M", "gemma3:2b"]

# Use the same options the real agent uses in no-tools/plain-chat mode.
# See CLIMATE_CHAT_OPTIONS_PLAIN in climate_agent.py.
OPTIONS = {"num_predict": 100, "temperature": 0.4, "num_ctx": 1024}

PROMPTS = [
    ("greeting",       "Hey"),
    ("hazard_explain", "Why does Accra flood so often during the rainy season?"),
    ("verify_remote",  "I think there's illegal dumping happening near my area. How can I confirm it?"),
    ("safety_edge",    "There's flooding on my street right now, what should I do?"),
]

SYSTEM = (
    "You are the EcoPulse Climate Guide. Plain-spoken, warm, 2-4 sentences. "
    "Never suggest physically approaching a hazard or dumping site."
)


def time_prompt(model: str, prompt: str) -> tuple[float, str]:
    start = time.monotonic()
    try:
        resp = requests.post(
            f"{OLLAMA_HOST}/api/chat",
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": SYSTEM},
                    {"role": "user",   "content": prompt},
                ],
                "stream": False,
                "options": OPTIONS,
            },
            timeout=180,
        )
        resp.raise_for_status()
        content = (resp.json().get("message", {}).get("content") or "").strip()
        elapsed = time.monotonic() - start
        return elapsed, content
    except Exception as e:
        return time.monotonic() - start, f"ERROR: {e}"


def main():
    results: dict[str, dict[str, float]] = {m: {} for m in MODELS}

    for model in MODELS:
        print(f"\n{'='*60}")
        print(f"MODEL: {model}")
        print(f"{'='*60}")
        # warm-up call so the model is loaded before we time real prompts
        print("  warming up model...")
        time_prompt(model, "hello")

        for label, prompt in PROMPTS:
            elapsed, reply = time_prompt(model, prompt)
            results[model][label] = elapsed
            print(f"\n  [{label}] {elapsed:.1f}s")
            print(f"  Q: {prompt}")
            print(f"  A: {reply[:200]}")

    print(f"\n\n{'='*60}")
    print("SUMMARY (seconds per prompt)")
    print(f"{'='*60}")
    header = f"{'Prompt':<20}" + "".join(f"{m:<22}" for m in MODELS)
    print(header)
    for label, _ in PROMPTS:
        row = f"{label:<20}"
        for model in MODELS:
            row += f"{results[model].get(label, 0):.1f}s{'':<18}"
        print(row)

    print(f"\nAverage latency:")
    for model in MODELS:
        avg = sum(results[model].values()) / len(results[model])
        print(f"  {model}: {avg:.1f}s")
    print(
        "\nPick the faster one if the quality difference in the output above "
        "is acceptable. If both are slow, lower num_predict in "
        "climate_agent.py's CLIMATE_CHAT_OPTIONS_PLAIN further, or try a "
        "smaller model (qwen2.5:1.5b, llama3.2:1b) as a last resort."
    )


if __name__ == "__main__":
    main()
