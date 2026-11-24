package dev.sense.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        var state = context.getSharedPreferences("install", 0);
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2) != state.getInt("session", -1)) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            InstallerActivity.confirmation = confirm;
            state.edit().putString("message", "Tap Continue to approve installation.").apply();
        } else {
            var edit = state.edit().putInt("session", -1);
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            boolean existingFramework = state.getInt("index", 0) == 0 && detail != null
                    && detail.contains("INSTALL_FAILED_DUPLICATE_PACKAGE")
                    && detail.contains("static-shared libs");
            if (status == PackageInstaller.STATUS_SUCCESS || existingFramework) {
                int next = state.getInt("index", 0) + 1;
                edit.putInt("index", next).putString("message", next == 8 ? "All eight packages installed." : existingFramework ? "Framework already installed. Android will verify its version and signing certificate when installing the dependent apps." : "Installed. Continue with the next package.");
            } else {
                edit.putString("message", "Installation stopped: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + ". You can retry; installed apps are kept.");
            }
            edit.apply();
            InstallerActivity.confirmation = null;
        }
        context.sendBroadcast(new Intent("dev.sense.installer.UPDATE").setPackage(context.getPackageName()));
    }
}
