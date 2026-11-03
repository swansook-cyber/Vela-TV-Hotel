"""Validation of published guest-facing content. Unverified fields stay explicit."""
from __future__ import annotations

import json
import re

SECTIONS = ("resort", "explore", "guest")
QUICK_APPS = ["Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI"]


def valid_room_service(menu: object) -> bool:
    if not isinstance(menu, dict) or set(menu) != {"menuVersion", "currency", "contact", "categories"}:
        return False
    if not isinstance(menu["menuVersion"], str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,48}", menu["menuVersion"]):
        return False
    if menu["currency"] != "THB" or not isinstance(menu["contact"], str) or not re.fullmatch(r"[0-9]{1,12}", menu["contact"]):
        return False
    categories = menu["categories"]
    if not isinstance(categories, list) or not 1 <= len(categories) <= 20:
        return False
    ids, numbers = set(), set()
    for category in categories:
        if not isinstance(category, dict) or set(category) != {"id", "title_en", "items"}:
            return False
        if not isinstance(category["id"], str) or not re.fullmatch(r"[a-z][a-z0-9_]{0,39}", category["id"]) or category["id"] in ids:
            return False
        ids.add(category["id"])
        if not isinstance(category["title_en"], str) or not 1 <= len(category["title_en"].strip()) <= 80:
            return False
        if not isinstance(category["items"], list) or not 1 <= len(category["items"]) <= 83:
            return False
        for item in category["items"]:
            if not isinstance(item, dict) or set(item) != {"number", "name_th", "name_en", "price_thb"}:
                return False
            number = item["number"]
            if type(number) is not int or not 1 <= number <= 83 or number in numbers:
                return False
            numbers.add(number)
            if any(not isinstance(item[key], str) or not 1 <= len(item[key].strip()) <= 160 for key in ("name_th", "name_en")):
                return False
            if type(item["price_thb"]) is not int or not 1 <= item["price_thb"] <= 10000:
                return False
    return bool(numbers)


def valid_content(value: object) -> bool:
    if not isinstance(value, dict) or type(value.get("schema_version")) is not int or value["schema_version"] != 1:
        return False
    if value.get("quick_apps") != QUICK_APPS:
        return False
    if not isinstance(value.get("hotel"), dict) or not isinstance(value["hotel"].get("name"), str):
        return False
    if value["hotel"]["name"] != "Ao Nang Fiore Resort":
        return False
    if "room_service" in value and not valid_room_service(value["room_service"]):
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
