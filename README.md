# GraduationAlbum

GraduationAlbum is an Android WebView application that wraps a Cloudflare-hosted Vue website for a graduating class photo album experience.

## Features

- Android WebView host for a Vue.js photo album site
- Native file chooser integration for image uploads
- Local auth token persistence in Android WebView
- Notification support and badge handling
- Admin review dashboard with ordered submissions
- Cloudflare Pages backend with D1 and R2 storage

## Project Structure

- `app/` - Android application module
- `functions/` - backend Cloudflare Functions API
- `src/` - Vue frontend source code
- `gradle/` - Gradle configuration and wrapper

## Usage

1. Open the project in Android Studio
2. Build and run the Android app
3. The app loads the Vue website in an embedded WebView

## Notes

This repository is designed to support the integration of web app functionality with native Android features, including authentication persistence and notifications.
