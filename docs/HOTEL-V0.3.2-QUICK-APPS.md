# V0.3.2 Quick Apps / Guest Greeting

The Welcome screen presents **exactly five slots in one row** (Netflix, Disney+, YouTube, Prime Video, iQIYI), controlled by `quick_apps` in the bundled `android-client/src/main/assets/hotel_content.json`. A missing app remains a disabled, labeled slot; it is never silently replaced by another app. Actual icons are loaded only for installed apps. Matching currently uses installed app labels (including a small alias list), and must be tested on the hotel TV model.

`guest_welcome.mode` can be `hidden`, `generic`, `room` or `name`. The optional `value` is used for room/name modes; a blank value safely falls back to "Welcome, Guest". Do NOT commit personal guest names or room occupancy information into this public repository. Guest personalization will require authenticated device-specific and time-limited delivery with automatic clearing on checkout. This pilot only provides an offline-safe presentation contract and neutral default.

**Important:** Bundled JSON configuration is NOT a live admin interface. Changing the five apps or greeting requires rebuilding the APK in this milestone. Central admin and secure sync remain future work. Do not put guest names into this file.

Physical device QA: five slots in one row at 1080p, no clipping, original logo readable, Focus/OK/Back/Google TV Home, app-not-installed placeholder, offline startup, and upgrade from 0.3.1. No HOME replacement or production deployment.
