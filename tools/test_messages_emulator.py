#!/usr/bin/env python3
"""Emulator-only SMS integration. Incoming emulation + intercepted outgoing transport, no carrier send."""
import argparse,json,os,subprocess,time,uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
PERMISSIONS=['READ_SMS','SEND_SMS','RECEIVE_SMS','RECEIVE_MMS','RECEIVE_WAP_PUSH','READ_CONTACTS','READ_PHONE_STATE','POST_NOTIFICATIONS']

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite',choices=['sms','mms','previews','widgets'],default='sms');parser.add_argument('--serial',default='emulator-5554');parser.add_argument('--shared',action='store_true');args=parser.parse_args()
    if not args.serial.startswith('emulator-'):parser.error('Only a local Android emulator is supported; physical-device sends are prohibited by this test.')
    adb=str(ROOT/'.toolchain/android-sdk/platform-tools/adb');prefix=[adb,'-s',args.serial]
    def run(*command,binary=False):
        result=subprocess.run(prefix+list(command),capture_output=True,text=not binary,timeout=180)
        if result.returncode:raise RuntimeError(str(command)+': '+str(result.stdout)+str(result.stderr))
        return result.stdout
    for _ in range(60):
        try:
            if run('shell','getprop','sys.boot_completed').strip()=='1':break
        except RuntimeError:pass
        time.sleep(1)
    else:raise RuntimeError('Emulator did not boot')
    roles=run('shell','cmd','role','get-role-holders','--user','0','android.app.role.SMS').split()
    for apk in (['build/outputs/shared-debug/messages-debug.apk','build/outputs/shared-debug/messages-debug-androidTest.apk'] if args.shared else ['messages/build/outputs/apk/debug/messages-debug.apk','messages/build/outputs/apk/androidTest/debug/messages-debug-androidTest.apk']):print(run('install','-r',str(ROOT/apk)),flush=True)
    package=run('shell','dumpsys','package','dev.sense.messages');permissions={p:'android.permission.'+p+': granted=true' in package for p in PERMISSIONS}
    backup=dict(roles=roles,permissions=permissions);(ROOT/'.toolchain/messages-test-before.json').write_text(json.dumps(backup,indent=2)+'\n')
    incoming='SENSE_SMS_EMULATOR_'+uuid.uuid4().hex;result=''
    try:
        run('shell','cmd','role','add-role-holder','--user','0','android.app.role.SMS','dev.sense.messages')
        assert run('shell','cmd','role','get-role-holders','--user','0','android.app.role.SMS').strip()=='dev.sense.messages','Role not assigned'
        run('shell','pm','grant','dev.sense.messages','android.permission.POST_NOTIFICATIONS')
        run('shell','am','instrument','-w','-e','cleanup-only','true','dev.sense.messages.test/dev.sense.messages.MessagesInstrumentation')
        if args.suite in ('mms','previews','widgets'):
            result=run('shell','am','instrument','-w','-e','suite',args.suite,'dev.sense.messages.test/dev.sense.messages.MessagesInstrumentation')
        else:
            process=subprocess.Popen(prefix+['shell','am','instrument','-w','-e','incoming',incoming,'dev.sense.messages.test/dev.sense.messages.MessagesInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            # Instrumentation restarts the target process. Inject only after that restart.
            time.sleep(1)
            print(run('emu','sms','send','5550182027',incoming),flush=True)
            result,error=process.communicate(timeout=180)
            if process.returncode:raise RuntimeError(result+error)
        (ROOT/'.toolchain/messages-instrumentation.log').write_text(result);print(result,flush=True)
        for image in ['messages-inbox.png','messages-conversation.png','messages-failure.png','messages-media-previews.png','messages-widget-list.png','messages-widget-summary.png','messages-widget-card.png']:
            try:(ROOT/'.toolchain'/image).write_bytes(run('exec-out','run-as','dev.sense.messages','cat','files/'+image,binary=True))
            except RuntimeError:pass
    finally:
        try:
            cleanup=run('shell','am','instrument','-w','-e','cleanup-only','true','dev.sense.messages.test/dev.sense.messages.MessagesInstrumentation')
            if 'INSTRUMENTATION_CODE: -1' not in cleanup:print('Fixture cleanup failed: '+cleanup,flush=True)
        except RuntimeError as error:print('Fixture cleanup failed: '+str(error),flush=True)
        if roles:
            for holder in roles:run('shell','cmd','role','add-role-holder','--user','0','android.app.role.SMS',holder)
        else:run('shell','cmd','role','remove-role-holder','--user','0','android.app.role.SMS','dev.sense.messages')
        for permission,granted in permissions.items():run('shell','pm','grant' if granted else 'revoke','dev.sense.messages','android.permission.'+permission)
        print('Restored SMS role: '+run('shell','cmd','role','get-role-holders','--user','0','android.app.role.SMS').strip(),flush=True)
    if 'INSTRUMENTATION_CODE: -1' not in result or 'INSTRUMENTATION_RESULT: error=' in result:raise SystemExit('SMS integration failed; see .toolchain/messages-instrumentation.log')
if __name__=='__main__':main()
