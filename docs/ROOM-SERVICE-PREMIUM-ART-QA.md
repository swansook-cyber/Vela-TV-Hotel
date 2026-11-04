# V1.1.3 Room Service promotional artwork

The provided `D:\aonangfiore\Vela_TV_Hotel_Room_Service_UI_Reference.png` guided the tropical green and gold visual treatment. It is not bundled or used as a static screen. All cards, labels, prices, focus states and navigation remain native Android views.

Fourteen new AI-generated photographs were created for this internal QA version: one In-Room Dining cover plus one illustration for each of the 13 menu categories. The source PNGs were visually inspected for subject and absence of text, watermark, price and logo. The compressed 16:9 WebP files in `android-client/src/main/assets/room_service_art/` total 796,054 bytes; their SHA-256 digests and dimensions are in `index.json`. The original generated PNGs remain outside the repository under the Codex generated-images directory.

| Category ID | Illustration subject |
| --- | --- |
| hero | Assorted Thai and international dishes; left space for native title |
| appetizer | Thai spring rolls |
| pasta | Tomato and basil pasta |
| pizza | Tomato, cheese and basil pizza |
| burger_sandwich | Burger, sandwich and fries |
| spicy_thai_salad | Thai-style spicy shrimp salad |
| thai_soup | Thai herb soup |
| spicy_thai_curry | Thai curry |
| noodles | Thai stir-fried noodles |
| rice | Thai fried rice |
| thai_a_la_carte | Thai basil stir-fry with rice |
| southern_thai_traditional_food | Southern Thai curry and side dishes |
| drink | Tropical fruit drinks |
| dessert | Mango sticky rice and tropical fruit |

These are promotional illustrations, not photographs of the hotel's actual dishes. Every item row labels its image as a **category illustration**. The footer on category, item-list and item-detail pages says **ภาพใช้เพื่อการโฆษณาเท่านั้น · Images for promotional purposes only**. The guest-facing menu still reads the approved 81 items, 13 categories, item numbers and prices from the unchanged `hotel_content.json`. The images carry no Room Service hours or ordering capability.

Artwork is decoded from the APK only when its view enters the visible area. Category images use RGB 565 sampling at half resolution, with an 8 MiB bitmap cache and one decode worker. The source JSON and Fiore24 assets remain unchanged. Physical TCL focus, memory and scroll performance must still be confirmed on the device.
