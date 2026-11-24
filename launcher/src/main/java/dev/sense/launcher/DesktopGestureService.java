package dev.sense.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Optional notification-shade action only; does not retrieve or inspect window contents. */
public final class DesktopGestureService extends AccessibilityService {
    private static DesktopGestureService connected;
    @Override protected void onServiceConnected(){connected=this;}
    static boolean openNotifications(){return connected!=null && connected.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(android.content.Intent intent){if(connected==this)connected=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){if(connected==this)connected=null;super.onDestroy();}
}
