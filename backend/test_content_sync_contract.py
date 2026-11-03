import hashlib
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

import jsonschema

import hotel_api
from content_sync_contract import valid_sync_manifest


ROOT = Path(__file__).parent.parent


class ContentSyncContractTests(unittest.TestCase):
    def test_bundled_content_matches_json_schema(self):
        schema = json.loads((ROOT / "docs/schema/hotel-content-v1.schema.json").read_text(encoding="utf-8"))
        content = json.loads((ROOT / "android-client/src/main/assets/hotel_content.json").read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator(schema).validate(content)

    def test_manifest_and_authenticated_content_download(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(hotel_api, "ROOT", Path(temp)):
            store = hotel_api.Store()
            store.save_draft(store.draft())
            media = b"\x89PNG\r\n\x1a\n" + b"test-media"
            media_sha = hashlib.sha256(media).hexdigest()
            (store.media_dir / (media_sha + ".png")).write_bytes(media)
            store.set_state("media", json.dumps([{
                "id": "lobby", "type": "image", "sha256": media_sha,
                "extension": "png", "duration_seconds": 5,
                "url": f"https://hotel.example/api/v1/media/{media_sha}.png"}]))
            with patch.dict("os.environ", {"HOTEL_PUBLIC_MEDIA_HOST": "hotel.example"}):
                store.publish()
            code = store.create_code("Content QA TV")
            _, token = store.enroll(code)
            store.db.close()
            server = ThreadingHTTPServer(("127.0.0.1", 0), hotel_api.Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                def get(path, authorized=True):
                    headers = {"Authorization": "Bearer " + token} if authorized else {}
                    return urllib.request.urlopen(urllib.request.Request(base + path, headers=headers))

                with self.assertRaises(urllib.error.HTTPError) as error:
                    get("/api/v1/device/content-manifest", False)
                self.assertEqual(error.exception.code, 401)
                manifest = json.load(get("/api/v1/device/content-manifest"))
                self.assertTrue(valid_sync_manifest(manifest))
                schema = json.loads((ROOT / "docs/schema/content-sync-manifest-v1.schema.json").read_text())
                jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker()).validate(manifest)
                content_file = manifest["files"][0]
                payload = get(content_file["downloadPath"]).read()
                self.assertEqual(len(payload), content_file["size"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), content_file["sha256"])
                self.assertEqual(json.loads(payload)["hotel"]["name"], "Ao Nang Fiore Resort")
                media_file = manifest["files"][1]
                self.assertEqual(media_file["path"], "media/lobby.png")
                self.assertEqual(get(media_file["downloadPath"]).read(), media)
                with self.assertRaises(urllib.error.HTTPError) as error:
                    get(media_file["downloadPath"], False)
                self.assertEqual(error.exception.code, 401)
                with self.assertRaises(urllib.error.HTTPError) as error:
                    get(content_file["downloadPath"] + "?unexpected=1")
                self.assertEqual(error.exception.code, 404)
                with self.assertRaises(urllib.error.HTTPError) as error:
                    get("/api/v1/device/content/1/" + "0" * 64 + ".json")
                self.assertEqual(error.exception.code, 404)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)

    def test_manifest_rejects_invalid_files_and_rollback_changes_version(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            store.save_draft(store.draft())
            first = store.publish()
            original = store.content_sync_manifest()
            self.assertEqual(original["contentVersion"], str(first))
            content = store.draft()
            content["guest"]["items"] = [{"title": "Updated", "description": "Reviewed", "verified": True}]
            store.save_draft(content)
            second = store.publish()
            self.assertNotEqual(original["files"][0]["sha256"], store.content_sync_manifest()["files"][0]["sha256"])
            store.rollback(first)
            self.assertEqual(store.content_sync_manifest()["contentVersion"], str(first))
            self.assertNotEqual(second, first)
            bad = dict(original)
            bad["files"] = [dict(original["files"][0], size=-1)]
            self.assertFalse(valid_sync_manifest(bad))
            store.db.close()


if __name__ == "__main__":
    unittest.main()
