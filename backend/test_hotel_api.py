import unittest
import tempfile
from pathlib import Path
from hotel_api import Store, authorized
from content_contract import valid_content


class ManifestTests(unittest.TestCase):
    def test_requires_configured_token(self):
        self.assertFalse(authorized("Bearer abc", None))
        self.assertFalse(authorized(None, "abc"))
        self.assertFalse(authorized("Bearer wrong", "abc"))
        self.assertTrue(authorized("Bearer abc", "abc"))

    def test_draft_publish_rollback_and_device_isolation(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(Path(temp))
            sample = store.draft()
            self.assertTrue(valid_content(sample))
            self.assertIsNone(store.published())
            store.save_draft(sample)
            first = store.publish()
            self.assertEqual(store.manifest()["revision"], str(first))
            code = store.create_code("Pilot TV")
            device_id, token = store.enroll(code)
            self.assertIsNone(store.enroll(code))
            self.assertEqual(store.device(token)["id"], device_id)
            self.assertIsNone(store.device("another-token"))
            store.db.execute("UPDATE devices SET revoked=1 WHERE id=?", (device_id,))
            store.db.commit()
            self.assertIsNone(store.device(token))
            second = store.publish()
            store.rollback(first)
            self.assertNotEqual(first, second)
            self.assertEqual(store.manifest()["revision"], str(first))
            store.db.close()

    def test_unverified_fields_must_be_marked(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(Path(temp))
            sample = store.draft()
            sample["guest"]["items"][0]["description"] = "Invented phone number"
            self.assertFalse(valid_content(sample))
            store.db.close()


if __name__ == "__main__":
    unittest.main()
