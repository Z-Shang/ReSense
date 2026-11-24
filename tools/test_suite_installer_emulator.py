#!/usr/bin/env python3
"""Approve a bundle installation on a disposable emulator, preserving installed app data."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(['adb', '-s', 'emulator-5554', *args], text=True)


def screen():
    adb('shell', 'uiautomator', 'dump', '/sdcard/suite-test.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/suite-test.xml')).iter('node'))


def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))


def main():
    adb('shell','am','start','-n','dev.sense.installer/.InstallerActivity')
    deadline=time.monotonic()+180
    last_status = None
    while time.monotonic()<deadline:
        nodes=screen()
        texts='\n'.join(n.get('text','') for n in nodes)
        progress = next((n.get('text') for n in nodes if 'Completed ' in n.get('text','')), None)
        if progress and progress != last_status:
            print(progress, flush=True); last_status = progress
        if 'Completed 8 / 8' in texts:
            print('PASS: all eight packages installed through Android approval');return
        if 'Installation stopped:' in texts or 'Could not install:' in texts:
            raise RuntimeError(texts)
        acted=False
        for n in nodes:
            text=n.get('text','').lower()
            package=n.get('package','')
            if text=='allow from this source' and package=='com.android.settings':
                # Only this installer source is enabled by the test.
                tap(n);adb('shell','input','keyevent','4');acted=True;break
            if text=='continue' and package=='dev.sense.installer':
                tap(n);acted=True;break
            if text in ('install','update') and package in ('com.google.android.packageinstaller','com.android.packageinstaller','com.google.android.permissioncontroller','com.android.permissioncontroller'):
                tap(n);acted=True;break
        if not acted:
            print(texts,flush=True)
        time.sleep(0.8)
    raise TimeoutError('Installer did not finish')

if __name__=='__main__':main()
