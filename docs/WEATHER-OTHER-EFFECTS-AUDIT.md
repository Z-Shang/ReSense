# Remaining full-screen weather effects: coverage audit

Checked the current renderer after the rain extension on Android API 35.
No production rendering or assets were changed for this audit.

The `weather_coverage` instrumentation suite rendered 306 poses: six effects,
three viewport sizes (270×480, 270×600, 600×270) and 17 source-frame samples
from 0 through 149, including thunder flash frame 121. All effects rendered
visible content, and each retained the original five-second duration.
The contact sheets were inspected at all three aspect ratios.

The tall overview is
`.toolchain/weather-coverage-audit/files/weather-coverage-overview.png`;
per-effect sheets and measurements are in the same directory.

## Findings and suggested adaptations

| Effect | Observed behavior | Appropriate next change |
| --- | --- | --- |
| 1: Sun and light clouds | Glare stays near the upper region; clouds occupy the source portrait footprint, especially obvious in landscape. A rotated glare quad edge is visible. | Keep the light localized. Expand cloud travel/placement; investigate glare material edges separately. |
| 2: Clouds | Top gap increases on tall screens; landscape clouds remain concentrated in a central portrait strip. Some cloud quad edges are visible in the samples. | Remap cloud anchors and travel to the viewport while preserving sprite proportions. Inspect material boundaries before adding more clouds. |
| 4: Ice and snow | Frost corners form an inset portrait frame on tall screens and a small central frame in landscape. Snow particles also remain within that footprint. | Anchor the frost corners/edges to the actual viewport, independently redistribute falling particles. |
| 5: Sun | Localized glare is appropriate; a visible rotated texture boundary also exists at the original aspect ratio. | Recover/check the glare material and edge treatment. A global stretch is not a sufficient fix. |
| 6: Thunder | The flash is a bounded rectangle instead of a full-screen wash on modern aspect ratios. Clouds/lightning also follow the source footprint. | Make only the flash wash fill the viewport; adapt cloud placement and lightning origins separately. |
| 7: Wind | Leaves and wind streaks occupy the old portrait travel area. The lower part of tall screens and outer sides of landscape screens are largely unused. | Extend particle travel and spread anchors, preserving leaf size, rotation and timing. |

These are separate components with different spatial roles. Applying one
non-uniform stretch to every effect would distort clouds, snowflakes and leaves.
The clearest priorities are the thunder wash and frost border, followed by
snow/wind trajectories and cloud placement. The glare edge needs its own check.

## Measured examples

The flash node is `White Solid 1`, whose original quad is 600×1024 with
source-frame overscan. Its reconstructed current bounds are:

| Surface | Flash mesh rectangle |
| --- | --- |
| 540×960 | (−30, −32) to (570, 992) |
| 1080×2400 | (−60, 176) to (1140, 2224) |
| 2400×1080 | (862.5, −36) to (1537.5, 1116) |

The tall case leaves 176px unflashed above and below the rectangle; the
landscape case leaves 862.5px on each side. The source opacity peaks around
0.76 at integer frame 121, making these boundaries conspicuous. The original
portrait overscan is sufficient for the original screen, so this is an
aspect-ratio adaptation problem.

Actual sampled ice/snow pixels at 270×600 extend from roughly Y=24 to Y=541
over the sampled frames. Wind pixels extend from roughly Y=69 to Y=466,
leaving the entire bottom fifth without sampled content. Landscape ice/snow
pixels are concentrated around X=222–397 of a 600px-wide viewport. These are
unions across samples, not a guarantee of continuous coverage within them.

Sun glare's visible hard boundary appears in both source-aspect and tall
previews. The original glare and wiper nodes both serialize BlendMode=2,
so the name of the asset alone does not justify treating that enum as additive
blending. The audit has not established whether the remaining glare edge is
caused by material parameters, texture alpha, projection or expected source
artwork. It should not be attributed solely to the screen's aspect ratio.

The supplied wallpaper APK contains only `assets/Port/weather_overlay.m10`;
there is no landscape overlay scene in this APK to substitute directly.

## Reproduction and limits

Build the shared test APK against the installed shared launcher:

```sh
source tools/android-env.sh
python3 tools/build_shared_launcher_tests.py
adb -s emulator-5554 install -r build/outputs/shared-debug/launcher-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e suite weather_coverage \
  dev.sense.launcher.test/dev.sense.launcher.WeatherSceneInstrumentation
```

The test writes `files/weather-coverage-audit.json`,
`files/weather-coverage-overview.png`, and `files/weather-coverage-NN.png`
inside the launcher's private data directory. Retrieve them with `adb exec-out
run-as dev.sense.launcher ...`. With the JSON at the default local path, run:

```sh
python3 tools/audit_other_weather_effects.py
```

This produces `references/3.33.401.153/other-weather-coverage-audit.json`.
The successful emulator log is `.toolchain/weather-coverage-audit-tests.log`.
Coverage metrics sample pixels every 3px using alpha >12/255. Peak fractions
are computed separately per frame and per screen region. Intentionally sparse
effects do not need opaque pixels throughout the screen. These held samples
do not establish full-motion quality, physical 120Hz performance, or exact
parity with the original HTC renderer.
