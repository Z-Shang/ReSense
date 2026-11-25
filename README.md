# Sense Launcher

An independent project that reverse engineers HTC software and uses original HTC assets with modern Android code to recreate an experience similar to HTC Sense 3.x on Android 15 and later.

**This is not an official HTC project. It is not affiliated with, endorsed by, or supported by HTC.**

## Current implementation

- Android HOME activity and default-home role request.
- Native Sense-inspired split-panel clock with live time/date, Phone shortcut, Settings shortcut.
- Four-column app drawer with installed launchable apps and app launch handling.
- System bar and display cutout insets; system Back returns to home.
- Configurable navigation-bar hiding in Sense settings → Desktop. Hidden navigation lets the dock sit flush with the bottom; an edge swipe temporarily reveals controls while the status bar stays visible.
- No third-party runtime dependencies. The build reads the extracted HTC resources in `assets/htc/`.
- Compact 64dp dock with curved tops, straight lower edges, reversed shading, and unwarped labels/icons.
- Borderless page track above the arc, with a centered single-page thumb and supersampled curved edges.

## Build and run

The local toolchain is installed in ignored `.toolchain/`: **JDK 17**, Android SDK **35**, Build Tools **35.0.0**, and Gradle **8.11.1**. The project pins Android Gradle Plugin **8.9.1** and includes a checksum-pinned Gradle wrapper. The build uses the checked-in HTC resources in `assets/htc/` and Python 3 to verify their hashes. The stock ROM is needed only to regenerate the assets, not to compile the apps. From the project root:

```sh
source tools/android-env.sh
./gradlew :launcher:assembleDebug
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
adb shell am start -n dev.sense.launcher/.MainActivity
```

See [Android setup](docs/ANDROID-SETUP.md) for installed versions and setup on another machine. The APK has also been installed and smoke-tested on the project-local Android 15 emulator. See [Emulator setup and commands](docs/EMULATOR.md).

GitHub Actions can build downloadable shared/standalone debug APK sets and the
framework AAR directly from the extracted assets. Use the **Build Android
artifacts** workflow; no ROM download secret is required. See [CI setup and artifact downloads](docs/CI-ARTIFACTS.md).

Tap “Set as home app” or choose Sense Launcher in Android Settings → Default apps → Home app. To switch back, select your previous launcher there.

## Sense Weather companion

The separate Android 15+ `:weather` app adapts the recovered HTC Weather layouts,
header/tab artwork and condition scenes to native views and Open-Meteo data.
It includes city search, current conditions, hourly and five-day forecasts, and
last-good cached weather. Tapping the launcher clock/weather widget opens it
with the widget's selected city when installed. See [weather app details](docs/WEATHER-APP.md).

```sh
source tools/android-env.sh
./gradlew :weather:assembleDebug
adb install -r weather/build/outputs/apk/debug/weather-debug.apk
adb shell am start -n dev.sense.weather/.WeatherActivity
```

## Full-screen home weather

The launcher can play the recovered HTC full-screen overlay after unlocking or
entering home. Sense Settings → Weather contains both switches and a preview
picker for sunlight, clouds, rain/wiper, ice/snow, thunder and wind. Playback uses
original tracks with display-paced interpolation, proportional scaling and touch
dismissal. See [implementation and fidelity limits](docs/FULLSCREEN-WEATHER-RESEARCH.md).

## ROM input

`res/3.33.401.153.zip` is the optional source for asset regeneration and research; normal builds use `assets/htc/`. It contains the Pyramid Android 4.0.3 system files, including Rosie and HTC clock/weather resources. See [the ROM audit](docs/ROM-AUDIT.md) for findings and the reproducible extraction command. Its literal Sense property is `3.5`; the visible release still needs verification.

`res/pyramid-ics-crc-3.0.16-31a4fc7.zip` is a separate kernel source release. See [the implementation roadmap](docs/PORTING.md).

## Mode10 references

Run `python3 tools/inspect_mode10.py` after the ROM inventory to extract scene references, widget previews and a string-candidate report. See [Mode10 findings and visual implementation](docs/MODE10.md) for scope and limitations. The clock face remains a native approximation. The dock uses a deformation mesh based on the recovered curve; original animations are not implemented.

`python3 tools/decode_navbar.py` now recovers the navbar geometry and default texture directly from the ROM. The decoded background is a flat 540 × 90 sprite; see [the geometry and texture findings](docs/NAVBAR-DECODE.md), including exact Android-native ETC1 comparison results. A broader [dock layer audit](docs/DOCK-LAYERS.md) subsequently found surviving curved artwork and the older three-button layout, with missing/default panel resources.

## Validation status

Project XML and project structure were checked locally. Mode10 inspection and 222 extracted scene/preview hashes were validated. The Gradle wrapper successfully ran `:launcher:assembleDebug :launcher:lintDebug` with JDK 17 and SDK 35. APK signature verification passed. Lint reports zero errors and four warnings (backup configuration, two draw-allocation findings, and missing app icon). An Android 15 Pixel 7 emulator passed home rendering, app drawer, app launch, Home/Back and crash-log checks. Physical-device tests have not run. Android 16+ compatibility is intended but untested. The prototype currently lists apps only in the current user profile; work profiles, Private Space, archived apps, widget hosting, pinned shortcuts and persisted home layouts are not implemented.

## Dock rendering

`BentDockLayout` bends the cleaned background and rounded button surfaces toward the recovered arc, with their lower edges held straight. Shading runs from light at the top to dark at the bottom. `DockButton` draws foreground labels and compound icons afterward without deformation. The dock body is 64dp tall, spans the screen width, and continues beneath system navigation.

Gradle reads the prepared resources directly from `assets/htc/launcher/res`. Regenerate the bundle explicitly with `./gradlew extractHtcAssets`; see [asset layout and verification](assets/README.md). See [rendering and validation](docs/DOCK-RENDERING.md).

The native Sense Phone companion can be built with `./gradlew :phone:assembleDebug`.
It reconstructs the ROM keypad, provides contact search and HTC-style system call
history, and can be selected as the default Phone app with native incoming/ongoing
call controls. See [Phone app](docs/PHONE-APP.md) for extraction and scope.

The standalone **Sense Contacts** companion reconstructs HTC People with original
ROM assets, system contacts, Favorites, Groups and contact details. Build with
`./gradlew :contacts:assembleDebug`. [Contacts app details](docs/CONTACTS-APP.md).

All four apps share native UI controls, HTC artwork and base themes through the
[`sense-framework` Android library](docs/SENSE-FRAMEWORK.md). Build its AAR with
`./gradlew :sense-framework:assembleDebug`; app builds include it automatically.
For an **install-once shared framework APK** and six dependent apps, run
`./gradlew assembleSharedDebug`. The APK set is in `build/outputs/shared-debug/`;
install the framework and `sense-services-debug.apk` first, or use `bash tools/install-shared-debug.sh DEVICE_SERIAL`.
See [shared framework installation and verification](docs/SENSE-FRAMEWORK.md#install-once-framework-apk).

Choose **⋮ → Make default Phone** inside Sense Phone to open Android’s default-app
picker. [Default Phone implementation and testing](docs/DEFAULT-PHONE.md).

The native Sense Messages companion recreates HTC SMS styling from `Mms.apk`,
with system conversations, text sending/receiving and Android default-SMS setup.
Build with `./gradlew :messages:assembleDebug`. SMS only; MMS and RCS are not
implemented. [Messages app details](docs/MESSAGES-APP.md).

## Sense ring lock screen

`:lockscreen` recreates the HTC ring, four configurable shortcuts and clock using
recovered ROM assets. Open it through **Sense settings → HTC lock screen**, its
app, or its Quick Settings tile. It uses Android's secure unlock flow and does
not automatically replace the system keyguard. See [lock-screen setup and limitations](docs/LOCKSCREEN.md).

```sh
./gradlew :lockscreen:assembleDebug
adb install -r lockscreen/build/outputs/apk/debug/lockscreen-debug.apk
```
