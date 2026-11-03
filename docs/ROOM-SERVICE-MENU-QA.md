# Room Service menu transcription for internal QA

The offline seed stores `room_service.menuVersion = 2026-11-04.1`, separately from the APK version and the server's published `contentVersion`. The existing authenticated content package publishes this JSON and its SHA-256; the Android client validates the complete package before activation and keeps the previous generation on failure. The same data can be edited as a CMS draft, previewed, validated and published without rebuilding the APK.

## Source evidence

Seven user supplied menu photographs in `C:\Users\ADMIN\Downloads` were read without changing them. They are not packaged in the APK; the TV shows a legible bilingual text transcription. `room-service-menu-transcription.csv` lists every published number, category, Thai name, English name, and THB price.

| Page | File | SHA-256 | Content |
| --- | --- | --- | --- |
| 1 | S__131489795_0.jpg | 7749689205C9A77007FDCE597C1CE95E258BC0C0A0DA0081972D2ADEA84FB669 | Cover |
| 2 | S__131489796_0.jpg | F9648257E6E98C252DEB5F00E95A234F1CD8E4EAEB3F8F7E26E22762834298A2 | 1–8 |
| 3 | S__131489797_0.jpg | DED15C440D1DE86BC80CC4F7B3F0EB89BBC5FEFBBA8E0CC51D4E84F583074EF3 | 9–20 |
| 4 | S__131489798_0.jpg | 7F5839211EB775337DA80628F86548F82317A18AE7B5F863BFFCD9CAF5C61D16 | 21–33 |
| 5 | S__131489799_0.jpg | D8AEAC6398B98296041FF7C5151F201DA23FEB95F64ACCF44724B2851D0933F3 | 34–50 |
| 6 | S__131489800_0.jpg | 94BD52421E9F681B2373AFF2A9DB880F90604F3AD60B0280EFD8EF2ECF31B050 | 51–67 |
| 7 | S__131489801_0.jpg | CA3CC5A59C06C7AE05B7F8F8BE6B7CF24C4F6310D22EEF3EE1AD0F04D2DBF71F | 68–83 |

## Items withheld for hotel verification

- **#37**: the printed Thai and English names describe different dishes. English appears to say “Friend noodle in thick gravy with chicken”; Thai appears to refer to fried noodles with shrimp or beef. The source needs correction before publication.
- **#38**: the printed Thai and English names also conflict about noodle type and protein. The source needs correction before publication.

The approved transcription therefore has **81 distinct items** in 13 printed categories. It does not claim that #37 or #38 is verified. No Room Service hours appear in the seed or UI. Prices are transcribed from the menu photographs and should be reconfirmed with the hotel before a room rollout. The interface instructs guests to call **4003** and contains no cart, ordering API, or payment flow.

## Update process

Edit the `room_service` object in the CMS draft. Increment `menuVersion` when the menu changes; the server assigns its own `contentVersion` at Publish. Validate and preview the draft, then Publish. Android Auto Sync validates the authenticated manifest, SHA-256, JSON schema fields and complete package before activation. Offline Only uses the bundled or last known good content and does not request the server. Older published content without `room_service` uses the bundled menu until republished.

This QA build is a debug signed internal artifact. Confirm the signing certificate against the APK already installed on TCL before attempting an in-place update. Physical navigation, readability and exact menu transcription still require owner QA.
