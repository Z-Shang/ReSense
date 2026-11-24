# Project-local Android toolchain

The SDK and build tools are installed under ignored `.toolchain/`. Load them from the project root in each terminal:

```sh
source tools/android-env.sh
java -version
adb version
./gradlew :launcher:assembleDebug
```

The environment script selects JDK 17, the local Android SDK and project-local Gradle/Android user directories. `local.properties` points Android Gradle Plugin at the installed SDK. Neither file changes your global shell configuration. Android Studio can use the same SDK directory and `.toolchain/jdk17` as its Gradle JDK.

## Installed versions

| Component | Version / location |
| --- | --- |
| Temurin JDK | 17.0.21+9, `.toolchain/jdk17` |
| Google command-line tools | 22.0, `.toolchain/android-sdk/cmdline-tools/latest` |
| Android platform | API 35, revision 2 |
| Android build tools | 35.0.0 |
| Android platform tools | 37.0.1 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.1, pinned in root build file |

Google command-line tools, JDK and Gradle archives were checked against their official SHA-256 values before extraction. Download metadata is recorded locally in `.toolchain/downloads/manifest.json`. The Gradle wrapper pins the Gradle distribution checksum.

The command-line tools report `sdkmanager` as deprecated in favor of their included `android sdk` CLI. The compatibility command worked for this installation. Installed packages can be inspected with:

```sh
source tools/android-env.sh
sdkmanager --list_installed
```

## Recreate on another machine

Install the [Google command-line tools](https://developer.android.com/studio#command-line-tools-only), a JDK 17 distribution and the required packages:

```sh
sdkmanager --sdk_root=/your/sdk/path 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
```

Review and accept the required SDK license prompts. Set `JAVA_HOME` to JDK 17, add the SDK tools to PATH, and put `sdk.dir=/your/sdk/path` in ignored `local.properties`. The checked-in Gradle wrapper downloads Gradle 8.11.1; no system Gradle is needed. The project environment script is optional and assumes the local `.toolchain/` layout above.

[AGP 8.9 compatibility requirements](https://developer.android.com/build/releases/agp-8-9-0-release-notes) specify Gradle 8.11.1 and JDK 17. An emulator and Android 15 system image were subsequently installed; see [Emulator setup](EMULATOR.md).

## Setup verification

Verified `java -version`, `sdkmanager --list_installed`, `adb version`, wrapper generation and `./gradlew --no-daemon :launcher:assembleDebug :launcher:lintDebug`. The APK is at `launcher/build/outputs/apk/debug/launcher-debug.apk`; `apksigner verify --verbose` passed with APK Signature Scheme v2. Android lint completed with zero errors and four existing prototype warnings: backup extraction rules, two drawing allocations and the missing application icon. Report: `launcher/build/reports/lint-results-debug.html`.

AGP emits an SDK XML version warning because its embedded SDK reader predates the latest command-line tools. Package installation and the complete build succeeded despite this warning. No application behavior was changed during toolchain setup, and subsequent emulator installation and smoke checks are documented in [EMULATOR.md](EMULATOR.md).
