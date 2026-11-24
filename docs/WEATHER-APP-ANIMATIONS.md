# Original Weather app animations

Recovered from the local `res/3.33.401.153.zip`, without changing the apps.

## Full-scene footage

The ROM has **18 MP4s** in `system/media/weather/`, totaling about 31 MiB.
All contain **960 × 960 H.264 video at 30 fps**. The square frame supplies
portrait/landscape crops; it is not a 540 × 960 recording of the phone UI.

| Files, omitting `weather_` and `.mp4` | Duration per file |
| --- | --- |
| sunny, clear, hot, partly_cloud, partly_cloud_night, partly_sunny | 8.033 s |
| cloudy_day, cloudy_night, fog_day, fog_night | 8.033 s |
| rain, rain_night, thunderstorm_day, thunderstorm_night | 30 s |
| snow_day, snow_night | 19.967 s |
| windy_day / windy_night | 7.533 / 8.033 s |

The cloud layers and sun rays in `weather_partly_sunny.mp4` visually match the
large scene in `res/weather_now.png`. This is different artwork from the compact
clock-widget animations currently used by `WeatherSceneView`.

## Stock loading path

Static DEX inspection, rather than an observed execution on HTC hardware:

1. `Weather.apk`: `com.htc.Weather.WeatherVideo` declares
   `/system/media/weather/`, all 18 filenames, and a 44-entry HTC icon-to-video
   table. `getVideoFilePathByIcon(String, boolean)` adds day/night substitutions
   for cloud, thunderstorm, fog, rain, snow and wind families.
2. `WeatherAnimFactory` creates `ImplFusionVideoView`. Its `changeCondition`
   and `play` methods call `WeatherVideoView.setVideoPath(...)` and `play()`.
   `changeConfig` changes the display size for portrait/landscape and full-screen
   modes.
3. `WeatherVideo.apk`: `FxWeatherVideoScene` loads
   `assets/Port/Weather_wallpaper.m10`. It combines a dynamic still image and
   `FxVideoTexture`, and uses Android `MediaPlayer` to load the video path.
   The scene references `Mask`, `timeline.weather_still`, `dynamicImg`,
   `timeline.weather_A` and `Weather_A`.

The original Mode10 file contains named `Mask`, `Scale`, still-image and video
nodes, including 960 × 960 planes and animated properties. The weather footage
is already rendered into the MP4s; those clouds do not need to be reconstructed
from the widget's Mode10 meshes. The exact crop, fade and repeat policy still
needs to be carried over from the wrapper when integrating playback.

`WeatherVideo.apk` also contains **18 JPEG stills**, one for each weather family,
usable as loading/fallback artwork. The MP4s are in the ROM media directory,
not inside that APK. Its `Weather_wallpaper.m10` is the nineteenth asset from
that APK, for a total of **37 byte-exact recovered assets**.

## Recovered files

Local output: `references/3.33.401.153/weather-app-animation/`

- `videos/`: the original 18 MP4s.
- `WeatherVideo/res/drawable/`: original 18 JPEG stills.
- `WeatherVideo/assets/Port/Weather_wallpaper.m10`: original Fusion scene.
- `previews/` and `contact-sheet.jpg`: frames sampled at two seconds.
- `provenance.json`: original ROM/APK paths, SHA-256 hashes and ffprobe results.
- `runtime-trace/`: selected disassembled app/player classes used as evidence.
- `htc-icon-video-base-map.json`: the recovered base mapping for HTC IDs 1–44;
  these are **not WMO codes**, and the separate day/night substitutions apply.
- `fusion-scene-nodes.json`: partial named-node/property inventory. Animated
  binary properties are retained as such, not misinterpreted as static vectors.

Re-extract assets and generate previews:

```sh
python3 tools/extract_weather_app_animation.py --previews
```

Asset extraction uses Python's standard library. Media inspection requires
`ffprobe`; previews additionally require `ffmpeg` and Pillow. Trace disassembly
was performed with the local SDK's baksmali; Android DEX 036 headers were
normalized to 035 in temporary copies, with recomputed signatures/checksums.
Original ROM bytes were not modified. All 37 recovered assets were verified
against their original archive entries and hashes.

The native Weather app now uses this footage and stills in its Now view, together
with Today/Tonight/Tomorrow cards and text-only HTC segmented tabs. It uses a
modern center-cropped TextureView/MediaPlayer rather than executing the original
Fusion wrapper. Exact original wrapper transitions are not reproduced. Video
frames remain 30 fps even on a faster display.
