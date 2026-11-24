# Build-ready HTC assets

`htc/<module>/res/` contains the Android resources used by the launcher,
framework, weather, phone, contacts, messages and lock-screen builds. Both
Gradle and the shared APK packager read these directories directly. A checkout
can build without the stock ROM, decoded APKs or generated extraction folders.

The bundle contains PNG/JPEG artwork, compiled nine-patches, fonts, weather
videos, original Mode10 widget resources and generated scene/animation metadata.
Common UI resources remain owned by `sense-framework`; the shared APK build
does not embed them again in each client.

`htc/manifest.json` records the source ROM's name/hash and each extracted file's
SHA-256 and byte length. Existing resource provenance JSON is retained within
the module resources. Git attributes preserve every resource byte, including
compiled nine-patch chunks, across checkout platforms.

Verify without the ROM:

```sh
python3 tools/verify_htc_assets.py
```

Regenerate explicitly after changing an extractor, with the original ROM in
`res/3.33.401.153.zip`:

```sh
python3 tools/extract_build_assets.py --replace
# Or, using the local Gradle environment:
./gradlew extractHtcAssets
```

Extraction runs in a fresh staging directory and validates the entire bundle
before replacing it. Only Android resource directories are retained; unrelated
research dumps and original APKs/libraries are excluded. Review the resource
and manifest changes together before committing them. Normal builds never
regenerate or overwrite this directory.

The initial bundle contains 1,084 resources (about 61 MiB). Validation used an
isolated source checkout with no ROM or old build outputs: standalone/shared
APKs and the framework AAR built successfully, the shared-resource ownership
checks passed, and all 27 compiled nine-patches retained their metadata.
