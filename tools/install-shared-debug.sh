#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/android-env.sh
serial="${1:-emulator-5554}"
directory="build/outputs/shared-debug"
# Required library must exist before installing any dependent client.
for module in sense-framework sense-services launcher weather phone contacts messages lockscreen; do
    adb -s "$serial" install -r "$directory/$module-debug.apk"
done
