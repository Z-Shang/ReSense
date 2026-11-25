# Build and install

Run the commands in this guide from the repository root. For app behavior and
configuration, use the [documentation index](README.md).

## Requirements

| Component | Requirement |
| --- | --- |
| Java | JDK 17 |
| Android SDK | Platform 35 and Build Tools 35.0.0 |
| Gradle | Checked-in wrapper, pinned to 8.11.1 |
| Android Gradle Plugin | 8.9.1, pinned in the root build file |
| Python | Python 3, used for resource verification and shared packaging |
| Device or emulator | Android 15 / API 35 or later; documented emulator checks use API 35 |

Follow [Android setup](ANDROID-SETUP.md) to configure another machine. If the
project-local toolchain is already installed under ignored `.toolchain/`, load it
in each terminal:

```sh
source tools/android-env.sh
```

That script selects the local SDK/JDK and sets `ANDROID_HOME`, `JAVA_HOME`,
`ANDROID_USER_HOME` and `GRADLE_USER_HOME`. Shared packaging needs all four; when
using a different toolchain, set them to the corresponding SDK, JDK and writable
Android/Gradle user directories.

## Single-file suite installer

Build an installer containing the shared framework, weather-data service and six
apps:

```sh
./gradlew assembleBundleDebug
adb install -r build/outputs/bundle-debug/sense-suite-debug.apk
```

Open **Sense Installer** on the device and continue through Android's installation
approvals. Framework and services are installed before their dependent apps.
Installing the outer APK alone updates only Sense Installer; open it to apply
updates to the bundled apps.

See [installer behavior, signing and CI downloads](CI-ARTIFACTS.md#single-file-installer).

## Standalone app APKs

A normal app build includes its UI framework. Build only the apps you want:

| App | Module | Build command |
| --- | --- | --- |
| Launcher | `launcher` | `./gradlew :launcher:assembleDebug` |
| Weather | `weather` | `./gradlew :weather:assembleDebug` |
| Phone | `phone` | `./gradlew :phone:assembleDebug` |
| People | `contacts` | `./gradlew :contacts:assembleDebug` |
| Messages | `messages` | `./gradlew :messages:assembleDebug` |
| Lock screen | `lockscreen` | `./gradlew :lockscreen:assembleDebug` |

Each APK is written to `<module>/build/outputs/apk/debug/<module>-debug.apk`.
For example, build, install and open the launcher with:

```sh
./gradlew :launcher:assembleDebug
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
adb shell am start -n dev.sense.launcher/.MainActivity
```

For Weather:

```sh
./gradlew :weather:assembleDebug
adb install -r weather/build/outputs/apk/debug/weather-debug.apk
adb shell am start -n dev.sense.weather/.WeatherActivity
```

For the lock screen:

```sh
./gradlew :lockscreen:assembleDebug
adb install -r lockscreen/build/outputs/apk/debug/lockscreen-debug.apk
```

Install `sense-services` as well when using shared weather data between apps:

```sh
./gradlew :sense-services:assembleDebug
adb install -r sense-services/build/outputs/apk/debug/sense-services-debug.apk
```

## Separate shared APKs and framework AAR

To build the shared distribution without the outer installer:

```sh
./gradlew assembleSharedDebug
bash tools/install-shared-debug.sh DEVICE_SERIAL
```

The eight APKs are in `build/outputs/shared-debug/`. If installing manually,
install `sense-framework-debug.apk` first, then `sense-services-debug.apk`, then
the six client APKs. Use APKs from the same build and distribution. The ordinary
standalone and shared variants use the same app IDs, so avoid mixing them.

For a project dependency rather than an installable APK:

```sh
./gradlew :sense-framework:assembleDebug
```

The AAR is written to
`sense-framework/build/outputs/aar/sense-framework-debug.aar`.
See [framework packaging and verification](SENSE-FRAMEWORK.md).

## Resources and ROM regeneration

Normal builds read checked-in resources from `assets/htc/<module>/res/` and verify
their inventory against `assets/htc/manifest.json`. They do not extract the ROM.

The optional `res/3.33.401.153.zip` is the original Pyramid system-software input
for research and regeneration. `res/pyramid-ics-crc-3.0.16-31a4fc7.zip` is a separate
kernel source release; it does not contain the Sense app resources.
See the [ROM audit](ROM-AUDIT.md) for source details and version-label caveats.

To explicitly regenerate the prepared resources from the local stock ROM:

```sh
./gradlew extractHtcAssets
```

Review and commit the regenerated resources and manifest together. See
[asset layout and integrity checks](../assets/README.md).

## Running and checking builds

- [Emulator setup and commands](EMULATOR.md).
- [Recorded validation and current scope](PROJECT-STATUS.md).
- [CI artifacts and signing across builds](CI-ARTIFACTS.md).
- [App-specific guides](README.md#apps-and-shared-components) for default-app roles,
  permissions, supported features and focused tests.
