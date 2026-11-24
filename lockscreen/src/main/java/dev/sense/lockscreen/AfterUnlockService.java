package dev.sense.lockscreen;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;

/** Optional foreground listener for protected screen-off and user-present broadcasts only. */
public final class AfterUnlockService extends Service {
    static final String ENABLED="afterSystemUnlock";
    private static AfterUnlockService connected;
    private boolean registered;private AfterUnlockPolicy policy;
    static boolean listening(){return connected!=null&&connected.registered;}
    static boolean armed(){return connected!=null&&connected.policy.armed();}
    static boolean enabled(Context c){return c.getSharedPreferences("lockscreen",0).getBoolean(ENABLED,false);}
    static void reconcile(Context c){
        Intent service=new Intent(c,AfterUnlockService.class);
        if(enabled(c)&&Settings.canDrawOverlays(c))c.startForegroundService(service);else c.stopService(service);
    }
    static void suppressCurrentUnlock(){if(connected!=null)connected.policy.suppress();}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
        android.util.Log.d("SenseAfterUnlock","Received "+intent.getAction());
        if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){policy.screenOff();return;}
        if(!Intent.ACTION_USER_PRESENT.equals(intent.getAction()))return;
        boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked()||getSystemService(KeyguardManager.class).isDeviceLocked();
        if(policy.userPresent(enabled(c),Settings.canDrawOverlays(c),getSystemService(PowerManager.class).isInteractive(),locked,LockScreenActivity.isShowing())){
            try{startActivity(new Intent(c,LockScreenActivity.class).putExtra("afterSystemUnlock",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}catch(SecurityException|ActivityNotFoundException failure){android.util.Log.w("SenseAfterUnlock","Android did not allow the HTC screen to open",failure);}
        }
    }};
    @Override public void onCreate(){super.onCreate();KeyguardManager keyguard=getSystemService(KeyguardManager.class);policy=new AfterUnlockPolicy(keyguard.isKeyguardLocked()||!getSystemService(PowerManager.class).isInteractive());connected=this;
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("after-unlock","HTC screen after unlock",NotificationManager.IMPORTANCE_LOW));
        PendingIntent settings=PendingIntent.getActivity(this,10,new Intent(this,LockScreenSettingsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disable=PendingIntent.getService(this,11,new Intent(this,AfterUnlockService.class).setAction("DISABLE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"after-unlock").setSmallIcon(R.drawable.ls_tile).setContentTitle("HTC screen after system unlock").setContentText("Enabled · tap to configure").setContentIntent(settings).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Turn off",disable).build()).build();
        startForeground(21,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        // Both actions are protected broadcasts; USER_PRESENT can come from SystemUI
        // rather than uid 1000, so the runtime receiver must accept that sender.
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);registered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"DISABLE".equals(intent.getAction()))getSharedPreferences("lockscreen",0).edit().putBoolean(ENABLED,false).apply();
        if(!enabled(this)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}return START_STICKY;
    }
    @Override public void onDestroy(){if(registered)unregisterReceiver(receiver);if(connected==this)connected=null;LockScreenActivity.closeAutomatic();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
