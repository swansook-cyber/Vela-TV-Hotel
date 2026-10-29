"""Validation of published guest-facing content. Unverified fields stay explicit."""
from __future__ import annotations

import json

SECTIONS = ("resort", "explore", "guest")
QUICK_APPS = ["Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI"]


def valid_content(value: object) -> bool:
    if not isinstance(value, dict) or type(value.get("schema_version")) is not int or value["schema_version"] != 1:
        return False
    if value.get("quick_apps") != QUICK_APPS:
        return False
    if not isinstance(value.get("hotel"), dict) or not isinstance(value["hotel"].get("name"), str):
        return False
    if value["hotel"]["name"] != "Ao Nang Fiore Resort":
        return False
    welcome = value.get("guest_welcome")
    if not isinstance(welcome, dict) or welcome.get("mode") != "generic" or welcome.get("value") != "":
        # Room-specific greetings belong to per-device runtime data, not shared content.
        return False
    for section in SECTIONS:
        data = value.get(section)
        if not isinstance(data, dict) or not isinstance(data.get("items"), list) or len(data["items"]) > 40:
            return False
        for item in data["items"]:
            if not isinstance(item, dict) or set(item) - {"title", "description", "verified", "category"}:
                return False
            if not isinstance(item.get("title"), str) or not 1 <= len(item["title"]) <= 100:
                return False
            if not isinstance(item.get("description"), str) or len(item["description"]) > 2000:
                return False
            if type(item.get("verified")) is not bool:
                return False
            if not item["verified"] and "TEST DATA" not in item["description"]:
                return False
    return len(json.dumps(value, ensure_ascii=False).encode()) <= 256 * 1024
