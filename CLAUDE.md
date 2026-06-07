# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GraduationAlbum is an Android WebView application that wraps a Cloudflare-hosted Vue.js photo album website. It also includes native photo browsing fragments via the Android Navigation component.

## Build & Run

```bash
# Build the Android app (debug)
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run a single unit test class
./gradlew app:test --tests "com.example.graduationalbum.ExampleUnitTest"

# Clean build
./gradlew clean
```

Open the project in Android Studio and run on an emulator or device (SDK 29+).

## Architecture

### Dual-Mode — WebView + Native Fragments

The app has two parallel navigation paths:

1. **WebView** (main mode): `MainActivity` loads the Vue.js site in a full-screen WebView (`activity_main.xml`). The WebView hosts the primary graduation album experience — user uploads, admin review, and album browsing.

2. **Native fragments** (via Navigation Component): `FirstFragment` fetches photos from the API and displays them in a 2-column grid (`RecyclerView` + `GridLayoutManager`). `SecondFragment` shows a photo detail view using Glide. Navigation graph at `res/navigation/nav_graph.xml`.

### Key Components

| Class | Role |
|---|---|
| `MainActivity` | Core activity — sets up WebView, handles file chooser, auth token persistence (SharedPreferences), notification badge, WebView back-button navigation |
| `WebAppInterface` | `@JavascriptInterface` bridge exposed as `window.AndroidApp` — notification display, badge count, auth token save/clear |
| `NotificationHelper` | Creates notification channel ("毕业相册通知") and dispatches system notifications with badge support |
| `FirstFragment` | Photo gallery — fetches from `GET /api/photos` via `ApiClient`, renders photos as Material Cards in RecyclerView |
| `SecondFragment` | Photo detail — receives photo data via navigation arguments, loads image with Glide |
| `ApiClient` | OkHttp + Gson client — fetches photos list, resolves relative URLs against API base URL |
| `Photo` | Data model — id, src, thumb, title, author, classGroup, status, mediaType, duration, createdAt, width, height |
| `PhotoAdapter` | RecyclerView adapter with `OnPhotoClickListener` callback, uses Glide to load thumbnails |

### JavaScript Bridge

The WebView exposes `window.AndroidApp` via `WebAppInterface`:

- `showNotification(title, message)`
- `showEventNotification(title, message, badgeCount)`
- `updateBadgeCount(count)`
- `saveAuthToken(token)` / `clearAuthToken()` — persists to SharedPreferences
- `clearBadge()`
- `getPlatform()` → "Android"

Auth tokens are injected into WebView localStorage (`album:token`) on page load. The app requests `POST_NOTIFICATIONS` permission on Android 13+.

### Configuration

- **App URL**: `https://graduationalbum-robotics2202.pages.dev/` (set in `strings.xml`)
- **API base URL**: `https://graduationalbum-robotics2202.pages.dev` (set in `strings.xml`)
- **Min SDK**: 29, **Target SDK**: 36, **Compile SDK**: 36
- **Build tools**: AGP 9.2.1, Gradle 9.4.1
- **Version catalog**: `gradle/libs.versions.toml`

### Dependencies

- Jetpack: AppCompat, Navigation (fragment+ui), ConstraintLayout, ViewBinding
- Material 3 (`com.google.android.material`)
- OkHttp 4.11.0 (HTTP client), Gson 2.10.1 (JSON parsing)
- Glide 4.15.1 (image loading)
- RecyclerView 1.3.1

### Theme & Layout

- Material 3 theme with night-mode variant (`values-night/themes.xml`)
- Multiple screen width qualifiers (`w1240dp`, `w600dp`, `land`)
- Adaptive icon with mipmap resources across all densities
- Badge view drawn as overlay on WebView (red circle drawable)

## Project Structure

- `app/` — Android application module (Java)
- `functions/` — Backend Cloudflare Functions API (empty/unpopulated)
- `src/` — Vue frontend source code (deployed to Cloudflare Pages; `src/public/` has PWA icons and `_routes.json`)
- `gradle/libs.versions.toml` — Version catalog
- `gradlew` / `gradlew.bat` — Gradle wrapper (Gradle 9.4.1)
