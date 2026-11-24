# Full-screen rain and widget typography audit

Investigation only: production renderer, generated app assets, settings and
installed APKs were not changed. Reproduce the evidence with:

```sh
python3 tools/audit_weather_framing_typography.py
```

The output is
`references/3.33.401.153/weather-framing-typography-audit.json`.
The geometry calculation reproduces the current Java transform, interpolation
and adapted camera math. It is not a recording of HTC's ARM runtime or a
measurement of visible texture pixels.

## Full-screen rain/wiper

`HtcWeatherWallpaper.apk!/assets/Port/weather_overlay.m10` has a 540×960 root;
its `03_Rain` group has 600×1024 dimensions. The exported effect preserves
those original transforms. The rain group's position/pivot introduce a local
−30, −32 displacement under our current transform interpretation.

`FullscreenWeather.Overlay.sceneBounds` uses a fixed aspect-preserving contain
fit, `min(width/540, height/960)`. At 1080×2400 this gives a 1080×1920 scene
centered between y=240 and y=2160. Both `Overlay` and `HtcWeatherScene.draw`
clip against the full overlay view. There is no half-height clip rectangle.

There are two additional constraints beyond the viewport fit:

- `b_raindrop_streaks.png` uses a 244×320 quad scaled about 2.463×2.459.
  With our current camera/transform math, its lower boundary is y≈753.4 in
  the 540×960 reference viewport. It already stops before the reference
  viewport's bottom; this is a mesh/material extent, not view clipping.
- The renderer adds a non-stock alpha fade over the last 15% of this texture
  to conceal a sharp streak boundary. The fade begins at reference y≈635.4.
- The 88×753 wiper quad pivots near the top of the reference scene. Across
  frames 45–104, its maximum projected bottom is y≈684.2 under our current
  projection. Much of its sweep intentionally leaves the viewport sideways.

| Current viewport | Fitted scene vertical range | Streak mesh bottom | Fade starts | Maximum wiper bottom |
| --- | --- | --- | --- | --- |
| 540×960 | 0–960 | 753.4 | 635.4 | 684.2 |
| 1080×2400 | 240–2160 | 1746.9 | 1510.8 | 1608.4 |
| 1440×3200 | 320–2880 | 2329.2 | 2014.4 | 2144.5 |

Streak figures use frame 75; they describe projected quad bounds, including
transparent texture pixels. Thus the apparent coverage can be smaller. On the
1080×2400 case, the fade starts around 63% of screen height and the wiper's
maximum reach is around 67%. This supports the reported upper-screen effect;
it does not establish a literal cutoff at exactly 50%.

Simply changing contain to cover would not resolve everything. It would crop
the sides while the source streak/wiper extents would still stop early under
this camera. A proper fix needs viewport-aware screen-space rain/wiper framing
or a recovered effective stock camera/viewport mapping, rather than globally
stretching every weather scene or merely adjusting the clip rectangle.

The stock service's `SurfaceHolder.Callback.surfaceChanged` passes the actual
surface width/height to `FxViewObject.setSurface`. That proves the native runtime
received the real viewport dimensions, but not how its camera adapted them.
The current renderer retains the previously documented unverified camera and
source-over blend adaptations. This investigation does not establish that HTC
itself rendered the same source quads at these screen positions.

## Standalone Weather widgets: 2×1, 2×2, 4×4

The packaged `DroidSans.ttf` and `HelveticaNeueLTPro35Th.ttf` are byte-identical
to the ROM. The nominal sizes selected from FontID match the recovered font
descriptor heights for these labels. The large temperature has separate 200px
and 140px thin-Helvetica variants; both specify a 260px line height. Font size
and line height are separate properties.

Native strings in `libmode10.so` confirm the previously hashed property names:

| Property | Hash |
| --- | --- |
| FontID | `0x333f0989` |
| LineHeight | `0x42312f46` |
| HorzAlignment | `0x13d62c47` |
| VertAlignment | `0x6947a4c5` |

The generator's old raw scalar fallback for the field `0x42312f46` must not be
described as an explicit font size: it is LineHeight. Current visible labels
normally take their nominal font size from FontID instead, so this naming
discovery does not mean all existing font sizes are wrong.

Confirmed gaps in `SenseWeatherView` / `WeatherWidgetAssets`:

- Horizontal placement is selected by node-name heuristics, not HorzAlignment.
  The visible temperature/city/condition/high-low choices generally agree with
  the corresponding left/center/right groupings, but this is not a general
  property-driven text implementation.
- VertAlignment, LineHeight and LineHeightMode are not used. Every string is
  drawn at local baseline y=0, so mixed vertical/line-box settings cannot be
  reproduced exactly. The ROM uses several vertical codes, including distinct
  settings on forecast day and high/low labels. Native text-layout enum/baseline
  semantics still need verification before replacing this with guessed offsets.
- `PageNumber` requests **Droid Sans Mono 18**, while the current font selector
  uses ordinary Droid Sans for every non-Helvetica label.
- All small-widget text controls and most large-widget controls enable ROM
  shadows. The standalone widget does not bind their shadow parameters.
- ROM maximum-line-count/ellipsis behavior is not reproduced generally;
  the adapter uses its own single-line width truncation.

The exact original font/alignment codes and sizes for every label, including
the second city-transition composition, are recorded in the JSON report.

## Flip-clock/weather widget

The flip digits themselves use recovered numeral textures. Its lower weather
text is a separate implementation: `SenseClockView.text` and `fittedText` use
Android `sans-serif-condensed`, rather than the original Droid Sans / Droid Sans
Mono fonts, with manually selected positions and baseline sizes.

For a width-normalized comparison from the 530px Mode10 scene to the current
374-unit canvas, multiply original font heights by 374/530:

| Label | Original font height | Width-normalized height | Current height |
| --- | --- | --- | --- |
| City | 26 | 18.35 | 20 |
| Condition | 22 | 15.52 | 15 |
| Date | 22 | 15.52 | 18 |
| Current temperature | 50 | 35.28 | 36 |
| High/low | 20 | 14.11 | 14 |

This normalization compares nominal sizes, not measured glyph ink or a proven
stock baseline transform. The font-family mismatch and manual text layout are
confirmed; exact position/baseline correction needs the original text layout
rules. The ROM also uses separate monospaced H/L labels; our widget draws
combined H:/L: strings and adds a modern attribution line.
