# Android TV pilot — next integration gate

The MediaSlideshow class plays **local verified files only**, and must not be fed directly from an untrusted manifest. It has not yet been wired into MainActivity. This is intentional: no remote content or default-home behavior until the cache pipeline and real-device tests exist.

Before connecting to production:
1. Import the exact supplied resort PNG into drawable-nodpi, preserving its colors and proportions. No logo reconstruction.
2. Add per-device enrollment and HTTPS client, strict media-host allowlist, streaming downloads with size limits, SHA-256 verification and atomic cache swap.
3. Wire MediaSlideshow to lifecycle onStart/onStop; measure video memory and test corrupt file fallback. Use sampled image decoding and hardware video decoder as needed.
4. Verify focus, app launching, Back and Home on a real Android TV and Google TV; keep stock launcher recoverable.
5. Test Android CI and debug APK. Do not deploy to guest rooms before explicit approval.

Note: this is an early implementation scaffold, not a production-safe slideshow player.
