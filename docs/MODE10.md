# Mode10 inspection and first visual reconstruction

## Reproduce inspection

After running the ROM inventory:

```sh
python3 tools/inspect_mode10.py
```

Output stays under ignored `references/3.33.401.153/mode10/`. This includes the original scene bytes, widget preview PNG/JPEGs and `scenes.json` with source paths, hashes, string candidate offsets and header observations.

| Package | Scenes | Widget previews |
| --- | ---: | ---: |
| Rosie | 57 | 0 |
| HtcClock3DWidget | 59 | 19 |
| HtcWeather3DWidget | 83 | 4 |

All 199 inspected scenes pass the observed `!01m` signature check. The next little-endian header word is recorded as an observation, not assigned a format meaning. Printable UTF-16LE runs expose names but can include adjacent binary bytes or truncated strings. That initial tool is an inspection tool, not a decoder. A subsequent structural decoder now recovers navbar node relationships, geometry, sampled values and the default background texture; see [NAVBAR-DECODE.md](NAVBAR-DECODE.md). Full timeline evaluation remains unimplemented.

Examples from the actual ROM:

- Navbar: `timeline.NavBar_phone_btn`, `Container.navbar_left`, `drop.navbar_left.hitbox`.
- Clock/weather: `button.clock.hitbox`, `button.weather.hitbox`, `timeline.flip_minute`, `timeline.tile_flip_ani`.
- Leap: `Leap_view_Main` and numbered IN/OUT names.

These are evidence of named controls/tracks, not sufficient specifications of their behavior.

## Native visual implementation

Visually inspected `HtcClock3DWidget.apk!/res/drawable-hdpi/preview_clock_weather.png`: silver twin clock panels, black condensed digits, a horizontal split and dark lower weather panel. `SenseClockView.java` reconstructs these broad features with Android Canvas gradients and live local time/date. It respects 12/24-hour preference and supplies a spoken time/date description. The update callback is removed on detach.

The clock face is a first approximation: font metrics, exact proportions and original flip animation are not recovered. The weather area explicitly states that weather is unconfigured. No sample location, temperature or condition from the preview is presented as real data. The launcher dock now has a rounded shaded background and translucent native press feedback; its geometry and controls remain provisional rather than a decoded copy of the navbar scene.

The dock build now generates and bundles two local ROM-derived PNG resources for the selected mask composition. No HTC font or runtime is bundled. Other reference files remain local. Navbar geometry and its default texture have now been recovered separately; other resource formats and full animation reconstruction remain pending.

## Validation

Ran scene inspection on all three APKs, checked all 222 extracted scene/preview SHA-256 hashes, checked invalid/truncated header rejection and checked string candidate text/offset recovery using a synthetic input. The subsequent SDK setup successfully compiled and packaged the Java implementation and ran Android lint (zero errors, four warnings). Subsequent Android 15 emulator smoke checks passed; physical-device testing has not run. Visual fidelity must be checked on a device before calling this a faithful recreation.

Historical context: the original M10 tool developers describe separate scene and image decoding work in their [2011 development post](https://www.virtuousrom.com/2011/12/m10-tools-is-going-open-source-help.html). That post does not supply a decoder specification; this implementation does not assume it does.
