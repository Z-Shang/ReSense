#!/usr/bin/env bash
# Compare the Python ETC1 output with Android's native decoder on a running emulator.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source tools/android-env.sh
serial="${1:?Usage: tools/verify_navbar_etc1.sh emulator-5554}"
case "$serial" in
    emulator-*) ;;
    *) echo "Specify an emulator serial, not a physical device." >&2; exit 1 ;;
esac
output=.toolchain/mode10-codec-check
mkdir -p "$output/classes"
javac --release 17 -classpath "$ANDROID_HOME/platforms/android-35/android.jar" \
    -d "$output/classes" tools/SenseEtc1Reference.java
"$ANDROID_HOME/build-tools/35.0.0/d8" --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
    --min-api 34 --output "$output/codec.jar" "$output/classes/SenseEtc1Reference.class"
adb -s "$serial" push "$output/codec.jar" /data/local/tmp/sense-etc1-codec.jar
adb -s "$serial" push references/3.33.401.153/navbar-decoded/home_nav_bg-planes.etc1 \
    /data/local/tmp/sense-navbar-planes.etc1
adb -s "$serial" shell 'CLASSPATH=/data/local/tmp/sense-etc1-codec.jar app_process /system/bin SenseEtc1Reference /data/local/tmp/sense-navbar-planes.etc1 540 92 /data/local/tmp/sense-navbar-reference.rgb'
adb -s "$serial" pull /data/local/tmp/sense-navbar-reference.rgb "$output/android-reference.rgb"
python3 - <<'PY'
from pathlib import Path
import hashlib
import sys
sys.path.insert(0, 'tools')
from mode10 import etc1
raw = Path('references/3.33.401.153/navbar-decoded/home_nav_bg-planes.etc1').read_bytes()
plane_size = len(raw) // 2
ours = etc1(raw[:plane_size], 540, 92) + etc1(raw[plane_size:], 540, 92)
reference = Path('.toolchain/mode10-codec-check/android-reference.rgb').read_bytes()
if ours != reference:
    raise SystemExit('Android ETC1 reference comparison failed')
print('Exact native ETC1 match:', len(ours), 'bytes; SHA-256:', hashlib.sha256(ours).hexdigest())
PY
