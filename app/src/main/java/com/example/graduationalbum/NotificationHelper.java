package com.example.graduationalbum;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {
    private static final String CHANNEL_ID = "graduation_album_notifications";
    private static final String CHANNEL_NAME = "毕业相册通知";
    private static final String CHANNEL_DESCRIPTION = "用于毕业相册应用的系统通知";

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }
    }

    public static void showNotification(Context context, String title, String body) {
        showNotification(context, title, body, 0);
    }

    public static void showNotification(Context context, String title, String body, int badgeCount) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title != null ? title : "毕业相册通知")
                .setContentText(body != null ? body : "你有一条新的消息。")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL);

        if (badgeCount > 0) {
            builder.setNumber(badgeCount);
        }

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }
}
