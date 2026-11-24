package dev.sense.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import org.json.*;

/** Self-contained bootstrap: the framework must not be installed to open this app. */
public final class InstallerActivity extends Activity {
    static Intent confirmation;
    private TextView status;
    private Button install;
    private static volatile boolean preparing;
    private android.content.SharedPreferences state;
    private JSONArray packages;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        state = getSharedPreferences("install", 0);
        try (InputStream input = getAssets().open("suite/manifest.json")) {
            packages = new JSONObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("packages");
        } catch (Exception e) { throw new IllegalStateException("Invalid suite payload", e); }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom); return insets;
        });
        TextView title = new TextView(this); title.setText("Sense Installer"); title.setTextSize(26); root.addView(title);
        TextView notes = new TextView(this);
        notes.setText("Framework • Weather services • Launcher • Weather • Phone • People • Messages • Lock screen\n\nInstall all eight packages from this build in order. Android asks you to approve each package. This does not change your default apps.\n");
        root.addView(notes);
        status = new TextView(this); root.addView(status);
        install = new Button(this); install.setOnClickListener(v -> proceed()); root.addView(install);
        Button stop = new Button(this); stop.setText("Stop / reset installation");
        stop.setOnClickListener(v -> {
            if (preparing) return;
            int id = state.getInt("session", -1);
            if (id >= 0) try { getPackageManager().getPackageInstaller().abandonSession(id); } catch (Exception ignored) {}
            confirmation = null;
            state.edit().clear().apply(); render();
        }); root.addView(stop);
        setContentView(root);
        registerReceiver(updates, new IntentFilter("dev.sense.installer.UPDATE"), RECEIVER_NOT_EXPORTED);
    }
    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onDestroy() { unregisterReceiver(updates); super.onDestroy(); }
    private void render() {
        int index = state.getInt("index", 0);
        status.setText(state.getString("message", "Ready to install.") + "\nCompleted " + index + " / 8");
        install.setText(index == 8 ? "Installed" : "Continue");
        install.setEnabled(!preparing && index < 8);
    }
    private void proceed() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); return;
        }
        if (confirmation != null) { startActivity(confirmation); return; }
        int old = state.getInt("session", -1);
        if (old >= 0) {
            // A lost confirmation after process death can safely be retried from this package.
            try { getPackageManager().getPackageInstaller().abandonSession(old); } catch (Exception ignored) {}
            state.edit().putInt("session", -1).apply();
        }
        preparing = true; render();
        new Thread(() -> stage(), "sense-suite-stage").start();
    }
    private void stage() {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            JSONObject item = packages.getJSONObject(state.getInt("index", 0));
            var params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(item.getLong("size"));
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE);
            id = installer.createSession(params);
            state.edit().putInt("session", id).putString("message", "Preparing " + item.getString("module") + "…").commit();
            try (PackageInstaller.Session session = installer.openSession(id)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream input = getAssets().open("suite/" + item.getString("file"));
                     OutputStream output = session.openWrite("base.apk", 0, item.getLong("size"))) {
                    byte[] bytes = new byte[65536]; int count;
                    while ((count = input.read(bytes)) != -1) { output.write(bytes, 0, count); digest.update(bytes, 0, count); total += count; }
                    session.fsync(output);
                }
                StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
                if (total != item.getLong("size") || !hex.toString().equals(item.getString("sha256"))) throw new IOException("Payload checksum mismatch");
                Intent result = new Intent(this, InstallReceiver.class).setAction("dev.sense.installer.RESULT." + id);
                PendingIntent pending = PendingIntent.getBroadcast(this, id, result, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                session.commit(pending.getIntentSender());
            }
        } catch (Exception e) {
            if (id >= 0) try { installer.abandonSession(id); } catch (Exception ignored) {}
            state.edit().putInt("session", -1).putString("message", "Could not install: " + e.getMessage()).apply();
        } finally {
            preparing = false; runOnUiThread(this::render);
        }
    }
}
