"""Build local-only resort photo assets from the official website for private TV QA.

These images have no confirmed redistribution license. The output directory is
gitignored and must not be committed, uploaded to CI, or released publicly.
"""

from __future__ import annotations

import hashlib
import io
import json
from datetime import date
from pathlib import Path

import requests
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "android-client/src/main/assets/fiore_internal"
SOURCES = {
    "hero": "https://www.aonangfiore.com/wp-content/uploads/2026/02/Aonang-Fiore2.webp",
    "rooms": "https://www.aonangfiore.com/wp-content/uploads/2018/12/Deluxe-Cottage-Double-Main.webp",
    "dining": "https://www.aonangfiore.com/wp-content/uploads/2026/02/Dining-Main.webp",
    "facilities": "https://www.aonangfiore.com/wp-content/uploads/2026/02/Swimming-Pool3.webp",
    "offers_backdrop": "https://www.aonangfiore.com/wp-content/uploads/2026/02/Aonang-Fiore10.webp",
}


def fetch_photo(url: str) -> tuple[Image.Image, str]:
    response = requests.get(url, timeout=30)
    response.raise_for_status()
    if not response.headers.get("Content-Type", "").lower().startswith("image/"):
        raise ValueError(f"Not an image: {url}")
    data = response.content
    if len(data) > 12_000_000:
        raise ValueError(f"Unexpectedly large image: {url}")
    with Image.open(io.BytesIO(data)) as original:
        return ImageOps.exif_transpose(original).convert("RGB"), hashlib.sha256(data).hexdigest()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    photos = {}
    records = []
    for name, url in SOURCES.items():
        photo, source_sha = fetch_photo(url)
        photos[name] = photo
        records.append({
            "slot": name,
            "source_url": url,
            "source_sha256": source_sha,
            "source_size": list(photo.size),
            "rights": "Unconfirmed; internal QA only; no public redistribution",
            "checked_utc_date": date.today().isoformat(),
        })
    hero = ImageOps.fit(photos["hero"], (1600, 900), method=Image.Resampling.LANCZOS)
    hero.save(OUTPUT / "hero.webp", "WEBP", quality=79, method=6)
    sheet = Image.new("RGB", (1600, 800))
    for index, name in enumerate(("rooms", "dining", "facilities", "offers_backdrop")):
        tile = ImageOps.fit(photos[name], (800, 400), method=Image.Resampling.LANCZOS)
        sheet.paste(tile, ((index % 2) * 800, (index // 2) * 400))
    sheet.save(OUTPUT / "resort_grid.webp", "WEBP", quality=79, method=6)
    (OUTPUT / "SOURCE-MANIFEST.json").write_text(
        json.dumps({"source_page": "https://www.aonangfiore.com/gallery/", "photos": records}, indent=2) + "\n",
        encoding="utf-8",
    )
    for path in OUTPUT.glob("*.webp"):
        print(f"{path.name}: {path.stat().st_size} bytes, sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
