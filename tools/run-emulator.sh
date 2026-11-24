#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source tools/android-env.sh
if [ ! -f "$ANDROID_AVD_HOME/Sense_API_35.ini" ]; then
    echo "Sense_API_35 is not configured. See docs/EMULATOR.md." >&2
    exit 1
fi
exec emulator -avd Sense_API_35 -accel on -gpu swiftshader -no-audio "$@"
