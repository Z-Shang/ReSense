# Condition-specific HTC weather animation catalogue

The local ROM contains 40 original clock scenes: codes 01–08, 11–26, 29–44.
`tools/extract_weather_catalog.py` exports every scene, all 45 referenced
textures, original track samples, node hierarchy, layer-parent references,
layer ordering, pivots, per-layer start times/durations and timeline markers.
41 textures come from Fusion's default shared container; four are embedded
in individual scenes. Formats include dual ETC1 and uncompressed RGBA8888 after
FastLZ decompression. Generated resources and artwork remain build-local.

The renderer uses the original source quads and UVs, including flipped textures,
and composes transforms using both hierarchy parents and sibling `LayerParent`
links. A zero-opacity transform helper can parent visible sprites without
hiding them: opacity inherits through the scene hierarchy, not layer-parent links.
Layer index ordering replaces our previous hand-arranged sun/cloud draw order.
Each layer samples its own local time after its `InFrame` offset, and its
`Duration` gates visibility. This staggers rain, snow, leaves and lightning.

`HtcWeatherScene` plays the recovered In and Rest ranges once at their authored
FPS/speed, then holds the settled pose. Playing Rest once is a modern host choice;
original callback/loop behavior remains unverified. The Out fade is not played
while showing a forecast. Animations stop when the clock detaches or system
animations are disabled. A shared 12 MiB texture cache bounds cached artwork.
Projection is still adapted; the original effective camera is not yet reproduced.

## Open-Meteo WMO mapping

| WMO codes | Original HTC scene | Mapping notes |
| --- | --- | --- |
| 0 | 01 Sunny / 33 Clear at night | Day/night variants |
| 1 | 02 Mostly Sunny / 34 Mostly Clear | Day/night variants |
| 2 | 03 Partly Sunny / 35 Partly Cloudy | Day/night variants |
| 3 | 07 Cloudy | No sun/moon implied |
| 45, 48 | 11 Fog | Rime-fog distinction unavailable |
| 51, 53, 55 | 12 Showers | Closest available art; no HTC drizzle scene |
| 56, 57, 66, 67 | 26 Freezing Rain | Freezing drizzle shares freezing-rain art |
| 61, 63, 65 | 18 Rain | Intensity remains in the WMO data |
| 71, 73, 75, 86 | 22 Snow | Heavy snow showers share snow art |
| 77, 85 | 19 Flurries | Snow grains/light snow showers approximation |
| 80, 81, 82 | 12 Showers | Cloud/sun coverage is not inferred |
| 95, 96, 97, 99 | 15 Thunderstorms | Hail has no exact matching clock storm scene |
| Unknown | No scene | Does not invent a sunny condition |

This covers all 29 WMO codes currently listed by Open-Meteo. Other HTC conditions
(hot/cold/windy/haze and mixed sky/precipitation variants) remain accessible in the
preview, but WMO condition alone does not identify them unambiguously. We do not
invent additional weather facts to select those scenes.

HTC labels and code meanings were checked against `StateResources.getConditionText`
in the supplied `HTCExtension.jar` and `com.htc.resources.apk`'s
`htc_weather_conditions` array. The 44-entry string array is compact: raw array
indices are not HTC scene codes. The framework's switch accounts for gaps such
as 09, 10, 27 and 28. Original `WeatherView.setTodayImage` formats the condition
ID into `s_XX.m10` before loading it.

## Preview and validation

Tap the weather widget → Preview HTC animations to select any original scene
and replay it. Preview selection does not change the live location or forecast.

The Android instrumentation runner `WeatherSceneInstrumentation` loads all
40 scenes, renders start/mid/end entrance and settled-phase samples, rejects blank
final output, and exports held-pose PNGs under the test target's app-private
`files/weather-scene-checks/`. Run it with:

```
adb shell am instrument -w dev.sense.launcher.test/dev.sense.launcher.WeatherSceneInstrumentation
```

The catalogue test checks scene/material completeness, timing, finite samples,
and acyclic sibling/hierarchy parent graphs. Host-side Java checks cover quaternion
and transform math, anchor preservation, condition coverage, and night mappings.

## Parent-widget sizing

Screen resolution is not the weather scene's coordinate system. The supplied
clock parent `clock_weather.m10` has a 530 × 344 root and embeds the 480 × 480
weather viewport at position (265, 284.61447), scale (0.56, 0.56, 0.56), with
pivot (−240, −240, 0). The weather viewport is therefore 268.8 source units wide,
or 50.7% of the authored widget width. The old fixed 96/374 design box used only
25.7%, making the original condition artwork roughly half its intended size.

The extractor now emits `raw/htc_weather_widget_geometry.json`. The clock derives
weather width and anchor from those values, preserves the weather scene's aspect,
and lets the normal outer view scaling handle emulator/device density. It does
not multiply assets by a hard-coded 1080/540 screen ratio. The current clock still
uses the stock preview's 374 × 272 reconstruction proportions, so anchor positions
are normalized to that design space; the entire widget has not been replaced by
the original 530 × 344 Fusion scene. Weather text draws above the artwork with a
subtle shadow for readability, and the translucent plate extends beneath it.

## Horizontal animation clipping

The parent weather rectangle defines the projection scale and anchor. It is not
also used as a hard horizontal mask in the clock: visible effects may extend
across the clock's available width (design x=2..372). Existing vertical clipping
and the original projection are preserved. This fixes the flat left/right cutoff
on Sunny's low horizontal effect without stretching the weather scene or changing
its tracks/camera adaptation.

`weather_width` instrumentation compares 120 poses across all 40 conditions.
Central pixels remain unchanged, and Sunny (scene 1) recovers previously clipped
side pixels. The comparison artifact is `weather-width-comparison.png` in the
instrumented app's private files directory.
