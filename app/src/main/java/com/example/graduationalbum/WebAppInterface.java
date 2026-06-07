package com.example.graduationalbum;

import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private final MainActivity activity;

    public WebAppInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void showNotification(String title, String message) {
        NotificationHelper.showNotification(activity, title, message);
    }

    @JavascriptInterface
    public void showEventNotification(String title, String message, int badgeCount) {
        NotificationHelper.showNotification(activity, title, message, badgeCount);
        activity.updateBadgeCount(badgeCount);
    }

    @JavascriptInterface
    public void updateBadgeCount(int count) {
        activity.updateBadgeCount(count);
    }

    @JavascriptInterface
    public void saveAuthToken(String token) {
        activity.saveAuthToken(token);
    }

    @JavascriptInterface
    public void clearAuthToken() {
        activity.clearAuthToken();
    }

    @JavascriptInterface
    public void clearBadge() {
        activity.clearBadge();
    }

    @JavascriptInterface
    public String getPlatform() {
        return "Android";
    }
}
