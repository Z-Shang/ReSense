#!/usr/bin/env python3
"""Verify shared APK ownership and exercise each installed app's shared runtime."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile
from build_shared_framework import ROOT, MODULES, run, d8_command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--emulator", action="store_true", help="Install and run probes on the named emulator")
    args = parser.parse_args()
    sdk = Path(os.environ["ANDROID_HOME"])
    build = sdk / "build-tools/35.0.0"
    platform = sdk / "platforms/android-35/android.jar"
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    apks = ROOT / "build/outputs/shared-debug"
    with zipfile.ZipFile(apks / "sense-framework-debug.apk") as lib:
        shared_assets = {name for name in lib.namelist() if name.startswith("res/")}
        checked=0
        for original in (ROOT / "assets/htc/sense-framework/res").glob("drawable-*/*.png"):
            if b"npTc" not in original.read_bytes():
                continue
            packaged=next(name for name in shared_assets if name.endswith("/"+original.name))
            assert b"npTc" in lib.read(packaged), f"Compiled nine-patch metadata stripped: {original.name}"
            checked+=1
        assert checked>0, "No nine-patches checked"
        print(f"Framework: {checked} compiled nine-patch chunks preserved", flush=True)
    for module in MODULES:
        with zipfile.ZipFile(apks / f"{module}-debug.apk") as apk:
            assert not shared_assets.intersection(apk.namelist()), f"{module} embeds shared assets"
            for name in apk.namelist():
                if name.endswith(".dex"):
                    # Class definitions, not mere references to the library.
                    dex = ROOT / "build/shared-framework" / f"check-{module}-{name}"
                    dex.write_bytes(apk.read(name))
                    dump = subprocess.check_output([str(build / "dexdump"), str(dex)], text=True)
                    assert "Class descriptor  : 'Ldev/sense/framework/" not in dump, f"{module} embeds framework code"
        print(f"{module}: no embedded framework code or assets", flush=True)
    if not args.emulator:
        return
    if not args.serial.startswith("emulator-"):
        raise SystemExit("The runtime probe is restricted to an emulator.")
    run("bash", ROOT / "tools/install-shared-debug.sh", args.serial)
    work = ROOT / "build/shared-framework/probe"
    work.mkdir(exist_ok=True)
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    run(java / "javac", "-source", "17", "-target", "17", "-classpath", platform,
        "-d", classes, ROOT / "tools/SharedFrameworkProbe.java")
    jar = work / "probe.jar"
    run(java / "jar", "--create", "--file", jar, "-C", classes, ".")
    dex = work / "dex"
    dex.mkdir(exist_ok=True)
    run(*d8_command(), "--min-api", "35", "--lib", platform, "--output", dex, jar)
    adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]
    key = Path(os.environ["ANDROID_USER_HOME"]) / "debug.keystore"
    for module in MODULES:
        manifest = work / "AndroidManifest.xml"
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="dev.sense.framework.probe"><uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/>
            <application android:debuggable="true"/>
            <instrumentation android:name="dev.sense.framework.probe.SharedFrameworkProbe"
                android:targetPackage="dev.sense.{module}"/></manifest>''')
        unsigned = work / "unsigned.apk"
        run(build / "aapt2", "link", "-o", unsigned, "--manifest", manifest, "-I", platform)
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as apk:
            apk.write(dex / "classes.dex", "classes.dex")
        signed = work / "probe.apk"
        run(build / "apksigner", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
            "--ks-pass", "pass:android", "--out", signed, unsigned)
        run(*adb, "install", "-r", signed)
        result = subprocess.run([*adb, "shell", "am", "instrument", "-w",
            "dev.sense.framework.probe/.SharedFrameworkProbe"], check=True,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90).stdout
        print(result, flush=True)
        assert "INSTRUMENTATION_CODE: -1" in result and "PASS" in result and "error=" not in result, module
    run(*adb, "uninstall", "dev.sense.framework.probe")
    run(*adb, "shell", "am", "start", "-n", "dev.sense.launcher/.MainActivity")


if __name__ == "__main__":
    main()
