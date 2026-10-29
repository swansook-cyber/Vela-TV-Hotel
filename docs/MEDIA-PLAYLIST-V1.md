# Hotel Media Playlist Contract v1

Endpoint: GET /api/v1/device/manifest (HTTPS in production).
Authorization: Bearer per-device scoped token (prototype currently uses one local-only test token; do not deploy that scheme).
Content-Type: application/json.

Example:
```json
{
  "schema_version": 1,
  "revision": "pilot-001",
  "hotel": {"name": "Ao Nang Fiore Resort"},
  "media": [
    {"id": "welcome", "type": "image", "url": "https://example.invalid/media/welcome.jpg", "sha256": "64 lowercase hex characters", "duration_seconds": 12},
    {"id": "resort", "type": "video", "url": "https://example.invalid/media/resort.mp4", "sha256": "64 lowercase hex characters", "duration_seconds": 30}
  ]
}
```

Rules: URL must be HTTPS on approved media host; never accept file:, content:, javascript:, localhost or private network addresses from a remote manifest. SHA-256 is required for every media asset. Images: JPEG/PNG. Videos: MP4 H.264, silent, 1080p target. Maximum durations and bytes enforced client-side. Download to temporary file, verify digest, then atomically activate a complete playlist. Keep previous good playlist on errors or offline. UI never blocks on network. On pause/stop, release video decoder. Never expose device token in logs.

This file defines a future client/server contract; sample URLs are nonfunctional placeholders, not production endpoints.
