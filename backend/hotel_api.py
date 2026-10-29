"""Loopback-only hotel staging CMS and device API. Put HTTPS reverse proxy in front."""
from __future__ import annotations

import hashlib
import hmac
import html
import base64
import json
import os
import secrets
import sqlite3
import time
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs

from content_contract import valid_content
from playlist_contract import valid_manifest

ROOT = Path(os.getenv("HOTEL_DATA_DIR", Path(__file__).parent / ".hotel-data"))
SAMPLE = Path(__file__).parent.parent / "docs" / "hotel-content-sample.json"
MAX_JSON = 256 * 1024
MAX_MEDIA = 50 * 1024 * 1024


def digest(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def authorized(header: str | None, token: str | None) -> bool:
    return bool(token and header and header.startswith("Bearer ") and
                hmac.compare_digest(header[7:], token))


class Store:
    def __init__(self, root: Path | None = None):
        self.root = root if root is not None else ROOT
        self.root.mkdir(parents=True, exist_ok=True)
        self.media_dir = self.root / "media"
        self.media_dir.mkdir(exist_ok=True)
        self.db = sqlite3.connect(self.root / "hotel.sqlite3")
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS revisions(
              id INTEGER PRIMARY KEY, content TEXT NOT NULL, media TEXT NOT NULL,
              created_at INTEGER NOT NULL, actor TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS state(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS devices(
              id TEXT PRIMARY KEY, label TEXT NOT NULL, room TEXT NOT NULL DEFAULT '',
              token_hash TEXT NOT NULL, revoked INTEGER NOT NULL DEFAULT 0,
              last_seen INTEGER);
            CREATE TABLE IF NOT EXISTS enrollments(
              code_hash TEXT PRIMARY KEY, label TEXT NOT NULL, expires_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS audit(
              id INTEGER PRIMARY KEY, at INTEGER NOT NULL, actor TEXT NOT NULL,
              action TEXT NOT NULL, detail TEXT NOT NULL);
        """)
        self.db.commit()

    def state(self, key: str) -> str | None:
        row = self.db.execute("SELECT value FROM state WHERE key=?", (key,)).fetchone()
        return row["value"] if row else None

    def set_state(self, key: str, value: str):
        self.db.execute("INSERT OR REPLACE INTO state VALUES (?,?)", (key, value))
        self.db.commit()

    def log(self, actor: str, action: str, detail: str = ""):
        self.db.execute("INSERT INTO audit(at,actor,action,detail) VALUES (?,?,?,?)",
                        (int(time.time()), actor, action, detail[:200]))
        self.db.commit()

    def initial_content(self) -> dict:
        return json.loads(SAMPLE.read_text(encoding="utf-8"))

    def draft(self) -> dict:
        raw = self.state("draft")
        return json.loads(raw) if raw else self.initial_content()

    def save_draft(self, content: dict):
        if not valid_content(content):
            raise ValueError("invalid content")
        self.set_state("draft", json.dumps(content, ensure_ascii=False))
        self.log("editor", "save_draft")

    def published(self) -> sqlite3.Row | None:
        rev = self.state("published")
        return self.db.execute("SELECT * FROM revisions WHERE id=?", (rev,)).fetchone() if rev else None

    def publish(self, actor: str = "publisher") -> int:
        content = self.draft()
        if not valid_content(content):
            raise ValueError("invalid draft")
        media = json.loads(self.state("media") or "[]")
        if media:
            manifest = {"schema_version": 1, "revision": "check",
                        "hotel": content["hotel"], "media": media}
            host = os.getenv("HOTEL_PUBLIC_MEDIA_HOST", "")
            if not host or not valid_manifest(manifest, host):
                raise ValueError("invalid media playlist or media host")
            for item in media:
                if not (self.media_dir / (item["sha256"] + "." + item["extension"])).is_file():
                    raise ValueError("missing media file")
        cur = self.db.execute(
            "INSERT INTO revisions(content,media,created_at,actor) VALUES (?,?,?,?)",
            (json.dumps(content, ensure_ascii=False), json.dumps(media), int(time.time()), actor))
        self.db.commit()
        self.set_state("published", str(cur.lastrowid))
        self.log(actor, "publish", str(cur.lastrowid))
        return cur.lastrowid

    def rollback(self, revision: int):
        row = self.db.execute("SELECT id FROM revisions WHERE id=?", (revision,)).fetchone()
        if not row:
            raise ValueError("unknown revision")
        self.set_state("published", str(revision))
        self.log("publisher", "rollback", str(revision))

    def create_code(self, label: str) -> str:
        code = secrets.token_urlsafe(12)
        self.db.execute("INSERT INTO enrollments VALUES (?,?,?)",
                        (digest(code), label[:80], int(time.time()) + 600))
        self.db.commit()
        self.log("publisher", "create_enrollment", label)
        return code

    def enroll(self, code: str) -> tuple[str, str] | None:
        row = self.db.execute("SELECT * FROM enrollments WHERE code_hash=?", (digest(code),)).fetchone()
        if not row or row["expires_at"] < time.time():
            return None
        token = secrets.token_urlsafe(32)
        device_id = secrets.token_hex(12)
        self.db.execute("DELETE FROM enrollments WHERE code_hash=?", (digest(code),))
        self.db.execute("INSERT INTO devices(id,label,token_hash) VALUES (?,?,?)",
                        (device_id, row["label"], digest(token)))
        self.db.commit()
        self.log("device", "enroll", device_id)
        return device_id, token

    def device(self, token: str) -> sqlite3.Row | None:
        if len(token) > 256:
            return None
        return self.db.execute("SELECT * FROM devices WHERE token_hash=? AND revoked=0",
                               (digest(token),)).fetchone()

    def manifest(self) -> dict:
        row = self.published()
        if not row:
            raise ValueError("nothing published")
        media = json.loads(row["media"])
        return {"schema_version": 1, "revision": str(row["id"]),
                "hotel": json.loads(row["content"])["hotel"],
                "content": json.loads(row["content"]), "media": media}


class Handler(BaseHTTPRequestHandler):
    server_version = "VelaHotelPilot/1.0"

    def respond(self, status: int, data: dict):
        payload = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def page(self, status: int, body: str):
        payload = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; img-src 'self'; media-src 'self'; form-action 'self'")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def role(self) -> str | None:
        header = self.headers.get("Authorization")
        if authorized(header, os.getenv("HOTEL_PUBLISHER_TOKEN")):
            return "publisher"
        if authorized(header, os.getenv("HOTEL_EDITOR_TOKEN")):
            return "editor"
        if header and header.startswith("Basic "):
            try:
                user, password = base64.b64decode(header[6:], validate=True).decode().split(":", 1)
                if user == "publisher" and authorized("Bearer " + password, os.getenv("HOTEL_PUBLISHER_TOKEN")):
                    return "publisher"
                if user == "editor" and authorized("Bearer " + password, os.getenv("HOTEL_EDITOR_TOKEN")):
                    return "editor"
            except (ValueError, UnicodeDecodeError):
                pass
        return None

    def admin_unauthorized(self):
        payload = b"Admin credentials required"
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="Vela Hotel Staging"')
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def body_json(self) -> dict:
        size = int(self.headers.get("Content-Length", "0"))
        if size < 1 or size > MAX_JSON:
            raise ValueError("invalid body size")
        value = json.loads(self.rfile.read(size))
        if not isinstance(value, dict):
            raise ValueError("object required")
        return value

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/healthz":
            return self.respond(200, {"status": "ok"})
        store = Store()
        try:
            if path == "/api/v1/device/manifest":
                token = (self.headers.get("Authorization") or "").removeprefix("Bearer ")
                device = store.device(token) if token else None
                if not device:
                    return self.respond(401, {"error": "unauthorized"})
                store.db.execute("UPDATE devices SET last_seen=? WHERE id=?", (int(time.time()), device["id"]))
                store.db.commit()
                try:
                    manifest = store.manifest()
                    manifest["room"] = device["room"]
                    return self.respond(200, manifest)
                except ValueError:
                    return self.respond(503, {"error": "unpublished"})
            if path.startswith("/api/v1/media/"):
                token = (self.headers.get("Authorization") or "").removeprefix("Bearer ")
                if not store.device(token):
                    return self.respond(401, {"error": "unauthorized"})
                filename = path.rsplit("/", 1)[-1]
                if not filename or filename != Path(filename).name:
                    return self.respond(404, {"error": "not_found"})
                row = store.published()
                allowed = {item["sha256"] + "." + item["extension"]
                           for item in json.loads(row["media"])} if row else set()
                if filename not in allowed:
                    return self.respond(404, {"error": "not_found"})
                data = (store.media_dir / filename).read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Cache-Control", "private, no-store")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                return self.wfile.write(data)
            role = self.role()
            if not role:
                return self.admin_unauthorized() if path == "/admin" else self.respond(401, {"error": "admin_auth_required"})
            if path == "/admin":
                return self.admin_page(store, role)
            if path == "/api/v1/admin/draft":
                return self.respond(200, store.draft())
            if path == "/api/v1/admin/preview":
                return self.respond(200, {"content": store.draft(), "media": json.loads(store.state("media") or "[]")})
            if path == "/api/v1/admin/status":
                devices = [dict(row) for row in store.db.execute(
                    "SELECT id,label,room,revoked,last_seen FROM devices ORDER BY label")]
                revisions = [dict(row) for row in store.db.execute(
                    "SELECT id,created_at,actor FROM revisions ORDER BY id DESC LIMIT 20")]
                audit = [dict(row) for row in store.db.execute(
                    "SELECT at,actor,action,detail FROM audit ORDER BY id DESC LIMIT 50")]
                return self.respond(200, {"published": store.state("published"), "devices": devices,
                                          "revisions": revisions, "audit": audit})
            if path.startswith("/api/v1/admin/media-file/"):
                filename = path.rsplit("/", 1)[-1]
                stem, dot, extension = filename.partition(".")
                if dot != "." or len(stem) != 64 or any(c not in "0123456789abcdef" for c in stem) or extension not in ("jpg", "jpeg", "png", "mp4"):
                    return self.respond(404, {"error": "not_found"})
                file = store.media_dir / filename
                if not file.is_file():
                    return self.respond(404, {"error": "not_found"})
                data = file.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", {"jpg": "image/jpeg", "jpeg": "image/jpeg",
                                                  "png": "image/png", "mp4": "video/mp4"}[extension])
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                return self.wfile.write(data)
            return self.respond(404, {"error": "not_found"})
        finally:
            store.db.close()

    def admin_page(self, store: Store, role: str):
        current = html.escape(json.dumps(store.draft(), ensure_ascii=False, indent=2))
        markup = f"""<!doctype html><html><meta charset="utf-8"><title>Vela Hotel CMS</title>
<style>body{{background:#082324;color:#fff;font:18px system-ui;max-width:1050px;margin:2rem auto}}
textarea{{width:100%;height:60vh;background:#123c3e;color:#fff;font:15px monospace}}
button{{padding:.8rem;margin:.5rem;background:#dbad61;border:0}}pre{{white-space:pre-wrap}}</style>
<h1>Vela Hotel CMS · {role}</h1><p>Staging only. Draft → Preview → Publish. Unverified text must say TEST DATA.</p>
<textarea id="content">{current}</textarea><p><button onclick="call('draft',JSON.parse(document.getElementById('content').value))">Save draft</button>
<button onclick="read('preview')">Preview</button><button onclick="call('publish',{{}})">Publish</button>
<button onclick="read('status')">Status / audit</button></p>
<p><input id="label" placeholder="TV label"><button onclick="call('enrollment',{{label:document.getElementById('label').value}})">Create 10-minute TV code</button></p>
<p><input id="revision" type="number" placeholder="Revision ID"><button onclick="call('rollback',{{revision:Number(document.getElementById('revision').value)}})">Rollback</button></p>
<p><input id="device" placeholder="Device ID"><input id="room" placeholder="Room assignment">
<button onclick="call('device',{{device_id:document.getElementById('device').value,room:document.getElementById('room').value,action:'assign'}})">Assign room</button>
<button onclick="call('device',{{device_id:document.getElementById('device').value,action:'revoke'}})">Revoke</button></p>
<p><input type="file" id="mediaFile" accept="image/jpeg,image/png,video/mp4">
<button onclick="upload()">Upload media</button></p>
<div id="mediaPreview"></div>
<p>Playlist JSON (published separately from upload)</p><textarea id="playlist" style="height:8rem">[]</textarea>
<button onclick="call('media',{{media:JSON.parse(document.getElementById('playlist').value)}})">Save playlist</button>
<pre id="result"></pre>
<script>async function call(path,data){{let r=await fetch('/api/v1/admin/'+path,{{method:'POST',
headers:{{'Content-Type':'application/json'}},body:JSON.stringify(data)}});
document.getElementById('result').textContent=await r.text()}}
async function read(path){{let r=await fetch('/api/v1/admin/'+path);
document.getElementById('result').textContent=await r.text()}}
async function upload(){{let file=document.getElementById('mediaFile').files[0];if(!file)return;
let bytes=await file.arrayBuffer();let hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(x=>x.toString(16).padStart(2,'0')).join('');
let ext=file.name.split('.').pop().toLowerCase();let r=await fetch('/api/v1/admin/media-file',{{method:'PUT',
headers:{{'X-Content-SHA256':hash,'X-Media-Extension':ext}},body:bytes}});
document.getElementById('result').textContent=await r.text();
if(r.ok){{let view=document.createElement(ext==='mp4'?'video':'img');view.src='/api/v1/admin/media-file/'+hash+'.'+ext;
view.style.maxWidth='600px';view.style.maxHeight='320px';if(ext==='mp4')view.controls=true;
document.getElementById('mediaPreview').replaceChildren(view)}}}}</script></html>"""
        return self.page(200, markup)

    def do_POST(self):
        path = urlsplit(self.path).path
        store = Store()
        try:
            if path == "/api/v1/device/enroll":
                body = self.body_json()
                code = body.get("code")
                result = store.enroll(code) if isinstance(code, str) and 1 <= len(code) <= 128 else None
                return self.respond(200, {"device_id": result[0], "token": result[1]}) if result else self.respond(403, {"error": "invalid_code"})
            role = self.role()
            if not role:
                return self.respond(401, {"error": "admin_auth_required"})
            body = self.body_json()
            if path == "/api/v1/admin/draft":
                store.save_draft(body)
                return self.respond(200, {"status": "draft_saved"})
            if role != "publisher":
                return self.respond(403, {"error": "publisher_required"})
            if path == "/api/v1/admin/publish":
                return self.respond(200, {"revision": store.publish()})
            if path == "/api/v1/admin/rollback":
                store.rollback(body["revision"])
                return self.respond(200, {"revision": body["revision"]})
            if path == "/api/v1/admin/enrollment":
                label = body.get("label", "")
                if not isinstance(label, str) or not 1 <= len(label) <= 80:
                    raise ValueError("invalid label")
                return self.respond(200, {"code": store.create_code(label), "expires_in": 600})
            if path == "/api/v1/admin/device":
                device_id = body.get("device_id")
                if not isinstance(device_id, str):
                    raise ValueError("invalid device")
                if body.get("action") == "revoke":
                    store.db.execute("UPDATE devices SET revoked=1 WHERE id=?", (device_id,))
                    store.log("publisher", "revoke", device_id)
                elif body.get("action") == "assign":
                    room = body.get("room", "")
                    if not isinstance(room, str) or len(room) > 30:
                        raise ValueError("invalid room")
                    store.db.execute("UPDATE devices SET room=? WHERE id=?", (room, device_id))
                    store.log("publisher", "assign_room", device_id)
                else:
                    raise ValueError("invalid action")
                store.db.commit()
                return self.respond(200, {"status": "updated"})
            if path == "/api/v1/admin/media":
                items = body.get("media")
                if not isinstance(items, list):
                    raise ValueError("media list required")
                if items:
                    manifest = {"schema_version": 1, "revision": "check",
                                "hotel": store.draft()["hotel"], "media": items}
                    host = os.getenv("HOTEL_PUBLIC_MEDIA_HOST", "")
                    if not host or not valid_manifest(manifest, host):
                        raise ValueError("invalid playlist")
                store.set_state("media", json.dumps(items))
                store.log("publisher", "save_media", str(len(items)))
                return self.respond(200, {"status": "media_saved"})
            return self.respond(404, {"error": "not_found"})
        except (ValueError, KeyError, json.JSONDecodeError) as exc:
            return self.respond(400, {"error": str(exc)})
        finally:
            store.db.close()

    def do_PUT(self):
        path = urlsplit(self.path).path
        if path != "/api/v1/admin/media-file":
            return self.respond(404, {"error": "not_found"})
        if self.role() != "publisher":
            return self.respond(403, {"error": "publisher_required"})
        expected = self.headers.get("X-Content-SHA256", "")
        extension = self.headers.get("X-Media-Extension", "")
        if len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected) or extension not in ("jpg", "jpeg", "png", "mp4"):
            return self.respond(400, {"error": "invalid_media_identity"})
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.respond(400, {"error": "invalid_size"})
        if not 1 <= size <= MAX_MEDIA:
            return self.respond(413, {"error": "size_limit"})
        store = Store()
        temporary = store.media_dir / (expected + ".part")
        try:
            data = self.rfile.read(size)
            if len(data) != size or hashlib.sha256(data).hexdigest() != expected:
                return self.respond(400, {"error": "hash_mismatch"})
            if extension in ("jpg", "jpeg") and not data.startswith(b"\xff\xd8\xff"):
                return self.respond(400, {"error": "invalid_jpeg"})
            if extension == "png" and not data.startswith(b"\x89PNG\r\n\x1a\n"):
                return self.respond(400, {"error": "invalid_png"})
            if extension == "mp4" and data[4:8] != b"ftyp":
                return self.respond(400, {"error": "invalid_mp4"})
            temporary.write_bytes(data)
            temporary.replace(store.media_dir / (expected + "." + extension))
            store.log("publisher", "upload_media", expected)
            return self.respond(200, {"sha256": expected, "extension": extension})
        finally:
            if temporary.exists():
                temporary.unlink()
            store.db.close()


if __name__ == "__main__":
    # TLS termination must be configured before a TV can connect.
    ThreadingHTTPServer(("127.0.0.1", 8131), Handler).serve_forever()
