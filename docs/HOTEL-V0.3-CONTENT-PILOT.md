# Vela TV Hotel V0.3 — Hotel-first Pilot

Four guest-facing sections exist: Entertainment (installed TV apps), Our Resort, Explore Krabi and Guest Information. Quick Apps appears on Welcome.

## Content editing
Bundled offline copy resides in `android-client/src/main/assets/hotel_content.json`. Replace sample descriptions with resort-verified text in a new branch, run CI, and install the new APK for the local content test. No guest credentials, Wi-Fi passwords, confidential personal information or unverifiable service claims should be committed to this public repository.

This is a **bundled-content pilot only**. Remote CMS editing and authenticated HTTPS Media Sync are separate future integrations; editing the bundled JSON needs a new APK build. The original resort logo remains bundled.

## Real device gate
Test all four category cards, D-pad focus, OK/Back, Quick Apps launches, scroll behavior, offline startup and 1080p resolution on Xiaomi TV Box S 2nd Gen. Do not switch the TV HOME default. Do not deploy to guest rooms until all content is verified and approved.
