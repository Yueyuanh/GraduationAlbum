package com.example.graduationalbum;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket client for receiving real-time events from the server.
 * Uses OkHttp's built-in WebSocket support (already a project dependency).
 * Auto-reconnects with exponential backoff (3s → 6s → 12s → 24s, capped at 30s).
 */
public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    private static final long BASE_RECONNECT_DELAY_MS = 3000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private final OkHttpClient client;
    private final String wsEndpoint;
    private final WebSocketCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private String token;
    private boolean shouldReconnect = true;
    private long reconnectDelay = BASE_RECONNECT_DELAY_MS;

    public interface WebSocketCallback {
        void onMessage(JSONObject message);
        void onConnectionStateChanged(boolean connected);
    }

    public WebSocketClient(String wsEndpoint, String token, WebSocketCallback callback) {
        this.wsEndpoint = wsEndpoint;
        this.token = token;
        this.callback = callback;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout for long-lived WS
                .build();
    }

    /** Open the WebSocket connection. */
    public void connect() {
        shouldReconnect = true;
        String url = wsEndpoint + "?token=" + (token != null ? token : "");
        Request request = new Request.Builder().url(url).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket connected");
                webSocket = ws;
                reconnectDelay = BASE_RECONNECT_DELAY_MS;   // reset backoff on success
                mainHandler.post(() -> callback.onConnectionStateChanged(true));
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    mainHandler.post(() -> callback.onMessage(json));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse WebSocket message", e);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + (t != null ? t.getMessage() : "unknown"), t);
                mainHandler.post(() -> callback.onConnectionStateChanged(false));
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed (" + code + "): " + reason);
                mainHandler.post(() -> callback.onConnectionStateChanged(false));
                scheduleReconnect();
            }
        });
    }

    /** Gracefully close the connection and stop reconnection. */
    public void disconnect() {
        shouldReconnect = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
            webSocket = null;
        }
    }

    /** Update the auth token and reconnect with the new credential. */
    public void updateToken(String newToken) {
        this.token = newToken;
        disconnect();
        connect();
    }

    // ---- private helpers ----

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        mainHandler.postDelayed(() -> {
            if (shouldReconnect) connect();
        }, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MS);
    }
}
