# Vela TV Hotel Android Client — V0.1 scaffold

This directory holds the Android TV/Google TV client design and initial source scaffold. It is not yet a complete Gradle project or a signed APK.

Package namespace proposal: `net.velalab.tvhotel` (distinct from personal Vela TV `net.velalab.tvlauncher`).

- Launcher: full-screen welcome UI, focusable app tiles, D-pad/OK/Back, separate admin access.
- Playback: photo slideshow or silent MP4 with Android Media3/ExoPlayer; release on lifecycle stop.
- Content: HTTPS manifest, digest-verified cached assets and offline fallback.
- No hardcoded URL, token, room identity, or resort private information.

See [media contract](../docs/MEDIA-PLAYLIST-V1.md). Next milestone: buildable Gradle Android project and emulator QA, then one-device hotel pilot.
