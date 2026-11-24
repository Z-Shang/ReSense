package dev.sense.launcher;

import android.app.AlertDialog;

final class DesktopGestures {
    static final String[] ACTIONS={"None","Open app drawer","Open notifications"};
    static int action(MainActivity activity,boolean up){return activity.getSharedPreferences("gestures",0).getInt(up?"up":"down",up?1:2);}
    static void show(MainActivity activity){activity.openLauncherSettings("gestures");}
}
