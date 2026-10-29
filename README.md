# Vela TV Hotel

Pilot project for **Ao Nang Fiore Resort** Android TV / Google TV guest-room launcher.

Development branch: `feature/hotel-v1-complete-pilot-20261029`. This is **not deployed**.

## V1.0 staging pilot
The Android TV app offers an offline guest guide and five fixed streaming shortcuts. Its bundled original Ao Nang Fiore Resort logo is unchanged. A loopback-only Python CMS/API offers draft, preview, publish, rollback, validated media upload, per-device enrollment and revocation. The TV connects to the server only through HTTPS and stores its device token with Android Keystore. No production deployment or TV HOME change is part of this branch.

To test locally:

```bash
cd backend
python -m unittest discover -p 'test_*.py' -v
HOTEL_EDITOR_TOKEN=<random-editor-secret> HOTEL_PUBLISHER_TOKEN=<different-random-publisher-secret> python hotel_api.py
```

The `/healthz` endpoint is public on loopback. See [admin staging guide](docs/ADMIN-STAGING-V1.md), [content checklist](docs/HOTEL-CONTENT-INPUT-CHECKLIST.md) and [TV pilot QA](docs/TV-PILOT-QA-V1.md). A staging HTTPS reverse proxy is required for TV enrollment. Physical TV QA and verified hotel content remain owner gates.

See [V0.1 Foundation](docs/V0.1-FOUNDATION.md). The accepted personal Vela TV v4.0.0 is unchanged.
