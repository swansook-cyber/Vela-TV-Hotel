import copy
import json
import tempfile
import unittest
from pathlib import Path

import jsonschema

import hotel_api
from content_contract import valid_content, valid_room_service


ROOT = Path(__file__).parent.parent
SEED = ROOT / "android-client/src/main/assets/hotel_content.json"


class RoomServiceMenuTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.content = json.loads(SEED.read_text(encoding="utf-8"))
        cls.schema = json.loads((ROOT / "docs/schema/hotel-content-v1.schema.json").read_text(encoding="utf-8"))

    def test_approved_seed_is_valid_and_skips_unconfirmed_items(self):
        menu = self.content["room_service"]
        numbers = [item["number"] for category in menu["categories"] for item in category["items"]]
        self.assertTrue(valid_content(self.content))
        jsonschema.Draft202012Validator(self.schema).validate(self.content)
        self.assertEqual(len(numbers), 81)
        self.assertEqual(set(range(1, 84)) - set(numbers), {37, 38})
        self.assertEqual(menu["contact"], "4003")
        self.assertEqual(menu["currency"], "THB")

    def test_invalid_menu_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            store.save_draft(copy.deepcopy(self.content))
            first = store.publish()
            malformed = copy.deepcopy(self.content)
            malformed["room_service"]["categories"][0]["items"][0]["price_thb"] = "120"
            with self.assertRaises(ValueError):
                store.save_draft(malformed)
            self.assertEqual(store.published()["id"], first)
            store.db.close()

    def test_menu_can_change_without_apk_version_change(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            store.save_draft(copy.deepcopy(self.content))
            first = store.publish()
            initial_digest = store.content_sync_manifest()["files"][0]["sha256"]
            updated = copy.deepcopy(self.content)
            updated["room_service"]["menuVersion"] = "2026-11-04.2"
            store.save_draft(updated)
            second = store.publish()
            self.assertNotEqual(first, second)
            self.assertNotEqual(initial_digest, store.content_sync_manifest()["files"][0]["sha256"])
            store.rollback(first)
            self.assertEqual(store.content_sync_manifest()["contentVersion"], str(first))
            store.db.close()

    def test_duplicate_or_unverified_fields_are_rejected(self):
        menu = copy.deepcopy(self.content["room_service"])
        menu["categories"][0]["items"].append(copy.deepcopy(menu["categories"][0]["items"][0]))
        self.assertFalse(valid_room_service(menu))
        menu = copy.deepcopy(self.content["room_service"])
        menu["hours"] = "unverified"
        self.assertFalse(valid_room_service(menu))


if __name__ == "__main__":
    unittest.main()
