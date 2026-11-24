# Stock Rosie dock animation audit

Inspected `res/3.33.401.153.zip`, specifically
`Rosie.apk!/assets/Port/scenes/Rosie_navbar.m10` and Rosie's classes.dex.
This audit changes no launcher implementation or generated assets.

## Confirmed scene tracks

The root `__Rosie_NavBar` and `timeline.NavBar_main`/`timeline.NavBar_edit`
contain the following named markers at approximately 29.97 authored frames/second:

| Marker | Start frame | Frame count | Meaning established from code/geometry |
| --- | ---: | ---: | --- |
| `slide_off` | 10 | 11 | Hide FunctionBar; phone controls/background and page rail descend |
| `slide_on` | 35 | 11 | Show FunctionBar; those elements return |
| `recent_apps_on` | 60 | 11 | NavigationBar's HideButtonBar marker |
| `recent_apps_off` | 100 | 11 | NavigationBar's ShowButtonBar marker |
| `rearrange_add_on` / `off` | 130 / 165 | 0 / 0 | Select add-panel FunctionBar states |
| `rearrange_remove_on` / `off` | 205 / 240 | 0 / 0 | Select remove-panel FunctionBar states |
| `edit_header_on` / `off` | 280 / 310 | 11 / 11 | Show/hide edit header |
| `remove_header_on` / `off` | 340 / 370 | 11 / 11 | Show/hide removal header |

The zero-length rearrange markers are authored state selections. They must not be
reported as 11-frame navbar tweens. The coordinated `Page_Slide_main` scene has
26-frame workspace rearrangement markers at 130/165/205/240 instead.

For `slide_off`, `timeline.NavBar_phone_btn` and `timeline.NavBar_phone_bg`
move from y=916 to y=1007, a 91-source-unit downward displacement on the 540×960
scene. The scroller moves from y=912.5 to y=1008.5, or 96 units. The slide is not
linear: phone-button samples at frames 12/15/18/20 are y=945.0853 / 994.9498 /
1005.5952 / 1007. The rail has its own matching, slightly different displacement.
`slide_on` returns them by frame 45.

The `edit_header` has both movement and opacity tracks. Across frames 280–290 it
moves from roughly y=-64.9 to 0 while fading to alpha 1. During remove-header
entry, frames 340–350, the edit header leaves and fades out while `remove_header`
moves from above the scene to y=0 and fades in. These are distinct header layers,
not merely one bottom control changing its text. The scene also contains separate
edit content/background, add content/button/background, and remove content/background.

There are additional child timelines for press/release and drag feedback:
`up_to_down`, `down_to_up`, `Drop_over`, `Drop_out`, `Drop`, `disable`, `idle`,
and `enable`. These cover button surfaces and bin/drop-target feedback separately
from the main dock's visibility animation.

Eleven samples at 29.97 FPS span approximately one third of a second; exact
completion semantics may include an endpoint frame. This is nominal authored
timing, not a recommendation to render at 30 FPS on a modern display.

## Confirmed runtime bindings

Existing dump:
`references/3.33.401.153/dock-layer-audit/rosie-dexdump.txt`.

- NavigationBar construction in `FxWorkspaceClient`, around lines 64228–64321,
  explicitly binds `recent_apps_off/on` to Show/HideButtonBar,
  `slide_on/off` to Show/HideFunctionBar, and the add/remove/header markers
  to their corresponding controls.
- `FxWorkspace$NavigationBar.hideFunctionBar` (line 49948, DEX 0x04bbb4)
  and `showFunctionBar` (50194, 0x04bd58) call `TimelineAnimation.play()`.
- `FxWorkspace$FxLeapListener.beginLeap/endLeap` and
  `onAnimationStateChanged` (around 47600–47900) coordinate edit-header visibility
  and restoration of the FunctionBar for desktop overview/edit transitions.
- `FxWorkspaceClient$11.onBegin/onCompleted` keeps the relevant layers visible
  while HideButtonBar plays, then hides the FunctionBar layer on completion.
  Other listeners likewise sequence the add/remove/header transitions rather
  than replacing everything with one instantaneous visibility change.
- `TimelineAnimation` defaults mSpeed to 1.0 (constructor around 76785), and its
  play method forwards marker endpoints and mSpeed to FxTimelineControl.playWithName
  (around 77040–77098). No runtime trace on original ARM hardware was performed.

`Launcher.onOpenDrawer` (around 171855) independently invokes the Java
`SlidingDrawer.animateOpen()` implementation. Thus the drawer panel animation
and Mode10 dock/header animations are separate mechanisms. Marker existence and
NavigationBar bindings are confirmed; the exact caller/synchronization sequence
for every app-drawer state has not yet been fully traced. In particular, the
name `recent_apps_on` alone must not be treated as proof that it denotes the
All apps drawer rather than another ButtonBar transition.

## Implication for the port

The recovered tracks call for animating the dock and rail separately,
keeping them laid out during movement, animating the edit/remove layers with their
recovered position/opacity samples, and sequencing layer visibility at animation
boundaries. Preserve the user's chosen bottom Remove placement when adapting
stock header tracks; do not silently move it to the ROM header position.

## Implemented adaptation

`extract_dock_motion.py` now generates `sense_dock_motion.json` directly from the
local ROM, recording the source hash, marker frames, nominal 334 ms duration,
original channel extents and normalized 11-sample curves. The curves are cached
as float arrays and interpolated at display cadence.

- Drawer entry/exit uses the recovered `slide_off/on` profile. Its progress drives
  the normal dock and a separately sampled rail; the rail retains the 96/91 travel
  ratio. Pull dismissal and predictive Back update the same state, including
  cancellation. The dock remains laid out; controls reject taps while displaced.
- Dragging fades/slides the existing bottom Remove target using
  `remove_header_on/off`, while the normal dock recedes/returns. Header motion is
  reflected and normalized to the user's bottom placement, not moved to the ROM's
  top-header position. The drop target stays registered during entry, including
  at zero alpha, and is hidden only after exit completes.
- The page overview now uses Rosie's recovered Leap panel tracks; see
  `LEAP-OVERVIEW-RESEARCH.md`. Its normal dock still recedes/returns through the
  dock controller. There is no extra platform dialog animation, and input is
  blocked during closing. The earlier horizontal overview/header adaptation
  has been replaced.

This deliberately adopts stock profiles into current transitions. It does not
claim the unresolved original drawer/caller synchronization has been reconstructed,
nor invent durations for zero-length add/remove state-selection markers.
