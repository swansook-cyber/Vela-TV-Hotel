# Fiore website seed for V1.0.3

Checked: 2026-10-30. The source repository is public. Website photographs have **no confirmed redistribution license**, so source photos and processed WebP files are excluded from Git and GitHub Actions. Use them only in a local, internal QA APK until the resort confirms reuse rights. Do not upload that APK or its screenshots publicly.

## Pages checked and seed use

| Official page | Verified use in offline seed |
| --- | --- |
| [Home](https://www.aonangfiore.com/) | Tropical forest setting, resort name, Ao Nang location |
| [Accommodation](https://www.aonangfiore.com/accommodation/) | Listed cottage and villa categories; guest room Wi-Fi |
| [Gallery](https://www.aonangfiore.com/gallery/) | Official hotel photo selection |
| [Dining](https://www.aonangfiore.com/dining/) | De’fish, Fiore Coffee, Pool Bar; cuisine descriptions |
| [Facilities](https://www.aonangfiore.com/facilities/) | Pool, massage, front desk and guest services |
| [Activities](https://www.aonangfiore.com/activities-excursion/) | On-site zipline and excursions |
| [FAQ](https://www.aonangfiore.com/faq/) | Check-in from 14:00, check-out by 11:00, Wi-Fi and shuttle details |
| [Contact](https://www.aonangfiore.com/contact/) | Resort telephone and email |
| [Offers](https://www.aonangfiore.com/offers/) | Link guests to current offers; no rates or promotion terms cached |

Hours, policies, availability and contact details can change. The seed descriptions include the check date and direct guests to reception for current information. No guest information, Wi-Fi password, prices or payment details are included.

## Source photographs

| Use | Official source URL | Rights status |
| --- | --- | --- |
| Hero, resort villas aerial | https://www.aonangfiore.com/wp-content/uploads/2026/02/Aonang-Fiore2.webp | Unconfirmed; internal QA only |
| Rooms | https://www.aonangfiore.com/wp-content/uploads/2018/12/Deluxe-Cottage-Double-Main.webp | Unconfirmed; internal QA only |
| Dining | https://www.aonangfiore.com/wp-content/uploads/2026/02/Dining-Main.webp | Unconfirmed; internal QA only |
| Facilities, pool | https://www.aonangfiore.com/wp-content/uploads/2026/02/Swimming-Pool3.webp | Unconfirmed; internal QA only |
| Generic resort backdrop for offers card | https://www.aonangfiore.com/wp-content/uploads/2026/02/Aonang-Fiore10.webp | Unconfirmed; internal QA only |

The fourth card uses a generic resort view rather than an offer graphic. The current offers themselves are intentionally not embedded because they change.

## Local-only asset preparation

Run `python tools/prepare_fiore_internal_seed.py` after installing `Pillow` and `requests`. The script checks image MIME type, resizes and compresses to one 1600×900 hero WebP and one 1600×800 four-photo sheet, and writes a source manifest with SHA-256 values to the ignored `android-client/src/main/assets/fiore_internal/` directory. Total generated image size is expected to be under 1 MB. These assets are packaged by a **local** debug build only. Builds without them use the existing clearly labeled illustrative fallback. The CMS-published content file still takes precedence over `hotel_content.json`.

This arrangement keeps website photographs out of the public repository and public CI artifacts. A private internal distribution channel or written photo permission is required before a website-photo APK can be shared beyond internal QA.
