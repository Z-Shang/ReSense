package dev.sense.phone;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.telecom.*;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.Executors;

/** Telecom owns the calls, ringtone and microphone. This service owns their UI. */
public final class SenseInCallService extends InCallService {
    static final String TOKEN="callToken",ANSWER="dev.sense.phone.ANSWER",END="dev.sense.phone.END";
    private static SenseInCallService instance;
    private static final Set<Runnable> listeners=new HashSet<>();
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService contacts=Executors.newSingleThreadExecutor();
    private List<CallEndpoint> endpoints=List.of();
    private CallEndpoint endpoint;
    private boolean muted;
    static SenseInCallService current(){return instance;}
    static void listen(Runnable listener){listeners.add(listener);}
    static void unlisten(Runnable listener){listeners.remove(listener);}
    static final class Entry {
        final String token=UUID.randomUUID().toString();final Call call;Call.Callback callback;
        String contactName,lookupNumber;
        Entry(Call call){this.call=call;}
        int state(){return call.getDetails().getState();}
        boolean ringing(){return state()==Call.STATE_RINGING||state()==Call.STATE_SIMULATED_RINGING;}
        String number(){var d=call.getDetails();Uri handle=d.getHandle();return d.getHandlePresentation()==TelecomManager.PRESENTATION_ALLOWED&&handle!=null?handle.getSchemeSpecificPart():"";}
        String title(){var d=call.getDetails();if(d.getHandlePresentation()==TelecomManager.PRESENTATION_RESTRICTED)return "Private number";if(number().isBlank())return call.getChildren().isEmpty()?"Unknown caller":"Conference call";if(contactName!=null&&!contactName.isBlank())return contactName;String name=d.getCallerDisplayName();return d.getCallerDisplayNamePresentation()==TelecomManager.PRESENTATION_ALLOWED&&name!=null&&!name.isBlank()?name:number();}
        String status(){return switch(state()){
            case Call.STATE_RINGING,Call.STATE_SIMULATED_RINGING->"Incoming call";
            case Call.STATE_ACTIVE->"Connected";case Call.STATE_HOLDING->"On hold";
            case Call.STATE_SELECT_PHONE_ACCOUNT->"Choose a SIM";case Call.STATE_DIALING->"Dialing…";
            case Call.STATE_DISCONNECTING->"Ending call…";case Call.STATE_DISCONNECTED->"Call ended";
            default->"Connecting…";
        };}
    }
    @Override public void onCreate(){super.onCreate();instance=this;
        NotificationChannel channel=new NotificationChannel("calls","Phone calls",NotificationManager.IMPORTANCE_HIGH);channel.setDescription("Incoming and ongoing calls");channel.setSound(null,null);channel.enableVibration(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    List<Entry> entries(){return List.copyOf(entries.values());}
    Entry entry(String token){return entries.get(token);}
    Entry primary(){for(Entry e:entries.values())if(e.ringing())return e;for(Entry e:entries.values())if(e.state()==Call.STATE_ACTIVE)return e;for(Entry e:entries.values())if(e.state()!=Call.STATE_DISCONNECTED)return e;return null;}
    boolean muted(){return muted;}List<CallEndpoint> endpoints(){return endpoints;}CallEndpoint endpoint(){return endpoint;}
    @Override public void onCallAdded(Call call){
        super.onCallAdded(call);Entry entry=new Entry(call);entries.put(entry.token,entry);
        entry.callback=new Call.Callback(){
            @Override public void onStateChanged(Call c,int state){changed(entry);}
            @Override public void onDetailsChanged(Call c,Call.Details details){changed(entry);}
            @Override public void onParentChanged(Call c,Call parent){changed(entry);}
            @Override public void onChildrenChanged(Call c,List<Call> children){changed(entry);}
            @Override public void onConferenceableCallsChanged(Call c,List<Call> calls){changed(entry);}
        };call.registerCallback(entry.callback,main);changed(entry);
        if(!entry.ringing()||PhoneActivity.visible||!getSystemService(NotificationManager.class).areNotificationsEnabled())show(entry.token,false);
    }
    @Override public void onCallRemoved(Call call){super.onCallRemoved(call);Entry removed=null;for(Entry e:entries.values())if(e.call==call){removed=e;break;}
        if(removed!=null){entries.remove(removed.token);call.unregisterCallback(removed.callback);}notification(null);notifyUi();
    }
    private void changed(Entry entry){
        if(entries.get(entry.token)!=entry)return;
        String number=entry.number();if(!Objects.equals(number,entry.lookupNumber)){
            entry.lookupNumber=number;entry.contactName=null;
            if(!number.isBlank()&&checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)contacts.execute(()->{
                String name=null;try(var cursor=getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(RuntimeException ignored){}
                String result=name;main.post(()->{if(entries.get(entry.token)==entry&&number.equals(entry.number())){entry.contactName=result;notification(entry);notifyUi();}});
            });
        }
        notification(entry);notifyUi();
    }
    private void notifyUi(){for(Runnable listener:List.copyOf(listeners))listener.run();}
    @Override public void onBringToForeground(boolean keypad){Entry e=primary();if(e!=null)show(e.token,keypad);}
    void show(String token,boolean keypad){try{startActivity(new Intent(this,InCallActivity.class).putExtra(TOKEN,token).putExtra("keypad",keypad).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException e){Toast.makeText(this,"Tap the call notification to return to your call",Toast.LENGTH_SHORT).show();}}
    @Override public void onAvailableCallEndpointsChanged(List<CallEndpoint> available){endpoints=List.copyOf(available);notifyUi();}
    @Override public void onCallEndpointChanged(CallEndpoint current){endpoint=current;notifyUi();}
    @Override public void onMuteStateChanged(boolean value){muted=value;notifyUi();}
    void route(CallEndpoint target){if(!endpoints.contains(target))return;requestCallEndpointChange(target,getMainExecutor(),new OutcomeReceiver<Void,CallEndpointException>(){
        @Override public void onResult(Void ignored){}
        @Override public void onError(CallEndpointException error){Toast.makeText(SenseInCallService.this,"Audio device unavailable",Toast.LENGTH_SHORT).show();}
    });}
    void answer(String token){Entry e=entry(token);if(e!=null&&e.ringing())e.call.answer(VideoProfile.STATE_AUDIO_ONLY);}
    void end(String token){Entry e=entry(token);if(e==null)return;if(e.ringing())e.call.reject(false,null);else e.call.disconnect();}
    private PendingIntent screenIntent(Entry entry,boolean answer){
        Intent intent=new Intent(this,InCallActivity.class).setAction(answer?ANSWER:Intent.ACTION_VIEW).setData(Uri.parse("sense-call:"+entry.token)).putExtra(TOKEN,entry.token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options=ActivityOptions.makeBasic();options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE,options.toBundle());
    }
    private PendingIntent endIntent(Entry entry){return PendingIntent.getBroadcast(this,0,new Intent(this,CallActionReceiver.class).setAction(END).setData(Uri.parse("sense-call:"+entry.token)).putExtra(TOKEN,entry.token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    // AGP does not associate the indirect InCallService subclass with its manifest service type.
    @android.annotation.SuppressLint("ForegroundServiceType")
    private void notification(Entry entry){
        entry=primary();
        if(entry==null){stopForeground(STOP_FOREGROUND_REMOVE);return;}
        Person person=new Person.Builder().setName(entry.title()).setImportant(true).setKey(entry.token).build();
        PendingIntent content=screenIntent(entry,false),end=endIntent(entry);
        Notification.Builder builder=new Notification.Builder(this,"calls").setSmallIcon(R.drawable.phone_call_status).setContentTitle(entry.title()).setContentText(entry.status()).setContentIntent(content).setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(this,"calls").setSmallIcon(R.drawable.phone_call_status).setContentTitle("Sense Phone").setContentText(entry.status()).build());
        if(entry.ringing())builder.setStyle(Notification.CallStyle.forIncomingCall(person,end,screenIntent(entry,true))).setFullScreenIntent(content,true);
        else builder.setStyle(Notification.CallStyle.forOngoingCall(person,end));
        long connected=entry.call.getDetails().getConnectTimeMillis();if(entry.state()==Call.STATE_ACTIVE&&connected>0)builder.setWhen(connected).setUsesChronometer(true);
        try{startForeground(100,builder.build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);}catch(SecurityException|IllegalArgumentException e){android.util.Log.e("SensePhone","Cannot show call notification",e);show(entry.token,false);}
    }
    @Override public void onDestroy(){for(Entry e:entries.values()){e.call.unregisterCallback(e.callback);}entries.clear();stopForeground(STOP_FOREGROUND_REMOVE);if(instance==this)instance=null;notifyUi();main.removeCallbacksAndMessages(null);contacts.shutdownNow();super.onDestroy();}
}
