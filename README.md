<div align="center">

# 🌌 CalabiYauVoice GUI

![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg?logo=kotlin&logoColor=white)
[![Compose-Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-3a7af2?logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-red)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE.txt)

A Kotlin Multiplatform [Strinova](https://wiki.biligame.com/klbq/) Wiki resource browser & downloader, built with Compose for Desktop and Android.

[简体中文](README_ZH_CN.md)

<br>

<table>
  <tr>
    <th>Desktop</th>
    <th>Android</th>
  </tr>
  <tr>
    <td><img src="snapshot.png" alt="Desktop Screenshot" height="500"></td>
    <td><img src="snapshot_android.png" alt="Android Screenshot" height="500"></td>
  </tr>
</table>

</div>

---

## ✨ Features

### 🤝 Shared (Both Platforms)

- **🔍 Smart Search** — 4 search modes: voice-only categories, all resource types, file search (namespace 6), and portrait/illustration.
- **⚡ Concurrent Downloads** — Scan category trees and download files with configurable concurrency.
- **🎵 Audio Playback** — In-app playback for `WAV`, `OGG`, `FLAC`, and `MP3` files.
- **🖼️ Rich Preview** — Live image previews for `PNG`, `JPG`, `WebP`, and animated `GIF`.
- **🗂️ File Selection Dialog** — Per-category file picker with search, language filtering (CN/JP/EN), and image preview.
- **🔍 Search History** — Persistent search suggestions.

### 🖥️ Desktop Extras (Windows)

- **🔄 MP3/FLAC → WAV Conversion** — Batch-convert downloaded audio to WAV with configurable sample rate and bit depth. Optional WAV merging.
- **⌨️ Keyboard Shortcuts** — `Ctrl+F` focus search, `F5` re-search, `Ctrl+D` download, `Ctrl+A` / `Ctrl+Shift+A` select / deselect all, `Ctrl+1~4` switch modes, `↑↓` navigate, and more.
- **🎛️ Windows Backdrop** — Switch between Mica, Tabbed, Acrylic, Aero, and other Windows 11 backdrop styles at runtime.
- **🪟 Custom Title Bar** — Borderless native window with custom caption buttons and drag-to-move support.
- **🖥️ Compatibility** — Graceful fallback on non-Windows-11 devices with gradient background.

### 📱 Android Extras

- **🏠 Wiki Hub** — Native client for browsing characters, weapons, maps, costumes, game modes, announcements, voting, and more — no WebView needed.
- **🖼️ Gallery** — Browse wallpapers, stickers, and comics in a native image gallery with section filtering and fullscreen preview.
- **🌐 Built-in Wiki Browser** — Embedded WebView with cookie persistence, auto-login detection, user info display, and file download/upload support.
- **🖼️ Portrait Viewer** — Swipeable multi-image preview per costume with image type labels and page indicators.
- **📁 File Manager** — Browse downloaded files with multi-select mode (long-press), batch delete/share, image gallery preview, and audio playback.
- **📊 Download History** — Track past downloads with status and file count.
- **⭐ Favorites** — Bookmark characters for quick access.
- **💾 Offline Cache** — Disk cache for Wiki resources with offline-first mode, cache pruning, and manual cache clearing.
- **🎨 Material You** — Dynamic color with wallpaper-based seed color, light/dark/system theme, and liquid glass effects.

---

## 🛠️ Tech Stack

### Shared (commonMain)

| Component     | Technology                   |
|---------------|------------------------------|
| Language      | Kotlin 2.3.10                |
| Async         | Kotlin Coroutines            |
| Network       | OkHttp 5                     |
| Serialization | kotlinx.serialization        |
| UI Foundation | Compose Multiplatform 1.10.3 |

### Desktop

| Component | Technology                                                                                                                                         |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| UI        | [Compose Fluent UI](https://github.com/composefluent/compose-fluent-ui), [ComposeWindowStyler](https://github.com/mayakapps/compose-window-styler) |
| Audio     | `javax.sound.sampled`, `mp3spi`, `jflac-codec`                                                                                                     |
| Image     | `javax.imageio.ImageIO` (GIF frame decoding)                                                                                                       |
| Native    | JNA 5 (Windows API)                                                                                                                                |

### Android

| Component     | Technology                   |
|---------------|------------------------------|
| UI            | Jetpack Compose + Material 3 |
| Image Loading | Coil 3 (async + GIF)         |
| Web           | Android WebView              |
| Audio         | Android MediaPlayer          |
| Architecture  | AndroidViewModel + StateFlow |

---

## 📂 Project Structure

```text
src/
├── commonMain/                         # Shared business logic
│   ├── data/                           #   Wiki API core, models, serialization, shared network DTOs
│   ├── portrait/                       #   Portrait parsing and organization
│   └── util/                           #   File extension helpers
├── desktopMain/                        # Desktop (Windows) target
│   ├── data/                           #   OkHttp client, image loader, cookies, desktop API glue
│   ├── viewmodel/                      #   Search/download state management
│   ├── ui/screens/                     #   Desktop screens
│   ├── ui/components/                  #   Reusable Fluent UI components
│   ├── util/                           #   Audio conversion, preferences, file helpers
│   └── jna/windows/                    #   Win32 API bindings and window effects
└── androidMain/                        # Android target
    ├── core/
    │   ├── cache/                      #   Offline cache, cache bootstrap, cache pruning
    │   ├── media/                      #   Audio playback and media helpers
    │   ├── navigation/                 #   MainScreen and app-level navigation shell
    │   ├── network/                    #   Network monitor and HTTP helpers
    │   ├── preferences/                #   AppPrefs and persisted Android settings
    │   ├── ui/                         #   Shared Compose UI: common, state, skeleton, preview, feedback
    │   └── wiki/                       #   Wiki auth, parse source, image URL helpers, parser utilities
    ├── feature/
    │   ├── character/                  #   Character list/detail, birthday dialog, costumes, selectors
    │   ├── weapon/                     #   Weapon list/detail and weapon skin filters
    │   ├── download/                   #   Resource search, category browse, downloads, history, portraits
    │   ├── settings/                   #   Settings, about, update checks, storage management
    │   ├── tools/                      #   Local file manager and desktop asset tooling
    │   └── wiki/
    │       ├── hub/                    #   Native Wiki Hub, routes/codecs, aggregate pages, WebView shell
    │       ├── gallery/                #   Wallpapers, stickers, comics, preview/save flow
    │       ├── map/                    #   Map list/detail parsing and native detail page
    │       ├── activity/announcement/  #   Activities and announcements
    │       ├── achievement/            #   Native achievements page
    │       ├── playerlevel/            #   Player levels, rewards, frame segments
    │       ├── oath/imprint/           #   Oath, gifts, bonds, imprints
    │       ├── bio/                    #   PC/mobile bio cards and deck sharing
    │       ├── item/decoration/        #   Item catalog and player decorations
    │       ├── bgm/meow/meme/tips/     #   BGM, meow language, memes, game tips
    │       ├── story/history/          #   Story and game history pages
    │       ├── collaboration/voting/   #   Collaborations and native voting
    │       └── stringer/navigation/    #   Stringer actions/talents/cards and Wiki navigation
    ├── MainActivity.kt
    ├── CrashHandler.kt                 # Android crash capture
    └── NotificationHelper.kt           # Download/status notifications
```

## 🚀 Build and Run

```powershell
# Build the project
./gradlew.bat build

# Run Desktop app
./gradlew.bat run

# Build Android APK
./gradlew.bat assembleDebug
```

> For macOS/Linux, use `./gradlew` instead of `./gradlew.bat`.

## ⚠️ Notes

- 📡 **API Dependency:** The app depends on Bilibili wiki endpoints; availability may vary depending on network conditions.
- 📱 **Android:** Requires Android 8.0 (API 26) or later.

## 📄 License

See [LICENSE.txt](LICENSE.txt) for more information.
