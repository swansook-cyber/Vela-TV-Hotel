"""Loopback-only CMS/device contract test. Public HTTPS is a deployment gate."""
import base64
import hashlib
import json
import os
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

import hotel_api

PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII="
)
EDITOR = "editor-test-only-" + "e" * 32
PUBLISHER = "publisher-test-only-" + "p" * 32
HOST = "hotel.velalab.net"


class StagingEndToEndTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root_patch = patch.object(hotel_api, "ROOT", Path(self.temp.name))
        self.env_patch = patch.dict(os.environ, {
            "HOTEL_EDITOR_TOKEN": EDITOR,
            "HOTEL_PUBLISHER_TOKEN": PUBLISHER,
            "HOTEL_PUBLIC_MEDIA_HOST": HOST,
            "HOTEL_DATA_DIR": self.temp.name,
        })
        self.root_patch.start()
        self.env_patch.start()
        self.start_server()

    def tearDown(self):
        self.stop_server()
        self.env_patch.stop()
        self.root_patch.stop()
        self.temp.cleanup()

    def start_server(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), hotel_api.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def stop_server(self):
        if getattr(self, "server", None):
            self.server.shutdown()
            self.server.server_close()
            self.thread.join(timeout=3)
            self.server = None

    def request(self, path, method="GET", data=None, role=None, token=None, headers=None):
        headers = dict(headers or {})
        if role:
            headers["Authorization"] = "Bearer " + (PUBLISHER if role == "publisher" else EDITOR)
        if token:
            headers["Authorization"] = "Bearer " + token
        if isinstance(data, (dict, list)):
            data = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=3) as response:
                raw = response.read()
                return response.status, json.loads(raw) if response.headers.get_content_type() == "application/json" else raw
        except urllib.error.HTTPError as error:
            raw = error.read()
            return error.code, json.loads(raw) if error.headers.get_content_type() == "application/json" else raw

    def test_complete_local_cms_device_media_offline_rollback(self):
        self.assertEqual(self.request("/api/v1/admin/draft")[0], 401)
        status, draft = self.request("/api/v1/admin/draft", role="editor")
        self.assertEqual(status, 200)
        draft["resort"]["items"][0]["description"] = "TEST DATA — staging tour"
        self.assertEqual(self.request("/api/v1/admin/draft", "POST", draft, role="editor")[0], 200)
        status, preview = self.request("/api/v1/admin/preview", role="editor")
        self.assertEqual(status, 200)
        self.assertEqual(preview["content"], draft)
        self.assertEqual(self.request("/api/v1/admin/publish", "POST", {}, role="editor")[0], 403)
        status, first = self.request("/api/v1/admin/publish", "POST", {}, role="publisher")
        self.assertEqual(status, 200)

        sha = hashlib.sha256(PNG).hexdigest()
        self.assertEqual(self.request("/api/v1/admin/media-file", "PUT", PNG, role="editor",
                                      headers={"X-Content-SHA256": sha, "X-Media-Extension": "png"})[0], 403)
        self.assertEqual(self.request("/api/v1/admin/media-file", "PUT", PNG, role="publisher",
                                      headers={"X-Content-SHA256": sha, "X-Media-Extension": "png"})[0], 200)
        item = {"id": "staging-image", "type": "image", "extension": "png", "sha256": sha,
                "duration_seconds": 8, "url": f"https://{HOST}/api/v1/media/{sha}.png"}
        self.assertEqual(self.request("/api/v1/admin/media", "POST", {"media": [item]}, role="publisher")[0], 200)
        status, second = self.request("/api/v1/admin/publish", "POST", {}, role="publisher")
        self.assertEqual(status, 200)

        status, enrollment = self.request("/api/v1/admin/enrollment", "POST", {"label": "Simulated TV"},
                                          role="publisher")
        self.assertEqual(status, 200)
        self.assertEqual(enrollment["expires_in"], 600)
        code = enrollment["code"]
        status, device = self.request("/api/v1/device/enroll", "POST", {"code": code})
        self.assertEqual(status, 200)
        self.assertEqual(self.request("/api/v1/device/enroll", "POST", {"code": code})[0], 403)
        token = device["token"]
        status, manifest = self.request("/api/v1/device/manifest", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(manifest["revision"], str(second["revision"]))
        self.assertEqual(manifest["content"], draft)
        self.assertEqual(manifest["media"], [item])
        self.assertEqual(manifest["room"], "")
        self.assertEqual(self.request(f"/api/v1/media/{sha}.png")[0], 401)
        status, media = self.request(f"/api/v1/media/{sha}.png", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(hashlib.sha256(media).hexdigest(), sha)
        self.assertEqual(self.request(f"/api/v1/media/../{sha}.png", token=token)[0], 404)
        self.assertEqual(self.request(f"/api/v1/media/%2e%2e/{sha}.png", token=token)[0], 404)

        # Local HTTP server outage simulates an unreachable public HTTPS origin.
        self.stop_server()
        with self.assertRaises((urllib.error.URLError, TimeoutError, ConnectionError)):
            urllib.request.urlopen(self.base + "/api/v1/device/manifest", timeout=1)
        self.assertEqual(hashlib.sha256(media).hexdigest(), sha)
        self.start_server()

        self.assertEqual(self.request("/api/v1/admin/rollback", "POST",
                                      {"revision": first["revision"]}, role="publisher")[0], 200)
        status, manifest = self.request("/api/v1/device/manifest", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(manifest["revision"], str(first["revision"]))
        self.assertEqual(manifest["media"], [])
        self.assertEqual(self.request(f"/api/v1/media/{sha}.png", token=token)[0], 404)
        self.assertEqual(self.request("/api/v1/admin/device", "POST",
                                      {"action": "revoke", "device_id": device["device_id"]},
                                      role="publisher")[0], 200)
        self.assertEqual(self.request("/api/v1/device/manifest", token=token)[0], 401)

    def test_expiration_and_concurrent_single_use(self):
        status, result = self.request("/api/v1/admin/enrollment", "POST",
                                      {"label": "Expired TV"}, role="publisher")
        self.assertEqual(status, 200)
        store = hotel_api.Store()
        store.db.execute("UPDATE enrollments SET expires_at=?", (int(time.time()) - 1,))
        store.db.commit()
        store.db.close()
        self.assertEqual(self.request("/api/v1/device/enroll", "POST", {"code": result["code"]})[0], 403)

        status, result = self.request("/api/v1/admin/enrollment", "POST",
                                      {"label": "Concurrent TV"}, role="publisher")
        self.assertEqual(status, 200)
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(
                lambda _: self.request("/api/v1/device/enroll", "POST", {"code": result["code"]})[0],
                range(2),
            ))
        self.assertEqual(sorted(responses), [200, 403])
        store = hotel_api.Store()
        self.assertEqual(store.db.execute("SELECT count(*) FROM devices").fetchone()[0], 1)
        store.db.close()

    def test_staging_config_and_https_playlist_contract(self):
        hotel_api.validate_staging_config()
        with patch.dict(os.environ, {"HOTEL_PUBLISHER_TOKEN": EDITOR}):
            with self.assertRaises(ValueError):
                hotel_api.validate_staging_config()
        with patch.dict(os.environ, {"HOTEL_PUBLIC_MEDIA_HOST": "hotel..velalab.net"}):
            with self.assertRaises(ValueError):
                hotel_api.validate_staging_config()
        item = {"id": "x", "type": "image", "extension": "png", "sha256": "a" * 64,
                "duration_seconds": 8, "url": f"http://{HOST}/api/v1/media/{'a' * 64}.png"}
        self.assertFalse(hotel_api.valid_manifest({
            "schema_version": 1, "revision": "1", "hotel": {"name": "Ao Nang Fiore Resort"},
            "media": [item]}, HOST))


if __name__ == "__main__":
    unittest.main()
