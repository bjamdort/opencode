package dev.bjamdort.opencode.remote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONException;
import org.json.JSONObject;

final class PushSettings {
    static final int PERMISSION_REQUEST = 2;
    static final String CHANNEL = "opencode";

    static boolean enabled(Context context) {
        return context.getSharedPreferences("push", Context.MODE_PRIVATE).getBoolean("enabled", false);
    }

    static void saveToken(Context context, String token) {
        context.getSharedPreferences("push", Context.MODE_PRIVATE).edit().putString("token", token).apply();
    }

    static void channel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.push_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static void show(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (FirebaseApp.getApps(activity).isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(R.string.push_title)
                    .setMessage(R.string.push_not_configured)
                    .setPositiveButton(R.string.push_close, null).show();
            return;
        }
        boolean ready = enabled(activity)
                && activity.getSharedPreferences("push", Context.MODE_PRIVATE).contains("token")
                && activity.getSystemService(NotificationManager.class).areNotificationsEnabled();
        new AlertDialog.Builder(activity).setTitle(R.string.push_title)
                .setMessage(ready ? R.string.push_ready : R.string.push_explanation)
                .setPositiveButton(ready ? R.string.push_share : R.string.push_enable, (dialog, which) -> {
                    if (ready) {
                        registration(activity, true);
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
                        return;
                    }
                    enable(activity);
                })
                .setNegativeButton(R.string.push_close, null)
                .setNeutralButton(ready ? R.string.push_disable : R.string.push_android_settings, (dialog, which) -> {
                    if (!ready) {
                        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                        return;
                    }
                    activity.getSharedPreferences("push", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).remove("token").apply();
                    FirebaseMessaging.getInstance().setAutoInitEnabled(false);
                    FirebaseMessaging.getInstance().deleteToken();
                    activity.getSystemService(NotificationManager.class).cancelAll();
                }).show();
    }

    static void enable(Activity activity) {
        activity.getSharedPreferences("push", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        channel(activity);
        FirebaseMessaging.getInstance().setAutoInitEnabled(true);
        Toast.makeText(activity, R.string.push_registering, Toast.LENGTH_SHORT).show();
        registration(activity, false);
    }

    private static void registration(Activity activity, boolean share) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (!enabled(activity)) return;
            saveToken(activity, token);
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (!share) {
                show(activity);
                return;
            }
            try {
                File directory = new File(activity.getCacheDir(), "notifications");
                Files.createDirectories(directory.toPath());
                File file = new File(directory, "opencode-phone-registration.json");
                JSONObject data = new JSONObject().put("format", "opencode-fcm-registration-v1")
                        .put("project_id", FirebaseApp.getInstance().getOptions().getProjectId()).put("token", token);
                Files.write(file.toPath(), data.toString().getBytes(StandardCharsets.UTF_8));
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
                Intent intent = new Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri(activity.getString(R.string.push_share_title), uri));
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.push_share_title)));
            } catch (IOException | JSONException failure) {
                Toast.makeText(activity, R.string.push_share_error, Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(failure -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                Toast.makeText(activity, R.string.push_registration_error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
