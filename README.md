# NexBrowser

[![Build APK](https://github.com/iNexunx/NexBrowser/actions/workflows/build.yml/badge.svg)](https://github.com/iNexunx/NexBrowser/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Ultra-lightweight, fast browser for Android built with Jetpack Compose + Material 3.
Target: **2–4 MB APK** with adblocking, YouTube download, incognito, and
persistent tabs. Runs from **Android 6 (API 23) to Android 17 (API 37)**.

## Features

- **Fast & light** — WebView-based, Compose M3 UI, R8-optimized, low memory.
- **Ad blocking** — EasyList/EasyPrivacy engine (domain + keyword + exceptions),
  cosmetic CSS injection, auto-updates filter lists.
- **YouTube download** — NewPipeExtractor 0.26.5, stream selection (video/audio),
  direct save to device storage.
- **Persistent tabs** — Up to 12 live WebViews survive rotation & background;
  no surprise reloads. `onTrimMemory` keeps memory in check.
- **Incognito mode** — Isolated WebView profile, no cache/cookies persistence.
- **Userscript extensions** — Inject custom JS/CSS per domain.
- **Privacy controls** — HTTPS-only, third-party cookie blocking, Do Not Track,
  JavaScript & image toggles, desktop mode, search-engine choice, clear data.
- **Modern M3 UI** — Dynamic color (Android 12+), light/dark/manual theme,
  omnibox with security indicator, find-in-page, bottom navigation, tabs sheet.

## Stack

- Kotlin 2.4, AGP 9.4, Compose BOM 2026.09, Material 3
- NewPipeExtractor 0.26.5 (JitPack)
- minSdk 23 (Android 6), targetSdk/compileSdk 37 (Android 17)

## Build

CI-only (GitHub Actions):

```bash
./gradlew assembleRelease   # R8 + AOSP testkey
# Gate: CI fails if APK > 4 MB
```

## Signing

Release is signed with the **public AOSP testkey** (`keystore/testkey.p12`,
subject `CN=Android`). Not for Play Store.

## License

MIT © 2026 iNexunx
