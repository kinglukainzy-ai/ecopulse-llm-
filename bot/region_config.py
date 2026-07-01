"""
region_config.py - Loads the active EcoPulse region configuration from
a JSON file in the configs/ directory.

Selected by ECOPULSE_REGION env var (default: ghana).
Supported values correspond to filenames in configs/:
    ghana, kenya  (add more by adding configs/<name>.json)

Exposes:
    REGION_NAME          str   e.g. "Ghana"
    LOCAL_LANGUAGE       str   e.g. "Ghanaian Pidgin"
    TIMEZONE             str   e.g. "Africa/Accra"
    GEOCODE_COUNTRY_CODE str   e.g. "GH"
    CITIES               dict  {city_name_lower: (lat, lon), ...}

Raises FileNotFoundError at import time if the selected config file is
not found, so misconfiguration fails loudly rather than silently.
"""
import json
import os
import logging

logger = logging.getLogger("region_config")

_REGION = os.getenv("ECOPULSE_REGION", "ghana").strip().lower()
_CONFIG_DIR = os.path.join(os.path.dirname(__file__), "configs")
_CONFIG_PATH = os.path.join(_CONFIG_DIR, f"{_REGION}.json")

if not os.path.isfile(_CONFIG_PATH):
    raise FileNotFoundError(
        f"EcoPulse region config not found: {_CONFIG_PATH!r}\n"
        f"Set ECOPULSE_REGION to one of: "
        + ", ".join(
            f.removesuffix(".json")
            for f in os.listdir(_CONFIG_DIR)
            if f.endswith(".json")
        )
    )

with open(_CONFIG_PATH, encoding="utf-8") as _f:
    _cfg = json.load(_f)

REGION_NAME: str          = _cfg["region_name"]
LOCAL_LANGUAGE: str       = _cfg["local_language"]
TIMEZONE: str             = _cfg.get("timezone", "UTC")
GEOCODE_COUNTRY_CODE: str = _cfg.get("geocode_country_code", "")
CITIES: dict[str, tuple[float, float]] = {
    k: (float(v[0]), float(v[1])) for k, v in _cfg["cities"].items()
}

logger.info(
    f"[region_config] loaded region={_REGION!r} "
    f"({REGION_NAME}, {LOCAL_LANGUAGE}, {len(CITIES)} cities)"
)
