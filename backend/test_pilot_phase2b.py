import copy
from contextlib import closing
import hashlib
import io
import json
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
import zipfile
from http.server import ThreadingHTTPServer
from unittest.mock import patch

from PIL import Image

import hotel_api
from media_validation import valid_media
from pilot_backup import backup, restore
from pilot_runtime import read_config


class PilotStoreTests(unittest.TestCase):
    def test_initial_draft_uses_accepted_seed_without_publishing(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            self.assertIsNone(store.published())
            self.assertEqual(store.draft()["hotel"]["name"], "Ao Nang Fiore Resort")
            self.assertEqual(store.draft()["explore"]["items"], [])
            self.assertIn("approved_content", store.draft())
            store.db.close()

    def test_existing_device_table_migrates_without_losing_enrollment(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with closing(sqlite3.connect(root / "hotel.sqlite3")) as db:
                db.execute("CREATE TABLE devices(id TEXT PRIMARY KEY,label TEXT,room TEXT,token_hash TEXT,revoked INTEGER,last_seen INTEGER)")
                db.execute("INSERT INTO devices VALUES ('old-tv','Old TV','101','hash',0,NULL)")
                db.commit()
            store = hotel_api.Store(root)
            row = store.db.execute("SELECT room,last_sync,content_version FROM devices WHERE id='old-tv'").fetchone()
            self.assertEqual(row["room"], "101")
            self.assertIsNone(row["last_sync"])
            store.db.close()

    def test_draft_does_not_change_published_or_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            first = store.publish()
            digest = store.content_sync_manifest()["files"][0]["sha256"]
            draft = copy.deepcopy(store.draft())
            draft["guest"]["items"][0]["description"] = "Revised pilot copy"
            store.save_draft(draft)
            self.assertEqual(store.published()["id"], first)
            self.assertEqual(store.content_sync_manifest()["files"][0]["sha256"], digest)
            store.db.close()

    def test_publish_failure_keeps_previous_version_and_history(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            first = store.publish()
            previous_count = store.db.execute("SELECT count(*) FROM revisions").fetchone()[0]
            store.set_state("media", json.dumps([{
                "id": "missing", "type": "image", "extension": "png",
                "sha256": "a" * 64, "duration_seconds": 5,
                "url": "https://hotel.example/api/v1/media/" + "a" * 64 + ".png"}]))
            with patch.dict(os.environ, {"HOTEL_PUBLIC_MEDIA_HOST": "hotel.example"}):
                with self.assertRaises(ValueError):
                    store.publish()
            self.assertEqual(store.published()["id"], first)
            self.assertEqual(store.db.execute("SELECT count(*) FROM revisions").fetchone()[0], previous_count)
            store.db.close()

    def test_publish_rolls_back_insert_when_manifest_validation_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            first = store.publish()
            with patch.object(store, "content_sync_manifest", side_effect=ValueError("invalid manifest")):
                with self.assertRaises(ValueError): store.publish()
            self.assertEqual(store.published()["id"], first)
            self.assertEqual(store.db.execute("SELECT count(*) FROM revisions").fetchone()[0], 1)
            store.db.close()

    def test_rollback_and_restart_preserve_history(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            first = store.publish()
            second = store.publish()
            store.rollback(first)
            store.db.close()
            reopened = hotel_api.Store(Path(temp))
            self.assertEqual(reopened.published()["id"], first)
            self.assertEqual(reopened.content_sync_manifest()["contentVersion"], str(first))
            self.assertEqual(reopened.db.execute("SELECT count(*) FROM revisions").fetchone()[0], 2)
            self.assertGreater(second, first)
            reopened.db.close()

    def test_concurrent_publish_serializes_versions(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            initial = hotel_api.Store(root)
            initial.save_draft(initial.draft())
            initial.db.close()
            gate = threading.Barrier(2)
            results = []
            errors = []

            def publish():
                store = hotel_api.Store(root)
                try:
                    gate.wait(timeout=3)
                    results.append(store.publish())
                except Exception as error:
                    errors.append(error)
                finally:
                    store.db.close()

            threads = [threading.Thread(target=publish) for _ in range(2)]
            for thread in threads: thread.start()
            for thread in threads: thread.join(timeout=5)
            self.assertEqual(errors, [])
            self.assertEqual(len(set(results)), 2)
            check = hotel_api.Store(root)
            self.assertEqual(check.db.execute("SELECT count(*) FROM revisions").fetchone()[0], 2)
            self.assertIn(int(check.state("published")), results)
            check.db.close()

    def test_backup_restore_verifies_database_and_media_without_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "data"
            store = hotel_api.Store(root)
            version = store.publish()
            (store.media_dir / "test.png").write_bytes(b"media-test")
            store.db.close()
            archive = Path(temp) / "backup.zip"
            records = backup(root, archive)
            self.assertIn("media/test.png", records)
            restored = Path(temp) / "restored"
            restore(archive, restored)
            new_store = hotel_api.Store(restored)
            self.assertEqual(new_store.published()["id"], version)
            new_store.db.close()
            self.assertEqual((restored / "media/test.png").read_bytes(), b"media-test")
            with self.assertRaises(ValueError):
                restore(archive, restored)
            tampered = Path(temp) / "tampered.zip"
            with zipfile.ZipFile(archive) as source, zipfile.ZipFile(tampered, "w") as target:
                for name in source.namelist():
                    value = b"different-media" if name == "media/test.png" else source.read(name)
                    target.writestr(name, value)
            rejected = Path(temp) / "rejected"
            with self.assertRaises(ValueError):
                restore(tampered, rejected)
            self.assertFalse(rejected.exists())

    def test_invalid_content_cannot_become_draft(self):
        with tempfile.TemporaryDirectory() as temp:
            store = hotel_api.Store(Path(temp))
            content = store.draft()
            content["guest_welcome"]["mode"] = "room"
            with self.assertRaises(ValueError):
                store.save_draft(content)
            self.assertIsNone(store.published())
            store.db.close()

    def test_runtime_config_rejects_weak_tokens_and_production(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            seed = root / "seed.json"
            seed.write_text("{}")
            config = root / "pilot.env"
            values = {"HOTEL_PROFILE": "pilot", "HOTEL_DATA_DIR": str(root / "data"),
                      "HOTEL_INITIAL_CONTENT": str(seed),
                      "HOTEL_PUBLIC_MEDIA_HOST": "hotel.example",
                      "HOTEL_EDITOR_TOKEN": "e" * 40,
                      "HOTEL_PUBLISHER_TOKEN": "p" * 40}
            config.write_text("\n".join(f"{key}={value}" for key, value in values.items()))
            self.assertEqual(read_config(config)["HOTEL_PROFILE"], "pilot")
            config.write_text(config.read_text().replace("HOTEL_PROFILE=pilot", "HOTEL_PROFILE=production"))
            with self.assertRaises(ValueError): read_config(config)
            config.write_text(config.read_text().replace("HOTEL_PROFILE=production", "HOTEL_PROFILE=pilot")
                              .replace("e" * 40, "weak"))
            with self.assertRaises(ValueError): read_config(config)

    def test_local_server_restart_keeps_published_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "data"
            seed = Path(hotel_api.SAMPLE).resolve()
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            config = root / "pilot.env"
            config.write_text("\n".join([
                "HOTEL_PROFILE=development", f"HOTEL_DATA_DIR={data}",
                f"HOTEL_INITIAL_CONTENT={seed}", "HOTEL_PUBLIC_MEDIA_HOST=hotel.example",
                "HOTEL_EDITOR_TOKEN=" + "e" * 40, "HOTEL_PUBLISHER_TOKEN=" + "p" * 40,
                f"HOTEL_BIND_PORT={port}"]))

            def start():
                process = subprocess.Popen([sys.executable, str(Path(__file__).parent / "pilot_runtime.py"),
                                            "--config", str(config)], stdout=subprocess.DEVNULL,
                                           stderr=subprocess.PIPE)
                for _ in range(80):
                    if process.poll() is not None:
                        raise AssertionError(process.stderr.read().decode())
                    try:
                        with urllib.request.urlopen(f"http://127.0.0.1:{port}/readyz", timeout=.2) as reply:
                            if json.load(reply)["status"] == "ready": return process
                    except (OSError, ValueError):
                        time.sleep(.05)
                process.terminate()
                process.wait(timeout=3)
                raise AssertionError("pilot server did not become ready")

            first = start()
            try:
                store = hotel_api.Store(data)
                revision = store.publish()
                store.db.close()
            finally:
                first.terminate()
                first.wait(timeout=3)
                first.stderr.close()
            second = start()
            try:
                store = hotel_api.Store(data)
                self.assertEqual(store.published()["id"], revision)
                store.db.close()
            finally:
                second.terminate()
                second.wait(timeout=3)
                second.stderr.close()


class PilotApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root_patch = patch.object(hotel_api, "ROOT", Path(self.temp.name))
        self.root_patch.start()
        self.env_patch = patch.dict(os.environ, {"HOTEL_EDITOR_TOKEN": "editor-test-only",
                                              "HOTEL_PUBLISHER_TOKEN": "publisher-test-only",
                                              "HOTEL_PUBLIC_MEDIA_HOST": "hotel.example"})
        self.env_patch.start()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), hotel_api.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.env_patch.stop()
        self.root_patch.stop()
        self.temp.cleanup()

    def request(self, path, method="GET", value=None, token=None, headers=None):
        header = dict(headers or {})
        if token: header["Authorization"] = "Bearer " + token
        data = json.dumps(value).encode() if value is not None else None
        if data is not None: header["Content-Type"] = "application/json"
        return urllib.request.urlopen(urllib.request.Request(self.base + path, data, header, method=method))

    def test_admin_login_role_and_cms_sections(self):
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/admin")
        self.assertEqual(error.exception.code, 401)
        page = self.request("/admin", token="editor-test-only")
        html = page.read().decode()
        self.assertIn("Content Management", html)
        self.assertIn("approved_content", html)
        self.assertIn("script-src 'self'", page.headers["Content-Security-Policy"])
        javascript = self.request("/admin/cms.js", token="editor-test-only").read().decode()
        self.assertIn("Accommodation", javascript)
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/api/v1/admin/status", token="wrong")
        self.assertEqual(error.exception.code, 401)

    def test_request_log_omits_query_credentials(self):
        with self.assertLogs("hotel_api", level="INFO") as captured:
            self.request("/healthz?token=secret-never-log").read()
        self.assertIn("/healthz", " ".join(captured.output))
        self.assertNotIn("secret-never-log", " ".join(captured.output))

    def test_publish_validate_history_and_device_token_controls(self):
        store = hotel_api.Store()
        code = store.create_code("Pilot 101")
        store.db.close()
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/api/v1/admin/publish", "POST", {}, "editor-test-only")
        self.assertEqual(error.exception.code, 403)
        draft = json.load(self.request("/api/v1/admin/draft", token="editor-test-only"))
        self.assertTrue(json.load(self.request("/api/v1/admin/validate", "POST", draft,
                                               "editor-test-only"))["valid"])
        version = json.load(self.request("/api/v1/admin/publish", "POST", {},
                                         "publisher-test-only"))["revision"]
        manifest = json.load(self.request("/api/v1/device/enroll", "POST", {"code": code}))
        token = manifest["token"]
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/api/v1/device/content-manifest", token="invalid")
        self.assertEqual(error.exception.code, 401)
        content_manifest = json.load(self.request("/api/v1/device/content-manifest", token=token))
        self.assertEqual(content_manifest["minAppVersionCode"], 15)
        self.assertEqual(content_manifest["contentVersion"], str(version))
        content = self.request(content_manifest["files"][0]["downloadPath"], token=token).read()
        self.assertEqual(hashlib.sha256(content).hexdigest(), content_manifest["files"][0]["sha256"])
        status = json.load(self.request("/api/v1/admin/status", token="publisher-test-only"))
        self.assertEqual(status["devices"][0]["content_version"], str(version))
        self.assertIsNotNone(status["devices"][0]["last_sync"])
        self.assertEqual(json.load(self.request(f"/api/v1/admin/revision/{version}",
                                               token="editor-test-only"))["id"], version)
        self.request("/api/v1/admin/device", "POST", {"device_id": manifest["device_id"],
                     "room": "101", "action": "assign"}, "publisher-test-only")
        self.request("/api/v1/admin/device", "POST", {"device_id": manifest["device_id"],
                     "action": "revoke"}, "publisher-test-only")
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/api/v1/device/content-manifest", token=token)
        self.assertEqual(error.exception.code, 401)

    def test_media_upload_rejects_disguised_and_traversal(self):
        fake = b"\x89PNG\r\n\x1a\nnot-a-real-image"
        digest = hashlib.sha256(fake).hexdigest()
        req = urllib.request.Request(self.base + "/api/v1/admin/media-file", fake,
            {"Authorization": "Bearer publisher-test-only", "X-Content-SHA256": digest,
             "X-Media-Extension": "png"}, method="PUT")
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(req)
        self.assertEqual(error.exception.code, 400)
        self.assertFalse(valid_media(fake, "png"))
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request("/api/v1/admin/media-file/../../hotel.sqlite3", token="publisher-test-only")
        self.assertEqual(error.exception.code, 404)

    def test_media_upload_publish_and_authenticated_sha_download(self):
        buffer = io.BytesIO()
        Image.new("RGB", (4, 4), "blue").save(buffer, format="PNG")
        media = buffer.getvalue()
        digest = hashlib.sha256(media).hexdigest()
        req = urllib.request.Request(self.base + "/api/v1/admin/media-file", media,
            {"Authorization": "Bearer publisher-test-only", "X-Content-SHA256": digest,
             "X-Media-Extension": "png"}, method="PUT")
        self.assertEqual(json.load(urllib.request.urlopen(req))["sha256"], digest)
        playlist = [{"id": "pilot_lobby", "type": "image", "extension": "png",
                     "sha256": digest, "duration_seconds": 5,
                     "url": f"https://hotel.example/api/v1/media/{digest}.png"}]
        self.request("/api/v1/admin/media", "POST", {"media": playlist}, "publisher-test-only")
        store = hotel_api.Store()
        code = store.create_code("Media QA TV")
        store.db.close()
        self.request("/api/v1/admin/publish", "POST", {}, "publisher-test-only")
        token = json.load(self.request("/api/v1/device/enroll", "POST", {"code": code}))["token"]
        manifest = json.load(self.request("/api/v1/device/content-manifest", token=token))
        self.assertEqual(len(manifest["files"]), 2)
        downloaded = self.request(manifest["files"][1]["downloadPath"], token=token).read()
        self.assertEqual(hashlib.sha256(downloaded).hexdigest(), digest)
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.request(manifest["files"][1]["downloadPath"])
        self.assertEqual(error.exception.code, 401)


if __name__ == "__main__":
    unittest.main()
