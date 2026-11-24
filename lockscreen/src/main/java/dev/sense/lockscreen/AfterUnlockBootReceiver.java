package dev.sense.lockscreen;
import android.content.*;

/** Restore only an explicitly enabled feature after boot or an app update. */
public final class AfterUnlockBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;try{AfterUnlockService.reconcile(c);}catch(IllegalStateException|SecurityException blocked){android.util.Log.w("SenseAfterUnlock","Open Sense Lock Screen settings to resume after-unlock mode",blocked);}}
}
