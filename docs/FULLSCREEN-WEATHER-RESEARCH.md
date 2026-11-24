# Stock full-screen home weather overlay

Confirmed in `res/3.33.401.153.zip`. This is a distinct scene from the clock's
`s_*.m10` weather icons and the WeatherVideo MP4 backgrounds. The initial investigation was read-only; a native runtime adaptation is now implemented (see below).

## Original implementation

`system/app/HtcWeatherWallpaper.apk` declares the
`com.htc.WeatherWallpaper.WeatherWallpaperService` service with SHOW/HIDE weather
wallpaper actions. It loads `assets/Port/weather_overlay.m10` into a Fusion
SurfaceView and adds a translucent full-display window. The scene's logical
root dimensions are **540 × 960**. The service manifest requires HTC Fusion FX.

`HtcClock3DWidget.apk` supplies the trigger and weather state. Its
`WeatherView.requestShowWeatherWallpaper()` starts the service with the
`SHOW_WEATHER_WALLPAPER` action, a `WeatherCondition` extra and an `iconInfo` int
array. The latter is the source weather icon's position/size information.
This is used for placement-aware animation; the sunlight control also selects a
`positionN` marker using a five-column position calculation.

`WeatherClockWidgetView` handles `HOST_USER_PRESENT` and `HOST_RESUME_IN_KEYGUARD`.
The former can dispatch `doResumeInKeyguard()` through the widget worker, subject
to its host flags and paused/uninitialized UI state. `checkToPlayWWP()` checks
resume cause/UI state and schedules `startWWPAnimation()` with resume-relative
delay calculations (including 1300/600 ms branches depending on clock animation).
`playWWPAtFirstTime()` separately checks `mbPlayWWPAtRosieStart` and
`mbPlayWWPAtAdd` before requesting playback. These are conditional lifecycle
paths, not evidence that every ordinary return to home always plays the effect.
`WeatherView` also checks its animation flag, location readiness and whether
weather playback is already active.

Rosie registers for `WEATHER_WALLPAPER_DISMISS`, and its hide path sends
`HIDE_WEATHER_WALLPAPER`. A legacy `WidgetHostHandle.requestShowWeatherWallpaper()`
method is marked deprecated in its own log and returns false; that stub is not
the clock widget's direct service-start implementation.

## Assets and timing

Seven primary effects share the overlay scene:

| Effect ID | Runtime control | Scene timeline |
| --- | --- | --- |
| 1 | `timeline.01_sun_light` | `01_Sun_cloud_light` |
| 2 | `timeline.02_party_cloudy` | `02_Party_cloudy` |
| 3 | `timeline.03_rain` | `03_Rain` |
| 4 | `timeline.04_ice_snow` | `04_ICE / snow` |
| 5 | `timeline.05_Sun` | `05_Sun` |
| 6 | `timeline.06_thunder` | `06_Thunder` |
| 7 | `timeline.07_windy` | `07_Windy` |

All seven primary timelines have 30 fps, speed 1, duration 150 frames, and a
`start` marker spanning frames 0–150 (approximately five seconds). The full scene
contains 138 timelines, including nested rain/sunlight effects and positional
markers, with 392 scene nodes and 254 textured sprites. Primary duration should
not be substituted for every nested timeline's duration.

Recovered all 32 referenced nonzero texture IDs: five are embedded in the overlay
and the rest resolve from `fusion.apk!/assets/Default/drawable/shared.m10`.
The remaining material ID is 0, not an unresolved nonzero texture reference.
Pixel SHA-256 values and source paths are preserved in the texture provenance.

The service has its own 54-entry `mMap2Overlay` table. It indexes HTC condition
code minus one, and zero entries suppress playback. Examples: Sunny 1 → effect 5;
Rain 18 → 3; Snow 22 → 4; Thunderstorms 15 → 6; Windy 32 → 7. Clear-night codes
33 and 34 map to zero. This mapping must be kept distinct from the clock's
40 per-condition scene mapping.

## Local evidence

Extracted material is under ignored
`references/3.33.401.153/fullscreen-weather/`:

- `HtcWeatherWallpaper/assets/Port/weather_overlay.m10` and `.r10`: original scene.
- `overlay-analysis.json`: scene hash/dimensions, exact mapping and all timelines.
- `overlay-textures/`: decoded PNGs plus `provenance.json`.
- `HtcWeatherWallpaper-evidence.txt`: service/control/mapping method instructions.
- `HtcClock3DWidget-evidence.txt`: widget triggers, flags and timing helpers.
- Per-package `dexdump.txt`: full disassembly.

The original DEX files identify themselves as version 036, which the local
modern dexdump rejects. The `analysis-035.dex` copies change only the version
header and recalculate the signature/checksum to permit static inspection;
original `classes.dex` files are preserved. No APK was rebuilt or installed.
Mode10 inspection used the existing bounded parser, `scene_timing()`, scene
geometry inspection and dual-ETC1/RGBA texture decoders. Checks confirmed the
seven main timeline durations and all 32 decoded PNG outputs.

## Adaptation boundary

The recovered scene and controls are enough to begin a native launcher overlay
port. Rendering fidelity, active camera, all nested timelines/blend modes and
placement markers still need verification. The existing clock renderer does not
already render this different full-screen scene. A launcher-owned overlay could
run above the home contents when home is visible, with modern unlock/resume
signals replacing HTC's widget-host callbacks. The native integration is described below.

## Native Android implementation

`FullscreenWeather` owns a transparent, non-interactive View above the home
contents. Entering home, completing an app-drawer close, rebuilding the home
view, and `ACTION_USER_PRESENT` can request playback. The receiver is exported
for these protected system actions so broadcasts from SystemUI reach it.
Resume queues a request even before keyguard dismissal completes; readiness
checks wait for a focused, unlocked home window before attaching the effect.
A screen-off flag also recognizes wake-up resumes when SystemUI does not emit
USER_PRESENT; a pending unlock is retained for ten seconds. An unlock arriving
just before Activity resume is remembered, and a
two-second duplicate guard avoids playing twice for the same transition.
A missing forecast waits for data; unknown conditions and stock zero mappings
do not invent an effect. Touch, drawer entry, pause, screen-off and destruction
cancel loading/playback. No system-overlay permission is used.

Sense Settings → Weather has independent unlock/home switches (both enabled by
default) and **Preview full-screen weather** with all seven effects. Preview
uses explicit effect selection and does not change the live city or forecast.
It waits for home window focus before attaching. If system animations are
disabled, automatic effects are suppressed and explicit previews show a short
held pose.

The build extracts `htc_overlay_scene_01` through `_07` using
`tools/extract_fullscreen_weather.py`; `htc_overlay_catalog.json` contains the
original condition table recovered directly from the APK's DEX array payload.
Only the selected branch of the original scene is loaded. Its 32 textures plus
the original untextured white flash material are resolved into local resources.
The generator preserves transform/opacity tracks, layer parents, source draw
order, nested timeline start offsets and quad UVs. Scene parsing and bitmap
loading happen on a worker, with stale requests discarded. Per-frame drawing
reuses matrices, track arrays and decoded textures.

The shared `HtcWeatherScene` renderer accepts logical dimensions; existing clock
scenes continue to use their original 480 × 480 projection. Full-screen effects
use 540 × 960, uniformly scaled by `min(viewWidth/540, viewHeight/960)` and
centered in the available window. This preserves original framing and avoids
stretching/cropping to a different aspect ratio. Extra space on taller/wider
displays remains transparent, not black letterboxing. Weather sprites may extend
outside the logical rectangle, clipped only by the actual overlay view.

Playback uses Choreographer frame timestamps and floating-point source frames:
150 source-frame intervals still take 5 seconds. At 120 Hz a new sample is about
0.25 source frames apart, with interpolated transforms/opacity and normalized
quaternion interpolation. The launcher's existing refresh-rate preference asks
for its highest supported same-resolution rate. This targets the current display
cadence; it does not force a refresh mode or guarantee 120 rendered frames/sec
on every GPU, power policy or scene. No physical 120 Hz phone was tested.

Remaining fidelity limits: the effective proprietary Fusion camera and blend
semantics are not fully recovered. The native renderer retains its adapted
perspective and standard alpha composition. Legacy sun-position controls named
by the service are absent from this ROM scene, so source scene placement is
retained rather than inventing those markers. The implementation is a native
adaptation using original assets/tracks, not a pixel-exact Fusion runtime port.

Validation: 22 Python extraction/geometry tests, seven visible effects across
35 held poses, fractional-frame image changes for all seven effects,
aspect-preserving portrait/tall/landscape geometry, synthetic 120 Hz timing,
actual emulator display callbacks, touch dismissal and drawer cancellation.
The `fullscreen_weather` instrumentation suite also checks automatic home playback
from a cached live-condition-shaped snapshot and cleanup when another Activity
opens. Test forecast/settings preferences are restored afterward. The exported
contact sheet is `files/fullscreen-weather-effects.png` in the launcher data.

Android timing reference: https://developer.android.com/reference/android/view/Choreographer

## Rain streak boundary on tall displays

Held frames 45–100 exposed a horizontal cutoff in `b_raindrop_streaks.png`.
The original quad is 244 × 320, scaled approximately 2.463 × 2.459 under
`Raindrop`; its lower edge is well inside the 540 × 960 source scene and
remains visible inside taller modern windows. The texture contains opaque
streaks right to that edge. This is a material boundary, not an extra clip
rectangle in the overlay View.

Full-screen playback now fades the last 15% of that texture's height using a
texture-space alpha gradient and an isolated Canvas layer with DST_IN. The mask
uses the same quad projection as the texture, including rotation/depth, and
keeps decoded ROM assets and transform/timing tracks intact. Other weather
materials and the widget renderer retain their previous behavior. The wiper
itself still swings outside the screen, as its source tracks specify.

The instrumentation suite exports a 12-pose tall-screen rain contact sheet to
`files/fullscreen-rain-framing.png`, in addition to the seven-effect sheet.


## Shared-build regression

For the install-once framework distribution, build the instrumentation against
its generated resource IDs rather than using the bundled AGP test APK:

```sh
source tools/android-env.sh
./gradlew assembleSharedDebug
python3 tools/build_shared_launcher_tests.py
adb -s emulator-5554 install -r build/outputs/shared-debug/launcher-debug.apk
adb -s emulator-5554 install -r build/outputs/shared-debug/launcher-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e suite fullscreen_weather dev.sense.launcher.test/dev.sense.launcher.WeatherSceneInstrumentation
```

The suite checks all seven effects, automatic home rebuilding and, on the
unsecured emulator, an actual screen-off/wake/keyguard-dismissal cycle with
home playback disabled and unlock playback enabled. Weather/effect preferences
are restored afterward. It does not set or remove a device credential.
