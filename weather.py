"""
weather.py - real weather and flood data from Open-Meteo for EcoPulse.

Replaces the stub get_active_alert() in climate_agent.py with two actual
API calls: Open-Meteo Forecast API (precipitation, temperature, weather
code) and Open-Meteo Flood API (river discharge). Both are completely
free, no API key, no signup, no rate-limit concerns at hackathon scale.

APIs used:
  Forecast: https://api.open-meteo.com/v1/forecast
  Flood:    https://flood-api.open-meteo.com/v1/flood
  Geocode:  https://geocoding-api.open-meteo.com/v1/search

All three are Open-Meteo services. Data licence: CC BY 4.0.
Attribution required - see ATTRIBUTION at the bottom of this file.

HOW TO WIRE INTO climate_agent.py:
  Replace the stub get_active_alert() inside _build_tools() with:

      from weather import get_active_alert
      # then just use get_active_alert directly as the tool function

  Or call it directly from anywhere:
      from weather import get_active_alert
      print(get_active_alert("Accra"))
      print(get_active_alert("5.6037,-0.1870"))   # raw lat,lon also works
"""
import logging
import requests
from datetime import datetime, timezone

logger = logging.getLogger("weather")

FORECAST_URL  = "https://api.open-meteo.com/v1/forecast"
FLOOD_URL     = "https://flood-api.open-meteo.com/v1/flood"
GEOCODE_URL   = "https://geocoding-api.open-meteo.com/v1/search"
TIMEOUT       = 10  # seconds - fast APIs, no reason to wait longer

# ---------------------------------------------------------------------------
# Ghana city coordinate table - avoids a geocoding round-trip for the most
# common inputs. Open-Meteo's geocoding API is used as a fallback for
# anything not in this list.
# ---------------------------------------------------------------------------
GHANA_CITIES: dict[str, tuple[float, float]] = {
    "accra":       (5.6037,  -0.1870),
    "kumasi":      (6.6885,  -1.6244),
    "tamale":      (9.4008,  -0.8393),
    "cape coast":  (5.1053,  -1.2466),
    "tema":        (5.6698,  -0.0166),
    "takoradi":    (4.8845,  -1.7554),
    "sunyani":     (7.3349,  -2.3276),
    "koforidua":   (6.0940,  -0.2607),
    "ho":          (6.6011,   0.4712),
    "wa":          (10.0601,  -2.5099),
    "bolgatanga":  (10.7856,  -0.8514),
    "sekondi":     (4.9437,  -1.7040),
    "ada":         (5.7860,   0.6260),   # coastal, flood-prone
    "keta":        (5.9100,   1.0050),   # coastal erosion area
    "korle-bu":    (5.5491,  -0.2341),   # Accra flood zone
    "lapaz":       (5.6219,  -0.2468),   # Accra flood zone
    "achimota":    (5.6317,  -0.2294),
    "kasoa":       (5.5269,  -0.4261),
    "ashaiman":    (5.6951,  -0.0286),
}

# ---------------------------------------------------------------------------
# WMO weather interpretation codes used by Open-Meteo.
# Maps code -> (short label, hazard level: 0=none 1=watch 2=warning 3=alert)
# ---------------------------------------------------------------------------
WMO_CODES: dict[int, tuple[str, int]] = {
    0:  ("clear sky",                    0),
    1:  ("mainly clear",                 0),
    2:  ("partly cloudy",                0),
    3:  ("overcast",                     0),
    45: ("fog",                          1),
    48: ("icy fog",                      1),
    51: ("light drizzle",                0),
    53: ("moderate drizzle",             0),
    55: ("heavy drizzle",                1),
    61: ("light rain",                   0),
    63: ("moderate rain",                1),
    65: ("heavy rain",                   2),
    71: ("light snow",                   0),
    73: ("moderate snow",                1),
    75: ("heavy snow",                   2),
    77: ("snow grains",                  1),
    80: ("light rain showers",           0),
    81: ("moderate rain showers",        1),
    82: ("violent rain showers",         3),
    85: ("slight snow showers",          1),
    86: ("heavy snow showers",           2),
    95: ("thunderstorm",                 2),
    96: ("thunderstorm with light hail", 3),
    99: ("thunderstorm with heavy hail", 3),
}


def _resolve_coordinates(location: str) -> tuple[float, float] | None:
    """Converts a location string to (lat, lon).

    Accepts:
      - Known Ghana city name (checked against GHANA_CITIES first)
      - Raw "lat,lon" string (e.g. "5.6037,-0.1870")
      - Anything else: tried against Open-Meteo's geocoding API,
        biased toward Ghana results (country_code=GH)

    Returns None if resolution fails.
    """
    # raw coordinates
    stripped = location.strip()
    if "," in stripped:
        parts = stripped.split(",", 1)
        try:
            lat, lon = float(parts[0].strip()), float(parts[1].strip())
            # sanity check: Ghana is roughly 4-11°N, 3.5°W-1.2°E
            return lat, lon
        except ValueError:
            pass  # fall through - might be "Cape Coast, Ghana" style

    # known city lookup
    key = stripped.lower()
    if key in GHANA_CITIES:
        return GHANA_CITIES[key]

    # partial match (e.g. "Accra, Ghana" or "Greater Accra")
    for city_name, coords in GHANA_CITIES.items():
        if city_name in key:
            return coords

    # Open-Meteo geocoding fallback
    try:
        resp = requests.get(
            GEOCODE_URL,
            params={"name": location, "count": 5, "language": "en", "format": "json"},
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        results = resp.json().get("results") or []
        # prefer Ghana results (country_code == "GH") if any
        ghana = [r for r in results if r.get("country_code") == "GH"]
        pick = (ghana or results)
        if pick:
            return pick[0]["latitude"], pick[0]["longitude"]
    except (requests.RequestException, KeyError, ValueError) as e:
        logger.warning(f"Geocoding failed for {location!r}: {e}")

    return None


def _fetch_forecast_wttr(lat: float, lon: float) -> dict | None:
    """Fallback weather fetch via wttr.in when Open-Meteo is unreachable.
    wttr.in is a separate free service (no API key, different infrastructure)
    that returns a JSON weather summary. We normalise its output into the
    same shape _interpret_hazards() expects from Open-Meteo so the rest of
    the pipeline doesn't need to know which source was used.

    Only the 'current' block is populated from wttr.in - daily precipitation
    sums and flood data are unavailable via this path, so hazard detection
    will be less detailed than the primary path. That's acceptable for a
    fallback: some data is better than an error message.

    wttr.in JSON format docs: https://wttr.in/:help
    """
    try:
        resp = requests.get(
            f"https://wttr.in/{lat},{lon}",
            params={"format": "j1"},
            headers={"User-Agent": "EcoPulse/1.0 (climate-guide-bot)"},
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()
        current_condition = (data.get("current_condition") or [{}])[0]

        temp_c    = float(current_condition.get("temp_C", 0))
        feels_c   = float(current_condition.get("FeelsLikeC", temp_c))
        precip_mm = float(current_condition.get("precipMM", 0))
        wind_kmh  = float(current_condition.get("windspeedKmph", 0))
        humidity  = float(current_condition.get("humidity", 0))
        desc      = (current_condition.get("weatherDesc") or [{}])[0].get("value", "")

        # Map wttr's English description to a rough WMO code for hazard scoring.
        # Not exhaustive, but covers the cases that matter most for Ghana.
        desc_lower = desc.lower()
        if "thunder" in desc_lower and "hail" in desc_lower:
            wmo = 96
        elif "thunder" in desc_lower:
            wmo = 95
        elif "heavy rain" in desc_lower or "torrential" in desc_lower:
            wmo = 65
        elif "moderate rain" in desc_lower:
            wmo = 63
        elif "light rain" in desc_lower or "drizzle" in desc_lower:
            wmo = 61
        elif "overcast" in desc_lower or "cloudy" in desc_lower:
            wmo = 3
        else:
            wmo = 1

        # Return in the same shape _interpret_hazards() reads from Open-Meteo
        return {
            "_source": "wttr.in (fallback)",
            "current": {
                "temperature_2m":      temp_c,
                "apparent_temperature": feels_c,
                "precipitation":        precip_mm,
                "weather_code":         wmo,
                "wind_speed_10m":       wind_kmh,
                "relative_humidity_2m": humidity,
            },
            "current_units": {},
            "daily": {},   # not available via wttr.in - flood/rain-sum checks will be skipped
        }
    except (requests.RequestException, ValueError, KeyError, IndexError) as e:
        logger.warning(f"wttr.in fallback also failed at ({lat},{lon}): {e}")
        return None


def _fetch_forecast(lat: float, lon: float) -> dict | None:
    """Fetches current weather conditions and today's precipitation forecast
    from Open-Meteo's Forecast API. Returns the parsed JSON or None on error.

    Variables requested:
      current: temperature_2m, apparent_temperature, precipitation,
               weather_code, wind_speed_10m
      daily:   precipitation_sum, precipitation_probability_max,
               temperature_2m_max, weather_code
    """
    try:
        resp = requests.get(
            FORECAST_URL,
            params={
                "latitude":  lat,
                "longitude": lon,
                "current": ",".join([
                    "temperature_2m",
                    "apparent_temperature",
                    "precipitation",
                    "weather_code",
                    "wind_speed_10m",
                    "relative_humidity_2m",
                ]),
                "daily": ",".join([
                    "weather_code",
                    "temperature_2m_max",
                    "precipitation_sum",
                    "precipitation_probability_max",
                ]),
                "timezone": "Africa/Accra",   # GMT+0, Ghana's timezone
                "forecast_days": 3,
            },
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        return resp.json()
    except (requests.RequestException, ValueError) as e:
        logger.warning(f"Forecast API error at ({lat},{lon}): {e}")
        return None


def _fetch_flood(lat: float, lon: float) -> dict | None:
    """Fetches 3-day river discharge forecast from Open-Meteo's Flood API
    for the largest river within 5km of the given coordinates.
    Returns the parsed JSON or None on error / no river nearby."""
    try:
        resp = requests.get(
            FLOOD_URL,
            params={
                "latitude":  lat,
                "longitude": lon,
                "daily": "river_discharge",
                "forecast_days": 3,
            },
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()
        # API returns an error field if there's no river in the area
        if data.get("error"):
            return None
        return data
    except (requests.RequestException, ValueError) as e:
        logger.warning(f"Flood API error at ({lat},{lon}): {e}")
        return None


def _interpret_hazards(forecast: dict, flood: dict | None) -> dict:
    """Converts raw Open-Meteo JSON into structured hazard info.
    Returns a dict with keys:
      hazard_level: int 0-3 (0=none, 1=watch, 2=warning, 3=alert)
      hazard_types: list of str (e.g. ["heavy rain", "flood risk"])
      current_temp: float (Celsius)
      current_rain_mm: float (mm in last hour)
      today_rain_mm: float (mm forecast for today)
      today_rain_prob: int (percent probability)
      wind_kmh: float
      weather_label: str (human-readable current condition)
      river_discharge_m3s: float | None
      flood_trend: str ("rising", "stable", "falling", None)
    """
    result = {
        "hazard_level": 0,
        "hazard_types": [],
        "current_temp": None,
        "current_rain_mm": None,
        "today_rain_mm": None,
        "today_rain_prob": None,
        "wind_kmh": None,
        "weather_label": "unknown",
        "river_discharge_m3s": None,
        "flood_trend": None,
    }

    # current conditions
    current = forecast.get("current", {})
    current_units = forecast.get("current_units", {})
    result["current_temp"]         = current.get("temperature_2m")
    result["apparent_temperature"] = current.get("apparent_temperature")
    result["current_rain_mm"] = current.get("precipitation", 0.0)
    result["wind_kmh"]        = current.get("wind_speed_10m")

    wmo = current.get("weather_code", 0)
    label, level = WMO_CODES.get(wmo, ("unknown conditions", 0))
    result["weather_label"] = label
    if level > result["hazard_level"]:
        result["hazard_level"] = level
    if level >= 2:
        result["hazard_types"].append(label)

    # heat warning: feel-like temp > 38°C is dangerous in Ghana context
    apparent = current.get("apparent_temperature")
    if apparent is not None and apparent >= 38:
        result["hazard_types"].append("extreme heat")
        if result["hazard_level"] < 2:
            result["hazard_level"] = 2
    elif apparent is not None and apparent >= 35:
        if "heat watch" not in result["hazard_types"]:
            result["hazard_types"].append("heat watch")
        if result["hazard_level"] < 1:
            result["hazard_level"] = 1

    # daily forecast - look at today (index 0)
    daily = forecast.get("daily", {})
    daily_rain    = (daily.get("precipitation_sum") or [None])[0]
    daily_prob    = (daily.get("precipitation_probability_max") or [None])[0]
    daily_wmo     = (daily.get("weather_code") or [None])[0]
    result["today_rain_mm"]  = daily_rain
    result["today_rain_prob"] = daily_prob

    # high daily rainfall threshold for Ghana (ITCZ-driven rain events):
    # > 25mm/day = significant; > 50mm/day = heavy/flood-risk
    if daily_rain is not None:
        if daily_rain >= 50:
            if "heavy rainfall" not in result["hazard_types"]:
                result["hazard_types"].append("heavy rainfall")
            if result["hazard_level"] < 2:
                result["hazard_level"] = 2
        elif daily_rain >= 25:
            if "significant rainfall" not in result["hazard_types"]:
                result["hazard_types"].append("significant rainfall")
            if result["hazard_level"] < 1:
                result["hazard_level"] = 1

    # flood / river discharge
    if flood:
        discharges = flood.get("daily", {}).get("river_discharge") or []
        if discharges:
            today_q = discharges[0]
            result["river_discharge_m3s"] = today_q
            # trend: compare today vs tomorrow if available
            if len(discharges) >= 2:
                delta = discharges[1] - discharges[0]
                if delta > discharges[0] * 0.10:   # >10% increase
                    result["flood_trend"] = "rising"
                    if "flood risk" not in result["hazard_types"]:
                        result["hazard_types"].append("flood risk")
                    if result["hazard_level"] < 2:
                        result["hazard_level"] = 2
                elif delta < -(discharges[0] * 0.10):
                    result["flood_trend"] = "falling"
                else:
                    result["flood_trend"] = "stable"

    return result


def _format_alert(location_name: str, h: dict) -> str:
    """Converts the hazard dict into a plain-language alert string, suitable
    for the Climate Guide to return directly or pass to the AI for further
    elaboration. Follows the Climate Guide's voice: short, plain, local."""
    level_labels = {0: "no active hazards", 1: "WATCH", 2: "WARNING", 3: "ALERT"}
    level_str = level_labels.get(h["hazard_level"], "WATCH")

    lines = []

    # headline
    if h["hazard_level"] == 0:
        lines.append(f"{location_name}: No active weather hazards right now.")
    else:
        hazard_list = ", ".join(h["hazard_types"]) if h["hazard_types"] else "weather hazard"
        lines.append(f"{location_name}: {level_str} — {hazard_list}.")

    # current conditions line
    cond_parts = []
    if h["current_temp"] is not None:
        cond_parts.append(f"{h['current_temp']:.0f}°C")
    if h["weather_label"] and h["weather_label"] != "unknown":
        cond_parts.append(h["weather_label"])
    if h["wind_kmh"] is not None and h["wind_kmh"] > 20:
        cond_parts.append(f"wind {h['wind_kmh']:.0f} km/h")
    if cond_parts:
        lines.append("Current conditions: " + ", ".join(cond_parts) + ".")

    # rain forecast line
    if h["today_rain_mm"] is not None and h["today_rain_mm"] > 0:
        prob_str = f" ({h['today_rain_prob']}% chance)" if h["today_rain_prob"] else ""
        lines.append(f"Rainfall today: {h['today_rain_mm']:.0f} mm forecast{prob_str}.")

    # flood / river line
    if h["river_discharge_m3s"] is not None:
        trend_str = ""
        if h["flood_trend"] == "rising":
            trend_str = " and rising — flood risk increasing"
        elif h["flood_trend"] == "falling":
            trend_str = " and falling"
        lines.append(
            f"Nearby river discharge: {h['river_discharge_m3s']:.1f} m³/s{trend_str}."
        )

    # action guidance by level
    if h["hazard_level"] >= 3:
        lines.append(
            "Recommended action: move to higher ground if near low-lying areas or "
            "drainage channels. Avoid all floodwater — moving water is deeper and "
            "faster than it looks. Stay indoors if there is active thunderstorm activity."
        )
    elif h["hazard_level"] == 2:
        lines.append(
            "Recommended action: avoid low-lying areas and open drains. "
            "Have emergency supplies accessible. Monitor updates closely."
        )
    elif h["hazard_level"] == 1:
        lines.append(
            "Recommended action: stay aware of conditions and avoid unnecessary "
            "travel near drainage channels or flood-prone areas."
        )

    return " ".join(lines)



def _llm_report(location_name: str, h: dict, using_fallback: bool = False) -> str | None:
    """Sends structured hazard data to the local Ollama model and asks it to
    write a plain-language flood/hazard report in the Climate Guide voice.

    Preferred output path when the LLM is running - produces a more natural
    report than _format_alert()'s hardcoded template. Returns None on any
    failure so get_active_alert() can fall back to _format_alert() silently.
    """
    import os
    import requests as _req

    ollama_host = os.getenv("OLLAMA_HOST", "http://localhost:11434").rstrip("/")
    model = os.getenv("OLLAMA_MODEL", "ecopulse-guide")

    level_labels = {0: "none", 1: "watch", 2: "warning", 3: "alert"}
    hazard_level_str = level_labels.get(h["hazard_level"], "watch")
    hazard_types_str = ", ".join(h["hazard_types"]) if h["hazard_types"] else "none detected"

    def fmt(val, unit, decimals=0):
        if val is None:
            return "unknown"
        return f"{val:.{decimals}f}{unit}"

    river_line = (
        f"{h['river_discharge_m3s']:.1f} m3/s, trend: {h['flood_trend'] or 'unknown'}"
        if h["river_discharge_m3s"] is not None
        else "no river within 5km (or backup source used)"
    )

    data_block = "\n".join([
        f"Location: {location_name}",
        f"Hazard level: {hazard_level_str} ({h['hazard_level']} of 3)",
        f"Hazard types: {hazard_types_str}",
        f"Current temperature: {fmt(h['current_temp'], 'C')}",
        f"Apparent (feels-like) temperature: {fmt(h.get('apparent_temperature'), 'C')}",
        f"Current rainfall last hour: {fmt(h['current_rain_mm'], 'mm', 1)}",
        f"Forecast rainfall today: {fmt(h['today_rain_mm'], 'mm')} "
        f"({h['today_rain_prob']}% probability)" if h["today_rain_mm"] is not None
        else "Forecast rainfall today: unknown",
        f"Wind speed: {fmt(h['wind_kmh'], ' km/h')}",
        f"Current sky conditions: {h['weather_label']}",
        f"Nearby river discharge: {river_line}",
    ])

    fallback_note = (
        "\nNote: this data is from a backup weather source - "
        "daily rainfall totals and river flood data are unavailable."
        if using_fallback else ""
    )

    prompt = (
        f"Here is the current weather and flood data for {location_name}:\n\n"
        f"{data_block}{fallback_note}\n\n"
        "Write a 3-5 sentence hazard report for a young person in Ghana reading "
        "this in a mobile app. Use plain, warm, local language - not textbook language. "
        "Include: what conditions are like right now, whether there is flood or storm "
        "risk, and one specific action they should or should not take. "
        "If hazard level is none, reassure them briefly. "
        "Never suggest physically going to inspect a hazard site. "
        "Do not repeat the raw numbers - explain what they mean instead."
    )

    try:
        resp = _req.post(
            f"{ollama_host}/api/chat",
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "stream": False,
                "options": {
                    "temperature": 0.5,
                    "num_predict": 180,
                    "num_ctx": 512,
                },
                "keep_alive": "30m",
            },
            timeout=60,
        )
        resp.raise_for_status()
        reply = (resp.json().get("message", {}).get("content") or "").strip()
        return reply if reply else None
    except Exception as e:
        logger.warning(f"LLM report generation failed for {location_name}: {e}")
        return None


def get_active_alert(location: str) -> str:
    """Main entry point. Accepts a Ghana city name, a district/area name, or
    a 'lat,lon' coordinate string. Returns a plain-language hazard alert
    string (no hazard, watch, warning, or alert) with action guidance.

    This function is designed to be used directly as the get_active_alert
    tool in climate_agent.py's _build_tools() - it returns a str and never
    raises, so it is safe to call from the tool-calling loop without extra
    error handling.

    Examples:
        get_active_alert("Accra")
        get_active_alert("Korle-Bu")
        get_active_alert("5.6037,-0.1870")
    """
    if not location or not location.strip():
        return "Please provide a location name (e.g. 'Accra', 'Kumasi', 'Tema')."

    coords = _resolve_coordinates(location)
    if coords is None:
        return (
            f"Could not find coordinates for '{location}'. "
            f"Try a major Ghana city name like 'Accra', 'Kumasi', or 'Tamale'."
        )

    lat, lon = coords
    # Use the cleaned location name (title-cased) for display
    display_name = location.strip().title()

    forecast = _fetch_forecast(lat, lon)
    if forecast is None:
        # Primary (Open-Meteo) failed - try wttr.in as fallback.
        logger.info(f"Open-Meteo unavailable for ({lat},{lon}), trying wttr.in fallback")
        forecast = _fetch_forecast_wttr(lat, lon)

    if forecast is None:
        return (
            f"Weather data for {display_name} is temporarily unavailable "
            f"(both Open-Meteo and wttr.in are unreachable). Try again in a moment."
        )

    using_fallback = forecast.get("_source") == "wttr.in (fallback)"
    flood = _fetch_flood(lat, lon) if not using_fallback else None

    hazards = _interpret_hazards(forecast, flood)

    # Try the LLM first - it produces a more natural, context-aware report
    # than the hardcoded template. Falls back to _format_alert() silently
    # if Ollama is unreachable or returns an empty response.
    import climate_llm
    if climate_llm.is_configured():
        llm_result = _llm_report(display_name, hazards, using_fallback=using_fallback)
        if llm_result:
            if using_fallback:
                llm_result += " (Note: backup weather source used - flood river data unavailable.)"
            return llm_result

    # Template fallback - always works, no LLM needed
    alert = _format_alert(display_name, hazards)
    if using_fallback:
        alert += " (Note: using backup weather source - flood data unavailable.)"
    return alert


# ---------------------------------------------------------------------------
# Quick test - run this file directly to check the API is working:
#   python weather.py
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import sys
    logging.basicConfig(level=logging.INFO)
    test_locations = ["Accra", "Kumasi", "Korle-Bu", "Tamale", "Keta"]
    for loc in test_locations:
        print(f"\n{'='*60}")
        print(f"Testing: {loc}")
        print(get_active_alert(loc))

# ---------------------------------------------------------------------------
# ATTRIBUTION (required under CC BY 4.0):
# Weather data provided by Open-Meteo (https://open-meteo.com).
# Weather data source: ECMWF, NOAA, DWD, Météo-France and others
# via Open-Meteo's open-source API.
# Flood data: Global Flood Awareness System (GloFAS) via Open-Meteo.
# ---------------------------------------------------------------------------
