"""Prepare the owner-selected Fiore photos without changing the originals.

Run from the repository root with Pillow installed:
  python tools/prepare_approved_fiore_media.py --source D:/aonangfiore/Photo \
    --inventory ../media-audit-20261031/selected-29.csv

The resulting media is opt-in for internal QA builds. The script validates every
source SHA-256 before writing anything, then exports center-cropped 16:9 WebP
images at no more than 1920x1080, without EXIF or GPS metadata.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageOps


APPROVED = {
    "welcome": (18, 28),
    "rooms": (1, 5, 37, 130, 164, 169, 200),
    "dining": (227, 234, 243, 251),
    "facilities": (346, 355, 407, 412),
    "guest": (321, 336),
    "grounds": (430, 435, 437, 452, 456),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(4 * 1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("media/fiore-approved/assets/fiore_approved"),
    )
    args = parser.parse_args()
    numbers = [number for group in APPROVED.values() for number in group]
    assert len(numbers) == len(set(numbers)) == 24

    with args.inventory.open(encoding="utf-8-sig", newline="") as inventory_file:
        inventory = {int(row["number"]): row for row in csv.DictReader(inventory_file)}

    # Complete the fail-closed preflight before creating any output directory.
    for number in numbers:
        row = inventory.get(number)
        expected_name = f"Aonang fiore resort-{number}.jpg"
        source = args.source / expected_name
        if not row or row["filename"] != expected_name or not source.is_file():
            raise SystemExit(f"Missing or mismatched source filename: {expected_name}")
        if sha256(source).lower() != row["sha256"].lower():
            raise SystemExit(f"SHA-256 mismatch: {expected_name}")

    args.output.mkdir(parents=True, exist_ok=True)
    items = []
    for category, group in APPROVED.items():
        for number in group:
            source = args.source / f"Aonang fiore resort-{number}.jpg"
            target = args.output / f"fiore_{number:03d}.webp"
            with Image.open(source) as original:
                width, height = original.size
                original.draft("RGB", (3840, 2160))
                image = ImageOps.exif_transpose(original).convert("RGB")
                # Crop to 16:9 after downsampling. The approved source photos
                # are all larger than 1920x1080, so no upscaling occurs.
                if image.width < 1920 or image.height < 1080:
                    raise SystemExit(f"Source too small for 1920x1080: {source.name}")
                image = ImageOps.fit(
                    image,
                    (1920, 1080),
                    method=Image.Resampling.LANCZOS,
                    centering=(0.5, 0.5),
                )
                image.save(target, "WEBP", quality=84, method=6)
            with Image.open(target) as check:
                if check.size != (1920, 1080) or check.getexif():
                    raise SystemExit(f"Invalid export or retained EXIF: {target}")
                check.load()
            items.append(
                {
                    "id": number,
                    "category": category,
                    "asset": target.name,
                    "source_filename": source.name,
                    "source_sha256": inventory[number]["sha256"],
                    "source_size": [width, height],
                    "export_sha256": sha256(target),
                    "export_size": [1920, 1080],
                    "export_bytes": target.stat().st_size,
                    "rights": "Owner-selected for internal QA; redistribution rights not documented",
                    "status": "Facility identity and service availability unconfirmed"
                    if number == 407
                    else "Selected for internal QA",
                }
            )
            print(f"Exported {number}: {target.stat().st_size} bytes", flush=True)

    (args.output / "index.json").write_text(
        json.dumps({"schema_version": 1, "images": items}, ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )
    print(f"Prepared {len(items)} images in {args.output}")


if __name__ == "__main__":
    main()
