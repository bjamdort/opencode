package dev.bjamdort.opencode.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class PushService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        if (PushSettings.enabled(this)) PushSettings.saveToken(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (!PushSettings.enabled(this)) {
            Log.i("OpenCodePush", "Ignored push: opted out");
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
            Log.i("OpenCodePush", "Ignored push: Android notifications blocked");
            return;
        }
        Map<String, String> data = message.getData();
        String target = PushRoute.href(data.get("server_url"), data.get("session_id"));
        if (target == null) {
            Log.w("OpenCodePush", "Ignored push: invalid session target");
            return;
        }
        PushSettings.channel(this);
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.parse(target))
                .putExtra("server_url", data.get("server_url")).putExtra("session_id", data.get("session_id"));
        PendingIntent tap = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, PushSettings.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(data.getOrDefault("notification_title", getString(R.string.push_default_title)))
                .setContentText(data.getOrDefault("notification_body", getString(R.string.push_default_body)))
                .setContentIntent(tap).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build();
        manager.notify(target + ":" + data.getOrDefault("kind", "update"), 0, notification);
        Log.i("OpenCodePush", "Posted push notification");
    }
}
