import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parent.parent
ART = ROOT / "android-client/src/main/assets/room_service_art"


class RoomServiceArtTests(unittest.TestCase):
    def test_all_menu_categories_have_compact_integrity_checked_webp(self):
        catalog = json.loads((ART / "index.json").read_text(encoding="utf-8"))
        seed = json.loads((ROOT / "android-client/src/main/assets/hotel_content.json").read_text(encoding="utf-8"))
        expected = {category["id"] for category in seed["room_service"]["categories"]} | {"hero"}
        self.assertEqual({entry["id"] for entry in catalog["assets"]}, expected)
        self.assertEqual(catalog["kind"], "ai_generated_promotional_illustration")
        self.assertEqual(len(catalog["assets"]), 14)
        self.assertLess(sum(entry["bytes"] for entry in catalog["assets"]), 2_000_000)
        for entry in catalog["assets"]:
            asset = ART / entry["asset"]
            self.assertEqual(asset.name, entry["id"] + ".webp")
            data = asset.read_bytes()
            self.assertEqual(data[:4], b"RIFF")
            self.assertEqual(data[8:12], b"WEBP")
            self.assertEqual(len(data), entry["bytes"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), entry["sha256"])
            self.assertEqual((entry["width"], entry["height"]),
                             (1280, 720) if entry["id"] == "hero" else (960, 540))


if __name__ == "__main__":
    unittest.main()
