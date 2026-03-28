<div align="center">

# 🌌 CalabiYauVoice GUI

![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg?logo=kotlin&logoColor=white)
[![Compose-Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-3a7af2?logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-red)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE.txt)

A Kotlin Multiplatform application for browsing and downloading [Strinova](https://wiki.biligame.com/klbq/) wiki resources, built with Compose for Desktop and Android.

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

### 🖥️ Desktop (Windows)

- **🔍 Smart Search** — Search by character voice categories, all resource types, file search (namespace 6), or portrait/illustration mode.
- **⚡ Concurrent Downloads** — Scan category trees and download files with configurable concurrency.
- **🖼️ Rich Preview** — Live image previews for `PNG`, `JPG`, `WebP`, and animated `GIF`. Click to enlarge, scroll to zoom.
- **🎵 Audio Playback** — In-app playback for `WAV`, `OGG`, `FLAC`, and `MP3` files directly from search results.
- **🔄 MP3/FLAC → WAV Conversion** — Batch-convert downloaded audio to WAV with configurable sample rate and bit depth. Optional WAV merging.
- **⌨️ Keyboard Shortcuts** — `Ctrl+F` focus search, `F5` re-search, `Ctrl+D` download, `Ctrl+A` / `Ctrl+Shift+A` select / deselect all, `Ctrl+1~4` switch modes, `↑↓` navigate, and more.
- **🎛️ Windows Backdrop** — Switch between Mica, Tabbed, Acrylic, Aero, and other Windows 11 backdrop styles at runtime.
- **🪟 Custom Title Bar** — Borderless native window with custom caption buttons and drag-to-move support.
- **🖥️ Compatibility** — Graceful fallback on non-Windows-11 devices with gradient background.

### 📱 Android

- **🔍 4 Search Modes** — Voice-only, all categories, file search, and portrait/illustration — switchable via bottom navigation bar.
- **⚡ Concurrent Downloads** — Same powerful download engine as Desktop with configurable concurrency.
- **🌐 Built-in Wiki Browser** — Embedded WebView with cookie persistence, auto-login detection, user info display, file download/upload support, and navigation controls.
- **🖼️ Portrait Viewer** — Swipeable multi-image preview per costume with image type labels and page indicators.
- **📁 File Manager** — Browse downloaded files with multi-select mode (long-press), batch delete/share, image gallery preview, and audio playback.
- **📊 Download History** — Track past downloads with status and file count.
- **🎵 Audio Playback** — Play `WAV`, `OGG`, `MP3` audio files from search results or local storage.
- **🗂️ File Selection Dialog** — Per-category file picker with search, language filtering (CN/JP/EN), and image preview.
- **⭐ Favorites** — Bookmark characters for quick access.
- **📶 Network Detection** — Offline status banner with custom error page in WebView.
- **🎨 Material You** — Dynamic color scheme with light/dark/system theme options.
- **🔍 Search History** — Persistent search suggestions as chips.

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
├── commonMain/          # Shared business logic
│   ├── data/            #   Wiki API core, models, serialization
│   ├── portrait/        #   Portrait parsing & organization
│   └── util/            #   File extension utilities
├── desktopMain/         # Desktop (Windows) target
│   ├── data/            #   OkHttp client, image loader, cookies
│   ├── viewmodel/       #   State management
│   ├── ui/screens/      #   Screen composables
│   ├── ui/components/   #   Reusable UI components
│   ├── util/            #   Audio conversion, preferences
│   └── jna/windows/     #   Win32 API bindings
└── androidMain/         # Android target
    ├── data/            #   OkHttp client, preferences, network monitor
    ├── ui/              #   Compose screens & components
    └── (MainActivity, MainViewModel)
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
