# Compact bent dock

The current renderer uses geometry deformation rather than alpha masking. It implements the chosen visual reconstruction; it is not a recovered original HTC rendering path.

`BentDockLayout` fits a quadratic arc to the recovered drawable’s left, center and right alpha-edge samples. A 48 × 4 mesh bends the cleaned background and the three button surfaces. The deformation tapers to zero at the lower edges, so the dock and buttons have curved tops and straight bottoms. The background’s central strip avoids embedded separators, omits its bottom border, and is reversed vertically to put light shading on top and dark shading below.

`DockButton` keeps its ripple/gradient surface separate from its foreground. Labels use the button’s TextPaint and icons use their original drawable bounds, both drawn after the surface meshes with translation only. This leaves glyphs, baselines and icon proportions unwarped. Pointer coordinates are inverse-mapped through the surface deformation. Accessibility bounds reflect the visible surfaces.

The visual dock body is **64dp**, reduced from the prior approximately 84dp layout. Button source rectangles are 48dp high with 6dp corner radii. The curved top shortens the outer visible ends. The background spans the screen width and extends below the body through the navigation inset, with a straight screen-bottom edge. Controls remain above system navigation.

Build inputs remain the user’s local ROM and two generated images under ignored `launcher/build/generated/senseDock/`. No HTC runtime code is used.

## Verification

`:launcher:assembleDebug :launcher:lintDebug` passed with zero errors and four existing warnings. The API 35 Pixel 7 emulator screenshot was visually checked. White glyph pixels were confirmed separately in all three button regions: Apps 804, Phone 925, Settings 1289. Their accessibility bounds share a straight lower baseline at y=2321 on this emulator.

Current screenshot: `.toolchain/compact-dock-home.png`. Pixel/bounds report: `.toolchain/compact-dock-validation.json`. These checks do not establish full fidelity across devices, screen sizes or accessibility font scales.

## Page track and edge smoothing

`DockPageTrack` draws a borderless translucent rail 3.2dp above the dock’s upper curve, with a small white thumb. There is no rim/outline stroke. A 6dp drawing gutter above the dock view prevents clipping; the visible dock body remains 64dp and its buttons and bottom edge keep their positions. The indicator is passive and centered for the current single home page. `setPageIndicator(page, count)` can update the thumb when real paging is added; this change does not create additional home pages.

The background and button surfaces render to a reusable bitmap at twice the physical resolution, then downsample with bitmap filtering. Labels, icons and the vector track are drawn afterward at native resolution. This supplies edge coverage because Android’s [Canvas.drawBitmapMesh](https://developer.android.com/reference/android/graphics/Canvas#drawBitmapMesh(android.graphics.Bitmap,int,int,float%5B%5D,int,int%5B%5D,int,android.graphics.Paint)) ignores the antialias paint flag. Buffers are resized only on layout-size changes, not allocated each frame.

Build and lint passed with zero errors and four existing warnings. On the emulator, the external indicator’s white pixels occupy y=2159–2161 while the dock center starts at y=2169. All three button bounds and white-glyph pixel counts match the earlier compact dock. Sampling the curved outline found 493 intermediate-color edge samples after supersampling versus zero before, confirming partial-pixel coverage. Current artifacts: `.toolchain/dock-track-home.png` and `.toolchain/dock-track-validation.json`.

### Paged workspace indicator and transition

The rail thumb occupies one equal fraction of its measured curved path per page:
`thumbLength = railLength / totalPageCount`. Total includes the leftmost Google
entry page. Both ends remain inside the rail; its position accepts fractional
page indices so it moves continuously during the page transition.

`tools/inspect_page_slide.py` reads `Rosie.apk` directly from the local ROM and
reports quaternion tracks from `assets/Port/scenes/Rosie_page_slide_main.m10`.
The seven `Page_template_*` tracks each include yaw samples at −30°, 0°, +30°,
and back to 0° (raw frame fields 0, 60, 120, 200). The modern workspace adapts
that angular range for a horizontal slide with perspective. Its 320 ms duration
and decelerating easing are implementation choices, not recovered HTC timing.
The native Fusion hierarchy, sensor rotations, depth tracks, and carousel loop
are not emulated. The outgoing page is captured once; the incoming page remains
live. The dock stays still while its indicator interpolates. Android's disabled
animation setting skips the transition, and detachment releases the snapshot.
