# Sense Launcher

An independent project that reverse engineers HTC software and uses original HTC assets with modern Android code to recreate an experience similar to HTC Sense 3.x on Android 15 and later.

**This is not an official HTC project. It is not affiliated with, endorsed by, or supported by HTC.**

## What's included

The project recreates the Sense home experience and several companion apps:

| App | Experience |
| --- | --- |
| **Launcher** | Home pages, app drawer, widgets, clock/weather, curved dock and page overview. |
| **Weather** | City selection, current conditions, hourly and five-day forecasts, and weather animations. |
| **Phone** | Dial pad, contact search, call history and call controls. |
| **People** | Contacts, favorites, groups and contact details. |
| **Messages** | SMS and single-recipient carrier MMS conversations. |
| **Lock screen** | HTC-style ring and configurable shortcuts, used alongside Android's secure lock screen. |

The apps share Sense-style controls and recovered artwork. Development is ongoing;
visual fidelity and device compatibility vary. See [project status](docs/PROJECT-STATUS.md)
for scope and validation notes.

## Getting started

Use the [build and installation guide](docs/BUILDING.md) to build locally, or see
[GitHub Actions artifacts](docs/CI-ARTIFACTS.md) for downloadable debug builds from
workflow runs. The stock ROM is needed for asset research and regeneration,
but is not required for normal builds.

After installation, choose **Sense Launcher** in Android's default Home app
settings. Phone and Messages can be selected as defaults separately. The Sense
lock screen uses Android's secure unlock flow and does not replace the system
keyguard automatically.

## Documentation

- [Documentation index](docs/README.md): app guides, setup, architecture and research.
- [Build and install](docs/BUILDING.md): toolchain requirements, APK variants and commands.
- [Project status](docs/PROJECT-STATUS.md): implementation scope and validation limits.
- [Reconstruction roadmap](docs/PORTING.md): original research goals and development milestones.
