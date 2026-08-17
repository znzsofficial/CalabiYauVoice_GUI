# 桌面端实现说明

本文记录 `desktopApp` 的当前实现结构、关键调用链、平台边界和静态审阅结论，供后续维护、重构和排障使用。

- 记录日期：2026-08-17
- 分析范围：`desktopApp/`、与桌面端直接相关的 `shared/` 代码及根 Gradle 配置
- 分析方式：静态阅读代码；风险项除特别说明外不代表已经运行复现
- 代码引用：均相对于仓库根目录，行号对应记录时版本，后续修改可能产生偏移

## 1. 核心结论

1. 桌面端是 Kotlin/JVM 上的 Compose Desktop 应用，不是 Electron，也不存在主进程、渲染进程或 IPC 层。
2. UI、状态管理、网络请求、文件处理、音频处理和 Win32 调用都运行在同一个 JVM 进程内；耗时任务主要通过 Kotlin Coroutines 调度。
3. `desktopApp` 依赖 `shared`，复用 Wiki 搜索、分类展开、下载编排、立绘解析及部分媒体算法；窗口、登录、WebView、图片加载和桌面媒体能力由 `desktopApp` 自己实现。
4. 桌面应用虽然引用 `compose.desktop.currentOs`，但主窗口启动链直接使用 User32、DWM、注册表和 Windows 版本 API，当前产品实际上是 Windows 定向实现。
5. 主界面采用普通 Kotlin 类 `MainViewModel` 加 `StateFlow` 管理业务状态；全局 `AppState` 只负责主题和窗口背景效果。
6. 主窗口和大部分子窗口共享 `StyledWindow`、`WindowsWindowFrame` 及自定义 Win32 WndProc，实现无边框标题栏、系统缩放边框和 Mica/Acrylic 等背景效果。
7. 下载流程具备目录级互斥、并发限制、安全文件名、临时文件和下载清单，但上层没有显式的用户取消入口。
8. 当前主要维护风险集中在 Compose 私有 API 反射、Windows 平台硬绑定、网络错误被降级为空结果、媒体内存峰值、部分同步 I/O，以及若干资源生命周期边界。

## 2. 模块关系

根项目声明四个模块：

```text
CalabiYauVoice_GUI
├── desktopApp   Kotlin/JVM + Compose Desktop 桌面应用
├── androidApp   Android 应用
├── shared       Kotlin Multiplatform 共享业务与媒体核心
└── webApp       Compose Web JS/Wasm 应用
```

模块定义位于 `settings.gradle.kts:47-50`。

桌面端实际依赖关系：

```text
desktopApp
├── shared
│   ├── WikiEngineCore
│   ├── Wiki 模型与请求 DSL
│   ├── PortraitRepository / PortraitLogic
│   ├── PCM WAV 与频谱算法
│   └── GIF 编码算法
├── Compose Desktop / Compose Fluent UI
├── Window Styler
├── OkHttp / Okio / kotlinx.serialization
├── AWT / Swing / Java Sound
├── JNA + Win32 API
├── Compose WebView
└── Windows x64 libFLAC.dll
```

关键配置：

| 文件 | 作用 |
|---|---|
| `desktopApp/build.gradle.kts:3-8` | Kotlin JVM、Compose、序列化插件 |
| `desktopApp/build.gradle.kts:10-39` | 桌面依赖及 `shared` 依赖 |
| `shared/build.gradle.kts` | JVM/Android 共享 source set |
| `gradle/libs.versions.toml` | Kotlin、Compose、OkHttp、JNA 和媒体依赖版本 |

`webApp` 不参与桌面窗口渲染，也不是桌面端内嵌前端。桌面端内的 Wiki 浏览器使用 Compose WebView，但主应用 UI 是 Compose Desktop 原生渲染。

## 3. 运行模型

桌面应用的执行模型是单 JVM 进程：

```text
JVM 进程
├── Compose Desktop UI / Skia 渲染
├── Compose 状态与协程
├── OkHttp 网络请求
├── AWT/Swing 文件选择与系统能力
├── Java Sound 解码、转换与播放
├── JNA 调用 User32、DWM 和 libFLAC
└── 本地文件、偏好和下载清单
```

因此：

- 没有 IPC 消息协议或 preload bridge。
- 全局单例和静态缓存会被所有窗口共享。
- 主窗口退出即结束整个应用进程。
- 未切换到后台调度器的阻塞调用会直接影响其调用线程。
- 子窗口并不是独立进程，关闭窗口只销毁对应 Compose composition 和本地状态。

## 4. 启动链

Gradle 将 `MainKt` 设置为入口：`desktopApp/build.gradle.kts:41-45`。

完整启动链：

```text
MainKt.main()
├── PortraitRepository.init(...)
│   └── 注入 Desktop WikiEngine 的分类、文件搜索和角色名能力
└── application
    ├── setupGlobalExceptionHandler()
    ├── rememberWindowState(1280 x 900)
    └── Window
        ├── 读取初始系统主题
        ├── 检测 Windows 11
        ├── 安装顶层窗口和 Skia 子窗口的原生过程
        ├── 准备透明 Skia 图层
        ├── 创建 AppState 并注入 LocalAppStore
        ├── FluentTheme
        ├── WindowStyle / Backdrop
        └── WindowsWindowFrame
            └── NewDownloaderContent()
                └── MainViewModel
```

关键位置：

- 立绘仓库平台函数注入：`desktopApp/src/main/kotlin/Main.kt:32-38`
- Compose application 和主窗口：`desktopApp/src/main/kotlin/Main.kt:40-49`
- 主题、Windows 版本、Skia 和 Backdrop：`desktopApp/src/main/kotlin/Main.kt:57-90`
- 自定义窗口框架和主内容：`desktopApp/src/main/kotlin/Main.kt:91-110`
- 全局未捕获异常弹窗：`desktopApp/src/main/kotlin/Main.kt:117-133`

`NewDownloaderContent` 首次组合时创建 `MainViewModel`。`MainViewModel` 构造完成后会执行默认搜索，因此冷启动不仅创建窗口，也会异步访问 Wiki API。

## 5. 窗口系统

### 5.1 主窗口

主窗口使用普通 Compose `Window`，外层叠加以下能力：

```text
Compose Window
└── WindowsWindowFrame
    ├── 自定义标题栏
    ├── 最小化、最大化、关闭按钮
    ├── Compose 控件命中区域记录
    ├── Win32 非客户区命中测试
    ├── 原生 resize border 和系统菜单
    └── DWM 阴影、边框、圆角及 Backdrop
```

主要实现：

| 文件 | 职责 |
|---|---|
| `ui/components/WindowsWindowFrame.kt` | Compose 标题栏、按钮矩形和 frame state 生命周期 |
| `ui/components/CaptionButtonRow.kt` | 最小化、最大化、还原和关闭行为 |
| `jna/windows/ComposeWindowProcedure.kt` | 顶层 HWND 的 WndProc、系统菜单、DWM 和主题消息 |
| `jna/windows/SkiaLayerWindowProcedure.kt` | Skia 子 HWND 的鼠标消息转发 |
| `jna/windows/LayoutHitTestOwner.kt` | 区分标题栏空白和 Compose 交互控件 |
| `util/TransparentSkiaLayer.kt` | 查找并设置透明 Skia 图层 |

原生命中测试大致按以下优先级返回：

```text
最大化按钮 -> HTMAXBUTTON
最小化按钮 -> HTMINBUTTON
关闭按钮   -> HTCLOSE
标题栏空白 -> HTCAPTION
其他区域   -> HTCLIENT
```

这样既能保留原生窗口拖拽、缩放、系统菜单和 Windows 11 Snap Layout，又允许标题栏中的 Compose 控件接收事件。

### 5.2 子窗口

大部分子窗口统一经过 `StyledWindow`：

```text
具体 *Window
└── StyledWindow
    └── Compose Window
        ├── FluentTheme
        ├── WindowStyle / 渐变回退
        ├── WindowsWindowFrame
        └── 具体内容
```

统一模板位于 `desktopApp/src/main/kotlin/ui/components/StyledWindow.kt:42-115`。

当前子窗口包括：

| 窗口 | 入口文件 | 主要职责 |
|---|---|---|
| 关于 | `ui/screens/AboutWindow.kt` | 版本与项目信息 |
| 快捷键 | `ui/screens/NewContent.kt` | 快捷键帮助 |
| 用户信息 | `ui/screens/UserInfoWindow.kt` | Cookie 导入、当前用户及公开用户查询 |
| 音频转换 | `ui/screens/Mp3ConverterWindow.kt` | 压缩音频转 WAV、合并与批处理 |
| 素材工具 | `ui/screens/AssetToolsWindow.kt` | 图片、字幕和音频工作台 |
| Wiki 浏览器 | `ui/screens/WikiBrowserWindow.kt` | 内嵌 Wiki WebView |
| 创作者中心 | `ui/screens/WikiBrowserWindow.kt` | 复用 WebView 并注入移动端 UA |
| 平衡数据 | `ui/screens/BalanceDataWindow.kt` | 官网平衡数据查询和筛选 |
| 时装投票 | `ui/screens/VotingWindow.kt` | Wiki 投票页面解析与提交 |
| 运行日志 | `ui/screens/LogWindow.kt` | 下载进度和日志 |
| 文件选择 | `ui/components/FileSelectionDialog.kt` | 分类文件筛选、试听和图片预览 |

图片预览是例外，直接使用 `DialogWindow`，实现位于 `ui/components/ImagePreviewDialog.kt`。

### 5.3 窗口生命周期

`NewDownloaderContent` 使用多个 Boolean `remember` 状态控制子窗口是否进入 composition：

```text
showX = true
  -> 子窗口进入 composition
showX = false
  -> 子窗口退出 composition
  -> Window 释放
  -> remember 状态丢弃
  -> rememberCoroutineScope 取消
  -> DisposableEffect 恢复原 WndProc
```

当前行为：

- 每一种子窗口最多打开一个实例。
- 不同种类子窗口可以同时存在。
- `StyledWindow` 使用普通 `Window`，这些窗口不是模态对话框。
- 子窗口关闭后，其局部 `remember` 状态不会保留。
- 窗口位置、大小、主题和 Backdrop 当前不持久化。
- `WikiUserApi.currentUser`、CookieJar、图片缓存和音频播放器属于进程级状态，不随单个窗口关闭而销毁。

## 6. 状态管理

### 6.1 全局视觉状态

`AppState` 通过 `LocalAppStore` 向所有窗口提供：

- `darkMode`
- `backdropType`
- `isWin11`
- `canUseNonWin11Backdrop`

定义位于 `desktopApp/src/main/kotlin/AppStore.kt:14-28`。

它不保存搜索、下载、登录或窗口导航等业务状态。

### 6.2 主业务状态

`MainViewModel` 是普通 Kotlin 类，不继承 AndroidX `ViewModel`。它接收 Compose 提供的 coroutine scope，并以私有 `MutableStateFlow` 和公开 `StateFlow` 管理：

- 搜索模式与关键词
- 角色、分类和文件搜索结果
- 分类勾选与手工文件选择
- 立绘角色、时装和资源选择
- 文件选择窗口状态
- 保存目录、转换选项和下载并发数
- 下载进度、日志和错误

主要状态定义：`desktopApp/src/main/kotlin/viewmodel/MainViewModel.kt:21-181`。

搜索、分类扫描、立绘和文件窗口分别保存 `Job` 与请求 ID。新请求会取消旧请求，迟到结果在写状态前再次核对请求 ID，减少旧响应覆盖新状态的可能。

### 6.3 子窗口局部状态

多数工具窗口直接使用 `remember` 和 `rememberCoroutineScope` 管理参数、Tab、加载状态及工作进度。例如：

- `Mp3ConverterWindow` 保存输入文件、采样率、位深、进度和日志。
- `AssetToolsWindow` 保存当前工具 Tab、输出目录和忙碌状态。
- `VotingWindow` 保存候选项、用户选择和提交状态。
- `BalanceDataWindow` 保存筛选、排序和加载结果。
- `WikiBrowserWindow` 保存 WebView state 和 navigator。

这些状态随窗口关闭而销毁，重新打开会重新初始化。

### 6.4 进程级状态

以下对象跨窗口共享：

| 状态 | 位置 |
|---|---|
| 当前登录用户 | `data/WikiUserApi.kt` 的 `StateFlow` |
| Wiki Cookie | `data/WikiEngine.kt` 的 CookieJar 与 `WikiCookieManager` |
| Wiki OkHttpClient | `data/WikiEngine.kt` |
| 图片和头像缓存 | `data/ImageLoader.kt` |
| 单实例音频播放器 | `ui/components/AudioPlayerManager.kt` |
| 立绘索引 | `shared/.../data/PortraitRepository.kt` |

## 7. 主业务数据流

### 7.1 通用 UI 数据流

```text
Compose 控件事件
-> MainViewModel 方法
-> MutableStateFlow / coroutine
-> Desktop WikiEngine
-> Shared WikiEngineCore 或 PortraitRepository
-> StateFlow 新值
-> collectAsState()
-> Compose 重组
```

### 7.2 角色与分类搜索

```text
关键词 + SearchMode
-> MainViewModel.performSearch()
-> WikiEngine.searchAndGroupCharacters()
-> MediaWiki namespace 14 搜索
-> 可选“仅语音”过滤
-> 获取官方角色名
-> 按角色前缀归并分类
-> CharacterGroup 列表
```

关键实现：

- UI 搜索编排：`viewmodel/MainViewModel.kt:255-343`
- 桌面门面：`data/WikiEngine.kt:184-207`
- 共享搜索与分组：`shared/src/commonMain/kotlin/data/WikiEngineCore.kt:88-116`

选择角色后，分类树使用按层 BFS 展开：

```text
根分类
-> 同层分类并发请求直属子分类
-> Set 去重并防循环
-> 继续下一层
-> 排序，根分类放在首位
```

实现位于 `WikiEngineCore.kt:121-139`。

### 7.3 文件搜索

文件搜索同时走两条 MediaWiki 路径：

```text
路径一：allimages 前缀搜索
路径二：File 命名空间全文搜索 + imageinfo
-> 前缀结果优先合并
-> URL 去重
-> 默认全选
```

实现位于 `WikiEngineCore.kt:144-207`。

### 7.4 分类文件选择

```text
分类名
-> generator=categorymembers
-> imageinfo(url|mime)
-> continuation 分页
-> 可选音频过滤
-> URL 去重
-> 默认全选或恢复手工选择
```

实现位置：

- 共享文件查询：`WikiEngineCore.kt:212-219` 及文件分页辅助函数
- 桌面窗口状态：`MainViewModel.kt:464-522`
- 文件选择 UI：`ui/components/FileSelectionDialog.kt`

### 7.5 下载

下载前，`MainViewModel` 会快照当前模式、选择和配置。

```text
文件搜索模式
-> 按 selectedUrls 过滤搜索结果

分类模式
-> 遍历 checkedCategories
-> 有手工选择则复用
-> 否则重新请求分类直属文件
-> 合并并按 URL 去重

最终文件列表
-> WikiEngineCore.downloadSpecificFiles()
-> 目标目录级 Mutex
-> 读取 .calabiyau-downloads.tsv
-> 安全文件名和大小写不敏感重名处理
-> Semaphore 限制并发
-> 同目录临时文件下载
-> 移动到正式目标
-> 汇总进度和失败
```

共享下载核心位于 `shared/src/commonMain/kotlin/data/WikiEngineCore.kt:224-347`，桌面流式写文件位于 `desktopApp/src/main/kotlin/data/WikiEngine.kt:247-260`。

下载后还可继续：

- 将压缩音频转换为 WAV。
- 可选删除原始压缩音频。
- 合并本次下载或转换得到的 WAV。
- 合并成功后可删除单个 WAV。

上层下载编排位于 `MainViewModel.kt:562-735`。

### 7.6 立绘

应用启动时将桌面平台能力注入共享 `PortraitRepository`：

```text
Desktop WikiEngine
-> PortraitRepository.init(...)
-> PortraitRepositoryCore
-> PortraitLogic
```

首次搜索立绘时会读取以下分类，构建进程内角色索引：

- `分类:角色立绘`
- `分类:晶源体文件`
- `分类:角色时装预览图`

选择角色后，文件名解析逻辑会识别立绘、正面预览、背面预览和额外素材，并按角色与时装组织资源。下载路径为：

```text
<保存根目录>/立绘/<安全角色名>/<安全时装名>/
```

核心实现：

- 平台门面：`shared/src/commonMain/kotlin/data/PortraitRepository.kt`
- 文件名解析和资源归组：`shared/src/commonMain/kotlin/portrait/PortraitLogic.kt`
- 桌面选择与下载：`desktopApp/src/main/kotlin/viewmodel/MainViewModel.kt:359-388,575-629`

## 8. 网络与外部服务

### 8.1 Wiki 客户端

`WikiEngine.client` 是桌面端 Wiki 搜索、用户、投票和文件下载共用的 OkHttpClient：

- connect timeout：30 秒
- read/write timeout：60 秒
- 按 host 保存 Cookie
- 每次请求随机选择 Chrome、Edge 或 Firefox 风格请求头
- 固定 Wiki Referer
- 对 `403`、`429`、`503` 最多额外重试 3 次
- 退避约为 2、4、8 秒并附加随机抖动

实现位于 `desktopApp/src/main/kotlin/data/WikiEngine.kt:86-177`。

Wiki API 基址：

```text
https://wiki.biligame.com/klbq/api.php
```

### 8.2 API 分类

| 能力 | 实现 | 数据来源 |
|---|---|---|
| 分类、文件和角色搜索 | `WikiEngine` / `WikiEngineCore` | MediaWiki API |
| 当前用户和公开用户 | `WikiUserApi` | MediaWiki user/query API |
| Cookie 导入 | `WikiCookieManager` | 用户粘贴的浏览器 Cookie |
| 时装投票 | `VotingApi` | MediaWiki parse、AJAXPoll、CSRF API |
| 平衡数据 | `BalanceDataApi` | `klbq-prod-www.idreamsky.com` |
| 图片和头像 | `ImageLoader` | MediaWiki imageinfo 与 CDN |

`BalanceDataApi` 使用独立 OkHttpClient，不共享 Wiki Cookie 和请求头。

### 8.3 Cookie 与登录

桌面端没有用户名密码登录或 OAuth。登录流程是：

```text
用户粘贴 Cookie
-> WikiCookieManager 规范化和解析
-> 注入 WikiEngine CookieJar
-> meta=userinfo 验证
-> 更新 WikiUserApi.currentUser
```

Cookie 仅保存在进程内存中，不写入 `prefs.json`，应用重启后需要重新导入。

### 8.4 序列化与错误表达

共享 JSON 配置会忽略未知字段并对部分输入值执行容错，位于 `shared/src/commonMain/kotlin/data/SharedJson.kt`。

仓库已经存在共享 `ApiResult` 和 `ErrorKind`，但桌面端几个 API 仍各自维护结果类型或以空列表、`null` 表示失败。因此当前错误语义并不统一，部分网络或解析错误在 UI 上可能表现为“没有结果”。

## 9. 持久化与缓存

### 9.1 用户偏好

桌面偏好写入：

```text
%APPDATA%\CalabiYauVoice\prefs.json
```

当前持久化内容：

- 分类提示是否已关闭
- 主下载目录
- 音频转换目录
- 素材工具输出目录
- 最近 BID 查询
- 最近 WikiID 查询

实现位于 `desktopApp/src/main/kotlin/util/AppPrefs.kt:14-99`。保存采用同目录临时文件、原子移动及普通替换回退。

当前不持久化：

- Cookie 和当前登录用户
- 窗口位置与大小
- 明暗主题
- Backdrop 类型
- 搜索结果与勾选状态
- 工具窗口的局部工作状态

### 9.2 运行时缓存

| 缓存 | 生命周期 | 当前失效方式 |
|---|---|---|
| 官方角色名 | `WikiEngine` 进程内 | 无 TTL；空结果允许后续重试 |
| 分类树 | `MainViewModel` | ViewModel 销毁 |
| 各模式搜索结果 | `MainViewModel` | ViewModel 销毁或新搜索覆盖 |
| 立绘索引 | `PortraitRepository` 进程内 | 无 TTL 和显式刷新 |
| 图片 bitmap/raw bytes | `ImageLoader` LRU | 按条目数淘汰 |
| 头像 URL | `ImageLoader` map | 无 TTL |
| 平衡数据设置 | `BalanceDataApi` | `forceRefresh` 绕过 |
| 当前用户 | `WikiUserApi` StateFlow | 清 Cookie 或重新导入 |

### 9.3 下载清单

每个下载目录使用 `.calabiyau-downloads.tsv` 记录 URL 到本地文件名的映射，以保持重名文件的稳定命名并避免重复下载。

格式：

```text
本地文件名<TAB>原始 URL
```

清单同样通过临时文件和原子替换发布。

## 10. 媒体与原生能力

### 10.1 音频播放

在线播放链：

```text
URL
-> OkHttp Response stream
-> FLAC: JNA/libFLAC
   其他: Java Sound SPI
-> PCM_SIGNED 16-bit
-> 必要时大端转小端
-> 多声道降为双声道
-> 选择本机 SourceDataLine 支持的采样率
-> 流式写入声卡
```

`AudioPlayerManager` 是进程级单实例播放器。开始新播放时会停止旧会话；停止时取消 OkHttp call、关闭音频线路和输入流。

### 10.2 音频转换

转换器支持输入：

- MP3
- FLAC
- OGG
- AAC
- M4A

输出统一为 WAV，可配置采样率、整数位深、float、抖动和合并。

主要实现：

| 文件 | 职责 |
|---|---|
| `util/AudioConverter.kt` | 批量解码、位深转换、重采样、WAV 合并 |
| `util/DesktopAudioDecode.kt` | 素材工具导入音频并转 PCM WAV |
| `util/DesktopWavTools.kt` | PCM WAV 读写和原子发布 |
| `util/PcmDownmix.kt` | 多声道流降混 |
| `ui/screens/Mp3ConverterWindow.kt` | 转换窗口和批处理编排 |

### 10.3 libFLAC

`libFLAC.dll` 只支持 Windows x64。加载顺序：

```text
1. JVM 属性 calabiyau.libflac.path
2. compose.application.resources.dir/libFLAC.dll
3. appResources/windows-x64/libFLAC.dll
4. desktopApp/appResources/windows-x64/libFLAC.dll
```

加载前会校验固定 SHA-256。当前没有 Java FLAC、FFmpeg 或系统解码器回退。

实现位置：

- `desktopApp/src/main/kotlin/jna/flac/NativeFlacDecoder.kt`
- `desktopApp/src/main/kotlin/jna/flac/LibFlac.kt`
- `desktopApp/appResources/windows-x64/libFLAC.dll`

### 10.4 素材工具

`AssetToolsWindow` 集成以下本地能力：

- JPG/PNG 压缩或转换
- 九宫格切分
- 指定比例居中裁切
- GIF 分帧
- 静态图片序列合成 GIF
- 字幕时间轴处理
- PCM 音频裁剪、声道、增益、噪声门、淡入淡出、DC 和相位处理
- 音频历史、撤销/重做和频谱预览/导出

图片和 GIF 主要依赖 Java2D/ImageIO；GIF 编码和音频/频谱核心部分复用 `shared`。

### 10.5 系统集成

桌面端还直接使用：

- FlatLaf `SystemFileChooser` 选择文件和目录
- AWT 原生拖放事件
- `Desktop.open` 打开目录
- `Desktop.browse` 打开浏览器
- AWT 系统剪贴板
- User32、DWM 和 Windows 注册表

这些能力进一步确认了当前实现的 headful、Windows 定向属性。

## 11. 并发、取消与资源生命周期

### 11.1 调度

当前主要约定：

- 网络、下载和文件操作主要使用 `Dispatchers.IO`。
- PCM 算法和频谱计算主要使用 `Dispatchers.Default`。
- Compose UI 和 StateFlow 收集运行在 UI 调度器。
- 在线播放使用独立 daemon thread。
- 部分 Java Sound 解码使用独立单线程 executor 和超时。

### 11.2 取消

共享 OkHttp DSL 中的异步请求会在协程取消时调用 `Call.cancel()`。但部分 API 和图片逻辑仍使用同步 `execute()`；取消协程不能立即中止这类阻塞调用。

主搜索相关 Job 有显式保存和替换。主下载流程只保存 `_isDownloading` 等状态，没有保存可由 UI 调用的下载 Job，因此用户当前不能主动取消长下载，只能依赖所属 scope 销毁。

### 11.3 资源清理

已有的主要保护：

- 子窗口退出 composition 时恢复原生 WndProc。
- Skia 透明层监听器在 `DisposableEffect` 中移除。
- 文件下载和 WAV 输出优先使用临时文件。
- FLAC decoder 正常关闭时释放 native decoder。
- 播放器停止时取消网络并关闭声卡线路和流。
- 音频历史工作目录在控制器销毁时清理。
- WAV 合并失败时关闭输入并回滚本批次结果。

## 12. 构建与分发

### 12.1 开发运行

入口类和 JVM 参数：

```kotlin
mainClass = "MainKt"
jvmArgs += "--enable-native-access=ALL-UNNAMED"
```

根 README 给出的 Windows 运行方式为：

```powershell
./gradlew.bat run
```

也可明确指定模块：

```powershell
./gradlew.bat :desktopApp:run
```

### 12.2 原生分发

`desktopApp/build.gradle.kts:50-67` 当前配置：

- 包名：`CalabiYauVoice_GUI`
- 版本：`2.1.4`
- 目标格式：MSI、EXE
- 安装目录选择
- 桌面快捷方式
- 开始菜单分组
- 固定 Windows upgrade UUID
- `appResources` 随原生分发复制

Release 构建启用 ProGuard optimize，并使用 `desktopApp/proguard-rules.pro`。

常用任务：

```powershell
./gradlew.bat :desktopApp:test
./gradlew.bat :desktopApp:run
./gradlew.bat :desktopApp:createReleaseDistributable
```

具体可用任务仍应以当前 Compose Gradle 插件输出为准：

```powershell
./gradlew.bat :desktopApp:tasks
```

### 12.3 本地依赖与资源

- `desktopApp/libs/window-styler-jvm-0.3.3-SNAPSHOT.jar` 通过 `fileTree("libs")` 加载。
- `desktopApp/appResources/windows-x64/libFLAC.dll` 随 Windows x64 分发。
- `desktopApp/appResources/common/` 保存第三方声明与许可证。

这意味着可复现构建不仅依赖 Maven 坐标，也依赖仓库内本地 JAR 和 DLL 的正确版本。

## 13. 测试现状

桌面测试位于 `desktopApp/src/test/kotlin`，当前主要覆盖：

- `WikiCookieManagerTest`：Cookie 解析与规范化
- `PcmDownmixTest`：PCM 降混
- `DesktopStreamingAudioTest`：流式音频识别和处理
- `DesktopAudioCodecProviderTest`：AAC/Vorbis Java Sound provider
- `AudioConverterTest`：转换失败保护、合并、回滚和重名
- `NativeFlacDecoderTest`：libFLAC 位深、ID3、截断、MD5 和样本数
- `GifImageTest`：GIF 预算与多帧解码
- `DesktopAudioHistoryControllerTest`：历史文件和频谱预算

目前明显缺少：

- 自定义窗口过程和命中测试的自动化验证
- Compose 版本升级后的反射兼容测试
- Release + ProGuard 下的完整启动测试
- 多窗口和窗口关闭期间的任务取消测试
- MediaWiki 失败、WAF HTML 和部分分页失败的契约测试
- 大图片、大 GIF 和大 WAV 的内存压力测试
- 投票部分成功后的恢复测试
- 实际 MP3/AAC/OGG/M4A fixture 的端到端转换测试

## 14. 静态审阅风险

以下按建议处理优先级排序。未标记“已复现”的项目均为静态代码审阅结论。

### 14.1 高优先级

#### Compose 私有 API 和反射耦合

`LayoutHitTestOwner.kt` 依赖 Compose scene 的内部类型、私有字段和内部方法，以识别标题栏上的交互控件。Compose/Skiko 升级或 ProGuard 优化可能导致字段不存在、命中测试失效或窗口初始化失败。

建议：每次升级 Compose 时执行 Windows Release 包的主窗口、子窗口、Snap Layout、拖拽和 resize 回归测试。

#### Windows 平台硬绑定

启动链直接访问 Kernel32、User32、DWM 和注册表，FLAC 也仅提供 Windows x64 DLL。当前不应把桌面模块描述为可运行于 macOS/Linux；如果产品只支持 Windows，应在构建和启动层明确该约束。

#### 网络失败被降级为空结果或部分结果

2026-08-17 已修：`WikiEngineCore` 的搜索、文件搜索、分类成员和分类文件在请求失败、WAF HTML 或解析失败时抛 `WikiQueryFailure`，不再把失败当成空列表。桌面空状态会显示 `searchError`。零结果仍返回空列表。立绘冷索引未改，仍可能缓存不完整结果。

#### 下载只校验 HTTP 成功和非零长度

2026-08-17 已修：`awaitGetToFile` 拒绝 `text/html`，并在落盘后检查 `<!doctype html` / `<html` 前缀。桌面下载保存了 Job，主按钮、菜单和 Esc 可取消。仍未校验 Content-Length、文件签名或 checksum。

#### 媒体内存峰值

PCM 工具会同时持有输入字节、处理结果、历史快照和频谱图；GIF 解码按帧保留完整画布；图片缓存按条目而不是总字节限制。大 WAV、大尺寸多帧 GIF 或大量网络图片可能导致明显内存压力甚至 OOM。

#### 多声道降混 EOF 边界

2026-08-17 已修：干净 EOF 不再被当成完整帧。`PcmDownmixTest` 覆盖了 `AudioSystem.NOT_SPECIFIED` 长度。

### 14.2 中优先级

#### 分类手工选择没有按模式隔离

2026-08-17 已修：桌面和 Android 都按 `SearchMode` 隔离手工选择。切换模式会先关掉文件窗口并恢复该模式的选择。文件窗口仍是非模态，但确认时写入当前模式。

#### 投票提交不是事务

多个候选项和总计锚点按顺序 POST。中途失败会留下部分修改；共用网络拦截器还可能重试 POST。当前提交逻辑只检查 HTTP 状态，未统一解析 MediaWiki 业务错误。

#### 下载缺少显式取消

2026-08-17 已修主下载：`MainViewModel` 保存下载 Job，主按钮、菜单和 Esc 可取消。转换器和素材工具的阻塞式媒体操作仍不一定立即响应取消。

#### 固定大小窗口仍有最大化按钮

`StyledWindow(resizable = false)` 仍复用无条件绘制最大化按钮的窗口框架，并声明 `HTMAXBUTTON`。关于、快捷键等固定窗口的最大化和 Snap 行为可能与 `resizable=false` 不一致。

#### 同步网络和同步偏好 I/O

部分用户、投票和图片请求使用同步 OkHttp `execute()`；协程取消无法立即终止底层 call。`AppPrefs` setter 会在锁内同步写磁盘，某些输入或最近查询更新可能发生在 UI 事件路径。

#### 系统主题只用于初始化

主窗口使用系统主题初始化 `darkMode`，但后续系统明暗切换不会自动同步。Native WndProc 虽然追踪主题消息，目前没有连接回 `AppState.darkMode`。

#### 图片和 GIF 解码边界

GIF 展示侧没有完整实现 `restoreToPrevious` 和文件 loop count；大 GIF 的总帧内存预算不完整。动画占位回退还可能在组合线程同步读取首帧。

#### FLAC 没有后端回退

DLL 缺失、哈希不一致或 ABI 加载失败会同时影响播放、素材工具和转换器。当前提示用户改用另一个内置工具并不能绕过同一个 native decoder。

### 14.3 低优先级或一致性问题

2026-08-17 复查时已修：Cookie 非法 `%` 编码不再抛错；过期 Cookie 不再发出；登录导入后会拉用户摘要；转换全失败不再显示“完成”；合并 WAV 不再用 `_mp3conv_tmp_*` 当文件名；播放器会尽早注册 FLAC 解码流。

仍开着：

- `StyledWindow` 文档称内容收到组合后的 inset modifier，当前实际传入的是空 `Modifier`，接口契约与实现不一致。
- 主窗口和 `StyledWindow` 对 frame/window inset 的职责有重叠，后续窗口框架修改容易引入重复 padding。
- `LogWindow` 接收 `progressText`，当前未显示该参数。
- 文件搜索模式下，菜单“全选/全不选”和快捷键可能操作不同的选择集合。
- Cookie 输入框明文回显，存在屏幕共享或截图泄漏风险。
- `desktopApp/bin/main` 存在不参与 Gradle 构建的源码镜像，容易让搜索或人工编辑命中错误副本；实际源码以 `desktopApp/src/main/kotlin` 为准。
- 本地 `SNAPSHOT.jar` 和 beta WebView 增加了依赖升级与可复现构建风险。

## 15. 建议的阅读顺序

首次理解桌面端时，建议按以下顺序阅读：

1. `desktopApp/build.gradle.kts`
2. `desktopApp/src/main/kotlin/Main.kt`
3. `desktopApp/src/main/kotlin/AppStore.kt`
4. `desktopApp/src/main/kotlin/ui/screens/NewContent.kt`
5. `desktopApp/src/main/kotlin/viewmodel/MainViewModel.kt`
6. `desktopApp/src/main/kotlin/data/WikiEngine.kt`
7. `shared/src/commonMain/kotlin/data/WikiEngineCore.kt`
8. `shared/src/commonMain/kotlin/data/PortraitRepository.kt`
9. `shared/src/commonMain/kotlin/portrait/PortraitLogic.kt`
10. `desktopApp/src/main/kotlin/ui/components/StyledWindow.kt`
11. `desktopApp/src/main/kotlin/ui/components/WindowsWindowFrame.kt`
12. `desktopApp/src/main/kotlin/jna/windows/ComposeWindowProcedure.kt`
13. 具体工具窗口和 `util/` 媒体实现

## 16. 维护边界

后续修改桌面端时，建议保持以下边界：

- 通用 Wiki 搜索、分类、下载命名和立绘解析优先放在 `shared`。
- Compose Window、AWT、Java Sound、JNA 和 Windows API 保留在 `desktopApp`。
- `AppState` 保持为窗口视觉状态，不把主业务状态继续堆入全局 CompositionLocal。
- 网络错误应逐步统一到明确的结果类型，避免继续用空列表代表所有失败。
- 引入新的进程级缓存时，应同时定义容量、TTL、清除入口和并发语义。
- 长任务应明确拥有者、取消入口、临时文件清理和窗口关闭行为。
- 升级 Compose、Skiko、JNA、Window Styler 或 ProGuard 规则时，必须验证自定义窗口链。
- 修改媒体算法时，优先补充小型 fixture 和资源生命周期测试，再扩展 UI。
- Fluent 控件坐标已切到 Nucleus fork；窗口栈在完成第 17 节检查清单前不要一起迁。

## 17. Nucleus Fluent / 窗口迁移

目标库：[NucleusFramework/compose-fluent-ui](https://github.com/NucleusFramework/compose-fluent-ui)。不要把控件升级和窗口替换绑成一次改动。

### 17.1 已完成

2026-08-17 只替换了 Fluent 控件坐标。随后阶段 A 已落地：入口改为 `nucleusApplication(backend = NucleusBackend.Awt, enableSingleInstance = false)`，主窗口和 `StyledWindow` 走 `FluentDecoratedWindow`，图片预览走 `FluentDecoratedDialog`。旧 WndProc、Window Styler SNAPSHOT 和 `jna-platform` 已删除。FLAC 仍用 JNA。`decorated-window-fluent` 传递引入的 Tao 后端必须在 Gradle 中排除，否则 Tao 会取代 Swing 的 `Dispatchers.Main`，导致 Compose Desktop Lifecycle 主线程检查失败。AWT 阶段没有原生 Mica/Acrylic API，Backdrop 菜单只保留选项；真正的 DWM 材质等 Tao 阶段再接。

| 项 | 旧值 | 新值 |
|---|---|---|
| Maven group | `io.github.compose-fluent` | `dev.nucleusframework.composefluent` |
| 版本 | `v0.1.0` | `1.0.0` |
| 模块 | `fluent`、`fluent-icons-extended` | 同名 |
| 源码包名 | `io.github.composefluent.*` | 未改 |

配置位置：`gradle/libs.versions.toml` 的 `compose-fluent`。`desktopApp/build.gradle.kts` 仍通过 `libs.compose.fluent` 引用，无需改调用点。

已验证：`:desktopApp:compileKotlin` 通过。当时只有既有的 `painterResource` 弃用警告，没有新的 Fluent API 编译错误。

阶段 A 仍保留 `io.github.kdroidfilter:composewebview` 和其他 AWT 集成；旧 `application {}`、`WindowsWindowFrame`、窗口 JNA 和 Window Styler 已移除。

### 17.2 不要一次做完的原因

`FluentDecoratedWindow` 是 `NucleusApplicationScope` 的扩展，必须放进 `nucleusApplication {}`，不能继续塞进现有 `application {}`。

完整窗口迁移还会碰到：

1. Nucleus `1.0.0` 绑定 Nucleus `2.2.0`；Nucleus 最新已到 `2.4.x`，AWT 后端已弃用。
2. 推荐的 Tao 后端没有 AWT。当前 Wry WebView、FlatLaf 文件选择、拖放、剪贴板、`Desktop.open/browse` 和 AWT 异常框都会受影响。
3. 窗口 JNA 可以删，`libFLAC` 仍需要 JNA，不能整库移除。
4. Backdrop 不是 1:1。现有 `Tabbed` / `Aero` / `Transparent` 没有严格对应；Nucleus 是 `Mica` / `MicaAlt` / `Acrylic`。
5. 现有发布链是 Compose Desktop jpackage MSI/EXE + ProGuard，不是 Nucleus Gradle 插件。

### 17.3 后续阶段

按顺序做，前一阶段通过后再开下一阶段。

#### 阶段 A：窗口迁到 Nucleus，先钉 AWT

目标：删掉自写标题栏和 Window Styler，但先保住现有 AWT 集成。

1. 增加 `decorated-window-fluent:1.0.0`。它会带上 Nucleus application / Tao 运行时；AWT 阶段必须从该依赖排除 `nucleus.decorated-window-tao`，并在入口显式使用 `NucleusBackend.Awt`，不要用 `Auto`。
2. 把 `Main.kt` 的 `application {}` 换成 `nucleusApplication(backend = NucleusBackend.Awt, enableSingleInstance = false)`。当前产品没有单实例锁。
3. 把 `FluentTheme` 提到 decorated window 外层。Tao 以后每个窗口是独立 Compose scene；现在外提可以避免后面再拆一次。
4. 用 `FluentDecoratedWindow` + `FluentTitleBar` 替换 `Main.kt` 和 `StyledWindow`。保留现有 `WindowState`、图标、`resizable`、`onKeyEvent`。
5. `ImagePreviewDialog` 单独迁到 `FluentDecoratedDialog`，并显式 `resizable = true`。Nucleus 对话框默认不可缩放。
6. 去掉自定义 `captionBarHeight` / `windowInsetsPadding` 叠加，避免和 Nucleus 标题栏重复留白。
7. 核对 `LocalAppStore` 是否还传到所有子窗口。
8. 手动回归 12 个窗口：主窗口、关于、快捷键、用户信息、音频转换、素材工具、Wiki 浏览器、创作者中心、平衡数据、投票、日志、文件选择、图片预览。
9. 通过后再删：
   - `desktopApp/src/main/kotlin/jna/windows/`
   - `ui/components/WindowsWindowFrame.kt`
   - `ui/components/CaptionButtonRow.kt`
   - `util/TransparentSkiaLayer.kt`
   - `util/SkikoUtil.kt`
   - `desktopApp/libs/window-styler-jvm-0.3.3-SNAPSHOT.jar`
10. 保留 `jna/flac/`、`jna-jpms` 和 `libFLAC.dll`。
11. 从 ProGuard 规则里去掉 Window Styler keep；JNA keep 留下。
12. 跑 `:desktopApp:test` 和 `:desktopApp:createReleaseDistributable`。

窗口映射：

| 现有入口 | Nucleus API |
|---|---|
| 主窗口 `Main.kt` | `FluentDecoratedWindow` |
| 10 个 `StyledWindow` 调用点 | `FluentDecoratedWindow` |
| `ImagePreviewDialog` 的 `DialogWindow` | `FluentDecoratedDialog` |

Backdrop 建议：

| 现有选项 | Nucleus 映射 |
|---|---|
| `Tabbed` | `WindowsBackdropStyle.MicaAlt` |
| `Mica` | `WindowsBackdropStyle.Mica` |
| `Acrylic` | `WindowsBackdropStyle.Acrylic` |
| `Aero` | 没有严格等价，可暂用 Acrylic |
| `Transparent` | 没有直接等价，需单独设计 |
| `null` / 渐变 | 不启用窗口 backdrop |

AWT 后端上窗口级 backdrop 可能不可用；这一项以阶段 B 为准。

#### 阶段 B：Tao 及相关系统集成

只有阶段 A 的 AWT 窗口稳定后再做。Tao 会拆掉 AWT，需要先换：

- Wiki / 创作者中心 WebView：从 `io.github.kdroidfilter:composewebview` 换到 Nucleus Tao `NativeView` WebView
- 文件选择：FlatLaf `SystemFileChooser` 换原生对话框
- 素材工具和转换器拖放：不要再强转 `DropTargetDropEvent`
- 剪贴板、`Desktop.open/browse`、AWT 异常框
- ProGuard：合并 Tao ServiceLoader / dispatcher 规则，并确认原生库打进 MSI/EXE

阶段 B 完成前不要把发布链改成 Nucleus Gradle 插件或 GraalVM。现有 jpackage 配置可以先留着，除非打包已经明确缺原生库。

#### 阶段 C：跨平台准备，不要先换皮肤

Linux / macOS 支持不要从换 Yaru / macOS 26 控件开始。官方 gallery 也是三端共用 Fluent 窗口，只用 `hostOs` 微调标题栏和 backdrop。本项目应先做到“同一套 Fluent UI 能在三端启动”，再考虑是否按系统换设计语言。

参考：

- [gallery/src/desktopMain/.../Main.kt](https://github.com/NucleusFramework/compose-fluent-ui/blob/master/gallery/src/desktopMain/kotlin/io/github/composefluent/gallery/Main.kt)
- [gallery/src/desktopMain/.../window/WindowFrame.kt](https://github.com/NucleusFramework/compose-fluent-ui/blob/master/gallery/src/desktopMain/kotlin/io/github/composefluent/gallery/window/WindowFrame.kt)
- [gallery/src/desktopMain/.../PlatformCapabilities.desktop.kt](https://github.com/NucleusFramework/compose-fluent-ui/blob/master/gallery/src/desktopMain/kotlin/io/github/composefluent/gallery/PlatformCapabilities.desktop.kt)

gallery 的实际分层：

```text
nucleusApplication
└── FluentThemeConfiguration          // 只提供 Local，不在 application 层排放布局
    └── FluentDecoratedWindow         // 三端同一窗口 API
        └── WindowFrame
            ├── hostOs.isWindows -> WindowsBackdrop
            ├── !hostOs.isMacOS  -> WindowControls
            ├── LocalWindowChromeInsets.controlsInsets
            └── App(...)              // 业务 UI 不感知 HWND / DWM
```

可直接学的点：

1. 入口用 `nucleusApplication`，窗口用 `FluentDecoratedWindow`，不要继续自写 WndProc。
2. 主题用 `FluentThemeConfiguration` 包在窗口外；完整 `FluentTheme` 只放窗口内。Tao 每个窗口是独立 Compose scene。
3. 系统暗色用 Nucleus `getPlatformDarkModeDetector()`，不要依赖 AWT `LocalSystemTheme`。
4. 用 `org.jetbrains.skiko.hostOs` 判断能力：`supportsWindowBackdrop = hostOs.isWindows`。
5. macOS 用原生 traffic lights，不要再画一套 `WindowControls`；Windows / Linux 用 `WindowControls`。
6. 标题栏交互控件加 `Modifier.noWindowDrag()`，空白区域加 `windowDragArea()`。
7. 用 `LocalWindowChromeInsets.current.controlsInsets` 给标题栏让位，不要手写 Win11 caption 高度。
8. Windows backdrop 只在 `hostOs.isWindows` 时调用；Linux / macOS 走 Compose 绘制的 mica/渐变。
9. 业务 UI 继续留在 `App` / `NewDownloaderContent`，不要把 `User32`、DWM、注册表带进页面。

本项目里必须先抽象、不能直接搬到 Linux / macOS 的点：

| 能力 | 现状 | 跨平台替换 |
|---|---|---|
| 窗口框架 | 自写 JNA WndProc + Window Styler | 阶段 A/B 的 `FluentDecoratedWindow` |
| 文件选择 | FlatLaf `SystemFileChooser` | [FileKit](https://nucleusframework.dev/en/docs/ecosystem/file-dialog/) |
| 拖放 | 强转 AWT `DropTargetDropEvent` | Compose/Tao `files: List<String>` |
| 打开目录/浏览器 | `java.awt.Desktop` | expect/actual 或 Nucleus/Desktop 封装 |
| 偏好路径 | `%APPDATA%\CalabiYauVoice` | Windows Roaming、macOS `~/Library/Application Support`、Linux `XDG_CONFIG_HOME` |
| FLAC | 仅 `windows-x64/libFLAC.dll` | 按 OS/arch 分发 `.dll` / `.so` / `.dylib`，或非 Windows 回退 Java Sound |
| WebView | Wry/AWT `composewebview` | Nucleus Tao `NativeView` WebView |
| 打包 | 只配了 Windows MSI/EXE | 另加 Deb/Dmg，或再评估 Nucleus 打包 DSL |

建议顺序：

1. 先完成阶段 A/B，让 Windows 不再依赖自写窗口 JNA。
2. 抽出 `DesktopPlatform` / `AudioDecoder` / `FileDialogs` / `AppDirs` 这类 expect 或接口，Windows 仍走现有实现。
3. 打开 Linux x64 和 macOS arm64 的编译与手动启动，允许 FLAC/WebView 降级，不要一上来追求功能对等。
4. 打包先加 Compose Desktop 的 `linux {}` / `macOS {}`，确认 jpackage 能出包。
5. 三端 Fluent 窗口稳定后，再决定 Linux 是否换 Yaru、macOS 是否换 `MacosDecoratedWindow`。那会重写几乎全部控件，不是跨平台的第一步。

不要做的：

- 不要把 `desktopApp` 先拆成 KMP `desktopMain/linuxMain/macosMain` 再迁窗口。现在是普通 Kotlin/JVM 模块，先换窗口更便宜。
- 不要为了 Linux/macOS 先引入 Yaru / macOS 26 设计系统。gallery 自己也没这么做。
- 不要假设删掉 JNA 就算跨平台完成。FLAC 仍然要原生库或解码回退。
- 不要在 Tao 落地前继续往页面里加 AWT/`User32` 调用。

### 17.4 回归清单

每次窗口或后端改动后至少检查：

- 主窗口启动、拖拽、缩放、最小化、最大化、关闭、系统菜单、Snap Layout
- 所有子窗口开关、默认尺寸、不可缩放窗口不会被最大化
- 主题和 Backdrop 切换
- Wiki WebView 滚动、导航、Cookie
- 素材工具和转换器拖放
- 文件/目录选择
- 图片预览缩放、拖拽、复制链接、浏览器打开
- 在线音频播放和 FLAC 解码
- Release + ProGuard 包能启动，且 `libFLAC.dll` 仍在分发资源里
- 若已打开 Linux/macOS：无 Windows API 的启动路径、文件选择、偏好目录、FLAC 降级提示
