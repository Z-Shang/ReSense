# Original HTC Weather widgets

Recovered from `res/3.33.401.153.zip`, entry
`system/app/HtcWeather3DWidget.apk` (package `com.htc.widget3d.weather`).
The original APK, extracted assets, decoded provider metadata and scene reports
are in `references/3.33.401.153/weather-widgets/`.

The provider metadata explicitly registers three variants:

| Grid size | Original class | Scene |
| --- | --- | --- |
| 2×1 | `.Weather2x1Widget` | `assets/Port/Weather_2x1.m10` |
| 2×2 | `.Weather2x2Widget` | `assets/Port/Weather_2x2.m10` |
| 4×4 | `.Weather4x4Widget` | `assets/Port/Weather_4x4.m10` |

`widget-previews.jpg` places the original provider preview images side by side.
The 4×4 preview includes a large temperature, city, condition, four forecast
cards and refresh time. These are standalone weather widgets, distinct from
the flip-clock/weather widget.

There are 83 Mode10 files: three widget layouts and 40 condition scenes in
each of the large and small sets. Forecast icons and provider previews are
also extracted. `layout-*.json` records geometry and timing decoded by the
existing research tools; these reports are partial interpretations of the
binary format, not a complete renderer or runtime trace.

The APK declares HTC Rosie/Fusion libraries and Rosie's proprietary widget
provider metadata and update broadcast. It is not a modern Android AppWidget
implementation. Recreating the layouts in our launcher can reuse this artwork
and scene evidence, backed by the existing shared weather repository.

Re-extract with `python3 tools/extract_weather_widgets.py`. The generated
`provenance.json` records source paths and SHA-256 hashes. The recovered APK
was compared byte for byte with the ROM entry, and all 141 extracted assets
were checked against their recorded hashes.

## Launcher integration

All three styles are available through **Add Widget → Sense Launcher → HTC
Sense Weather**. The picker uses the original ROM previews. Workspace entries
persist as `type: weather` with `weatherStyle: 2x1`, `2x2` or `4x4`; placement,
resizing, removal and page previews use the normal built-in-widget paths and
allocate no Android AppWidget IDs.

`SenseWeatherView` binds original scene node names to the existing
framework-backed `WeatherRepository` mirror. City/GPS selection, current weather
and forecasts follow the Weather app. Tapping opens Weather; the 4×4 footer
refreshes shared data. Missing temperatures display dashes, and unknown WMO
codes do not retain an old condition animation. Four forecast cards use the
next four daily entries, with day labels in the selected city's timezone.

The original coordinate systems are 260×142, 260×344 and 530×748. Each scales
uniformly to fit its grid allocation. Original Droid Sans and
`HelveticaNeueLTPro35Th.ttf` supply the typography. The two large temperature
controls are selected according to number width. Layout and condition scene
clocks are independent: stable layout marker poses remain visible while the
condition entrance plays on Choreographer. The second 4×4 composition is the
stock city-transition buffer and remains inactive for this version.

`tools/prepare_weather_widgets.py` packages all 83 original Mode10 scene files,
previews, forecast icons, fonts and a subset of 80 referenced shared texture
records. Per-scene embedded texture IDs retain precedence. Preparation and
bitmap/font loading happen on one worker; callbacks from detached views are
ignored. Typeface instances are reused so a live mapped font file is not
rewritten by another widget load.

The adapter uses the framework's Canvas backend for page transitions and
snapshots. Camera and interpolation remain adaptations; panel nine-slice caps
are explicit modern binding choices. This is not a claim of pixel-exact HTC
runtime reproduction. Multiple widgets currently follow the globally selected
shared city; per-widget city selection and the original animated city-switching
sequence are not implemented.

Validation: `python3 -m unittest discover -s tools -p test_weather_widgets_assets.py`
checks byte-exact scene/preview/font recovery and all material dependencies.
`python3 tools/test_fusion_core.py` covers shared runtime behavior. Native suite
`weather_widgets` checks all styles, shared forecast/condition updates, unknown
fallback, snapshots, picker registration, isolated workspace persistence,
resize/removal and absence of allocated provider IDs. It backs up and restores
local weather preferences with framework sharing suspended; the canonical
framework weather store is not modified by fixtures.
