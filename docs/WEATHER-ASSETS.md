# Recovered original weather textures

`tools/extract_weather_assets.py` follows `DiffuseMap` material IDs from
`HtcClock3DWidget.apk!/assets/Port/s_03.m10` into
`fusion.apk!/assets/Default/drawable/shared.m10`. The local ROM build extracts:

| Material ID | Resource | Size |
| --- | --- | --- |
| 821437838 | sense_weather_sun | 256 × 254 |
| 1131629602 | sense_weather_flare | 193 × 198 |
| 1433276943 | sense_weather_cloud04 | 247 × 171 |
| 1435648785 | sense_weather_cloud05 | 178 × 112 |

All four are format 14: FastLZ decompression produces two ETC1 planes, color
followed by alpha (red channel). PNG outputs preserve the source dimensions
and transparency. Material resolution and alpha coverage are checked by
`tools/test_weather_assets.py` when the local ROM is available.

The generated files live under `launcher/build/generated/senseDock/res/drawable-nodpi`,
with `weather-provenance.json` in the parent directory recording scene hash,
material IDs, dimensions, and source record offsets. Proprietary artwork is
not checked into source control.

The native widget uses the original sun and cloud textures with partial
track playback as described below. Placement is adapted to the modern layout.
The full condition set is not yet reproduced. This does not provide live weather.

## Original animation tracks

The scene export now includes all nine nodes, hierarchy offsets, static pivots,
material bindings, and 17 animated properties with 869 samples. Clouds use dense
vector arrays with an explicit `StartFrame` and consecutive implicit frame
indices; other tracks use the previously decoded sparse frame/value records.
The decoder retains the raw sparse interpolation fields without assuming their
full Fusion semantics. Build output includes `raw/sense_weather_animation.json`
and `weather-animation-tracks.json` beside the generated resource directories.

Native playback uses original cloud position/scale/opacity samples and both
sun layers' scale/opacity/Z rotation, plus flare rotation/opacity. It plays raw
frames 0–30 on attachment, at the recovered 30 FPS (1000 ms for the sample span), then holds the central pose.
Frames 31–60, which fade the sprites away, remain available in the export.
Position deltas and sprite placement are adapted to the widget's weather area.
Cloud 3D rotations and depth are now evaluated by the native preview, as
described below. Full parent transforms and original blend semantics remain
unimplemented. This is partial playback,
not a complete Fusion renderer. Disabled system animations show the held pose.


### Rotation and depth projection

The native preview samples all four quaternion components and normalizes a
shortest-path interpolation between adjacent samples. It rotates sprite corners
around recovered `PivotPosition` anchors, includes each corner's rotated Z,
and projects the quad through a shared perspective camera. Position Z also
controls a stable back-to-front sprite order. Texture mapping uses a four-point
projective matrix; no extra bitmaps are allocated for each animation frame.

The native camera uses focal distance 180 in widget logical units, scales scene
position deltas by 0.18, and treats positive Z as toward the viewer. These are
explicit rendering adaptations, not recovered Fusion camera parameters. The
original hierarchy, blend equations and particles still require further work.
`tools/WeatherProjectionCheck.java` checks identity and 90° rotations, equivalent
quaternion signs, intermediate orientation, depth magnification and camera-plane
bounds without requiring an Android runtime.

See [WEATHER-CAMERA-TIMING.md](WEATHER-CAMERA-TIMING.md) for recovered FPS, marker
ranges, original caller evidence, and the distinction between native camera
defaults and the still-unconfirmed active weather camera.


## Current renderer

The earlier s_03-only implementation above is superseded by the complete
[condition catalogue](WEATHER-CATALOG.md): 40 scenes, 45 textures, original
layer-parent links, per-layer timing, and condition-specific playback.
