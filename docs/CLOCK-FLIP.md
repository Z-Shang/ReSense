# Clock flap animation

`tools/extract_clock_flip.py` reads the user's stock ROM, opens
`system/app/HtcClock3DWidget.apk`, and decodes `assets/Port/clock_weather.m10`.
It follows the node ancestry to `Flip_Minute / flip_anim / top_0 / tile` and
`bottom_0 / tile`. Each tile supplies 11 quaternion rotation samples, with
raw frame indices 0–10. X-axis angles are extracted as `2 atan2(qx, qw)`:

- Upper flap: 0° to −180°, crossing −90° at sample 5; linear angular steps.
- Lower flap: +180° to 0°, crossing +90° at sample 5; eased angular steps.

The build generates `raw/sense_clock_flip.json` alongside the local dock
resources. No proprietary scene files are checked into the app source.
The native clock interpolates the recovered angles, draws only front-facing
halves, and changes only the hour/minute panels whose text changed. It uses
the minute flap samples for both panels; it does not reproduce the complete
Fusion hierarchy, materials, digit textures, or depth animation.

The launcher chooses a 1000 ms duration because the original runtime FPS and
trigger semantics have not been recovered. Disabled system animations show
an immediate change. Detaching the view cancels its animator and minute timer.
`tools/test_clock_flip.py` validates the extracted samples against the local ROM.

The dock indicator counts desktop pages only. Desktop page 1 maps to indicator
position 0; the Google minus-one entry is clamped to position 0 and contributes
no extra segment. Fractional desktop positions still animate during swipes.

The lower date/weather section follows the 374 × 272 stock widget preview:
location and condition on the left, weather art in the middle, date on the
right above current and high/low temperatures. Current conditions and temperatures use the same framework-backed weather
repository as the Weather app and standalone Weather widgets.
Text in the location, condition, and date regions is ellipsized to avoid overlap.

Hours display without a leading zero in either time format. A shared gradient
backplate at 65% opacity extends behind the clock and weather information;
the white flip cards retain their opaque surfaces.

## Original numeral artwork and sizing

The large clock numerals are not Android font glyphs. The clock scene references
20 shaded, alpha-bearing textures in `fusion.apk!/assets/Default/drawable/shared.m10`.
Their baked tonal gradient and fine bright edging provide the original highlights
and stroke weight. `extract_clock_digits.py` recovers them during resource generation;
`sense_clock_digits.json` records their IDs, dimensions and decoded pixel hashes.

Each numeral is 99 source pixels wide; the visibly upper and lower halves are 79
and 83 pixels tall. The original `first`/`second` positions are 108.5/192.5, giving
84 pixels of advance. These are scene coordinates within the 530-pixel-wide widget
on the Sensation's 540 × 960 display, not sp or a font point size. The launcher
scales by 374/530 into its existing design canvas: approximately 69.9 wide,
114.3 total height and 59.3 advance, before the view's final scale. Artwork is
filtered when scaled, and its colors are preserved rather than tinted by the
panel gloss. The existing reconstructed card placement remains; the complete
original card geometry and camera are not reproduced by this change.

The visible halves are named by bitmap orientation. The recovered bitmap rows are vertically reflected when rendered in Canvas.
Animated back-face node names do not directly describe the upright glyph. Single-digit hours remain
centered without a leading zero, and the requested one-second flip is retained.

## Clock and weather tap regions

The clock-card region (23–350 by 10–169 in the widget's 374×272 coordinate
system) opens the user's clock handler with Android's
[ACTION_SHOW_ALARMS](https://developer.android.com/reference/android/provider/AlarmClock#ACTION_SHOW_ALARMS).
This opens the clock UI without creating an alarm. The lower weather region
continues to open Sense Weather. Hit testing reverses the same uniform scaling
and centering used for drawing, including letterboxed layouts. Long press and
workspace dragging retain precedence. Accessibility exposes separate Open clock
and Open weather actions; the default widget click remains Open weather.
If no clock handler is available, the launcher displays a brief message.

The `drawer_actions` native suite checks both regions at the natural aspect
ratio and in a square view, plus the clock accessibility action.
