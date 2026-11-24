package dev.sense.lockscreen;

import android.app.*;
import android.app.StatusBarManager;
import android.content.*;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import dev.sense.framework.*;
import java.util.concurrent.*;

public final class LockScreenSettingsActivity extends Activity {
    private Switch afterUnlock;private TextView afterUnlockStatus;private boolean updatingToggle;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void action(LinearLayout content,String label,Runnable task){Button button=new SensePreferences.ButtonControl(this);button.setText(label);button.setOnClickListener(v->task.run());content.addView(button,new LinearLayout.LayoutParams(-1,dp(52)));}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xffededed);SenseHeaderBar header=new SenseHeaderBar(this);header.setTitle("Sense Lock Screen");header.setAction("Done","Close settings",this::finish);root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(12),dp(12),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView explanation=new TextView(this);explanation.setText("The classic HTC ring and four shortcuts. Pull the ring upward to unlock, or drag a shortcut into the ring to open it. Android still handles your PIN, fingerprint and face unlock.");explanation.setTextColor(0xff222222);explanation.setTextSize(17);explanation.setPadding(0,0,0,dp(16));content.addView(explanation);
        action(content,"Open HTC lock screen",()->startActivity(new Intent(this,LockScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Switch capture=new SensePreferences.Toggle(this);capture.setText("Allow screenshots and screen recording");capture.setTextColor(0xff222222);
        capture.setChecked(getSharedPreferences("lockscreen",0).getBoolean(LockScreenActivity.ALLOW_SCREEN_CAPTURE,false));
        capture.setOnCheckedChangeListener((button,checked)->getSharedPreferences("lockscreen",0).edit().putBoolean(LockScreenActivity.ALLOW_SCREEN_CAPTURE,checked).apply());content.addView(capture);
        TextView captureInfo=new TextView(this);captureInfo.setText("Applies to the HTC lock screen. Changes take effect when it opens or resumes.");captureInfo.setTextColor(0xff555555);captureInfo.setTextSize(14);captureInfo.setPadding(dp(8),dp(4),dp(8),dp(12));content.addView(captureInfo);
        afterUnlock=new SensePreferences.Toggle(this);afterUnlock.setText("Show after system unlock");afterUnlock.setTextColor(0xff222222);afterUnlock.setChecked(AfterUnlockService.enabled(this));afterUnlock.setOnCheckedChangeListener((button,checked)->{if(updatingToggle)return;getSharedPreferences("lockscreen",0).edit().putBoolean(AfterUnlockService.ENABLED,checked).apply();if(checked&&!Settings.canDrawOverlays(this))openOverlayAccess();else updateAfterUnlock();});content.addView(afterUnlock);
        afterUnlockStatus=new TextView(this);afterUnlockStatus.setTextColor(0xff555555);afterUnlockStatus.setTextSize(14);afterUnlockStatus.setPadding(dp(8),dp(4),dp(8),dp(12));content.addView(afterUnlockStatus);
        action(content,"Display over other apps access",this::openOverlayAccess);
        action(content,"Add Quick Settings tile",()->getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this,LockScreenTile.class),"HTC lock screen",Icon.createWithResource(this,R.drawable.ls_tile),getMainExecutor(),result->{if(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED||result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)Toast.makeText(this,"Use the HTC lock screen tile to open the ring screen.",Toast.LENGTH_LONG).show();}));
        action(content,"Choose system wallpaper",()->{try{startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));}catch(ActivityNotFoundException unavailable){Toast.makeText(this,"Choose wallpaper in Android settings.",Toast.LENGTH_LONG).show();}});
        TextView wallpaperInfo=new TextView(this);wallpaperInfo.setText("Uses your current system wallpaper, including live wallpapers.");wallpaperInfo.setTextColor(0xff555555);wallpaperInfo.setTextSize(14);wallpaperInfo.setPadding(dp(8),dp(4),dp(8),dp(12));content.addView(wallpaperInfo);
        TextView title=new TextView(this);title.setText("Lock screen shortcuts");title.setTextSize(20);title.setTextColor(0xff222222);title.setPadding(0,dp(20),0,dp(8));content.addView(title);
        for(int i=0;i<4;i++){final int slot=i;action(content,"Shortcut "+(i+1)+": "+LockShortcuts.name(this,i),()->choose(slot));}
        action(content,"Restore original shortcuts",()->{var edit=getSharedPreferences("lockscreen",0).edit();for(int i=0;i<4;i++)edit.remove("shortcut:"+i);edit.apply();recreate();});
        TextView note=new TextView(this);note.setText("Show after system unlock adds the HTC ring screen after Android unlocks. It needs display-over-other-apps access and an ongoing background notification. Android authentication remains first; this is not a replacement for system security.");note.setTextColor(0xff555555);note.setTextSize(15);note.setPadding(0,dp(20),0,dp(12));content.addView(note);action(content,"Android screen lock settings",()->startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        root.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);
    }
    private void openOverlayAccess(){try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException error){Toast.makeText(this,"Open Android app settings to allow display over other apps.",Toast.LENGTH_LONG).show();}}
    private void updateAfterUnlock(){if(afterUnlock==null)return;boolean enabled=AfterUnlockService.enabled(this);updatingToggle=true;afterUnlock.setChecked(enabled);updatingToggle=false;
        if(!enabled){AfterUnlockService.reconcile(this);afterUnlockStatus.setText("Off · open the HTC screen manually or from Quick Settings.");return;}
        if(!Settings.canDrawOverlays(this)){AfterUnlockService.reconcile(this);afterUnlockStatus.setText("Permission needed · allow display over other apps to activate.");return;}
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("lockscreen",0).getBoolean("askedAfterUnlockNotifications",false)){getSharedPreferences("lockscreen",0).edit().putBoolean("askedAfterUnlockNotifications",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},22);}
        try{AfterUnlockService.reconcile(this);afterUnlockStatus.setText("Enabled · HTC appears after Android unlocks. Turn off here or from its notification.");}catch(IllegalStateException|SecurityException failure){afterUnlockStatus.setText("Could not start after-unlock mode. Reopen this screen to try again.");}
    }
    @Override protected void onResume(){super.onResume();updateAfterUnlock();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==22)updateAfterUnlock();}
    private void choose(int slot){loader.execute(()->{var apps=LockShortcuts.apps(this);runOnUiThread(()->{if(isDestroyed()||isFinishing())return;new AlertDialog.Builder(this).setTitle("Choose shortcut "+(slot+1)).setItems(apps.stream().map(LockShortcuts.App::title).toArray(String[]::new),(dialog,which)->{getSharedPreferences("lockscreen",0).edit().putString("shortcut:"+slot,apps.get(which).component()).apply();recreate();}).setNegativeButton("Cancel",null).show();});});}
    @Override protected void onDestroy(){loader.shutdownNow();super.onDestroy();}
}
