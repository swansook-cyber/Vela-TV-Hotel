"""V1 content package contract. The legacy playlist API remains unchanged."""
from __future__ import annotations

import re
from datetime import datetime

_SHA = re.compile(r"^[0-9a-f]{64}$")
_VERSION = re.compile(r"^[1-9][0-9]*$")
_MEDIA = re.compile(r"^media/[A-Za-z0-9_-]{1,64}\.(?:jpg|jpeg|png|mp4)$")


def valid_sync_manifest(value: object) -> bool:
    if not isinstance(value, dict) or set(value) != {
        "schemaVersion", "contentVersion", "publishedAt", "minAppVersionCode", "files"
    }:
        return False
    if type(value["schemaVersion"]) is not int or value["schemaVersion"] != 1:
        return False
    if not isinstance(value["contentVersion"], str) or not _VERSION.fullmatch(value["contentVersion"]):
        return False
    if type(value["minAppVersionCode"]) is not int or value["minAppVersionCode"] < 15:
        return False
    try:
        if not isinstance(value["publishedAt"], str) or not value["publishedAt"].endswith("Z"):
            return False
        datetime.fromisoformat(value["publishedAt"].replace("Z", "+00:00"))
    except ValueError:
        return False
    files = value["files"]
    if not isinstance(files, list) or not 1 <= len(files) <= 31:
        return False
    if not isinstance(files[0], dict) or files[0].get("kind") != "content" or files[0].get("path") != "content.json":
        return False
    seen = set()
    for file in files:
        if not isinstance(file, dict):
            return False
        kind = file.get("kind")
        required = {"kind", "path", "downloadPath", "sha256", "size"}
        if set(file) != (required if kind == "content" else required | {"durationSeconds"}):
            return False
        path = file["path"]
        if not isinstance(path, str) or path in seen:
            return False
        seen.add(path)
        if type(file["size"]) is not int or not isinstance(file["sha256"], str) or not _SHA.fullmatch(file["sha256"]):
            return False
        if kind == "content":
            if path != "content.json" or file is not files[0] or not 1 <= file["size"] <= 256 * 1024:
                return False
            expected = f"/api/v1/device/content/{value['contentVersion']}/{file['sha256']}.json"
        elif kind in ("image", "video"):
            if not _MEDIA.fullmatch(path) or not 1 <= file["size"] <= 50 * 1024 * 1024:
                return False
            extension = path.rsplit(".", 1)[-1]
            if (kind == "image") != (extension != "mp4"):
                return False
            if type(file["durationSeconds"]) is not int or not 1 <= file["durationSeconds"] <= 120:
                return False
            expected = f"/api/v1/media/{file['sha256']}.{extension}"
        else:
            return False
        if file["downloadPath"] != expected:
            return False
    return sum(item["size"] for item in files) <= 200 * 1024 * 1024
