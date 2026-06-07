package com.example.graduationalbum;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 前台通知服务 — 原生直连服务器处理所有通知。
 *
 * 完全不依赖 WebView / Vue，原生直接：
 * 1. WebSocket 连 Worker，实时接收推送
 * 2. OkHttp 每 15s 轮询 /api/notifications（WS 断开时兜底）
 * 3. 收到通知 → 弹系统通知 + 实时注入 WebView 更新红点/列表
 *
 * 启动：MainActivity.onCreate()
 * 停止：完全退出 APP
 */
public class WebSocketForegroundService extends Service {

    private static final String TAG = "NotifyService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "graduation_album_service";
    private static final long TOKEN_RETRY_MS = 5_000;
    private static final long POLL_INTERVAL_MS = 15_000;

    /** Activity 在前台时设置此回调，用于实时注入 WebView */
    public static Consumer<JSONObject> onEventCallback;

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private String currentToken;
    private android.os.Handler handler;
    private boolean isRunning = false;
    private int lastUnreadCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureServiceChannel(this);
        startForeground(NOTIFICATION_ID, NotificationHelper.buildServiceNotification(this));

        httpClient = new OkHttpClient.Builder().build();
        handler = new android.os.Handler(getMainLooper());
        isRunning = true;

        tryConnect();
    }

    // ══════════════════════════════════════════════
    // Token & 连接管理
    // ══════════════════════════════════════════════

    private void tryConnect() {
        currentToken = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(MainActivity.KEY_AUTH_TOKEN, null);

        if (currentToken == null || currentToken.isEmpty()) {
            Log.d(TAG, "无 token，5s 后重试");
            if (isRunning) handler.postDelayed(this::tryConnect, TOKEN_RETRY_MS);
            return;
        }

        // 同时启动 WS + HTTP 轮询
        connectWebSocket();
        startPolling();
    }

    private void connectWebSocket() {
        disconnectWebSocket();
        String wsUrl = getString(R.string.ws_url) + "?token=" + currentToken;

        Request req = new Request.Builder().url(wsUrl).build();
        httpClient.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, okhttp3.Response response) {
                Log.d(TAG, "WS 已连接");
                webSocket = ws;
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    handleEvent(msg);
                } catch (Exception e) {
                    Log.e(TAG, "WS 消息解析失败", e);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, okhttp3.Response response) {
                Log.e(TAG, "WS 断开: " + (t != null ? t.getMessage() : "未知"));
                webSocket = null;
                // HTTP 轮询仍在继续，不丢通知
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                webSocket = null;
            }
        });
    }

    private void disconnectWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Service stopping");
            webSocket = null;
        }
    }

    // ══════════════════════════════════════════════
    // HTTP 轮询 /api/notifications（原生直连）
    // ══════════════════════════════════════════════

    private void startPolling() {
        if (!isRunning) return;
        pollNotifications();
        handler.postDelayed(this::startPolling, POLL_INTERVAL_MS);
    }

    private void pollNotifications() {
        if (currentToken == null || currentToken.isEmpty()) return;

        String url = getString(R.string.api_base_url) + "/api/notifications";
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + currentToken)
                .build();

        httpClient.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // HTTP 失败，WS 可能还活着，忽略
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject data = new JSONObject(body);
                    int unread = data.optInt("unread", 0);
                    JSONArray notifications = data.optJSONArray("notifications");

                    // 1. 新通知 → 弹系统通知（带上导航目标）
                    if (unread > lastUnreadCount && notifications != null && notifications.length() > 0) {
                        JSONObject latest = notifications.getJSONObject(0);
                        if (!latest.optBoolean("seen", false)) {
                            String title = latest.optString("title", "毕业相册");
                            String bodyText = latest.optString("body", "你有新消息");
                            String nType = latest.optString("type", "");
                            // 审核类通知跳转 /admin，其余跳转 /feed
                            String navAction = "review".equals(nType) ? "navigate_admin" : "navigate_feed";
                            NotificationHelper.showNotification(
                                WebSocketForegroundService.this, title, bodyText, unread, navAction);
                        }
                    }
                    lastUnreadCount = unread;

                    // 2. 注入 WebView — 无论 Vue 是否连了 WS，都覆盖更新
                    injectToWebView(unread, notifications);

                } catch (Exception e) {
                    Log.e(TAG, "轮询解析失败", e);
                } finally {
                    response.close();
                }
            }
        });
    }

    // ══════════════════════════════════════════════
    // 实时推送 WebSocket 事件处理
    // ══════════════════════════════════════════════

    private void handleEvent(JSONObject msg) throws Exception {
        String type = msg.optString("type");

        // 全部事件都尝试注入 WebView
        relayToWebView(msg);

        if ("notification".equals(type)) {
            String title = msg.optString("title", "毕业相册");
            String body = msg.optString("message", "你有新消息");
            int badgeCount = msg.optInt("badgeCount", 0);
            // 审核通知跳 /admin，其他跳 /feed
            String navAction = (title != null && title.contains("审核")) ? "navigate_admin" : "navigate_feed";
            NotificationHelper.showNotification(this, title, body, badgeCount, navAction);

            // WS 消息到了，触发一次 HTTP 轮询获取完整列表
            pollNotifications();
        }
    }

    // ══════════════════════════════════════════════
    // 注入 WebView — 原生直连通知数据驱动 Vue UI
    // ══════════════════════════════════════════════

    private void injectToWebView(int unread, JSONArray notifications) {
        if (onEventCallback == null) return;

        // 构造一个标准化的通知事件，Vue 端 handleWebSocketEvent 就能处理
        try {
            JSONObject event = new JSONObject();
            event.put("type", "native_notifications");
            event.put("unread", unread);
            event.put("notifications", notifications != null ? notifications : new JSONArray());
            relayToWebView(event);
        } catch (Exception e) {
            Log.e(TAG, "注入失败", e);
        }
    }

    private void relayToWebView(JSONObject event) {
        if (onEventCallback != null) {
            try {
                onEventCallback.accept(event);
            } catch (Exception e) {
                Log.e(TAG, "relayToWebView 失败", e);
            }
        }
    }

    // ══════════════════════════════════════════════
    // 生命周期 & 静态工具
    // ══════════════════════════════════════════════

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("UPDATE_TOKEN".equals(action)) {
                String token = intent.getStringExtra("token");
                this.currentToken = token;
                disconnectWebSocket();
                connectWebSocket();
                // token 变了，重新开始轮询（旧的也可能还在跑，但 header 会更新）
            } else if ("STOP".equals(action)) {
                isRunning = false;
                handler.removeCallbacksAndMessages(null);
                disconnectWebSocket();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        disconnectWebSocket();
        NotificationHelper.cancelServiceNotification(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    public static void updateToken(Context context, String token) {
        Intent i = new Intent(context, WebSocketForegroundService.class);
        i.setAction("UPDATE_TOKEN");
        i.putExtra("token", token);
        context.startService(i);
    }

    public static void start(Context context) {
        Intent i = new Intent(context, WebSocketForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(i);
        else
            context.startService(i);
    }

    public static void stop(Context context) {
        Intent i = new Intent(context, WebSocketForegroundService.class);
        i.setAction("STOP");
        context.startService(i);
    }
}
