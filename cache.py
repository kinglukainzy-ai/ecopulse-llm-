"""
cache.py - static FAQ cache for the Climate Guide.

Sits in front of the Ollama model. Common questions that repeat constantly
(e.g. "what is climate change", "what do I do in a flood") are served from
a static lookup table, bypassing inference entirely. This is the single
biggest practical latency win for a Q&A-style bot on CPU - an instant dict
lookup vs 5-30s of inference for the same answer every time.

HOW TO USE:
    from cache import get_cached_response

    reply = get_cached_response(user_text)
    if reply:
        return reply   # served from cache, no Ollama call needed
    return ask(user_text, user_id)  # fall through to climate_agent.ask()

This module is wired into climate_agent.py's _run_ollama_agent() so
common FAQ questions are answered instantly before calling the local model.
It remains a good place to add more cached climate-guide responses.

EXTENDING THE CACHE:
Add more entries to CLIMATE_FAQ below. Keys are lowercased canonical
question forms; the matching function does a simple substring check so
"what causes climate change", "what is causing climate change", and
"climate change causes" all match the "climate change" key. Keep answers
short (2-4 sentences) and Ghana-local where relevant, consistent with the
Climate Guide's voice in Modelfile.ecopulse.
"""

CLIMATE_FAQ: dict[str, str] = {
    "what is climate change": (
        "Climate change is the long-term shift in global temperatures and "
        "weather patterns, mainly caused by burning fossil fuels and cutting "
        "down forests, which release gases that trap heat in the atmosphere. "
        "In Ghana, the effects that hit hardest are erratic rainfall, "
        "flooding, and coastal erosion - especially in places like Accra, "
        "Keta, and Ada."
    ),
    "what do i do in a flood": (
        "Move to higher ground if water is rising near you, and avoid "
        "walking or driving through moving floodwater - it is deeper and "
        "faster than it looks. Stay away from drainage channels and "
        "low-lying areas until the water has fully receded and the alert "
        "in the app shows the risk has passed."
    ),
    "what causes flooding": (
        "Flooding in Ghana is mainly caused by intense or prolonged "
        "rainfall overwhelming drainage systems - a problem made worse by "
        "blocked drains, building on floodplains, and the loss of "
        "vegetation that would normally absorb water. Climate change is "
        "making rainfall more erratic and intense, so flood events are "
        "becoming more frequent and unpredictable."
    ),
    "what is deforestation": (
        "Deforestation is the large-scale clearing of forests, usually for "
        "farming, logging, or mining. It worsens climate change because "
        "trees store carbon - cutting them down releases that carbon as CO2 "
        "and removes the forest's ability to absorb future emissions. In "
        "Ghana it also increases flooding by removing the vegetation that "
        "holds soil and slows water runoff."
    ),
    "how do i report illegal dumping": (
        "Use the Report tab in the app to submit a photo and location of "
        "the dumping site. Do not go to the site in person to gather more "
        "evidence - a photo taken from a safe distance is enough to start "
        "the investigation process. The app will walk you through "
        "comparing it against satellite imagery to strengthen the report."
    ),
    "what is osint": (
        "OSINT stands for Open Source Intelligence - using publicly "
        "available information (satellite images, public records, online "
        "maps, geotagged photos) to investigate and verify claims without "
        "needing physical access to a location. In EcoPulse, you use basic "
        "OSINT techniques to verify environmental reports remotely, which "
        "is the same skill used in professional environmental compliance "
        "and investigative journalism."
    ),
    "how do i earn points": (
        "You earn points by logging real climate actions (recycling, using "
        "public transit, planting trees), submitting environmental reports, "
        "completing investigation steps, and answering quiz questions "
        "correctly. Verified reports earn more points than unverified ones "
        "because they produce usable data for local authorities."
    ),
    "what is a green job": (
        "Green jobs are jobs in sectors that help protect or restore the "
        "environment - things like renewable energy, environmental "
        "compliance, conservation, climate data analysis, and "
        "environmental journalism. The digital investigation skills you "
        "build in EcoPulse (OSINT, satellite image analysis, evidence "
        "verification) are directly transferable to several of these roles."
    ),
}


def get_cached_response(text: str) -> str | None:
    """Returns a cached answer if the user's text matches a known FAQ key
    (substring match on the lowercased input), otherwise returns None so
    the caller falls through to the full Ollama/Gemini pipeline.

    Substring matching means short canonical keys ("what is climate change")
    match a variety of natural phrasings ("can you explain what climate
    change is", "tell me what causes climate change") without needing fuzzy
    matching or an embedding lookup."""
    lowered = text.lower()
    for key, answer in CLIMATE_FAQ.items():
        if key in lowered:
            return answer
    return None
