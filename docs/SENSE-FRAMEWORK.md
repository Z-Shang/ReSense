# Shared Sense UI framework

`:sense-framework` supplies the common controls, themes and HTC assets for
`:launcher`, `:weather`, `:phone`, `:contacts`, `:messages` and `:lockscreen`.
Its Java namespace is `dev.sense.framework`. There are two distributions:
normal AGP builds bundle the library into each app; `assembleSharedDebug` builds
an install-once framework APK, a framework-services runtime, and six client apps.
Both use Android SDK 35 and Java 17 without root, system-image changes or the old
HTC runtime. Shared data uses a signature-protected framework content provider.

## Install-once framework APK

From the repository root:

```sh
source tools/android-env.sh
./gradlew assembleSharedDebug
bash tools/install-shared-debug.sh emulator-5554
```

The eight APKs are in `build/outputs/shared-debug/`. Install
`sense-framework-debug.apk` **first**, then `sense-services-debug.apk`, followed
by the six app APKs from that directory.
The installer accepts an adb device serial as its first argument and defaults
to `emulator-5554`. Updating an existing app with the same local debug signing key
preserves its data. The framework has no launch activity.

This is a real Android static shared-library APK: it declares `<static-library>`;
clients declare `<uses-static-library>` with the library name, exact version and
SHA-256 signing certificate digest. Android manages class loading and resource
package-ID remapping. Each client excludes the framework classes and assets.
The digest is derived from the local debug keystore, not hard-coded.

`assembleSharedDebug` first runs the normal AGP debug builds using checked-in assets,
generating AIDL, weather source copies and merged manifests. The packaging tool then uses
AAPT2 `--shared-lib`, exports the resource API, qualifies client XML references,
compiles against the shared framework JAR, dexes, aligns and signs the APK set.
Resource compilation disables PNG re-crunching so the ROM's compiled `npTc`
nine-patch chunks survive packaging. These chunks carry stretch regions and
content padding; losing them distorts popup frames, selection panels and inputs.
Sources and app IDs are unchanged; use all APKs from the same distribution.
`distribution.json` records the library version and signing certificate digest.

This target currently produces **debug builds only**. The ordinary
`assembleDebug`/`assembleRelease` targets still produce self-contained APKs.
Do not ship the debug key. A production shared distribution needs a persistent
release signing key and explicit library versioning: publish a new library
version for API/resource changes and rebuild clients against that version.
The debug packager's `VERSION` constant controls the library dependency version
and framework APK version code together; client certificate pins are generated
from its signing key on every build.
Keep older versions installed while any clients still depend on them; Android
will not let users remove a library that is required by installed apps.

Verify ownership without a device, or run the full emulator probe:

```sh
python3 tools/test_shared_framework.py
python3 tools/test_shared_framework.py --emulator --serial emulator-5554
```

The emulator probe installs the set and temporarily installs an instrumentation
APK for each client. It verifies shared resource-ID remapping, HTC drawables,
the popup's actual `NinePatchDrawable` type, themes, construction of native shared
controls, and actual activity startup. The host check also verifies that compiled
nine-patch chunks remain present in the packaged framework PNGs.
It removes its test APK and returns to the launcher after success. It does not
make calls, send messages, change default app roles or grant data permissions.

Platform references: [AAPT2 shared-library linking](https://developer.android.com/tools/aapt2),
[Android's static-library manifest definitions](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/res/res/values/attrs_manifest.xml),
and [runtime resource-ID remapping](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/LoadedApk.java).

## Ownership

The library owns the common native controls and visual resources:

- `SenseButton`, `SenseHeaderBar`, `SenseTabBar`, `SenseTabButton`: dark Rosie-style
  controls moved from the launcher. Header navigation takes a description and
  callback supplied by the caller, rather than knowing about the app drawer.
- `SensePreferences`: silver action buttons, light preference rows, HTC checkbox
  toggles, radio choices and input styling, moved from launcher settings.
- `SenseCarouselTabs`: textured icon/label navigation using the recovered tiled
  background, glossy selected panel and original tab icons.
- `SenseStyle`: shared beveled tabs, state-list backgrounds, title/section styles,
  list dividers/selectors, input fields and icon actions, extracted from People
  and the phone/weather styling code.
- `SenseFramework.Light`, `.Dark`, `.DialogDark` and `.DialogButton`: base themes
  used by the apps' local theme wrappers. Wallpaper, app background and feature
  choices remain in the apps.

`tools/prepare_sense_framework.py` extracts 90 shared images from
`com.htc.resources.apk`, `HtcContacts.apk` and `HomePersonalize.apk` inside the local ROM. This includes
buttons, tabs, titlebar controls, checkboxes/radios, inputs, list dividers,
popup/portrait frames, shared icons, the People default portrait and search icon.
Resources have a `sense_` prefix; generated files live under
`assets/htc/sense-framework/res`. The provenance manifest is
`raw/sense_framework_provenance.json`, with original APK paths and SHA-256 hashes.
Compiled nine-patch PNGs are copied unchanged and retain their `npTc` chunks.

Per-app extractors exclude these common resources. The checked-in bundle keeps
shared artwork in the framework directory. Original weather scenes/condition
icons, dial-pad/call artwork and People-specific action icons remain app owned.
The framework contains no app imports, contact/call-log access, weather fetching,
application state or Android components/permissions in its manifest.

## Use

Each app declares:

```kotlin
dependencies { implementation(project(":sense-framework")) }
```

Java uses the library's resource namespace explicitly (AGP's non-transitive R
classes remain enabled):

```java
import dev.sense.framework.SenseStyle;
import dev.sense.framework.SensePreferences;

TextView tab = SenseStyle.tab(context, "People", 0); // 0 left, 1 center, 2 right
SenseStyle.list(listView);
SenseStyle.input(editText);
Switch enabled = new SensePreferences.Toggle(context);
Drawable frame = context.getDrawable(
        dev.sense.framework.R.drawable.sense_common_popupmenu);
```

Layouts/styles reference the merged resource names normally:

```xml
<style name="MyTheme" parent="SenseFramework.Light" />
```

Callbacks, text, selection, layout dimensions and actual feature behavior belong
to the consuming app. Prefer these controls over copying drawables/state-list
builders into an app. Add genuinely common artwork to the one extractor; keep
feature-specific illustrations in their existing generators.

## Verification

```sh
source tools/android-env.sh
./gradlew :sense-framework:assembleDebug :sense-framework:lintDebug \
  :launcher:assembleDebug :weather:assembleDebug :phone:assembleDebug :contacts:assembleDebug
python3 -m unittest discover -s tools -p test_sense_framework.py
```

The Python checks validate original PNG bytes and hashes, compiled nine-patch
chunks, required resource families, and the library's permission/app-dependency
boundary. Integration validation covers launcher settings/drawer/Frequent,
weather forecasts/city popup, phone call history/contact picker, and People
search/details/favorites/groups. Provider tests clean up their own fixtures;
restore any runtime permissions they temporarily grant after they finish.

`SenseScreenHost` supplies the recovered 200 ms activity fades for native screen
navigation; `sense_screen_*` animation XML supports equivalent window transitions.
Messages uses both. See [Messages transition evidence](MESSAGES-RESEARCH.md#screen-transitions).

`SenseSearchBox` recreates HTC’s inset search field and conditional clear control,
using original panel, input and clear assets. See [People styling](PEOPLE-LAYOUT.md).

Drawer grid/list, close and bottom-tab icons now use original HTC state pairs.
Weather uses `SenseHeaderIconButton` for Add/Refresh, preserves the recovered
city-dropdown arrow, and uses the HTC delete active/rest pair for city removal.
Feels-like and humidity metrics use their correctly matched Weather.apk artwork.


## Framework-owned shared weather

Android static shared libraries cannot declare providers, services or activities
([AOSP package validation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/ScanPackageUtils.java)).
The framework therefore has two installable parts:

- `sense-framework-debug.apk`: shared controls, assets and the public
  `dev.sense.framework.weather.WeatherShareClient` data API.
- `sense-services-debug.apk` (`dev.sense.framework.services`): the canonical
  weather store, HTTPS refresh engine and `dev.sense.framework.weather` provider.
  It has no launch activity and needs no foreground-service notification.

Launcher and Weather keep private rendering mirrors of the same active city,
GPS mode, saved-city list, per-city forecasts and update status. Their semantic
commands run in the runtime's process, which serializes changes and coalesces
refresh requests. Data IPC and migration run off the UI thread. A content
observer updates both mirrors; a stable provider connection retains the runtime
while clients use it. Without the runtime, ordinary app builds retain their
local weather behavior.

The shared repository source lives under `sense-framework/src/shared/java` and
is generated into the two clients and runtime with their respective namespaces.
The UI library itself stays free of manifest components and permissions.

On first connection to a framework store, old saved cities are merged by coordinates and newer cached
forecasts win. The Weather app's selected city takes precedence on its first
migration. A persistent store ID allows re-import from the app mirrors if the runtime
store is reset or reinstalled. GPS stays separate from saved cities. Signed clients need
`dev.sense.framework.permission.WEATHER`; the provider explicitly checks callers
for command methods as well as protected state reads. JSON state travels over a
read-only pipe, rather than putting the whole cache into a Binder transaction.
No shared UID, world-readable preference file or storage permission is used.

Both debug and release builds must use a common signing key to share data.
Cross-process emulator regression (after installing the three apps and Weather's
instrumentation APK):

```sh
python3 tools/test_weather_sharing.py
```

The runner backs up and restores all three stores. It checks migration,
bidirectional city/GPS changes, cache reuse, deletion, convergence of both app
mirrors, and denial of unrelated-UID reads/commands. Legacy UI fixture tests
suspend their shared client to keep those fixtures local.

## Fusion runtime

The shared framework also contains a modern Mode10/Fusion runtime subset:
bounded binary and texture decoding, scene evaluation, GLES rendering, native
control adapters and widget hosting. See [FUSION-RUNTIME.md](FUSION-RUNTIME.md)
for supported formats, ownership/lifecycle contracts, validation and remaining
HTC compatibility gaps. Weather data continues to live in Sense Framework
Services; the rendering engine has no weather-provider dependency.
