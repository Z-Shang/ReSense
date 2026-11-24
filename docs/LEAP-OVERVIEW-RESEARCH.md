# Stock Rosie Leap overview

Read-only inspection of the supplied `res/3.33.401.153.zip` confirms a separate
Leap overview and page rearrangement system. No launcher behavior changed during
this investigation.

## Gesture and runtime

In `references/3.33.401.153/dock-layer-audit/rosie-dexdump.txt`:

- Around line 243000, `com.htc.launcher.widget.GestureDetector$2` forwards Android
  ScaleGestureDetector scale factors through OnMultiTouchListener.onScale.
- Line 200574: `LeapController$1.onScale(float)` checks canEnterLeapMode and calls
  enterLeapMode. Its zoom-out branch derives mAnimationProgress from scale,
  clamps it to 0..1000, and notifies listeners. This is gesture-driven progress,
  rather than only a fixed animation after recognizing a pinch.
- Line 202595: enterLeapMode records the selected page, sets ZOOM_OUT and notifies
  beginLeap/onAnimationStateChanged listeners.
- Around line 50366, FxWorkspace.PageLeapControl builds Main_0N_IN/OUT marker
  arrays. Its setZoomIn/OutAnimationProgress methods drive the corresponding
  Fusion timeline.
- FxLeapRearrangeController, LeapRearrangeController and their listeners handle
  drag, drop, cancellation, movement and completion. DesktopItemController and
  Workspace receive the completed page order.

## Scene assets

Under `references/3.33.401.153/mode10/Rosie/assets/Port/scenes/`:

- Rosie_leap.m10: Leap_view contains Main_00_IN/OUT through Main_07_IN/OUT,
  each 15 samples at approximately 29.97 authored fps, plus rearrange_pop_up
  (5 samples). Seven numbered page hitboxes (00..06) are present. Eight marker
  pairs must not be interpreted as proof of eight user pages.
- Rosie_leap_rearrange.m10: separate drag/drop panels, numbered drop targets,
  timeline.homepage, leap_view_panel.png and leap_view_frame.9.png.
  Drop_activate/deactivate/over/out are zero-length state markers, not timed
  tweens.

## Port implications

The former horizontal PageOverview dialog did not reproduce this original Leap scene.
An adaptation can reuse the existing page operations and thumbnails, but should
add pinch-driven zoom progress and recover the scene's thumbnail transforms,
selection/home decoration and rearrange tracks. Exact thresholds, release
settling, full layout geometry and marker selection remain to be traced before
claiming a faithful reproduction. Preserve the existing minus-one page rules.

## Implemented adaptation

The horizontal picker is replaced by seven spatial thumbnails in the recovered
2/3/2 slot arrangement. `extract_leap.py` reads the ROM at build time and exports
all seven selected-page variants of panel position/quaternion tracks, the stock
panel/frame textures, and rearrangement marker metadata. The OUT marker boundary
is included as the settled endpoint of each IN track; the source span is 501 ms.
The port plays enter/exit at 60% of that duration (about 300 ms), keeping the
recovered curves and finger-driven pinch progress.
Runtime interpolation follows display vsync, not the authored 29.97 fps.

`HtcLeapMotion` projects the recovered depth and rotation into the native view
hierarchy and calibrates endpoints against the stock hitboxes. Its focal distance
is inferred from panel scale and settled geometry; this does not reconstruct the
original Fusion renderer or claim a recovered runtime camera. The same tracks
are reversed for selection/exit. Page thumbnails are rendered once per refresh,
keeping real widget host views attached to the workspace. The original panel and
frame artwork is reused. Off-page widgets use provider preview artwork (or their provider icon); invalid widget IDs are omitted, matching the desktop.

An inward two-finger gesture drives partial overview progress; release completes
or cancels it. Long-press on blank space and Manage pages remain alternate entry
points. Tap selects a page; hold/drag reorders; accessible left/right actions offer
an alternative. Add/remove controls preserve existing confirmation and widget-ID
cleanup. Groups of seven keep more than seven existing pages reachable; Google
Discover is excluded from the editable slots. These extra controls and grouping
are adaptations, not claims about the stock Leap UI.

The page-shift animation uses the recovered rearrange position curve (334 ms),
and drag presentation uses the recovered scale/opacity endpoint. The modern
Android drag system handles the drag shadow and drop dispatch.

Validation: 18 Python extraction tests pass. Android instrumentation covers
pinch progress/commit/cancel, real UiAutomation two-finger input across the Dialog
window, spatial slot ordering, page operations and persisted selection. Drawer
and Remove regressions pass. The recovered .9.png selection texture uses
nine-slice rendering so its border remains thin as thumbnails scale.

Thumbnail correction: the selected page is captured from its actual item views in
workspace coordinates. Other pages share the desktop shortcut view factory and
clock sizing. Preview bitmaps preserve the workspace aspect ratio and fit inside
the card above its caption, rather than stretching independently drawn icons,
labels and widgets to the stock tile dimensions. Stale saved widget IDs are
skipped without deleting workspace data. Regression checks compare clean and
stale-widget bitmaps on both current and off-screen pages and verify aspect ratio.

The selected thumbnail border fades with the closing Leap progress instead of
remaining opaque through the zoom-out. Instrumentation checks an intermediate
border alpha during dismissal.

The Leap dialog now uses the non-floating launcher theme with explicit
edge-to-edge layout and transparent system bars. Its translucent root background
covers the full window, including beneath the status bar; WindowInsets padding
applies only to the child controls. The Leap instrumentation verifies shade bounds
against the physical window and checks that the title stays below the status bar.
