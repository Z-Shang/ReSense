#!/usr/bin/env python3
"""Local emulator only: temporarily grant overlay/notification access, then restore both."""
import argparse,re,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5554');args=parser.parse_args()
    if not args.serial.startswith('emulator-'):parser.error('Only a local emulator is supported')
    adb=[str(ROOT/'.toolchain/android-sdk/platform-tools/adb'),'-s',args.serial]
    def run(*command):
        result=subprocess.run(adb+list(command),capture_output=True,text=True,timeout=120)
        if result.returncode:raise RuntimeError(result.stdout+result.stderr)
        return result.stdout
    for apk in ['lockscreen/build/outputs/apk/debug/lockscreen-debug.apk','lockscreen/build/outputs/apk/androidTest/debug/lockscreen-debug-androidTest.apk']:print(run('install','-r',str(ROOT/apk)),flush=True)
    try:
        preferences=ET.fromstring(run('exec-out','run-as','dev.sense.lockscreen','cat','shared_prefs/lockscreen.xml'));feature=preferences.find("./boolean[@name='afterSystemUnlock']")
        had_feature=feature is not None;enabled=had_feature and feature.get('value')=='true'
    except (RuntimeError,ET.ParseError):had_feature=False;enabled=False
    operation=run('shell','appops','get','dev.sense.lockscreen','SYSTEM_ALERT_WINDOW');match=re.search(r'SYSTEM_ALERT_WINDOW:\s*(\w+)',operation);mode=match.group(1)if match else'default'
    notification='android.permission.POST_NOTIFICATIONS: granted=true'in run('shell','dumpsys','package','dev.sense.lockscreen')
    try:
        run('shell','appops','set','dev.sense.lockscreen','SYSTEM_ALERT_WINDOW','allow');run('shell','pm','grant','dev.sense.lockscreen','android.permission.POST_NOTIFICATIONS')
        result=run('shell','am','instrument','-w','-e','suite','after_unlock','dev.sense.lockscreen.test/dev.sense.lockscreen.LockScreenInstrumentation');(ROOT/'.toolchain/lockscreen-after-unlock-test.log').write_text(result);print(result,flush=True)
        if 'INSTRUMENTATION_CODE: -1'not in result or 'INSTRUMENTATION_RESULT: error='in result:raise RuntimeError('After-unlock tests failed')
    finally:
        try:
            cleanup=run('shell','am','instrument','-w','-e','suite','restore_after_unlock','-e','had',str(had_feature).lower(),'-e','enabled',str(enabled).lower(),'dev.sense.lockscreen.test/dev.sense.lockscreen.LockScreenInstrumentation')
            if 'INSTRUMENTATION_CODE: -1'not in cleanup:print('Feature cleanup failed: '+cleanup,flush=True)
        finally:
            run('shell','appops','set','dev.sense.lockscreen','SYSTEM_ALERT_WINDOW',mode)
            run('shell','pm','grant'if notification else'revoke','dev.sense.lockscreen','android.permission.POST_NOTIFICATIONS')
            print('Restored overlay mode: '+mode+'; notification permission: '+str(notification),flush=True)
if __name__=='__main__':main()
