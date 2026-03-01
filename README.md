<div align="center">

# 🌌 CalabiYauVoice GUI

![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg?logo=kotlin&logoColor=white)
![Compose Desktop](https://img.shields.io/badge/Compose_Desktop-4285F4?logo=jetpackcompose&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Windows-red)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE.txt)

A Compose Desktop application for browsing and downloading Strinova wiki resources.<br>
一个基于 Compose Desktop 构建的桌面应用，用于浏览与下载卡拉彼丘 Wiki 资源。

[English](#english) • [简体中文](#简体中文)

<br>

![App Screenshot](snapshot.png)
*截图 | Screenshot*

</div>

---

## 🇺🇸 English

### ✨ Features

- **🔍 Smart Search:** Search character categories (voice-only or all resource types), or switch to file search mode (namespace 6).
- **⚡ Concurrent Downloads:** Scan category trees and download files with built-in concurrency control.
- **🖼️ Rich Preview:** Live image previews for `PNG`, `JPG`, `WebP`, and animated `GIF` (frame-by-frame animation, no extra library required).
- **🎵 Audio Playback:** In-app playback for `WAV`, `OGG`, `FLAC`, and `MP3` files directly from search results.
- **🔄 MP3 → WAV Conversion:** Batch-convert downloaded MP3 files to WAV with configurable sample rate and bit depth.
- **🎛️ Windows Backdrop:** Switch between Mica, Tabbed, Acrylic, Aero, and other Windows 11 backdrop styles.
- **🕰️ Legacy Support:** Traditional HTML-based downloader retained as an optional fallback.

### 🛠️ Tech Stack

- **Core:** Kotlin, Coroutines
- **UI:** Compose Desktop, [Compose Fluent UI](https://github.com/composefluent/compose-fluent-ui), [ComposeWindowStyler](https://github.com/mayakapps/compose-window-styler)
- **Network & Data:** OkHttp, kotlinx.serialization
- **Audio:** `javax.sound.sampled` (WAV/OGG/FLAC), [mp3spi](https://github.com/robingarbo/mp3spi) (MP3)
- **Image:** `javax.imageio.ImageIO` (GIF frame decoding, no Coil needed)

---

## 🇨🇳 简体中文

### ✨ 特性

- **🔍 智能搜索：** 支持分类搜索（仅语音 / 所有类型），也可切换为文件搜索模式（命名空间 6）。
- **⚡ 并发下载：** 扫描分类树并并发下载文件，内置完善的并发控制。
- **🖼️ 丰富预览：** 支持 `PNG`、`JPG`、`WebP` 静态图实时预览，以及 `GIF` 逐帧动画播放（无需额外依赖）。
- **🎵 音频播放：** 可直接在搜索结果中播放 `WAV`、`OGG`、`FLAC` 及 `MP3` 格式音频。
- **🔄 MP3 转 WAV：** 下载后批量将 MP3 转换为 WAV，支持自定义采样率与位深。
- **🎛️ 窗口特效：** 支持在 Mica、Tabbed、Acrylic、Aero 等 Windows 11 背景特效之间切换。
- **🕰️ 旧版支持：** 保留传统 HTML 下载器作为可选备用方案。

### 🛠️ 技术栈

- **核心：** Kotlin、协程 (Coroutines)
- **UI 框架：** Compose Desktop、[Compose Fluent UI](https://github.com/composefluent/compose-fluent-ui)、[ComposeWindowStyler](https://github.com/mayakapps/compose-window-styler)
- **网络与数据：** OkHttp、kotlinx.serialization
- **音频：** `javax.sound.sampled`（WAV/OGG/FLAC）、[mp3spi](https://github.com/robingarbo/mp3spi)（MP3）
- **图像：** `javax.imageio.ImageIO`（GIF 多帧解码动画，无需 Coil）

---

## 📂 Project Structure / 项目结构 (MVVM)

```text
src/main/kotlin/
├── data/          # 🌐 Wiki access, image/audio loading (数据与网络请求层)
├── viewmodel/     # 🧠 ViewModel layer: state + actions (视图模型层)
├── ui/
│   ├── screens/   # 🖥️ Screen composables (页面组件)
│   └── components/# 🧩 Reusable UI components (可复用 UI 组件)
├── util/          # 🛠️ Utilities (工具类)
```

## 🚀 Build and Run / 构建与运行

You can build and run the application via Gradle.
> *Windows PowerShell examples / Windows 命令行示例:*

```powershell
# Build the project / 构建项目
./gradlew.bat build

# Run Compose Desktop app / 运行桌面应用
./gradlew.bat run
```
*(For macOS/Linux, use `./gradlew` instead of `./gradlew.bat`)*

## ⚠️ Notes / 注意事项

- 📡 **API Dependency:** The app depends on Bilibili wiki endpoints; availability may vary depending on network conditions. / 本应用依赖于 Bilibili Wiki 的 API 接口，可用性可能受网络环境影响。
- 👁️ **GIF Animation:** Animated GIFs are decoded frame-by-frame using the JVM built-in `javax.imageio`, no additional library needed. / 动态 GIF 使用 JVM 内置的 `javax.imageio` 逐帧解码，无需额外依赖。

## 📄 License

See [LICENSE.txt](LICENSE.txt) for more information.
