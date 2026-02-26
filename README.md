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

- **🔍 Smart Search:** Search character categories (voice-only or all types).
- **⚡ Concurrent Downloads:** Scan category trees and download files with built-in concurrency control.
- **🖼️ Rich Preview:** File picker dialog with live image previews for `PNG`, `JPG`, `GIF`, and `WebP`.

### 🛠️ Tech Stack

- **Core:** Kotlin, Coroutines
- **UI:** Compose Desktop, [Compose Fluent UI](https://github.com/composefluent/compose-fluent-ui), [window-styler](https://github.com/mayakapps/compose-window-styler)
- **Network & Data:** OkHttp, kotlinx.serialization, Jsoup

---

## 🇨🇳 简体中文

### ✨ 特性

- **🔍 智能搜索：** 支持搜索角色分类（仅语音或所有类型资源）。
- **⚡ 并发下载：** 扫描分类树并下载文件，内置完善的并发控制。
- **🖼️ 丰富预览：** 文件选择器对话框，支持 `PNG`、`JPG`、`GIF` 和 `WebP` 格式的实时图像预览。
- **🕰️ 旧版支持：** 保留了传统的 HTML 下载器（可选）。

### 🛠️ 技术栈

- **核心：** Kotlin, 协程 (Coroutines)
- **UI框架：** Compose Desktop, [Compose Fluent UI](https://github.com/composefluent/compose-fluent-ui), [window-styler](https://github.com/mayakapps/compose-window-styler)
- **网络与数据：** OkHttp, kotlinx.serialization, Jsoup

---

## 📂 Project Structure / 项目结构 (MVVM)

```text
src/main/kotlin/
├── data/          # 🌐 Wiki access and data logic (数据与网络请求层)
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
- 👁️ **Preview:** Image previews are enabled out-of-the-box for common image extensions. / 常见图片格式的预览功能已默认开启。

## 📄 License

See [LICENSE.txt](LICENSE.txt) for more information.
