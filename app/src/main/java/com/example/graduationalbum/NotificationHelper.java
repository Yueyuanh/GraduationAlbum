package com.example.graduationalbum;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {
    private static final String CHANNEL_ID = "graduation_album_notifications";
    private static final String CHANNEL_NAME = "毕业相册通知";
    private static final String CHANNEL_DESCRIPTION = "用于毕业相册应用的系统通知";

    private static final String SERVICE_CHANNEL_ID = "graduation_album_service";
    private static final String SERVICE_CHANNEL_NAME = "毕业相册后台服务";
    private static final String SERVICE_CHANNEL_DESCRIPTION = "保持 WebSocket 长连接的后台服务";

    /** 确保事件通知渠道存在（高优先级，弹出横幅） */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        // 先删除旧渠道（如果存在），确保新级别生效
        // IMPORTANCE_DEFAULT 不会弹出横幅，必须 IMPORTANCE_HIGH
        NotificationChannel old = manager.getNotificationChannel(CHANNEL_ID);
        if (old != null && old.getImportance() < NotificationManager.IMPORTANCE_HIGH) {
            manager.deleteNotificationChannel(CHANNEL_ID);
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setBypassDnd(false);
            manager.createNotificationChannel(channel);
        }
    }

    /** 确保服务常驻通知渠道存在（低优先级，不打扰用户） */
    public static void ensureServiceChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = manager.getNotificationChannel(SERVICE_CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(SERVICE_CHANNEL_ID, SERVICE_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(SERVICE_CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    /** 构建前台服务的常驻通知（显示服务运行状态） */
    public static Notification buildServiceNotification(Context context) {
        return new NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("毕业相册")
                .setContentText("该软件正在运行")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }

    /** 取消前台服务的常驻通知 */
    public static void cancelServiceNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(1001);
    }

    public static void showNotification(Context context, String title, String body) {
        showNotification(context, title, body, 0, "navigate_feed");
    }

    public static void showNotification(Context context, String title, String body, int badgeCount) {
        showNotification(context, title, body, badgeCount, "navigate_feed");
    }

    /** 完整参数：支持自定义点击跳转目标 */
    public static void showNotification(Context context, String title, String body, int badgeCount, String navAction) {
        ensureChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(navAction != null ? navAction : "navigate_feed");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title != null ? title : "毕业相册通知")
                .setContentText(body != null ? body : "你有一条新的消息。")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body != null ? body : "你有一条新的消息。"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(pendingIntent);

        if (badgeCount > 0) {
            builder.setNumber(badgeCount);
        }

        int notifId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        NotificationManagerCompat.from(context).notify(notifId, builder.build());
    }
}
