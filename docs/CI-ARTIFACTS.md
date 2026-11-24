# GitHub Actions artifacts

The **Build Android artifacts** workflow builds debug APKs and the framework AAR
on pushes to `main` and `feature/**`, or through **Actions → Build Android
artifacts → Run workflow**. Manual dispatch is available after the workflow is
on the repository's default branch, as required for
[manual dispatch](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

## Required ROM input

The ROM is local and ignored by Git, so a hosted runner needs a download source.
Add the repository Actions secret **SENSE_ROM_URL**, containing a direct HTTPS
URL for the same `res/3.33.401.153.zip` used locally. A signed storage-object URL
works; renew the secret when that URL expires. An HTML download page does not
work. No ROM URL is committed to this repository.

CI verifies the archive against `.github/rom.sha256` before any extraction.
The pinned SHA-256 is
`5bc5a4424ce09730a83232835f910c90a7a288976455ce3eaff69f1acf8cf141`.
The workflow does not upload the original ROM, toolchains or signing key as
artifacts; it uploads the built applications and library containing the
resources prepared by the existing build.

## Downloads

After a successful run, open its **Artifacts** section:

| Artifact prefix | Contents |
| --- | --- |
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
  assembleSharedDebug :sense-framework:assembleDebug
python3 tools/collect_ci_artifacts.py
```

It uses the runner's SDK/JDK environment instead of `tools/android-env.sh`,
which intentionally points at the local ignored toolchain. Python extraction
uses only the standard library. The Android user directory and Gradle home are
set explicitly under the runner's temporary directory, as required by the
shared APK packager. CI verifies the shared APKs' resource/code ownership and
runs artifact-collector tests. No emulator tests or release publishing run in
this workflow.

The collector requires an empty output directory. To stage local outputs more
than once, choose a fresh directory with `--output`.

Action usage follows the official [checkout](https://github.com/actions/checkout),
[Java setup](https://github.com/actions/setup-java),
[Android SDK setup](https://github.com/android-actions/setup-android) and
[artifact upload](https://github.com/actions/upload-artifact) documentation.
