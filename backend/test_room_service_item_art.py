"""The 81 item illustrations must match approved menu IDs without substituting a dish."""
import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "android-client/src/main/assets/room_service_items"


class RoomServiceItemArtTests(unittest.TestCase):
    def test_one_integrity_checked_illustration_per_published_item(self):
        seed = json.loads((ROOT / "android-client/src/main/assets/hotel_content.json").read_text(encoding="utf-8"))
        items = {item["number"]: item for category in seed["room_service"]["categories"]
                 for item in category["items"]}
        catalog = json.loads((ART / "index.json").read_text(encoding="utf-8"))
        images = catalog["images"]
        self.assertEqual(len(items), 81)
        self.assertEqual(len(images), 81)
        self.assertEqual({image["number"] for image in images}, set(items))
        self.assertTrue({37, 38}.isdisjoint(items))
        self.assertEqual(len({image["sha256"] for image in images}), 81)
        self.assertLess(sum(image["bytes"] for image in images), 12_000_000)
        for image in images:
            number = image["number"]
            self.assertEqual(image["asset"], f"item_{number:03d}.webp")
            self.assertEqual((image["name_en"], image["name_th"]),
                             (items[number]["name_en"], items[number]["name_th"]))
            data = (ART / image["asset"]).read_bytes()
            self.assertEqual(data[:4], b"RIFF")
            self.assertEqual(data[8:12], b"WEBP")
            self.assertEqual(len(data), image["bytes"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), image["sha256"])
            self.assertEqual((image["width"], image["height"]), (960, 540))


if __name__ == "__main__":
    unittest.main()
