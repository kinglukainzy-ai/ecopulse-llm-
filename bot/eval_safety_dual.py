"""
eval_safety_dual.py - dual-layer eval: regex (_safety_check) +
gated classifier (_safety_check_v2) against the SAME test cases used in
eval_safety.py, so results are directly comparable.

IMPORTANT CAVEAT: there's no live Ollama instance in this sandbox, so the
classifier layer is tested with an injected MOCK backend rather than a
real model call. The mock is intentionally NOT a perfect oracle - it uses
a looser, independent heuristic (different from the regex patterns) to
approximate what a real small-model classifier might catch, so this eval
is validating the GATING ARCHITECTURE (does layering help, does gating
correctly skip irrelevant replies, is latency contained) rather than
claiming real-world classifier accuracy. The real number only comes from
swapping in the actual Ollama call (the default, ungated `backend=None`
path in `_classify_physical_risk`) once a model is running.
"""
import re
from climate_agent import _safety_check, _safety_check_v2, _SAFE_FALLBACK_REPLY, _should_run_classifier
from eval_safety import CASES

# ---------------------------------------------------------------------------
# Mock classifier backend: a deliberately independent, looser heuristic
# (not reusing the _RISK_PATTERNS regex) standing in for a real small-model
# call. Looks for suggestion phrasing + going/seeing/checking ideas without
# requiring an exact noun match within N chars, and is negation-aware via a
# whole-sentence check rather than the same windowed logic as the regex.
# ---------------------------------------------------------------------------
_MOCK_SUGGESTION = re.compile(
    r"\b(go|head|swing|pop|stop|drop|walk|drive|waka|wander|see|get|take|"
    r"check|visit|confirm|verify|inspect)\b"
)
_MOCK_LOCATION_IDEA = re.compile(
    r"\b(site|location|spot|area|mine|dump|bush|place|there|that side|"
    r"yourself|in person|own eyes|closer)\b"
)
_MOCK_NEGATION = re.compile(
    r"\b(don't|do not|dont|never|avoid|shouldn't|should not|not safe|"
    r"unsafe|no need|cannot|can't|won't)\b"
)


def mock_classifier_backend(reply: str) -> str:
    lowered = reply.lower()
    has_suggestion = bool(_MOCK_SUGGESTION.search(lowered))
    has_location = bool(_MOCK_LOCATION_IDEA.search(lowered))
    has_negation = bool(_MOCK_NEGATION.search(lowered))

    # Rhetorical "why don't you X" - negation word present but used as a
    # suggestion opener, not a refusal. A real semantic classifier should
    # catch this; the mock approximates it by checking if negation is
    # immediately followed by "you" + a suggestion verb (the rhetorical
    # construction), in which case it's NOT treated as real negation.
    rhetorical = bool(re.search(r"\bdon't you\b", lowered)) or bool(re.search(r"\bwhy not\b", lowered))

    if rhetorical:
        has_negation = False

    if has_suggestion and has_location and not has_negation:
        return "yes"
    return "no"


def run():
    results = {"TP": [], "TN": [], "FP": [], "FN": []}
    classifier_calls = 0

    for category, label, reply, should_fire in CASES:
        regex_only_fired = (_safety_check(reply) == _SAFE_FALLBACK_REPLY)
        if not regex_only_fired and _should_run_classifier(reply):
            classifier_calls += 1

        output = _safety_check_v2(reply, classifier_backend=mock_classifier_backend)
        fired = (output == _SAFE_FALLBACK_REPLY)

        if should_fire and fired:
            bucket = "TP"
        elif not should_fire and not fired:
            bucket = "TN"
        elif should_fire and not fired:
            bucket = "FN"
        else:
            bucket = "FP"

        layer = "regex" if regex_only_fired else ("classifier" if fired else "none")
        results[bucket].append((category, label, reply, layer))

    total = len(CASES)
    tp, tn, fp, fn = (len(results[k]) for k in ("TP", "TN", "FP", "FN"))

    print("=" * 70)
    print("DUAL-LAYER (regex + gated classifier) EVAL RESULTS")
    print("=" * 70)
    print(f"Total cases: {total}")
    print(f"Classifier was invoked on {classifier_calls}/{total} cases "
          f"({classifier_calls/total:.0%}) - i.e. it was SKIPPED on "
          f"{total - classifier_calls} cases either because regex already "
          f"caught it or the reply wasn't topically risky.")
    print()
    print(f"  True Positives  (caught real violation):     {tp}")
    print(f"  True Negatives  (correctly left alone):       {tn}")
    print(f"  False Positives (wrongly blocked safe reply): {fp}")
    print(f"  False Negatives (MISSED real violation):      {fn}  <-- most dangerous")
    print()

    violations = tp + fn
    safe = tn + fp
    if violations:
        print(f"Violation catch rate: {tp}/{violations} = {tp/violations:.0%}")
    if safe:
        print(f"Safe-reply pass rate: {tn}/{safe} = {tn/safe:.0%}")

    print("\n--- Which layer caught each true positive ---")
    layer_counts = {"regex": 0, "classifier": 0}
    for category, label, reply, layer in results["TP"]:
        layer_counts[layer] = layer_counts.get(layer, 0) + 1
    for layer, count in layer_counts.items():
        print(f"  {layer}: {count}")

    for bucket in ("FN", "FP"):
        if results[bucket]:
            print(f"\n--- {bucket} cases ---")
            for category, label, reply, layer in results[bucket]:
                print(f"  [{category}] {label}: {reply[:90]}")

    print("\n--- Comparison vs regex-only ---")
    regex_only_tp = sum(
        1 for c, l, r, sf in CASES if sf and _safety_check(r) == _SAFE_FALLBACK_REPLY
    )
    print(f"  Regex-only catch rate:  {regex_only_tp}/{violations} = {regex_only_tp/violations:.0%}")
    print(f"  Dual-layer catch rate:  {tp}/{violations} = {tp/violations:.0%}")
    print(f"  Classifier calls spent to gain that improvement: {classifier_calls}/{total} replies")


if __name__ == "__main__":
    run()
