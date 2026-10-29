import unittest
from playlist_contract import valid_manifest

HOST = "media.example.invalid"
SHA = "a" * 64


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.data = {"schema_version": 1, "revision": "pilot-1",
                     "hotel": {"name": "Ao Nang Fiore Resort"},
                     "media": [{"id": "welcome", "type": "image",
                                "url": "https://media.example.invalid/welcome.jpg",
                                "sha256": SHA, "extension": "jpg", "duration_seconds": 10}]}

    def test_valid(self):
        self.assertTrue(valid_manifest(self.data, HOST))

    def test_reject_missing_extension(self):
        self.data["media"][0].pop("extension")
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_reject_mismatched_extension(self):
        self.data["media"][0]["extension"] = "mp4"
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_accept_video_mp4(self):
        self.data["media"][0]["type"] = "video"
        self.data["media"][0]["extension"] = "mp4"
        self.assertTrue(valid_manifest(self.data, HOST))

    def test_reject_http(self):
        self.data["media"][0]["url"] = "http://media.example.invalid/welcome.jpg"
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_reject_other_host(self):
        self.data["media"][0]["url"] = "https://other.example.invalid/welcome.jpg"
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_reject_missing_digest(self):
        self.data["media"][0]["sha256"] = ""
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_reject_invalid_duration(self):
        self.data["media"][0]["duration_seconds"] = True
        self.assertFalse(valid_manifest(self.data, HOST))

    def test_reject_duplicate_id(self):
        self.data["media"].append(dict(self.data["media"][0]))
        self.assertFalse(valid_manifest(self.data, HOST))


if __name__ == "__main__":
    unittest.main()
