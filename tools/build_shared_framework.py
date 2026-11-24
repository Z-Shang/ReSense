#!/usr/bin/env python3
"""Package install-once Sense runtime and clients using Android's shared-library linker.

Invoked by assembleSharedDebug after AGP prepares the normal debug builds. The
normal APKs remain self-contained; this distribution has a required shared APK.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])
MODULES = ("launcher", "weather", "phone", "contacts", "messages", "lockscreen")
LIBRARY = "dev.sense.framework"
VERSION = 1


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def d8_command():
    # Use AGP's compiler, which supports API 35; the SDK 35.0.0 executable
    # bundles an older D8 that only understands min-api through 34.
    version = re.search(r'id\("com.android.application"\) version "([^"]+)"',
                        (ROOT / "build.gradle.kts").read_text())[1]
    cache = Path(os.environ["GRADLE_USER_HOME"]) / "caches/modules-2/files-2.1"
    jars = list((cache / "com.android.tools.build/builder" / version).glob(f"*/builder-{version}.jar"))
    if len(jars) != 1:
        raise SystemExit("AGP compiler missing; run ./gradlew assembleSharedDebug first.")
    return [Path(os.environ["JAVA_HOME"]) / "bin/java", "-cp", jars[0], "com.android.tools.r8.D8"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    sdk = Path(os.environ["ANDROID_HOME"])
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    build = sdk / "build-tools/35.0.0"
    platform = sdk / "platforms/android-35/android.jar"
    output = ROOT / "build/outputs/shared-debug"
    work = ROOT / "build/shared-framework"
    # Only generated directories owned by this packaging task.
    for directory in (output, work):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    key = Path(os.environ["ANDROID_USER_HOME"]) / "debug.keystore"
    if not key.is_file():
        raise SystemExit("Debug keystore missing; run the Gradle debug builds first.")
    certificate = subprocess.check_output([
        str(java / "keytool"), "-exportcert", "-keystore", str(key),
        "-storepass", "android", "-alias", "androiddebugkey"], cwd=ROOT)
    digest = hashlib.sha256(certificate).hexdigest()
    library_apk = output / "sense-framework-debug.apk"
    library_jar = work / "sense-framework.jar"
    summary = {"library": LIBRARY, "version": VERSION, "certificateSha256": digest,
               "buildType": "debug", "applications": []}
    shared_symbols = set()
    framework_resources = [ROOT / "sense-framework/src/main/res",
                           ROOT / "sense-framework/build/generated/htcSense/res"]
    for directory in framework_resources:
        for path in directory.glob("*/*"):
            kind = path.parent.name.split("-")[0]
            if kind == "values":
                for item in ET.parse(path).getroot():
                    if "name" in item.attrib:
                        shared_symbols.add((item.get("type", item.tag), item.get("name")))
            else:
                shared_symbols.add((kind, path.name.split(".")[0]))
    public_resources = work / "public-res/values"
    public_resources.mkdir(parents=True)
    public = ET.Element("resources")
    for kind, name in sorted(shared_symbols):
        ET.SubElement(public, "public", {"type": kind, "name": name})
    ET.ElementTree(public).write(public_resources / "public.xml", encoding="utf-8")
    framework_resources.append(public_resources.parent)

    def qualify(text):
        text = re.sub(r"@([a-z]+)/([A-Za-z0-9_.]+)",
                      lambda m: "@" + LIBRARY + ":" + m[1] + "/" + m[2]
                      if (m[1], m[2]) in shared_symbols else m[0], text)
        return re.sub(r'parent="([A-Za-z0-9_.]+)"',
                      lambda m: 'parent="@' + LIBRARY + ':style/' + m[1] + '"'
                      if ("style", m[1]) in shared_symbols else m[0], text)

    def package(module, manifest, resource_dirs, shared=False):
        dest = work / module
        dest.mkdir()
        tree = ET.parse(manifest)
        app = tree.getroot().find("application")
        if app is None:
            app = ET.SubElement(tree.getroot(), "application")
        if shared:
            ET.SubElement(app, "static-library", {
                ANDROID + "name": LIBRARY, ANDROID + "version": str(VERSION)})
        else:
            ET.SubElement(app, "uses-static-library", {
                ANDROID + "name": LIBRARY, ANDROID + "version": str(VERSION),
                ANDROID + "certDigest": digest})
        merged = dest / "AndroidManifest.xml"
        tree.write(merged, encoding="utf-8", xml_declaration=True)
        if not shared:
            merged.write_text(qualify(merged.read_text()))
        archives = []
        for index, directory in enumerate(resource_dirs):
            if directory.is_dir():
                if not shared:
                    staged = dest / f"res-{index}"
                    shutil.copytree(directory, staged)
                    for xml in staged.rglob("*.xml"):
                        xml.write_text(qualify(xml.read_text()))
                    directory = staged
                archive = dest / f"resources-{index}.zip"
                # ROM PNGs include already-compiled nine-patch chunks under .png
                # names. Re-crunching strips npTc and turns frames into stretched
                # bitmaps, losing both stretch regions and content padding.
                run(build / "aapt2", "compile", "--no-crunch", "--dir", directory, "-o", archive)
                archives.extend(("-R", archive))
        resources = dest / "resources.apk"
        generated = dest / "java"
        generated.mkdir()
        includes = [] if shared else ["-I", library_apk]
        run(build / "aapt2", "link", "-o", resources, "--manifest", merged,
            "-I", platform, *includes, "--java", generated, "--auto-add-overlay", "-0", "mp4",
            *( ["--shared-lib"] if shared else [] ), *archives)
        sources = list((ROOT / module / "src/main/java").rglob("*.java"))
        sources += list(generated.rglob("*.java"))
        if module == "launcher":
            sources += list((ROOT / module / "build/generated/aidl_source_output_dir/debug/out").rglob("*.java"))
        if module in ("weather", "launcher"):
            sources += list((ROOT / module / "build/generated/sharedWeather/java").rglob("*.java"))
        if module == "sense-services":
            sources += list((ROOT / module / "build/generated/weather/java").rglob("*.java"))
        classes = dest / "classes"
        classes.mkdir()
        classpath = str(platform) + (os.pathsep + str(library_jar) if not shared else "")
        arguments = dest / "javac.args"
        arguments.write_text("\n".join('"' + str(s).replace("\\", "\\\\").replace('"', '\\"') + '"' for s in sources))
        run(java / "javac", "-encoding", "UTF-8", "-source", "17", "-target", "17",
            "-classpath", classpath, "-d", classes, "@" + str(arguments))
        jar = library_jar if shared else dest / "classes.jar"
        run(java / "jar", "--create", "--file", jar, "-C", classes, ".")
        dex = dest / "dex"
        dex.mkdir()
        run(*d8_command(), "--debug", "--min-api", "35", "--lib", platform,
            *( ["--classpath", library_jar] if not shared else [] ), "--output", dex, jar)
        unsigned = dest / "unsigned.apk"
        shutil.copyfile(resources, unsigned)
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as apk:
            for path in dex.glob("*.dex"):
                apk.write(path, path.name)
        aligned = dest / "aligned.apk"
        run(build / "zipalign", "-f", "-p", "4", unsigned, aligned)
        final = output / f"{module}-debug.apk"
        run(build / "apksigner", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
            "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", final, aligned)
        run(build / "apksigner", "verify", final)
        print(f"Packaged {final.relative_to(ROOT)}", flush=True)
        return final

    manifest = work / "framework-manifest.xml"
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="{LIBRARY}" android:versionCode="{VERSION}" android:versionName="{VERSION}.0">
        <uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/>
        <application android:label="Sense Framework" android:hasCode="true"/>
        </manifest>''')
    package("sense-framework", manifest, framework_resources, shared=True)
    for module in ("sense-services", *MODULES):
        generated = ROOT / module / "build/generated"
        resource_dirs = [ROOT / module / "src/main/res"] + sorted(generated.glob("*/res"))
        package(module, ROOT / module / "build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml", resource_dirs)
        if module=="sense-services":summary["services"]=[module]
        else:summary["applications"].append(module)
    (output / "distribution.json").write_text(json.dumps(summary, indent=2) + "\n")
    print("Install sense-framework-debug.apk and sense-services-debug.apk first, then the six client APKs.", flush=True)


if __name__ == "__main__":
    main()
