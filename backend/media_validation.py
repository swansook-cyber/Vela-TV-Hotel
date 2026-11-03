"""Conservative validation for files offered to hotel TVs."""
from __future__ import annotations

import io
import json
import shutil
import subprocess

from PIL import Image, UnidentifiedImageError


def valid_media(data: bytes, extension: str) -> bool:
    if extension in ("jpg", "jpeg", "png"):
        try:
            with Image.open(io.BytesIO(data)) as image:
                expected = "PNG" if extension == "png" else "JPEG"
                if image.format != expected or image.width < 1 or image.height < 1 or \
                        image.width > 3840 or image.height > 2160:
                    return False
                image.verify()
            return True
        except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
            return False
    if extension == "mp4":
        if len(data) < 12 or data[4:8] != b"ftyp":
            return False
        ffprobe = shutil.which("ffprobe")
        if not ffprobe:
            return False  # Pilot never accepts an unverified video codec.
        try:
            result = subprocess.run(
                [ffprobe, "-v", "error", "-show_entries",
                 "format=duration:stream=codec_name,codec_type,width,height", "-of", "json", "-i", "pipe:0"],
                input=data, capture_output=True, timeout=10, check=True)
            probe = json.loads(result.stdout)
            video = [stream for stream in probe.get("streams", [])
                     if stream.get("codec_type") == "video"]
            duration = float(probe.get("format", {}).get("duration", 0))
            return len(video) == 1 and video[0].get("codec_name") == "h264" and \
                1 <= int(video[0].get("width", 0)) <= 1920 and \
                1 <= int(video[0].get("height", 0)) <= 1080 and 0 < duration <= 120
        except (subprocess.SubprocessError, ValueError, TypeError, KeyError):
            return False
    return False
