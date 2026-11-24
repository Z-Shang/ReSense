# ROM 3.33.401.153 audit

Source: `res/3.33.401.153.zip` (1,484 ZIP entries).

SHA-256: `5bc5a4424ce09730a83232835f910c90a7a288976455ce3eaff69f1acf8cf141`.

Build properties identify Android 4.0.3 / API 15, device `pyramid`, build `IML74K`, product version `3.33.401.153`, and fingerprint `htc_europe/htc_pyramid/pyramid:4.0.3/IML74K/391535.153:user/release-keys`. The file contains duplicate model properties: an earlier Sensation XE name and a later HTC Pyramid name.

`ro.build.sense.version=3.5` is the literal property. This audit does not treat that property alone as proof of the user-visible Sense release. Verify against the ROM’s software-information screen and launcher screenshots before labeling visual fidelity as Sense 3.6.

## Reproduce

```sh
python3 tools/inventory_rom.py res/3.33.401.153.zip
```

This extracts 48 selected files to ignored `references/3.33.401.153/`: launcher, clock/weather packages, two resource frameworks, Rosie framework JAR, build properties, fonts and six numbered HTC wallpapers. Original archives remain unchanged. No extracted assets are included in the launcher build.

`inventory.json` records ROM and individual extracted-file SHA-256 hashes, original paths, package resource paths, sizes and ZIP CRCs. APK XML and resource tables are still in Android compiled format; they have not been decoded.

| Package | Resource/asset entries | M10 scenes |
| --- | ---: | ---: |
| `system/framework/com.htc.android.rosie.jar` | 0 | 0 |
| `system/app/HtcClock3DWidget.apk` | 86 | 59 |
| `system/framework/com.htc.resources.apk` | 2780 | 0 |
| `system/app/HtcWeather3DWidget.apk` | 141 | 83 |
| `system/app/Rosie.apk` | 338 | 57 |
| `system/app/Weather.apk` | 119 | 0 |
| `system/framework/framework-res.apk` | 2451 | 0 |
| `system/app/WorldClock.apk` | 98 | 0 |

## Findings and next work

Rosie and HtcClock3DWidget contain `classes.dex`. Rosie includes `Rosie_navbar.m10`, `Rosie_screen.m10`, `Rosie_page_slide_main.m10`, `Rosie_leap.m10` and `Rosie_leap_rearrange.m10` under `assets/Port/scenes/`. Clock assets include `clock_weather.m10`, `clock_digital_2x1.m10` and widget preview images. These scenes require inspection/decoding or visual reconstruction; ordinary PNG extraction alone will not recover the full UI.

Next: decode Android resources with the supplied HTC resource frameworks, inspect clock previews and wallpapers, and determine which M10 geometry/textures can be recovered. Rebuild these interactions using modern Android views rather than loading HTC framework binaries. Subsequent scene inspection and the first native clock/dock approximation are documented in [MODE10.md](MODE10.md).

Validation: re-read all 48 extracted files and matched their SHA-256 hashes against the generated inventory. Android build/device validation remains pending toolchain setup.
