# Vela Hotel V1.0 staging admin guide

The Python server binds to `127.0.0.1:8131`. It must run behind a staging HTTPS reverse proxy with access controls before TVs connect. Keep `backend/.hotel-data` private and backed up. Never commit tokens or this database.

Set two different, long random environment variables `HOTEL_EDITOR_TOKEN` and `HOTEL_PUBLISHER_TOKEN`. Set `HOTEL_DATA_DIR` to a private persistent staging directory. Set `HOTEL_PUBLIC_MEDIA_HOST` to the HTTPS host used by the TVs. Start with `python backend/hotel_api.py`. For a local admin browser, open `/admin`, authenticate with username `editor` or `publisher`, and use the matching token as password. A remote browser must use HTTPS.

Editor can save draft and preview. Publisher can additionally publish, roll back, upload media, create one-time enrollment codes, assign rooms, revoke devices and inspect the audit log. All changes stay in staging until Publish. The first draft comes from `docs/hotel-content-sample.json`; there is no published content until a publisher acts.

The dashboard shows revision IDs, device last-seen time and recent audit actions. One-time TV enrollment codes expire after 10 minutes and can be used once. TV device tokens are random, stored hashed on the server, and encrypted with Android Keystore on the device. Revoke a TV from the dashboard if it is lost or decommissioned; enroll again to rotate its token. Do not use the same enrollment code for multiple TVs.

Upload a JPG, PNG or MP4 with the CMS. The server checks byte size, SHA-256 and file signature. Then save a playlist entry with `id`, `type`, `extension`, `sha256`, `duration_seconds` and URL `https://<HOTEL_PUBLIC_MEDIA_HOST>/api/v1/media/<sha256>.<extension>`. The media endpoint requires that TV's bearer token; it serves only files in the published playlist. Upload does not publish. Preview and validate on a pilot TV before guest use.

The server is a local pilot implementation. Put the proxy and server on a trusted staging network. Keep HTTPS certificates valid, restrict admin access, back up the database/media directory, and do not expose port 8131 directly. The browser dashboard uses HTTP Basic authentication over the proxy; its token must therefore never travel over plain HTTP outside loopback.

## Rollback

Use Status / audit to find the last good revision, then Rollback with that revision ID. The active TV keeps its prior verified media when sync fails. For an APK regression, uninstall the V1.0 pilot from the test TV and reinstall the verified V0.3.3 debug APK. This does not change the TV's default HOME app. Avoid clearing app data until the previous media and device enrollment are no longer needed.
