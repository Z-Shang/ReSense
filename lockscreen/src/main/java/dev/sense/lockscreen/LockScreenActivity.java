package dev.sense.lockscreen;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;

/** HTC interaction over the system keyguard; it never implements or bypasses credentials. */
public final class LockScreenActivity extends Activity {
    static final String ALLOW_SCREEN_CAPTURE="allowScreenCapture";
    private static java.lang.ref.WeakReference<LockScreenActivity> current=new java.lang.ref.WeakReference<>(null);private boolean automatic,showing;
    static boolean isShowing(){var activity=current.get();return activity!=null&&activity.showing;}
    static void closeAutomatic(){var activity=current.get();if(activity!=null&&activity.automatic)activity.finishAndRemoveTask();}
    private LockSurface surface;private boolean unlocking;private UnlockGate gate;
    @Override public void onCreate(Bundle state){super.onCreate(state);automatic=getIntent().getBooleanExtra("afterSystemUnlock",false);setShowWhenLocked(!automatic&&!AfterUnlockService.enabled(this));if(automatic){KeyguardManager keyguard=getSystemService(KeyguardManager.class);if(!AfterUnlockService.enabled(this)||keyguard.isKeyguardLocked()||keyguard.isDeviceLocked()||!getSystemService(android.os.PowerManager.class).isInteractive()){finishAndRemoveTask();return;}}current=new java.lang.ref.WeakReference<>(this);getWindow().setDecorFitsSystemWindows(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);applyCapturePreference();getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setDimAmount(0f);getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);getWindow().setNavigationBarContrastEnforced(false);getWindow().setStatusBarContrastEnforced(false);KeyguardManager manager=getSystemService(KeyguardManager.class);gate=new UnlockGate(new UnlockGate.Keyguard(){public boolean locked(){return manager.isKeyguardLocked()||manager.isDeviceLocked();}public void dismiss(UnlockGate.Callback callback){manager.requestDismissKeyguard(LockScreenActivity.this,new KeyguardManager.KeyguardDismissCallback(){@Override public void onDismissSucceeded(){callback.success();}@Override public void onDismissCancelled(){callback.cancelled();}@Override public void onDismissError(){callback.cancelled();}});}},this::complete,this::reset);surface=new LockSurface(this,this::requestUnlock);setContentView(surface);getWindow().getInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::finish);hideNavigationBar();}
    void requestUnlock(int shortcut){
        if(unlocking)return;AfterUnlockService.suppressCurrentUnlock();unlocking=true;surface.setAuthenticating(true);gate.request(shortcut);
    }

    private void complete(int shortcut){if(isDestroyed()||isFinishing())return;KeyguardManager keyguard=getSystemService(KeyguardManager.class);if(keyguard.isKeyguardLocked()||keyguard.isDeviceLocked()){reset();return;}if(shortcut>=0){Intent target=LockShortcuts.intent(this,shortcut);try{if(target!=null)startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(this,"This shortcut is unavailable. Choose another app in Sense Lock Screen settings.",Toast.LENGTH_LONG).show();reset();return;}}finishAndRemoveTask();}
    private void reset(){if(isDestroyed())return;unlocking=false;surface.setAuthenticating(false);surface.reset(true);hideNavigationBar();}
    private void hideNavigationBar(){if(surface==null||unlocking)return;WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.navigationBars());}}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)hideNavigationBar();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);recreate();}
    private void applyCapturePreference(){if(getSharedPreferences("lockscreen",0).getBoolean(ALLOW_SCREEN_CAPTURE,false))getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    @Override protected void onResume(){super.onResume();applyCapturePreference();}
    @Override protected void onStart(){super.onStart();showing=true;}
    @Override protected void onStop(){showing=false;super.onStop();}
    @Override protected void onDestroy(){if(current.get()==this)current.clear();super.onDestroy();}
    @Override protected void onPause(){if(surface!=null)surface.stopMotion();super.onPause();}
}
