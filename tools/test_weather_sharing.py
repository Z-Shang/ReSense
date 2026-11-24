#!/usr/bin/env python3
"""Emulator-only shared weather test; restores both apps' complete weather XML stores."""
import os
from pathlib import Path
import subprocess
import json,time,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ADB=[str(ROOT/'.toolchain/android-sdk/platform-tools/adb'),'-s','emulator-5554']
PACKAGES=('dev.sense.launcher','dev.sense.weather','dev.sense.framework.services')
def adb(*args,check=True,input=None):
    return subprocess.run([*ADB,*args],input=input,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check)
def main():
    backups={}
    for package in PACKAGES:
        result=adb('exec-out','run-as',package,'cat','shared_prefs/weather.xml',check=False)
        backups[package]=result.stdout if result.returncode==0 else None
    try:
        adb("shell","am","start","-W","-n","dev.sense.launcher/.MainActivity")
        result=adb('shell','am','instrument','-w','-e','suite','weather_share','dev.sense.weather.test/dev.sense.weather.WeatherInstrumentation').stdout.decode()
        print(result)
        assert 'INSTRUMENTATION_CODE: -1' in result and 'PASS:' in result and 'error=' not in result
        for action in [('call','--method','refresh'),('read',)]:
            denied=adb('shell','content',*action,'--uri','content://dev.sense.framework.weather/state',check=False).stdout.decode()
            assert 'Permission' in denied or 'SecurityException' in denied, 'Non-Sense UID obtained weather access'
        print('PASS: unrelated shell UID denied both reads and commands')
        deadline=time.monotonic()+8
        while True:
            stores=[]
            for package in PACKAGES:
                raw=adb('exec-out','run-as',package,'cat','shared_prefs/weather.xml').stdout
                stores.append({n.get('name'):(n.get('value') if n.get('value') is not None else n.text) for n in ET.fromstring(raw)})
            if all(p.get('currentLocation')=='true' and json.loads(p.get('data','{}')).get('current',{}).get('temperature_2m')==17 for p in stores):break
            assert time.monotonic()<deadline, 'Launcher/weather/framework did not converge'
            time.sleep(.1)
        print('PASS: launcher, Weather and framework stores all received the same GPS forecast')
    finally:
        # Keep system Settings foreground so Android does not restart the default
        # launcher while its private preferences are being restored.
        adb('shell','am','start','-a','android.settings.SETTINGS',check=False)
        for package in PACKAGES:adb('shell','am','force-stop',package)
        for package,data in backups.items():
            adb('shell','run-as',package,'mkdir','-p','shared_prefs')
            if data is None:adb('shell','run-as',package,'rm','-f','shared_prefs/weather.xml','shared_prefs/weather.xml.bak')
            else:
                adb('exec-in','run-as',package,'sh','-c','cat > shared_prefs/weather.xml',input=data)
                adb('shell','run-as',package,'rm','-f','shared_prefs/weather.xml.bak')
        print('Restored all weather stores')
if __name__=='__main__':main()
