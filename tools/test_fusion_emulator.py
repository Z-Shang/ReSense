#!/usr/bin/env python3
"""Build isolated native Fusion tests against the installable framework, then run on an emulator."""
import json
import os
from pathlib import Path
import subprocess
import zipfile
import io
from build_shared_framework import ROOT, run, d8_command

def main():
    sdk=Path(os.environ['ANDROID_HOME']); java=Path(os.environ['JAVA_HOME'])/'bin'
    build=sdk/'build-tools/35.0.0'; platform=sdk/'platforms/android-35/android.jar'
    work=ROOT/'build/fusion-tests';work.mkdir(parents=True,exist_ok=True)
    classes=work/'classes';classes.mkdir(exist_ok=True)
    framework=ROOT/'build/shared-framework/sense-framework.jar'
    run(java/'javac','-source','17','-target','17','-classpath',os.pathsep.join(map(str,[platform,framework])),
        '-d',classes,ROOT/'tools/FusionRuntimeProbe.java')
    jar=work/'probe.jar';run(java/'jar','--create','--file',jar,'-C',classes,'.')
    dex=work/'dex';dex.mkdir(exist_ok=True)
    run(*d8_command(),'--min-api','35','--lib',platform,'--classpath',framework,'--output',dex,jar)
    cert=json.loads((ROOT/'build/outputs/shared-debug/distribution.json').read_text())['certificateSha256']
    manifest=work/'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.sense.fusion.test">
      <uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/>
      <application android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar">
        <uses-static-library android:name="dev.sense.framework" android:version="1" android:certDigest="{cert}"/>
        <activity android:name="dev.sense.framework.probe.FusionRuntimeProbe$ProbeActivity" android:exported="false"/>
      </application>
      <instrumentation android:name="dev.sense.framework.probe.FusionRuntimeProbe" android:targetPackage="dev.sense.fusion.test"/>
    </manifest>''')
    unsigned=work/'unsigned.apk';run(build/'aapt2','link','-o',unsigned,'--manifest',manifest,'-I',platform)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as apk:
        apk.write(dex/'classes.dex','classes.dex')
        for p in sorted((ROOT/'references/3.33.401.153/weather-widgets/assets/Port').glob('*.m10')):
            apk.write(p,'assets/fusion/scenes/'+p.name)
        with zipfile.ZipFile(ROOT/'res/3.33.401.153.zip') as rom:
            with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as stock:
                apk.writestr('assets/fusion/shared.m10',stock.read('assets/Default/drawable/shared.m10'))
    aligned=work/'aligned.apk';run(build/'zipalign','-f','4',unsigned,aligned)
    signed=work/'probe.apk';run(build/'apksigner','sign','--ks',Path(os.environ['ANDROID_USER_HOME'])/'debug.keystore',
        '--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--out',signed,aligned)
    adb=[str(sdk/'platform-tools/adb'),'-s','emulator-5554']
    run(*adb,'install','-r',ROOT/'build/outputs/shared-debug/sense-framework-debug.apk')
    run(*adb,'install','-r',signed)
    try:
        result=subprocess.run([*adb,'shell','am','instrument','-w','dev.sense.fusion.test/dev.sense.framework.probe.FusionRuntimeProbe'],
            text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180).stdout
        print(result,flush=True)
        if 'INSTRUMENTATION_CODE: -1' not in result or 'error=' in result or 'PASS GPU' not in result:
            raise SystemExit('Fusion device checks failed')
    finally:
        run(*adb,'uninstall','dev.sense.fusion.test')

if __name__=='__main__':main()
