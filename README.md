# Vela TV Hotel

Pilot project for **Ao Nang Fiore Resort** Android TV / Google TV guest-room launcher.

Development branch: `develop/hotel-v0.1-foundation`. This is **not deployed**.

## Current prototype
A minimal read-only, local-only Python manifest API is available at `backend/hotel_api.py`. It has no media uploads, room enrollment, admin UI or Android APK yet. Never expose it directly to the public Internet.

To test locally:

```bash
cd backend
python -m unittest discover -p 'test_*.py' -v
HOTEL_DEVICE_TOKEN=replace-with-random-local-test-secret python hotel_api.py
```

The `/healthz` endpoint is public on loopback; `/api/v1/device/manifest` requires `Authorization: Bearer <token>`. This single-token prototype is **not** suitable for a hotel fleet. Production must use per-device enrollment, token rotation, HTTPS, authorization and a proper media store.

See [V0.1 Foundation](docs/V0.1-FOUNDATION.md). The accepted personal Vela TV v4.0.0 is unchanged.
