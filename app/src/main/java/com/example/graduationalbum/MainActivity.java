package com.example.graduationalbum;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.graduationalbum.databinding.ActivityMainBinding;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 2001;
    public static final String PREFS_NAME = "graduation_album_prefs";
    public static final String KEY_AUTH_TOKEN = "auth_token";

    private ActivityMainBinding binding;
    private ValueCallback<Uri[]> filePathCallback;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // 立即请求通知权限（无需延迟）
        requestNotificationPermissionIfNeeded();
        setupWebView(binding.webView);

        // 启动后台 WebSocket 服务（独立于 Activity 生命周期）
        WebSocketForegroundService.start(this);
        
        // 处理从通知栏点击进来的 Intent
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Activity 在前台 → 注册事件回调，Service 会把事件转发给 WebView
        WebSocketForegroundService.onEventCallback = this::handleServiceEvent;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Activity 不在前台 → 移除回调，Service 仍然自己处理通知
        WebSocketForegroundService.onEventCallback = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    /** 收到 Service 转发的 WebSocket 事件 → 注入 WebView */
    private void handleServiceEvent(JSONObject event) {
        if (binding == null || binding.webView == null) return;
        String safeJson = JSONObject.quote(event.toString());
        binding.webView.evaluateJavascript(
                "try { window.handleWebSocketEvent(" + safeJson + "); } catch (e) {}",
                null
        );
    }

    // ---- 通知权限 ----

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // 立即请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }

    /** 处理从通知栏点击进来的 Intent */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (binding == null || binding.webView == null) return;
        binding.webView.postDelayed(() -> {
            String js;
            if ("navigate_admin".equals(action)) {
                js = "if (window.__router) window.__router.push('/admin');";
            } else {
                // 默认跳转到动态页
                js = "if (window.__router) window.__router.push('/feed');";
            }
            binding.webView.evaluateJavascript(js, null);
        }, 500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationIntent(intent);
    }

    // ---- WebView ----

    private void setupWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // 禁用 WebView 强制深色模式（国产 ROM 默认开启，导致颜色反转）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webSettings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webSettings.setAlgorithmicDarkeningAllowed(false);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectStoredToken(view);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.progressBar.setProgress(newProgress);
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (request.getOrigin() != null && request.getOrigin().toString().startsWith(getString(R.string.web_app_url))) {
                    request.grant(request.getResources());
                } else {
                    request.deny();
                }
            }
        });
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidApp");
        webView.loadUrl(getString(R.string.web_app_url));
    }

    // ---- Badge 角标（通知栏图标右上角数字） ----

    public void updateBadgeCount(int count) {
        if (binding == null) return;
        if (count > 0) {
            binding.badgeView.setVisibility(View.VISIBLE);
            binding.badgeView.setText(String.valueOf(count));
        } else {
            binding.badgeView.setVisibility(View.GONE);
        }
    }

    public void clearBadge() {
        updateBadgeCount(0);
    }

    // ---- Token 管理（与后台 Service 同步） ----

    public void saveAuthToken(String token) {
        if (token == null || token.isEmpty() || prefs == null) return;
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
        // 同步到后台 Service（无论 Activity 是否在前台）
        WebSocketForegroundService.updateToken(this, token);
    }

    public void clearAuthToken() {
        if (prefs == null) return;
        prefs.edit().remove(KEY_AUTH_TOKEN).apply();
        WebSocketForegroundService.updateToken(this, null);
    }

    private void injectStoredToken(WebView webView) {
        if (prefs == null) return;
        String nativeToken = prefs.getString(KEY_AUTH_TOKEN, null);

        // 注入一段 JS，同时完成三件事：
        // 1. 将 native 的 token 写入 localStorage（覆盖可能过期的值）
        // 2. 从 localStorage 反读 token 并通过一个全局变量暴露
        // 3. 如果 localStorage 有 token 而 native 没有，触发 auth-changed 事件让 Vue 连接 WebSocket
        webView.evaluateJavascript(
            "(function(){\n" +
            "  var nativeToken = " + (nativeToken != null ? JSONObject.quote(nativeToken) : "null") + ";\n" +
            "  var lsToken = localStorage.getItem('album:token');\n" +
            "  // 写：native 有 token 就写入 localStorage\n" +
            "  if (nativeToken) { localStorage.setItem('album:token', nativeToken); }\n" +
            "  // 读：如果 localStorage 有 token 但 native 没有 -> 暴露给 native 侧\n" +
            "  var effectiveToken = nativeToken || lsToken;\n" +
            "  window.__albumToken = effectiveToken || '';\n" +
            "  // 触发 auth-changed -> Vue 会调用 connectStandalone()\n" +
            "  window.dispatchEvent(new CustomEvent('auth-changed'));\n" +
            "  return effectiveToken || '';\n" +
            "})()",
            value -> {
                if (value == null || value.isEmpty() || "\"\"".equals(value) || "null".equals(value)) return;
                String webToken = value;
                if (webToken.startsWith("\"") && webToken.endsWith("\"")) {
                    webToken = webToken.substring(1, webToken.length() - 1).replace("\\\"", "\"");
                }
                if (!webToken.isEmpty() && !webToken.equals(nativeToken)) {
                    Log.d("MainActivity", "Syncing token from WebView to native/WebSocket");
                    prefs.edit().putString(KEY_AUTH_TOKEN, webToken).apply();
                    WebSocketForegroundService.updateToken(this, webToken);
                }
            }
        );
    }

    // ---- 文件选择 + 返回键 ----

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST_CODE || filePathCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
