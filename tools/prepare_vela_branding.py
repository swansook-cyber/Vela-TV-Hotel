"""Create Android launcher art from the two owner-provided Vela TV Hotel PNGs."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from PIL import Image, ImageOps


EXPECTED_SQUARE = "aea94fdf9ccdecca365391497ba6dfbffc0ae0afec6034c958254c014d89993a"
EXPECTED_HORIZONTAL = "66d44eb30561ab0ec804234bebe107a0f44e31dc55cfb7733bc578b9c33ee2f8"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--square", type=Path, required=True)
    parser.add_argument("--horizontal", type=Path, required=True)
    parser.add_argument("--res", type=Path, default=Path("android-client/src/main/res"))
    args = parser.parse_args()
    for path, expected in ((args.square, EXPECTED_SQUARE), (args.horizontal, EXPECTED_HORIZONTAL)):
        if not path.is_file() or digest(path) != expected:
            raise SystemExit(f"Missing or unexpected logo: {path}")

    with Image.open(args.square) as source:
        square = ImageOps.exif_transpose(source).convert("RGBA")
        square = square.crop(square.getchannel("A").getbbox())
    # Legacy launcher icon density set. Keep the whole user-provided mark.
    for qualifier, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                            ("xxhdpi", 144), ("xxxhdpi", 192)):
        icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mark = ImageOps.contain(square, (int(size * 0.92), int(size * 0.92)),
                                Image.Resampling.LANCZOS)
        icon.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
        folder = args.res / f"mipmap-{qualifier}"
        folder.mkdir(parents=True, exist_ok=True)
        icon.save(folder / "vela_tv_hotel.png", optimize=True)

    # Adaptive masks can hide the outer 18dp on each side. Inset the mark into
    # the central safe area rather than allowing the TV silhouette to be cut.
    foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    mark = ImageOps.contain(square, (288, 288), Image.Resampling.LANCZOS)
    foreground.alpha_composite(mark, ((432 - mark.width) // 2, (432 - mark.height) // 2))
    drawables = args.res / "drawable-nodpi"
    drawables.mkdir(parents=True, exist_ok=True)
    foreground.save(drawables / "vela_tv_hotel_foreground.png", optimize=True)

    with Image.open(args.horizontal) as source:
        horizontal = ImageOps.exif_transpose(source).convert("RGBA")
        horizontal = horizontal.crop(horizontal.getchannel("A").getbbox())
    banner = Image.new("RGBA", (640, 360), (7, 33, 36, 255))
    mark = ImageOps.contain(horizontal, (600, 260), Image.Resampling.LANCZOS)
    banner.alpha_composite(mark, ((640 - mark.width) // 2, (360 - mark.height) // 2))
    banner.convert("RGB").save(drawables / "vela_tv_hotel_banner.png", optimize=True)
    print("Prepared five launcher densities, adaptive foreground, and 640x360 TV banner")


if __name__ == "__main__":
    main()
