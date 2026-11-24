package dev.sense.phone;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.telecom.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Default-role integration over an explicitly selected local account; no cellular calls. */
final class DefaultDialerInstrumentation {
    private final Instrumentation test;
    private Activity activity;
    private PhoneAccountHandle account;
    private Instrumentation.ActivityMonitor callMonitor;
    private final Set<String> tokens=new HashSet<>();
    DefaultDialerInstrumentation(Instrumentation test){this.test=test;}
    private void main(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private void await(BooleanSupplier condition,String label)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){AtomicReference<Boolean> ready=new AtomicReference<>(false);main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}throw new AssertionError(label+" timed out");}
    private String shell(String command)throws Exception{try(var fd=test.getUiAutomation().executeShellCommand(command);var input=new FileInputStream(fd.getFileDescriptor())){return new String(input.readAllBytes());}}
    private SenseInCallService.Entry call(){SenseInCallService s=SenseInCallService.current();return s==null?null:s.primary();}
    private View find(View v,String label){if(!v.isShown())return null;if(v instanceof TextView t&&label.contentEquals(t.getText()))return v;if(label.equals(v.getContentDescription()))return v;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View hit=find(group.getChildAt(i),label);if(hit!=null)return hit;}return null;}
    private void click(String label){main(()->{View view=find(activity.getWindow().getDecorView(),label);if(view==null||!view.performClick())throw new AssertionError("Missing control "+label);});}
    private void showCall()throws Exception{
        await(()->call()!=null,"Telecom InCallService binding");main(()->tokens.add(call().token));
        main(()->SenseInCallService.current().show(call().token,false));
        await(()->callMonitor.getLastActivity()!=null&&!callMonitor.getLastActivity().isFinishing()&&callMonitor.getLastActivity().hasWindowFocus(),"In-call activity foreground");
        main(()->activity=callMonitor.getLastActivity());test.waitForIdleSync();
    }
    private void incoming(TelecomManager telecom){Bundle extras=new Bundle();extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,Uri.parse("tel:5550177999"));telecom.addNewIncomingCall(account,extras);}
    void run(){Bundle report=new Bundle();TelecomManager telecom=test.getTargetContext().getSystemService(TelecomManager.class);
        try{
            if(!test.getTargetContext().getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER)||!test.getTargetContext().getPackageName().equals(telecom.getDefaultDialerPackage()))throw new AssertionError("Sense Phone not assigned the real default role");
            for(String permission:new String[]{Manifest.permission.CALL_PHONE,Manifest.permission.READ_PHONE_STATE,Manifest.permission.POST_NOTIFICATIONS})test.getUiAutomation().grantRuntimePermission(test.getTargetContext().getPackageName(),permission);
            account=new PhoneAccountHandle(new ComponentName(test.getTargetContext(),LocalTestConnectionService.class),"sense-local-instrumentation");
            telecom.registerPhoneAccount(PhoneAccount.builder(account,"Sense local instrumentation").setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER).setSupportedUriSchemes(List.of(PhoneAccount.SCHEME_TEL)).build());
            String enabled=shell("telecom set-phone-account-enabled "+account.getComponentName().flattenToString()+" "+account.getId()+" 0");if(!enabled.isEmpty())report.putString("account",enabled.strip());
            if(telecom.getPhoneAccount(account)==null||!telecom.getPhoneAccount(account).isEnabled())throw new AssertionError("Local test account not enabled");
            activity=test.startActivitySync(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:5550177999")).setPackage(test.getTargetContext().getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{if(find(activity.getWindow().getDecorView(),"5550177999")==null)throw new AssertionError("ACTION_DIAL did not prefill number");});
            callMonitor=test.addMonitor(InCallActivity.class.getName(),null,false);
            incoming(telecom);showCall();await(()->call()!=null&&call().ringing(),"Incoming call");
            Thread.sleep(250);try(var output=test.getTargetContext().openFileOutput("default-phone-incoming.png",0)){var bitmap=test.getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}
            android.app.PendingIntent oldAnswer=null;
            var activeNotifications=test.getTargetContext().getSystemService(NotificationManager.class).getActiveNotifications();
            if(activeNotifications.length==0)throw new AssertionError("Incoming call notification missing");
            for(var item:activeNotifications){Notification notification=item.getNotification();if(notification.fullScreenIntent==null)throw new AssertionError("Incoming full-screen intent missing");if(notification.actions!=null)for(Notification.Action action:notification.actions)if("Answer".contentEquals(action.title))oldAnswer=action.actionIntent;}
            if(oldAnswer==null)throw new AssertionError("Answer notification action missing");
            oldAnswer.send();await(()->call()!=null&&call().state()==Call.STATE_ACTIVE,"Answer");
            click("Mute");await(()->SenseInCallService.current().muted(),"Mute");click("Unmute");await(()->!SenseInCallService.current().muted(),"Unmute");
            await(()->!SenseInCallService.current().endpoints().isEmpty(),"Audio endpoints");
            main(()->{for(CallEndpoint endpoint:SenseInCallService.current().endpoints())if(endpoint.getEndpointType()==CallEndpoint.TYPE_SPEAKER){SenseInCallService.current().route(endpoint);break;}});
            await(()->SenseInCallService.current().endpoint()!=null&&SenseInCallService.current().endpoint().getEndpointType()==CallEndpoint.TYPE_SPEAKER,"Speaker route");
            click("Hold");await(()->call().state()==Call.STATE_HOLDING,"Hold");click("Resume");await(()->call().state()==Call.STATE_ACTIVE,"Resume");
            click("Keypad");click("Send tone 5");await(()->LocalTestConnectionService.calls.stream().anyMatch(c->c.tone=='5'),"DTMF delivered to local provider");click("Hide keypad");
            Thread.sleep(200);try(var output=test.getTargetContext().openFileOutput("default-phone-active.png",0)){var bitmap=test.getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}
            String oldToken=call().token;click("End call");await(()->call()==null,"Hangup and UI cleanup");
            incoming(telecom);showCall();oldAnswer.send();Thread.sleep(150);main(()->{if(call()==null||!call().ringing())throw new AssertionError("Stale answer affected new call");});click("Decline");await(()->call()==null,"Decline");
            if(LocalTestConnectionService.calls.stream().noneMatch(c->c.rejected))throw new AssertionError("Reject not delivered to provider");
            Bundle outgoing=new Bundle();outgoing.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,account);telecom.placeCall(Uri.parse("tel:5550177998"),outgoing);showCall();await(()->call()!=null&&call().state()==Call.STATE_DIALING,"Outgoing local call");
            main(()->{test.getTargetContext().sendBroadcast(new Intent(test.getTargetContext(),CallActionReceiver.class).setAction(SenseInCallService.END).putExtra(SenseInCallService.TOKEN,oldToken));});Thread.sleep(150);
            main(()->{if(call()==null||call().state()!=Call.STATE_DIALING)throw new AssertionError("Stale notification action affected a new call");LocalTestConnectionService.calls.get(LocalTestConnectionService.calls.size()-1).setActive();});await(()->call().state()==Call.STATE_ACTIVE,"Outgoing connected");
            click("End call");await(()->call()==null,"Outgoing cleanup");
            report.putString("result","PASS: default role, ACTION_DIAL, local incoming/outgoing Telecom binding, answer/reject/end, mute, hold/resume, DTMF, stale-action isolation and cleanup. No cellular calls placed.");
        }catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            if(callMonitor!=null)test.removeMonitor(callMonitor);
            main(()->{SenseInCallService s=SenseInCallService.current();if(s!=null)for(String token:tokens)s.end(token);for(var call:LocalTestConnectionService.calls)if(call.getState()!=Connection.STATE_DISCONNECTED)call.onDisconnect();if(activity!=null)activity.finish();});
            if(account!=null)telecom.unregisterPhoneAccount(account);
        }
        test.finish(report.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);
    }
}
