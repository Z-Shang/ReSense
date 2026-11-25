# GitHub Actions artifacts

The **Build Android artifacts** workflow builds debug APKs and the framework AAR
on pushes to `main` and `feature/**`, or through **Actions → Build Android
artifacts → Run workflow**. Manual dispatch is available after the workflow is
on the repository's default branch, as required for
[manual dispatch](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

## Extracted asset input

The workflow uses the committed resources in `assets/htc/<module>/res/`.
It verifies every file against `assets/htc/manifest.json` before building.
The ROM and extraction tools are not invoked by normal builds, and the
`SENSE_ROM_URL` secret is no longer needed.

To regenerate resources after changing an extractor, use the local ROM and
`./gradlew extractHtcAssets`, then commit the resource changes and manifest
together. See [asset preparation](../assets/README.md). CI uploads the built
applications/library, not the stock ROM, toolchains or signing key.

## Downloads

After a successful run, open its **Artifacts** section:

| Artifact prefix | Contents |
| --- | --- |
| `sense-bundle-debug` | Single Sense Installer APK containing all eight shared APKs. |
| `sense-shared-debug` | Eight APKs: framework, services and six apps; distribution metadata and installation notes. |
| `sense-standalone-debug` | Six self-contained app APKs and weather-sharing services. |
| `sense-framework-debug-aar` | Reusable framework AAR. |
| `sense-build-log` | Gradle build log, including failed builds that reached Gradle. |

Artifact names include the commit SHA. Binary downloads are retained for 14
days and logs for 7 days. Each binary download includes `SHA256SUMS` and a
README. The shared set requires the framework and services to be installed
before its client apps. The AAR is a project dependency, not an APK.

## Debug signing across runs

All APKs in one run share the normal Android debug key. By default a fresh
hosted runner generates a new key, so installing a later run over an earlier
run can require uninstalling the earlier apps.

For consistent update signatures, optionally set the Actions secret
**ANDROID_DEBUG_KEYSTORE_BASE64** to a Base64-encoded Android debug keystore.
Use the standard alias `androiddebugkey` and store/key passwords `android`.
For example, use the existing local `.toolchain/android-user/debug.keystore`
if CI builds should update APKs signed by that local key. The secret is restored
only into the runner's Android user directory. Release signing is not configured.

## Build environment

The workflow installs JDK 17, Android platform 35 and Build Tools 35.0.0 on an
Ubuntu runner, then uses the repository's checksum-pinned Gradle wrapper:

```sh
./gradlew --no-daemon --stacktrace --console=plain --max-workers=2 \
  assembleBundleDebug :sense-framework:assembleDebug
python3 tools/collect_ci_artifacts.py
```

It uses the runner's SDK/JDK environment instead of `tools/android-env.sh`,
which intentionally points at the local ignored toolchain. Asset verification
uses only the Python standard library. The Android user directory and Gradle home are
set explicitly under the runner's temporary directory, as required by the
shared APK packager. CI verifies the shared APKs' resource/code ownership and
runs asset-integrity and artifact-collector tests. No emulator tests or release publishing run in
this workflow.

The collector requires an empty output directory. To stage local outputs more
than once, choose a fresh directory with `--output`.

Action usage follows the official [checkout](https://github.com/actions/checkout),
[Java setup](https://github.com/actions/setup-java),
[Android SDK setup](https://github.com/android-actions/setup-android) and
[artifact upload](https://github.com/actions/upload-artifact) documentation.

## Single-file installer

Build from the repository root with `./gradlew assembleBundleDebug`.
The output is `build/outputs/bundle-debug/sense-suite-debug.apk`.
The same task works on main and feature branches and embeds that branch's APKs.

Install and open Sense Installer, allow installation from this source when Android
asks, then tap Continue to stage and approve each package. Framework and services
come first. The installer streams and verifies each embedded APK off the UI thread.
Android requires approval for each package; this is not an atomic transaction.
Cancellation leaves previously installed packages in place. Continue retries the
current package; Stop/reset starts the sequence again without removing apps.
If Android kills the installer, reopen it and retry the current package.

The bootstrap APK has no dependency on the installed Sense framework. It contains
no signing keys and does not grant permissions or choose default apps. Updates
require matching signatures, just like individual APK installation. Do not mix
standalone and shared installations. The suite is a debug build.

The status callback uses an explicit mutable PendingIntent, as required for
[PackageInstaller commits targeting Android 15](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#commit(android.content.IntentSender)).

Android static framework libraries are immutable at a given library version.
When Android reports the version already installed, the installer reuses it;
Android checks the required version and signing certificate for each dependent
app. Framework code/resource changes require increasing `senseFrameworkVersion` in
`gradle.properties`; the bundle cannot overwrite an existing
static framework version. A mismatched signature stops client installation.

## Git-based versions

Every application and the installer use the first 12 characters of `HEAD` as
`versionName`. Android requires an integer `versionCode`, so it is the total
reachable commit count (`git rev-list --count HEAD`), rather than a hash converted
to an unordered number. Normal new commits increase it. Rebases, amendments and
switching between branches can change the hash without increasing that count;
Android permits manual same-code reinstalls, but rejects lower version codes.
Commit new changes before distributing builds intended as newer updates.

Uncommitted builds add `-dirty.<fingerprint>` to the name. The fingerprint covers
tracked and nonignored untracked files, distinguishing local changes made after
the same commit. Rebuilding the same source tree produces the same version.
`tools/resolve_version.py` resolves the identity once during Gradle configuration;
`build/version.properties` passes that exact identity to the shared packager.
`distribution.json` records it alongside the library and signing information.

CI checks out full Git history (`fetch-depth: 0`). Shallow checkouts fail with
an actionable error instead of generating a misleading version code of 1.
For source archives without Git, or an explicit numeric update sequence, supply
both overrides consistently to Gradle:

```sh
SENSE_VERSION_CODE=100 SENSE_VERSION_NAME=archive-build ./gradlew assembleBundleDebug
```

The static shared framework retains its independent `senseFrameworkVersion` in
`gradle.properties`. Published static-library revisions are immutable, so increase
that number when its code/resources change. Clients automatically request the
matching framework version. Android can keep older library versions for apps
that still need them.

Installing the installer APK updates only Sense Installer. Open it and continue
through Android's package approvals to update the eight embedded packages.
Updates retain app data and still require the same signing certificate.

Android's distinction between the visible version name and numeric update code
is documented in [app versioning](https://developer.android.com/studio/publish/versioning).
