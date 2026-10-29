# Ao Nang Fiore Resort original logo — Android TV Pilot

Original supplied image: `Aonang Fiore(3).png`.
Bundled path: `android-client/src/main/res/drawable-nodpi/aonang_fiore_logo.png`.
Pixel dimensions: 1071 × 457. Format: PNG RGBA, transparent background.
SHA-256 (uploaded original and committed drawable): `3a1c4b7459df4555b1953cf54514462983bd2ec86453f21e153786df1d3c5c54`.

The committed resource is the **unaltered binary original**, not a newly drawn logo,
a vector trace, an approximate recreation, or a color-adjusted image.

The Android Welcome screen loads the drawable into an ImageView with
`FIT_CENTER` so its 1071:457 aspect ratio is preserved on Android TV/Google TV.
The image is packaged with the APK and remains visible without a network.
The app is still a `LEANBACK_LAUNCHER` activity, **not** a replacement
for the device's default HOME app.

CI checks the SHA-256 source hash and assembles a debug pilot APK.

Remaining release gates: inspect real-TV layout and focus, D-pad/OK/Back,
offline slideshow, video playback, enrolled HTTPS sync, cache corruption
recovery, and explicit authorization before installing in occupied rooms.
