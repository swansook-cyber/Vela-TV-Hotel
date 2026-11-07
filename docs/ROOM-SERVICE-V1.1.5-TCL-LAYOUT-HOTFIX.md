# V1.1.5 TCL layout hotfix QA

This hotfix responds to three physical TV photos supplied on 7 November 2026:
the Appetizer and Southern Thai Traditional Food lists show a partial fourth row
while the first row has focus, and the About the Resort copy is clipped inside
its focused panel. The photos establish the V1.1.4 problem; they do not show
V1.1.5 on the physical TV.

## Root cause and change

- About the Resort used a fixed 82 dp panel at compact sizes despite its text,
  padding, and focus ring needing more height. It now uses content height, shows
  the complete first approved sentence, and explicitly offers **Press OK to read
  the full description**. The existing detail page contains the full approved
  copy and Back restores focus to the About panel.
- The supplied list photos show the first row focused and an unfocused fourth
  row partially visible; they do not establish whether the fourth row was cut
  **after it received focus**. The previous focus scroll was checked only once,
  60 ms after focus, while native ScrollView scrolling and layout can occur
  later. The focused card is now
  checked on the next animation frame and again after 140 ms. The calculation
  uses the measured viewport, system bar/cutout insets when available, and a
  larger lower TV safe margin. The list retains all items and adds 120 dp of
  trailing scroll space. No menu copy, prices, or typography were reduced.
- A partially visible *unfocused* next row may still appear at the viewport
  edge as a scroll cue. The acceptance check is that the row becomes fully
  visible, including its focus border, when selected with D-pad. Physical TV
  overscan behavior still requires V1.1.5 installation and photos.

## Local evidence (7 November 2026)

Screenshots are outside Git in the local `Vela-TV-Hotel-RoomService-QA` folder:

| Check | Before V1.1.4 | After V1.1.5 |
| --- | --- | --- |
| About at 720p | `v115-before-about-720.png` | `v115-after-about-720.png` |
| Appetizer #4 focused at 720p | `v115-before-appetizer-fourth-720.png` | `v115-after-appetizer-fourth-720.png` |
| Appetizer #4 focused at 1080p | prior V1.1.4 emulator capture | `v115-after-appetizer-fourth-1080.png` |
| Southern long item #65 and last #67 at 720p | physical photo supplied by user | `v115-after-southern-long-720.png`, `v115-after-southern-last-720.png` |
| Smaller logical canvas, 1152×648 | no directly comparable capture | `v115-after-appetizer-fourth-overscan-sim.png` |

Emulator focus bounds (pixels): Appetizer #4 `[68,424][1212,572]` at
1280×720, #8 `[68,424][1212,572]`; Southern #65 and #67 had the same bounds.
At 1920×1080, Appetizer #4 and #8 were `[85,760][1835,932]`. On the
1152×648 stress canvas, #4 was `[70,352][1082,500]`. D-pad returned from
the last item to the first; OK then Back restored the selected item, and Back
again restored the Southern category. The About panel showed its entire first
sentence and read-more instruction on 720p and 1080p; OK opened the full text
and Back restored About focus.

The smaller logical canvas approximates reduced usable space but is **not** a
measurement of the TV's actual overscan. V1.1.5 needs real TV verification of
Appetizer #4, both categories' last items, focus borders, the About panel,
and return navigation before declaring the physical issue resolved.
