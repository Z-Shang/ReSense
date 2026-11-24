# Sense 3.6 reconstruction

## Input audit

Inspected `res/pyramid-ics-crc-3.0.16-31a4fc7.zip` without extracting it. The ZIP contains `pyramid-ics_readme.txt` and `pyramid-ics-crc-3.0.16-31a4fc7.tar.gz`. The readme describes compiling `msm8660_defconfig` into `arch/arm/boot/zImage`. The nested TAR has 39,980 entries, including Linux headers and drivers. It contains no APKs, ODEX files or `system.img`. Kernel code does not contain Sense UI resources.

## Needed for faithful reconstruction

`res/3.33.401.153.zip` is now available and has been inventoried and selectively extracted. See [ROM-AUDIT.md](ROM-AUDIT.md). Android 4.0.3 and the build number are confirmed; the literal Sense version property is `3.5`, so verify the visible release against software information and visual references.

Useful inputs include:

- `system/app/Rosie.apk` and companion ODEX if present.
- `system/framework/framework-res.apk`, `com.htc.resources.apk`, and HTC framework files.
- HTC clock/weather widget APKs, wallpapers, fonts and skin resources.
- Screenshots or recordings of the home screen, dock, app drawer, Leap overview, page transitions and clock/weather behavior.

Exact paths and APK names vary by release; inventory the actual image first. Preserve source archives. Extract only relevant assets into a separate working directory and record each asset’s source path, density and modifications. Keep extracted proprietary assets out of a public source tree pending a distribution decision.

## Architecture

Reimplement launcher behavior using modern Android APIs. Original Rosie code depends on obsolete HTC framework interfaces and cannot simply be installed as the modern launcher. Decode original layouts and artwork as reference; do not transplant the old framework. Use LauncherApps and user/profile-aware identities as app management grows. Use AppWidgetHost for modern widgets. HTC widgets may require separate recreations because their providers depend on the HTC framework.

The initial native Views implementation deliberately establishes the HOME entry point, app discovery and launch flow before the visual reconstruction. It has no weather backend and displays no fabricated weather.

## Milestones

1. Obtain a real ROM and inventory Rosie, frameworks, skins and clock/weather assets. Establish visual references at original density.
2. Reconstruct the Sense 3.6 dock, wallpaper, typography, clock and app drawer from verified references. Support modern screen sizes and accessibility.
3. Add persisted home pages, placement, drag/drop, folders and Leap overview; build paging and gesture behavior against recordings.
4. Add AppWidgetHost binding/configuration, resizing and recreation after process death. Recreate HTC clock/weather behavior with explicit location and weather-source choices.
5. Add profile-aware app discovery, package-change handling, pinned shortcuts and Android 15 Private Space support. Handle locked and quiet profiles before advertising profile support.
6. Validate on Android 15 and newer: default home selection, Home/Back, gesture and button navigation, cutouts, rotation, font scaling, process restart, app installation/removal, widget lifecycle and profile privacy. Capture screenshot comparisons for fidelity.

## Platform references

- Android Gradle Plugin 8.9 compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Android 15 edge-to-edge behavior: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Android 15 behavior changes, including Private Space launcher requirements: https://developer.android.com/about/versions/15/behavior-changes-all
