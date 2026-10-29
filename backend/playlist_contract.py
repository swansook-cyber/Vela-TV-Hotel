"""Pure validation for a future authenticated hotel media manifest."""
import re
from urllib.parse import urlsplit

_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_ID = re.compile(r"^[a-zA-Z0-9_-]{1,64}$")


def valid_manifest(manifest: dict, allowed_media_host: str) -> bool:
    if not isinstance(manifest, dict) or type(manifest.get("schema_version")) is not int or manifest["schema_version"] != 1:
        return False
    if not isinstance(manifest.get("revision"), str) or not manifest["revision"]:
        return False
    hotel = manifest.get("hotel")
    if not isinstance(hotel, dict) or not isinstance(hotel.get("name"), str) or not hotel["name"]:
        return False
    media = manifest.get("media")
    if not isinstance(media, list) or not 1 <= len(media) <= 30:
        return False
    ids = set()
    for item in media:
        if not isinstance(item, dict) or not _ID.fullmatch(str(item.get("id", ""))):
            return False
        if item["id"] in ids:
            return False
        ids.add(item["id"])
        if item.get("type") not in ("image", "video"):
            return False
        if not isinstance(item.get("sha256"), str) or not _SHA256.fullmatch(item["sha256"]):
            return False
        extension = item.get("extension")
        allowed = {"image": {"jpg", "jpeg", "png"}, "video": {"mp4"}}
        if extension not in allowed[item["type"]]:
            return False
        duration = item.get("duration_seconds")
        if type(duration) is not int or not 1 <= duration <= 120:
            return False
        url = item.get("url")
        # Android rejects query strings, fragments and control characters.
        if not isinstance(url, str) or "?" in url or "#" in url or any(ord(c) < 32 for c in url):
            return False
        try:
            parsed = urlsplit(url)
            if (parsed.scheme != "https" or parsed.hostname != allowed_media_host
                    or parsed.username is not None or parsed.password is not None or parsed.port not in (None, 443)
                    or parsed.fragment):
                return False
        except ValueError:
            return False
    return True
