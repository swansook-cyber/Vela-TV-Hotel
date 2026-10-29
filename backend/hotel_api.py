"""Read-only hotel media manifest API prototype. No uploads or deployment."""
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MANIFEST = {
    "schema_version": 1,
    "revision": "pilot-empty",
    "hotel": {"name": "Ao Nang Fiore Resort"},
    "media": [],
}


def authorized(header: str | None, token: str | None) -> bool:
    if not token or not header or not header.startswith("Bearer "):
        return False
    return hmac.compare_digest(header[7:].encode(), token.encode())


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/healthz":
            self.respond(200, {"status": "ok"})
        elif self.path == "/api/v1/device/manifest":
            if not authorized(self.headers.get("Authorization"), os.getenv("HOTEL_DEVICE_TOKEN")):
                self.respond(401, {"error": "unauthorized"})
            else:
                self.respond(200, MANIFEST)
        else:
            self.respond(404, {"error": "not_found"})

    def respond(self, status: int, data: dict):
        payload = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    # Local-only pilot scaffold; production requires TLS proxy and device enrollment.
    ThreadingHTTPServer(("127.0.0.1", 8131), Handler).serve_forever()
