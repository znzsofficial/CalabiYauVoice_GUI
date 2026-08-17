<div align="center">

# 🌌 CalabiYauVoice GUI

![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg?logo=kotlin&logoColor=white)
[![Compose-Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-3a7af2?logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-red)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE.txt)

基于 Kotlin Multiplatform 构建的[卡拉彼丘](https://wiki.biligame.com/klbq/) Wiki 资源浏览与下载工具，支持桌面端和 Android 端。

[![下载 Android APK](https://img.shields.io/badge/下载-Android%20APK-3DDC84?logo=android&logoColor=white)](https://wiki.nekolaska.vip/)
[![网页工具](https://img.shields.io/badge/Wiki资源站-0A0A0A?logo=cloudflare&logoColor=white)](https://wiki.nekolaska.vip/search)

**官网入口：** [wiki.nekolaska.vip](https://wiki.nekolaska.vip/)  
**备用链接：** [calabiyauwiki.pages.dev](https://calabiyauwiki.pages.dev/)

[English](README.md)

<br>

<table>
  <tr>
    <th>桌面端</th>
    <th>Android 端</th>
  </tr>
  <tr>
    <td><img src="snapshot.png" alt="桌面端截图" height="500"></td>
    <td><img src="snapshot_android.png" alt="Android 截图" height="500"></td>
  </tr>
</table>

</div>

---

## ✨ 功能特性

### 🤝 共享功能（双端通用）

- **🔍 智能搜索** — 四种搜索模式：仅语音分类、全部分类、文件搜索（命名空间 6）、立绘预览。
- **⚡ 并发下载** — 扫描分类树并并发下载文件，支持自定义并发数。
- **🎵 音频播放** — 支持播放 `WAV`、`OGG`、`FLAC`、`MP3` 格式音频。
- **🖼️ 丰富预览** — 支持 `PNG`、`JPG`、`WebP` 静态图与 `GIF` 逐帧动画预览。
- **🗂️ 文件选择对话框** — 按分类浏览文件，支持搜索、语言筛选（中/日/英）与图片预览。

### 🖥️ 桌面端增强（Windows）

- **🔄 音频转 WAV** — 批量将 `MP3`、`FLAC`、`OGG`、`AAC`、`M4A` 转换为 WAV，支持自定义采样率、位深、抖动处理与可选 WAV 合并。
- **🧰 素材工作库** — 集中处理图片和字幕时间轴；音频工具支持静音裁剪、声道转换、增益/标准化、噪声门、淡入淡出、相位处理、撤销/重做以及频谱图预览与导出。
- **⌨️ 键盘快捷键** — `Ctrl+F` 聚焦搜索，`F5` 重新搜索，`Ctrl+D` 开始下载，`Ctrl+A` / `Ctrl+Shift+A` 全选 / 取消全选，`Ctrl+1~4` 切换模式，`↑↓` 导航列表等。
- **🎛️ 窗口特效** — 运行时动态切换 Mica、Tabbed、Acrylic、Aero 等 Windows 11 背景特效。
- **🪟 自定义标题栏** — 无边框原生窗口，自定义标题栏按钮，支持拖拽移动。
- **🖥️ 兼容性** — 非 Windows 11 设备自动降级，使用渐变背景保证可读性。

### 📱 Android 端增强

- **🏠 Wiki Hub** — 原生 Wiki 客户端，可浏览角色图鉴、武器百科、地图一览、时装筛选、玩法模式、公告资讯、时装投票等，无需 WebView。
- **🖼️ 画廊** — 原生浏览壁纸、表情包、四格漫画，支持分区筛选与全屏预览。
- **🌐 内置 Wiki 浏览器** — 嵌入式 WebView，支持 Cookie 持久化、自动检测登录状态、用户信息展示、文件下载/上传与页面导航。
- **🖼️ 立绘查看器** — 多时装切换，每套时装支持横向滑动浏览全部图片，显示图片类型标签与页码指示器。
- **📁 文件管理器** — 浏览已下载文件，支持多选模式（长按进入）、批量删除/分享、图片画廊预览与音频播放。
- **📊 下载历史** — 记录历史下载，显示状态与文件数。
- **⭐ 收藏功能** — 收藏常用角色，方便快速访问。
- **💾 离线缓存** — Wiki 资源磁盘缓存，支持优先缓存模式、过期清理与手动清空缓存。
- **🎨 Material You** — 支持动态取色与壁纸跟随主题色，亮色/暗色/跟随系统，液态玻璃效果。

---

## 🛠️ 技术栈

### 共享层（commonMain）

| 组件    | 技术                           |
|-------|------------------------------|
| 语言    | Kotlin 2.4.10                |
| 异步    | Kotlin Coroutines 1.11       |
| 网络    | OkHttp 5.4                   |
| 序列化   | kotlinx.serialization 1.11   |
| UI 基础 | Compose Multiplatform 1.11.1 |

### 桌面端

| 组件    | 技术                                                                                                                                                |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| UI 框架 | [Compose Fluent UI](https://github.com/NucleusFramework/compose-fluent-ui)、[ComposeWindowStyler](https://github.com/mayakapps/compose-window-styler) |
| 音频    | Java Sound SPI（`MP3` / `OGG` / `AAC` / `M4A`）、通过 JNA 接入的官方 `libFLAC` 1.5.0                                                               |
| 图像    | `javax.imageio.ImageIO`（GIF 多帧解码）                                                                                                                 |
| 原生调用  | JNA 5.19.1（Windows API 与原生 FLAC 解码）                                                                                                               |

### Android 端

| 组件    | 技术                           |
|-------|------------------------------|
| UI 框架 | Jetpack Compose + Material 3 |
| 图片加载  | Coil 3.5（异步加载 + GIF 支持）      |
| 网页    | Android WebView              |
| 音频    | Android MediaPlayer          |
| 架构    | AndroidViewModel + StateFlow |
| 最低系统  | Android 7.1（API 25）         |

### 下载页 / 网页工具站（`downloadPage/`）

| 组件    | 技术                                      |
|-------|-----------------------------------------|
| 前端框架  | Svelte 5 + TypeScript                   |
| 构建工具  | Vite 8                                  |
| 部署    | Cloudflare Pages                        |
| 边缘接口  | Cloudflare Worker（`src/api/_worker.js`） |
| 媒体处理  | `gifenc`、`gifuct-js`、`jszip`            |
| 页面    | 首页 / Wiki 搜索 / 视频素材工具台                  |

---

## 📂 项目结构

```text
.
├── androidApp/                         # Android 应用模块
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/nekolaska/calabiyau/
│       │   ├── core/                   #   缓存、媒体、导航、网络、偏好设置、UI 工具
│       │   ├── feature/                #   Android 页面与功能 API
│       │   │   ├── character/          #   角色列表/详情、时装
│       │   │   ├── weapon/             #   武器列表/详情与外观筛选
│       │   │   ├── download/           #   资源搜索、分类浏览、下载、历史、立绘
│       │   │   ├── settings/           #   设置、关于、更新检查、存储管理
│       │   │   ├── tools/              #   本地文件管理与工具
│       │   │   └── wiki/               #   原生 Wiki Hub、WebView、解析器
│       │   ├── MainActivity.kt
│       │   ├── CrashHandler.kt
│       │   └── NotificationHelper.kt
│       └── res/
├── desktopApp/                         # 桌面应用模块
│   ├── build.gradle.kts
│   ├── icon.ico
│   ├── libs/
│   └── src/main/
│       ├── kotlin/
│       │   ├── data/                   #   OkHttp 客户端、图片加载器、Cookie
│       │   ├── viewmodel/              #   搜索/下载状态管理
│       │   ├── ui/screens/             #   桌面端页面
│       │   ├── ui/components/          #   可复用 Fluent UI 组件
│       │   ├── util/                   #   音频转换、偏好设置、文件工具
│       │   ├── jna/windows/            #   Win32 绑定与窗口特效
│       │   └── Main.kt
│       └── resources/
├── shared/                             # Kotlin Multiplatform 共享模块
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── data/                   #   Wiki API 核心、模型、DTO
│       │   ├── portrait/               #   立绘解析与组织
│       │   ├── util/                   #   共享工具
│       │   └── com/nekolaska/calabiyau/ #   共享媒体 / 偏好设置
│       ├── commonMain/composeResources/
│       ├── commonTest/
│       ├── jvmTest/
│       └── androidHostTest/
├── downloadPage/                       # 网页下载站与浏览器端工具
│   ├── index.html                      #   首页：APK 下载、QQ 群、平衡数据
│   ├── search/index.html               #   Wiki 搜索 / 分类打包 / 语音字幕
│   ├── video/index.html                #   本地视频/GIF 素材工具
│   ├── package.json
│   ├── vite.config.ts                  #   多页 Vite 构建 + 本地 API 代理
│   ├── download.css / base.css
│   ├── downloads/                      #   latest.json 与 APK 资源
│   └── src/
│       ├── main.ts                     #   首页入口
│       ├── App.svelte                  #   首页 UI
│       ├── BalanceDialog.svelte        #   平衡数据弹窗
│       ├── CustomSelect.svelte
│       ├── api/_worker.js              #   CF Worker：GitHub stars、Wiki/图片代理、平衡数据代理
│       ├── search/                     #   Wiki 搜索、语音索引、批量下载
│       │   ├── SearchApp.svelte
│       │   ├── searchApi.ts
│       │   ├── download/               #   ZIP / 队列 / Blob 工具
│       │   ├── panels/                 #   Wiki / 分类 / 语音面板
│       │   └── voice/                  #   语音索引构建与播放 UI
│       └── video/                      #   本地视频/GIF 工作台
│           ├── NativeVideoApp.svelte
│           └── native-video.css
├── webApp/                             # 可选 Compose/Web 相关模块
├── gradle/libs.versions.toml           # 集中管理依赖版本
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 构建与运行

### 环境要求

- JDK 21（Gradle toolchain 可自动配置项目指定的 Amazon Corretto JDK）
- 构建 Android 应用时需要 Android SDK
- 构建 `downloadPage/` 时需要 Node.js 与 npm
- 桌面端 FLAC 播放/转换目前需要 Windows x64；项目已内置官方 `libFLAC.dll` 1.5.0，并在加载前校验 SHA-256

```powershell
# 构建项目
./gradlew.bat build

# 运行桌面应用
./gradlew.bat run

# 构建 Android APK
./gradlew.bat assembleDebug

# 下载页 / 网页工具站
cd downloadPage
npm install
npm run dev
npm run build
```

> macOS / Linux 请使用 `./gradlew` 代替 `./gradlew.bat`。

## ⚠️ 注意事项

- 📡 **API 依赖：** 本应用依赖 Bilibili Wiki 的 API 接口，可用性可能受网络环境影响。
- 📱 **Android：** 需要 Android 7.1（API 25）或更高版本。
- 🖥️ **桌面端 FLAC：** 原生 FLAC 解码目前支持 Windows x64；桌面发行包已包含所需 DLL 与第三方许可声明。
- 🌐 **网站入口：** 请优先访问 [wiki.nekolaska.vip](https://wiki.nekolaska.vip/)；[calabiyauwiki.pages.dev](https://calabiyauwiki.pages.dev/) 为 Cloudflare Pages 备用链接。

## 📄 许可证

详见 [LICENSE.txt](LICENSE.txt)。
