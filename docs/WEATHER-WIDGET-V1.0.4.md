# Weather widget V1.0.4

Checked 2026-10-31. Provider: [MET Norway Locationforecast 2.0 compact](https://api.met.no/weatherapi/locationforecast/2.0/documentation). The API provides a **forecast**, not a measured temperature at the TV. The widget uses the closest current forecast for a fixed Ao Nang reference point (`8.0322, 98.8205`); it never requests device location or GPS permission. The coordinate is rounded to four decimals per provider guidance.

## Provider decision and terms

[Open-Meteo's free endpoint is limited to non-commercial use](https://open-meteo.com/en/terms), so it was not selected for a hotel guest TV. [MET Norway permits commercial use](https://docs.api.met.no/doc/FAQ.html) of its weather data under CC BY 4.0. [Its terms](https://docs.api.met.no/doc/TermsOfService.html) require an identifying User-Agent, attribution, caching until `Expires`, conditional requests using `If-Modified-Since`, and reasonable traffic. The app identifies itself with its public issue tracker, displays `MET Norway · CC BY 4.0`, and does not use the Yr brand or icon. [CC BY 4.0 license](https://creativecommons.org/licenses/by/4.0/). Weather symbols and presentation are adapted from the provider's numeric/symbol data; MET Norway does not endorse this app.

For this small pilot the Android client calls the public API directly. MET Norway recommends a shared caching proxy for larger installations; that would require a separately approved backend scope. No API secret is embedded or required. The provider has no service-level guarantee.

## Behavior

- Activity-owned repository; re-rendering Home does not initiate a request. A 60-second UI tick updates Bangkok time and checks freshness.
- On resume, stale data are refreshed. Network requests occur after at least 30 minutes **and** after the provider's `Expires` time, with up to five minutes of per-process jitter to spread device traffic.
- Requests use HTTPS, 5-second connect/read timeouts, and `If-Modified-Since` when a `Last-Modified` value exists. `304` reuses cached content. Failures retain the previous reading and use a five-minute retry delay.
- Successful readings are saved privately in Android SharedPreferences. A cached reading shows `Last updated` even without a network. An empty cache shows `Weather unavailable` while Bangkok time/date continue to update.
- Weather text has no D-pad focus or click action. Quick Apps and hotel content behavior are unchanged.

## Internal QA and remaining gates

Local unit tests cover success, duplicate refresh, timeout, HTTP error, cache retention, 304 and Bangkok date/time. Emulator screenshots and remote key runs are evidence of layout and navigation only. The 4K AVD reports a 3840×2160 physical display but Android applies a 1920×1080 logical override, so a physical 4K TCL test remains necessary. A debug-signed APK is for internal QA; no release keystore is created. Official resort photos remain in ignored local assets and are excluded from the public CI artifact pending image rights confirmation.

YouTube from Quick Apps opens the YouTube Android TV Activity. On this emulator, YouTube displays “not supported on this device” and retains the foreground after a Back key. `dumpsys activity` confirms YouTube is the resumed Activity at that point; Vela does not receive or intercept that Back event. Home returns to the system launcher, and opening Vela again succeeds. This is an emulator/YouTube compatibility limitation, not evidence of a Vela Back handler defect; verify on a physical TCL before closing the regression gate. Vela does not force Back on another app.
