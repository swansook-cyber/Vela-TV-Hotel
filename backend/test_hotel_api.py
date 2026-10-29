import unittest
from hotel_api import MANIFEST, authorized


class ManifestTests(unittest.TestCase):
    def test_requires_configured_token(self):
        self.assertFalse(authorized("Bearer abc", None))
        self.assertFalse(authorized(None, "abc"))
        self.assertFalse(authorized("Bearer wrong", "abc"))
        self.assertTrue(authorized("Bearer abc", "abc"))

    def test_empty_pilot_manifest(self):
        self.assertEqual(MANIFEST["schema_version"], 1)
        self.assertEqual(MANIFEST["media"], [])
        self.assertEqual(MANIFEST["hotel"]["name"], "Ao Nang Fiore Resort")


if __name__ == "__main__":
    unittest.main()
