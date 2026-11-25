#!/usr/bin/env python3
"""Compile launcher or Messages instrumentation against shared APK resource IDs.

Run assembleSharedDebug first; pass --module messages for the MMS/SMS suites. Ordinary AGP test APKs link bundled resource IDs
and must not be used to test the differently linked shared distribution.
"""
import argparse
import os
from pathlib import Path
import shutil
import zipfile
from build_shared_framework import ROOT, run, d8_command


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--module',choices=['launcher','messages'],default='launcher')
    module=parser.parse_args().module
    package='dev.sense.'+module
    runner='WeatherSceneInstrumentation' if module=='launcher' else 'MessagesInstrumentation'
    sdk=Path(os.environ['ANDROID_HOME']);java=Path(os.environ['JAVA_HOME'])/'bin'
    build=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar'
    work=ROOT/('build/shared-'+module+'-tests')
    if work.exists():shutil.rmtree(work)
    work.mkdir(parents=True);classes=work/'classes';classes.mkdir()
    app=ROOT/f'build/shared-framework/{module}/classes.jar'
    framework=ROOT/'build/shared-framework/sense-framework.jar'
    if not app.is_file():raise SystemExit('Run ./gradlew assembleSharedDebug first')
    cp=os.pathsep.join(map(str,[platform,app,framework]))
    sources=list((ROOT/f'{module}/src/androidTest/java').rglob('*.java'))
    args=work/'sources.args';args.write_text('\n'.join('"'+str(p)+'"' for p in sources))
    run(java/'javac','-encoding','UTF-8','-source','17','-target','17','-classpath',cp,'-d',classes,'@'+str(args))
    jar=work/'tests.jar';run(java/'jar','--create','--file',jar,'-C',classes,'.')
    dex=work/'dex';dex.mkdir()
    run(*d8_command(),'--debug','--min-api','35','--lib',platform,'--classpath',app,'--classpath',framework,'--output',dex,jar)
    manifest=work/'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}.test">
        <uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/>
        <application android:debuggable="true"/>
        <instrumentation android:name="{package}.{runner}" android:targetPackage="{package}"/>
        </manifest>''')
    unsigned=work/'unsigned.apk';run(build/'aapt2','link','-o',unsigned,'--manifest',manifest,'-I',platform)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as apk:
        for path in dex.glob('*.dex'):apk.write(path,path.name)
        for path in (ROOT/f'{module}/src/androidTest/assets').rglob('*'):
            if path.is_file():apk.write(path,'assets/'+str(path.relative_to(ROOT/f'{module}/src/androidTest/assets')))
    aligned=work/'aligned.apk';run(build/'zipalign','-f','4',unsigned,aligned)
    output=ROOT/f'build/outputs/shared-debug/{module}-debug-androidTest.apk'
    run(build/'apksigner','sign','--ks',Path(os.environ['ANDROID_USER_HOME'])/'debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--out',output,aligned)
    print(output)


if __name__=='__main__':main()
