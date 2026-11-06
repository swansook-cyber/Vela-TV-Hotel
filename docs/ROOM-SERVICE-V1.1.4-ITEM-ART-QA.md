# Room Service V1.1.4 item artwork and TCL QA

The approved `hotel_content.json` is the sole source for item numbers, Thai and
English names, prices, categories, and menu version. This version adds one AI
promotional illustration for each of its 81 published items. Numbers 37 and 38
remain excluded. The AI images are **not photos of dishes served by the hotel**.
The guest UI continues to show the Thai and English promotional disclaimer.

`android-client/src/main/assets/room_service_items/index.json` is the complete
number-to-file mapping and includes the approved names, dimensions, byte counts,
and SHA-256 for each WebP. Every file is 960×540. Item art totals 5,866,570 bytes.
The 14 existing category/hero promotional images remain unchanged. The source
PNG files and the local source map stay outside Git; the reproducible conversion
script is `tools/prepare_room_service_items.py` and requires Pillow.

Artwork is decoded only as a row becomes visible. The loader verifies the WebP
signature, byte count, and SHA-256 before displaying it. It samples into RGB 565
and retains at most 8 MiB of decoded bitmaps. A missing or corrupt image yields a
plain green placeholder labelled **Image unavailable**; it never substitutes a
different dish or a category photo.

Room Service item screens add 80 dp bottom content padding and scroll focused
cards inside a 72 dp lower safe margin. Dining Detail alone gains
the dark forest-green and gold treatment. Home, Quick Apps, Fiore24, Offline
Only, Auto Sync, and the approved menu data remain untouched.

## Evidence and checks

- The first ten source images were inspected as a contact sheet before generating
  the rest. Later batches were inspected separately for dish type and uniqueness.
- Automated checks assert 81 distinct hashes, exact number/name mapping to the
  seed, no #37–38, bounded dimensions and total bytes, and APK integrity.
- Emulator QA must exercise 1080p and 720p, the first/middle/last item in a long
  category, footer visibility, D-pad/OK/Back, and restored focus.
- TCL QA must verify real overscan, card/focus clipping, scrolling latency, image
  quality at viewing distance, and upgrade signing compatibility. Emulator results
  do not prove physical TCL behavior.

The three TCL photos described in the V1.1.4 request were not attached to the
pasted-text message. They remain needed for direct before/after comparison with
the reported real-device overflow.

## Emulator results (6 November 2026)

| Check | Observed result |
| --- | --- |
| 720p first item | #1 focused within `[68,259][1212,407]` |
| 720p middle item | #4 focused within `[68,467][1212,615]`; full gold border visible |
| 720p last item | #8 focused within `[68,467][1212,615]`; bilingual footer visible |
| 1080p middle item | #4 focused within `[85,799][1835,971]`; full gold border visible |
| Navigation | Detail Back restored #4; Categories Back restored the Home entry |
| Dining route | Dining Detail opened Room Service; Back restored Dining menu action |
| Memory | Emulator total PSS measured about 62 MiB after scrolling one category |

A narrower 1152×648 logical canvas showed Room Service category focus inside its
visible area. This is a layout stress check, not a real overscan measurement.
The pre-existing Home welcome text truncates at this narrower size; the V1.1.4
Room Service change does not alter the Home header. Real TCL overscan remains
unverified without the device screenshots and physical QA.
