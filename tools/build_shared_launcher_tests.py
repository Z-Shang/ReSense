#!/usr/bin/env python3
"""Compile native launcher instrumentation against the shared APK resource IDs.

Run assembleSharedDebug first. Ordinary AGP test APKs link bundled resource IDs
and must not be used to test the differently linked shared distribution.
"""
import os
from pathlib import Path
import shutil
import zipfile
from build_shared_framework import ROOT, run, d8_command


def main():
    sdk=Path(os.environ['ANDROID_HOME']);java=Path(os.environ['JAVA_HOME'])/'bin'
    build=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar'
    work=ROOT/'build/shared-launcher-tests'
    if work.exists():shutil.rmtree(work)
    work.mkdir(parents=True);classes=work/'classes';classes.mkdir()
    app=ROOT/'build/shared-framework/launcher/classes.jar'
    framework=ROOT/'build/shared-framework/sense-framework.jar'
    if not app.is_file():raise SystemExit('Run ./gradlew assembleSharedDebug first')
    cp=os.pathsep.join(map(str,[platform,app,framework]))
    sources=list((ROOT/'launcher/src/androidTest/java').rglob('*.java'))
    args=work/'sources.args';args.write_text('\n'.join('"'+str(p)+'"' for p in sources))
    run(java/'javac','-encoding','UTF-8','-source','17','-target','17','-classpath',cp,'-d',classes,'@'+str(args))
    jar=work/'tests.jar';run(java/'jar','--create','--file',jar,'-C',classes,'.')
    dex=work/'dex';dex.mkdir()
    run(*d8_command(),'--debug','--min-api','35','--lib',platform,'--classpath',app,'--classpath',framework,'--output',dex,jar)
    manifest=work/'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.sense.launcher.test">
        <uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/>
        <application android:debuggable="true"/>
        <instrumentation android:name="dev.sense.launcher.WeatherSceneInstrumentation" android:targetPackage="dev.sense.launcher"/>
        </manifest>''')
    unsigned=work/'unsigned.apk';run(build/'aapt2','link','-o',unsigned,'--manifest',manifest,'-I',platform)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as apk:
        for path in dex.glob('*.dex'):apk.write(path,path.name)
    aligned=work/'aligned.apk';run(build/'zipalign','-f','4',unsigned,aligned)
    output=ROOT/'build/outputs/shared-debug/launcher-debug-androidTest.apk'
    run(build/'apksigner','sign','--ks',Path(os.environ['ANDROID_USER_HOME'])/'debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--out',output,aligned)
    print(output)


if __name__=='__main__':main()
