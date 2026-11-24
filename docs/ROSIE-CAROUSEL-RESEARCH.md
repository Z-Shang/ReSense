# Stock Rosie carousel audit (research only)

Inspected the supplied `res/3.33.401.153.zip`, not a different Sense release.
No carousel implementation changes were made for this audit.

Rosie's classes.dex contains `com.htc.launcher.RingSlideController` (DEX code
`0x0a2b00` constructor), `RingSlideAnimator` (`0x058998` scrollXToFrame),
`PageSlideControl`, and `PageSinkControl`. The controller chooses a page offset
from gesture velocity and horizontal travel, wraps page indices, starts sinking
(depth), and chooses short or long easing. The animator converts continuous
scroll X into a marker frame; the marker FPS is not the runtime display rate.

`Rosie.apk!/assets/Port/scenes/Rosie_page_slide_main.m10` contains:

- Root dimensions 540 × 960; working page dimensions 530 × 748.
- `Center Axis` rotation tracks, plus seven authored `Container_Main_00`…`06`
  templates at radius 900 source units. Adjacent authored positions are 45° apart
  (for example x/z = 0/900, 636.396/636.396, 900/0). Do not confuse authored
  templates with the runtime number of screens: code and markers cover eight
  slots, and there are special six/seven-slot mapping cases.
- Normal `0to1`…`7to0` and reverse markers, and separate `s_` marker variants.
  `RingSlideAnimator` selects sinking variants for a sufficiently large snap
  offset while the snap is in progress, rather than merely increasing tilt.
- `Page_Slide_Z` markers `z_move` / `z_move_rev`, with displacement sampled on
  `Page_Slide_X`. Source Z samples reach roughly -1000 units. Runtime sink/rise
  controls determine the actual depth and return to the normal page pose.
- Per-container opacity tracks control visibility around the ring.
- Authoring rates near 29.97/30 FPS. Java drives frames from scrolling, so these
  are sample-space coordinates, not a mandate to render at 30 Hz.

The earlier ±30° page-template tracks are separate from the central ring rotation;
using them alone does not recreate the carousel. A faithful renderer will need
ring transforms, depth, opacity/depth ordering, continuous wrap, gesture-driven
marker evaluation, and easing/settling.

Settings are device-dependent. Static initializers list portrait velocity defaults
2000/6000 and 70% travel, but `SettingUtil.loadSettings` overwrites values from
resources and enables multipage portrait scrolling. Therefore these initializer
numbers are not yet established as the active Pyramid thresholds. Similarly the
final effective camera has not been recovered for the carousel. No original
32-bit ARM runtime execution was performed.

Evidence: existing dexdump in
`references/3.33.401.153/dock-layer-audit/rosie-dexdump.txt`, notably
`RingSlideController.getSnapOffset` (line 226539), `getDestinationScreen` (226370),
`RingSlideAnimator.scrollXToFrame` (75544), and `SettingUtil.loadSettings` (233400+).
