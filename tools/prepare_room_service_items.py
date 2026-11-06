"""Convert individually reviewed AI source PNGs into bounded Android TV WebP assets.

The source-map JSON maps approved menu numbers to local generated PNG paths. It
stays outside Git; generated PNGs are never copied into the repository.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "android-client/src/main/assets/hotel_content.json"
OUTPUT = ROOT / "android-client/src/main/assets/room_service_items"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_map", type=Path)
    args = parser.parse_args()
    source_map = json.loads(args.source_map.read_text(encoding="utf-8"))
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    items = [item for category in seed["room_service"]["categories"]
             for item in category["items"]]
    numbers = {item["number"] for item in items}
    assert len(items) == len(numbers) == 81
    assert {37, 38}.isdisjoint(numbers)
    assert {int(number) for number in source_map} == numbers, "Missing or extra artwork source"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    images = []
    for item in sorted(items, key=lambda item: item["number"]):
        number = item["number"]
        source = Path(source_map[str(number)])
        assert source.is_file(), f"Missing source image for item #{number}: {source}"
        asset = f"item_{number:03d}.webp"
        with Image.open(source) as original:
            image = ImageOps.fit(ImageOps.exif_transpose(original).convert("RGB"),
                                 (960, 540), method=Image.Resampling.LANCZOS)
            image.save(OUTPUT / asset, format="WEBP", quality=75, method=6, exif=b"")
        data = (OUTPUT / asset).read_bytes()
        assert data[:4] == b"RIFF" and data[8:12] == b"WEBP"
        images.append({
            "number": number,
            "name_en": item["name_en"],
            "name_th": item["name_th"],
            "asset": asset,
            "width": 960,
            "height": 540,
            "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest(),
        })
    assert len({image["sha256"] for image in images}) == len(images)
    (OUTPUT / "index.json").write_text(json.dumps({
        "kind": "ai_generated_item_promotional_illustration",
        "disclaimer_th": "ภาพใช้เพื่อการโฆษณาเท่านั้น",
        "disclaimer_en": "Images for promotional purposes only",
        "images": images,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(images)} unique WebP assets, {sum(image['bytes'] for image in images)} bytes")


if __name__ == "__main__":
    main()
