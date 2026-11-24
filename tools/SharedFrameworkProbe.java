package dev.sense.framework.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

/** Built without framework classes: verifies the installed client's real runtime. */
public final class SharedFrameworkProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        try {
            Context target = getTargetContext();
            var info = target.getPackageManager().getApplicationInfo(target.getPackageName(),
                    PackageManager.GET_SHARED_LIBRARY_FILES);
            if (info.sharedLibraryFiles == null || info.sharedLibraryFiles.length == 0)
                throw new AssertionError("No installed shared library on client");
            ClassLoader loader = target.getClassLoader();
            Class<?> resources = Class.forName("dev.sense.framework.R$drawable", true, loader);
            int drawable = resources.getField("sense_common_popupmenu").getInt(null);
            if ((drawable >>> 24) == 0 || (drawable >>> 24) == 0x7f)
                throw new AssertionError("Shared resource IDs were not remapped: " + drawable);
            Resources res = target.getResources();
            if (!"dev.sense.framework".equals(res.getResourcePackageName(drawable)))
                throw new AssertionError("Drawable resolved outside installed framework");
            if (!(res.getDrawable(drawable, target.getTheme()) instanceof android.graphics.drawable.NinePatchDrawable))
                throw new AssertionError("Shared HTC popup lost nine-patch stretch regions/padding");
            int theme = Class.forName("dev.sense.framework.R$style", true, loader)
                    .getField("SenseFramework_Light").getInt(null);
            Throwable[] failure = {null};
            runOnMainSync(() -> {
                try {
                    Context themed = new ContextThemeWrapper(target, theme);
                    Class.forName("dev.sense.framework.SenseSearchBox", true, loader)
                            .getConstructor(Context.class, String.class, String.class)
                            .newInstance(themed, "Search", "Clear");
                    Class.forName("dev.sense.framework.SenseHeaderIconButton", true, loader)
                            .getMethod("overflow", Context.class, String.class)
                            .invoke(null, themed, "More");
                } catch (Throwable e) { failure[0] = e; }
            });
            if (failure[0] != null) throw new AssertionError("Shared controls failed", failure[0]);
            Intent launch = target.getPackageManager().getLaunchIntentForPackage(target.getPackageName());
            if (launch == null) throw new AssertionError("No launch activity");
            activity = startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            result.putString("result", target.getPackageName() + ": shared classes, remapped HTC drawable, theme, native controls and launch activity PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("error", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
