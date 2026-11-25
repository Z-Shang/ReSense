# Project status and implementation notes

This is an independent reconstruction of the HTC Sense 3.x experience using
recovered resources and modern Android implementations. It includes native apps
and reconstructed rendering components; it does not install the original Rosie
launcher or the old HTC Android framework as a modern system replacement.

The target is Android 15 and later. Visual comparisons and focused emulator tests
are used during development, but fidelity and platform coverage remain incomplete.

## Implemented areas

- Launcher Home activity, default-home selection, app discovery and launching.
- Persisted home pages, shortcut/widget placement, movement, resizing and page overview.
- Clock/weather presentation, app drawer, curved dock and page indicator.
- System bar and cutout insets, including configurable navigation-bar hiding.
- Weather, Phone, People, Messages and ring-lock-screen companion apps.
- Shared Sense UI controls, recovered artwork and shared weather-data services.
- Shared-framework APKs, self-contained app APKs and a suite installer.

Use the [app guides](README.md#apps-and-shared-components) for the supported
behavior of each component. SMS and single-recipient carrier MMS are implemented;
[RCS and group MMS composition remain outside Messages' current scope](MESSAGES-APP.md#scope).
App discovery and work-profile/Private Space behavior need separate validation;
widget-provider profile handling is not proof of complete launcher profile support.

## Rendering and reverse engineering

The dock uses a deformation mesh based on the recovered curve. `BentDockLayout`
bends its background and rounded button surfaces while keeping the lower edges
straight. `DockButton` draws labels and icons afterward without deformation.
The dock's original implementation notes describe a 64dp body spanning the screen
width, light-to-dark shading and a borderless page track with a supersampled thumb.
[Dock rendering](DOCK-RENDERING.md) and [dock layer research](DOCK-LAYERS.md)
record the geometry, artwork and visual constraints in more detail.

The raw Mode10 background quad is a flat 540 × 90 sprite; recovering it alone does
not recover the complete curved dock. See [navbar decoding](NAVBAR-DECODE.md).
Clock rendering combines recovered evidence with native implementation; see
[clock flip](CLOCK-FLIP.md) and [typography/framing audit](WEATHER-FRAMING-TYPOGRAPHY-AUDIT.md).

The full-screen home-weather overlay uses recovered tracks and display-paced
interpolation. Its settings expose unlock/home-entry switches and previews for
sunlight, clouds, rain/wiper, ice/snow, thunder and wind. See
[full-screen weather research](FULLSCREEN-WEATHER-RESEARCH.md),
[playback triggers](WEATHER-PLAYBACK-TRIGGERS.md) and
[runtime mapping recovery](OVERLAY-RUNTIME-RECOVERY.md) for implementation and
fidelity limits. Research findings do not by themselves imply that every original
Rosie/Fusion behavior is implemented.

## Source inspection

Original archives remain local research inputs. The system ROM identifies Android
4.0.3 and Pyramid build `3.33.401.153`; its literal Sense property is `3.5`.
That property alone does not establish the user-visible Sense release. The
project's broader 3.x recreation goal should be distinguished from claims of
exact release fidelity. See [the ROM audit](ROM-AUDIT.md).

From the repository root, the existing inspection tools can inventory the ROM,
collect Mode10 references and decode dock geometry:

```sh
python3 tools/inventory_rom.py res/3.33.401.153.zip
python3 tools/inspect_mode10.py
python3 tools/decode_navbar.py
```

Their findings are documented in [Mode10 research](MODE10.md),
[navbar decoding](NAVBAR-DECODE.md) and the [original reconstruction roadmap](PORTING.md).
Build-time asset preparation is covered in [the build guide](BUILDING.md#resources-and-rom-regeneration).

## Validation record and limits

Existing project notes record Android 15 emulator checks for home rendering,
app drawer behavior, app launching, Home/Back, crash logs and focused app/widget
behavior. APK signature verification and asset-integrity checks are part of the
build workflow. See [emulator notes](EMULATOR.md),
[CI verification](CI-ARTIFACTS.md) and the app-specific guides for concrete checks.

Early launcher validation recorded:

- Project XML/structure checks and 222 extracted scene/preview hashes.
- `:launcher:assembleDebug` and `:launcher:lintDebug` with JDK 17 and SDK 35.
- APK signature verification and Android 15 Pixel 7 emulator smoke checks.
- Zero lint errors and four prototype warnings: backup configuration, two drawing
  allocations and the then-missing application icon.

These are historical results, not a current lint-warning count or a claim that
all present features were covered by that early test run. Emulator results do
not establish compatibility with every physical device or later Android release.
Use [the roadmap](PORTING.md) as research context alongside the implementation
and current app guides, rather than as a completed-feature checklist.
