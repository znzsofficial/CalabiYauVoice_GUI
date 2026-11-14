# Android 端整体 UI 优化与重构计划

制定日期：2026-11-14。基于 **2.1.30（60）** 发布后及工作区已完成的多页 UI 改版现状梳理，用于指导后续分批实施与验收。

## 范围与边界

### 纳入范围

- 跨页面导航一致性、顶栏规范、提示机制（Snackbar）与 Edge-to-Edge 安全区适配。
- 下载页、设置页、存储页的布局细节与主题色收敛。
- 剩余仍停留在全屏转圈（`LoadingState`）的 Wiki 页面骨架屏补齐。
- `core/ui/` 设计 Token（圆角、间距、字号、图标尺寸）的逐步收敛。

### 明确不做（保持既有约束）

- 不恢复已否决的旧视觉方案：角色详情页 `440.dp` 全宽顶部裁切立绘、`28.dp` 语录引号图标、工具页 Mesh 背景及已删除的 `ToolPageIntro` 顶部横幅（见 [工具页视觉回退记录](tools-mesh-tab-surfaces.md)）。
- 不修改已通过评审的页面交互基线：例如关于页不在版本胶囊显示「复制」文字、不增加「检查更新」按钮（更新入口保留在设置页）。
- 不改动已验证的业务逻辑、解析器、WebView Cookie 票据管理、资源下载 manifest/原子写入、文件管理根目录校验与分享码编解码协议。
- 不新建大而全的万能页面或额外引入第三方 UI 依赖。

---

## 现状基线（已覆盖 vs 待改进）

工作区已完成并验证通过的近期 UI 改版（未提交）：

- 关于页（`AboutScreen.kt`）、更新弹窗（`UpdateAvailableDialog.kt`）、留言板（`MessageBoardScreen.kt`）。
- 首页（`WikiHomePage.kt`）、角色详情（`CharacterDetailScreen.kt`）、武器详情（`WeaponDetailScreen.kt`）、生化卡牌（`BioCardScreen.kt`）。
- 武器列表与平衡数据的对齐骨架屏（`WeaponListScreen.kt`、`BalanceDataScreen.kt`）。

尚未统一处理的全局与遗留问题分为以下 **4 个阶段** 推进。

---

## Phase 1：全局导航、Snackbar 与下载页一致性（高优先级）

本阶段解决真实可用性与交互冲突问题，改动面小、收益高。

### 1.1 平板 / 折叠屏展开模式隐藏无效抽屉按钮

- **现状**：`core/navigation/MainScreen.kt` 在 `WindowWidthSizeClass.Expanded` 时改用 `PermanentNavigationDrawer`，`openDrawer` 回调内部判断 `!useExpandedLayout` 后成为空操作；但 `WikiHomePage.kt` 与 `DownloaderScreen.kt` 仍始终展示左上角抽屉触发按钮，点击无反馈。
- **方案**：
  - 将 `showDrawerButton = !useExpandedLayout`（或等效标志）透传至 `WikiHubScreen` → `WikiHomePage` 与 `DownloaderScreen`。
  - 常驻侧栏模式下隐藏顶栏抽屉图标（`WikiHomePage` 同步保留对称标题布局）。

### 1.2 统一一级抽屉页顶栏菜单图标

- **现状**：`DownloaderTopBar`（`feature/download/DownloaderScreen.kt`）使用 `BackNavButton(onClick = onOpenDrawer)`，图标为返回箭头（`ArrowBack`），与 `WikiHomePage` 使用的 `Icons.Outlined.Menu` 不一致，容易误导用户以为是“返回上一页”。
- **方案**：
  - 将 `DownloaderTopBar` 左侧按钮更换为 `Icons.Outlined.Menu`（可保留一致的圆底/图标按钮尺寸与无障碍描述 `"菜单"`）。

### 1.3 消除资源下载页双重 `SnackbarHost` 叠弹

- **现状**：`MainScreen.kt` 提供了全局 `LocalSnackbarHostState` 并收集 `searchVM.errorEvent`；同时 `DownloaderScreen.kt` 内部又自建局部 `SnackbarHostState` 并再次收集 `searchVM.errorEvent`（带「重试」动作），出错时会同时触发两个 Snackbar。
- **方案**：
  - 由 `DownloaderScreen` 统一承担 `searchVM` / `downloadVM` 的错误提示（或让 `MainScreen` 不再重复消费当前处于下载页时的 `searchVM.errorEvent`），确保全应用同一时间只存在单一清晰的提示宿主。
  - 若保留 `DownloaderScreen` 局部宿主以配合底栏避让，需从 `MainScreen` 移除重复收集，并确保页面内的 `rememberSnackbarLauncher()` 也能正确避开悬浮工具栏/底栏。

### 1.4 下载页悬浮工具栏模式遮挡与横幅规范化

- **现状**：
  - 启用悬浮工具栏（`AppPrefs.BAR_STYLE_DOCKED_TOOLBAR`）时，高度 `64.dp` + 上移 `16.dp` 的工具栏悬浮在列表底部之上。`CategoryGroupList`（`CategoryComponents.kt`）、`PortraitGrid`（`PortraitComponents.kt`）和 `FileSearchList`（`FileSearchComponents.kt`）均使用固定 `16.dp` 底部内边距，最后一项容易被悬浮栏遮挡；`FileSearchList` 选中文件后出现的底部「下载选中文件」按钮栏也会与悬浮工具栏重叠。
  - `DownloaderScreen.kt` 顶部的“有未完成的下载任务”提示当前只是裸放的 `Column + Text + Row(TextButton)`，与同区域的 `NetworkBanner`、`SearchErrorBanner` 视觉风格割裂。
- **方案**：
  - 向 `CategoryGroupList`、`PortraitGrid`、`FileSearchList` 传入随底栏模式及下载状态栏变化的安全底部留白（悬浮工具栏可见时至少预留 `96.dp` 底部空间）；在 `FileSearchList` 出现底部下载确认条时隐藏或自动抬高悬浮工具栏，避免控件叠压。
  - 将“未完成任务恢复条”抽成与 `NetworkBanner` 同体系的圆角 `Surface` 提示卡片，对齐边距、字号与按钮风格。

### 1.5 文件管理与素材工具的交互及视觉梳理

在严格遵循“不加 Mesh、不恢复 `ToolPageIntro` 横幅”的前提下，补齐另外两个工具页的交互细节与一致性：

1. **文件管理（`feature/tools/FileManagerScreen.kt`）**：
   - **顶栏规范化**：普通模式与多选模式当前使用手写 `Row + statusBarsPadding()`，建议统一对齐 `TopAppBar` 高度/内边距规范（普通模式左按钮使用 `BackNavButton` 风格或保持多选模式 `Close` 一致）。
   - **加载态骨架**：`isLoading` 当前为全屏居中 `CircularProgressIndicator()`，可替换为 6–8 行对齐 `FileListItem`（左侧文件图标块 + 两行文本条）的轻量化骨架。
   - **安全区与弹层留白**：文件操作 `ModalBottomSheet` 底部目前硬编码 `AppSpacing.xxxLarge`，图片画廊 `Dialog` 未加 `systemBarsPadding()`；需统一补齐 `navigationBarsPadding()`。
   - **音频播放迷你条**：在列表内直接点击播放音频时，目前仅靠列表行底色高亮提示，滚动离开后无法快捷停止；可在列表顶部或底部增加一条轻量“正在播放 + 停止”控制条（复用 `AudioPlayerManager` 状态，不改播放链路）。

2. **素材工具（`feature/tools/ToolsHomeScreen.kt` 及 4 个子页）**：
   - **修复 `ToolCard` 整卡点击误收起**：当前 `Modifier.clickable { expanded = !expanded }` 挂在包含展开内容的外层 `Column` 上，点击展开区空白处会意外折叠整个工具卡片；应将点击区域收敛到卡片标题行（`Row`），仅标题行控制展开/收起。
   - **入口卡片右侧图标语义**：`ToolsSectionCard` 右侧目前统一显示 `Icons.Outlined.BuildCircle`，看起来像静态装饰或状态图标，建议改为 `Icons.AutoMirrored.Filled.KeyboardArrowRight` 明确表达“进入子页”。
   - **顶栏收敛**：首页态的静态 `LargeTopAppBar`（未接 `scrollBehavior`）改为紧凑 `TopAppBar`，与图片/文本/音频/视频四个子页的 `TopAppBar` 保持一致（同时归入 Phase 2 顶栏规范）。
   - **边到边底部留白**：`ToolsHomeContent`、`ToolPageColumn`（图片/音频/视频子页）和 `TextToolsPage` 统一将底部安全区作为滚动 `contentPadding`（而非外层容器裁切），保证滚动到底时内容贴合手势条上方且不留白边。

---

## Phase 2：顶栏规范与 Edge-to-Edge 安全区收敛

本阶段解决跨页面视觉割裂与全面屏底部手势条适配。

### 2.1 顶栏形态分类收敛

- **现状**：
  - `ToolsHomeScreen.kt` 在首页态使用了静态 `LargeTopAppBar`（未绑定 `scrollBehavior`），占用大块顶部空间且不随滚动收起。
  - `ActivityScreen.kt`、`StoryScreen.kt`、`MeowLanguageScreen.kt`、`GameTipsScreen.kt`、`GameHistoryScreen.kt` 仍使用可折叠 `LargeTopAppBar`，而近期改版页（`WeaponDetailScreen`、`BioCardScreen`、`MessageBoardScreen`、`PcGameSettingsScreen`）均已收敛为紧凑 `TopAppBar`。
  - 返回按钮在全应用存在三种形态：`BackNavButton`（圆底 `FilledTonalIconButton`）、裸 `IconButton`（`MessageBoardScreen`、`FileManagerScreen`）、手写 `Row` 顶栏（`SettingsScreen`、`StorageSettingsScreen`）。
- **方案**：
  - **规则约定**：
    1. 一级抽屉页（首页、资源下载）：顶栏左按钮为菜单图标；仅首页保留可折叠大标题。
    2. 普通二级子页：统一使用紧凑 `TopAppBar` + `BackNavButton`（或明确规范 `BackNavButton` 为全应用二级页标准返回控件），去除无 `scrollBehavior` 的静态 `LargeTopAppBar`。
    3. 逐步将 `ToolsHomeScreen`、`ActivityScreen`、`StoryScreen`、`MeowLanguageScreen`、`GameTipsScreen`、`GameHistoryScreen` 统一为 `TopAppBar`，并将 `SettingsScreen`、`StorageSettingsScreen` 的手写 `Row` 顶栏替换为标准 `TopAppBar`（保留 `headlineMedium` / `titleLarge` 视觉重心或与二级页一致的 `title` 规范）。

### 2.2 公共弹层与设置滚动页补齐底部安全区

- **现状**：
  - `core/ui/WikiSheetComponents.kt` 中的 `WikiContentSheet` 与 `WikiScrollableSheet` 仅使用硬编码 `bottom = 32.dp`。
  - `SettingsScreen.kt` 与 `StorageSettingsScreen.kt` 的垂直滚动区域底部也仅放置了 `32.dp` 间距，未叠加 `WindowInsets.navigationBars`。
- **方案**：
  - 为 `WikiContentSheet`、`WikiScrollableSheet` 内容容器补充 `navigationBarsPadding()`（叠加适度视觉留白如 `16.dp`–`24.dp`），确保手势条与三键导航栏设备均不被裁切或紧贴边缘。
  - 为 `SettingsScreen.kt`、`StorageSettingsScreen.kt` 及其余仍使用固定底部留白的滚动页统一加上 `navigationBarsPadding()`（或由 `Scaffold` `innerPadding` 正确传导底部 inset，避免外层 `Scaffold` 吞掉而内层未消费）。

---

## Phase 3：遗留 Wiki 页面骨架屏补齐

本阶段消除进页瞬间“整页白屏 + 居中转圈”带来的跳变感，使加载态与真实卡片结构对齐。

### 涉及页面清单（共 10 页）

目前以下页面在 `ApiResourceContent` 的 `loading` 插槽仍传入 `LoadingState(...)`：

| 页面文件 | 页面名称 | 真实布局特征 | 建议骨架结构 |
| --- | --- | --- | --- |
| `feature/wiki/game/GameModeScreen.kt` | 战斗模式 | 纵向可折叠模式大卡片列表（左图标 + 标题 + 副标题） | 4–5 张与 `GameModeCard` 同尺寸圆角骨架卡 |
| `feature/wiki/bgm/BgmScreen.kt` | 游戏原声 / BGM | 搜索框 + 2 排筛选/分组胶囊 + 分组曲目卡片 | 搜索框占位 + 胶囊行 + 带封面缩略图的行骨架 |
| `feature/wiki/story/StoryScreen.kt` | 剧情故事 | 分区卡片（标题 + 16:9 封面/条目芯片列表） | 分区标题 + 横向/网格章节骨架卡 |
| `feature/wiki/meow/MeowLanguageScreen.kt` | 喵言喵语 | 分区卡片 / 语音条目列表 | `WikiListSkeleton` 或带播放按钮占位的条目骨架 |
| `feature/wiki/tips/GameTipsScreen.kt` | 游戏 Tips | 分类提示卡片列表 | 标题 + 多行正文骨架卡 |
| `feature/wiki/history/GameHistoryScreen.kt` | 游戏历史 / 版本沿革 | 时间线 / 版本事件卡片列表 | 左侧时间点指示 + 标题与正文块骨架 |
| `feature/wiki/navigation/NavigationMenuScreen.kt` | Wiki 导航 | 可折叠分区卡片（图标 + 标题 + 条目数） | 6 张折叠态分区骨架卡 |
| `feature/wiki/submission/SubmissionScreen.kt` | 玩家投稿 | 筛选条 + 投稿作品网格/卡片 | `CatalogGridSkeleton` 或卡片骨架列表 |
| `feature/wiki/collaboration/CollaborationScreen.kt` | 联动数据 | 联动专题大卡（大图 + 标题 + 描述） | 横幅图占位 + 标题行 + 摘要行骨架 |
| `feature/wiki/meme/MemeScreen.kt` | 梗百科 | 搜索栏 + 词条卡片列表 | `WikiListSkeleton(chipRows = 0)` 或词条卡片骨架 |

### 实施原则

- 可直接复用 `core/ui/SkeletonComponents.kt` 中的 `ShimmerBox`、`SkeletonCard`、`WikiListSkeleton`、`CatalogGridSkeleton`；结构差异较大的页面（如 `BgmScreen`、`GameModeScreen`、`CollaborationScreen`）在本文件或同模块补充专用轻量 `*Skeleton` 组合项。
- **不改变** 各页面既有 `rememberLoadState` 缓存策略、`LocalHasWallpaper` 透明背景适配及空/错误状态组件。

---

## Phase 4：设置页细节与设计系统 Token 收敛

### 4.1 存储图表色值主题化

- **现状**：`feature/settings/StorageSettingsScreen.kt` 中 `CacheCategory` 硬编码了 `0xFF4F8CFF`、`0xFF22C55E`、`0xFFF59E0B` 三个十六进制色值，不随动态取色、`ColorSpec2025` 或高对比度模式自适应，在部分主题色下对比度与风格协调性较弱。
- **方案**：
  - 将存储占比图条与图例色值映射为 `MaterialTheme.colorScheme` 语义角色色（例如：`primary`、`secondary`、`tertiary`、`error` 或各自对应的 `Container` / 高区分度组合），或仅保留符合当前调色板对比度的动态派生色。

### 4.2 设置页分组间距与单选弹窗统一

- **现状**：
  - `SettingsScreen.kt` 分组之间混用 `AppSpacing.itemGap`（`8.dp`）与 `AppSpacing.sectionGap`（`16.dp`），「外观」到「首页」与「存储」到「关于」间距不一致。
  - 「快捷入口」「底栏样式」单选弹窗采用 `AlertDialog(..., confirmButton = {})`，未提供明确的「取消/关闭」按钮，且与 Wiki 页面近期使用的 `WikiScrollableSheet` + `SelectionOptionRow` 体验分裂。
- **方案**：
  - 统一 `SettingsScreen` 各分组间的垂直间距为同一 Token（推荐 `AppSpacing.itemGap` 配合 `SettingsGroupHeader` 自带的 `top = AppSpacing.cardContent`）。
  - 为单选 `AlertDialog` 补齐 `dismissButton`（「取消」），或统一抽成 `SettingsSingleChoiceSheet` / 复用 `SelectionOptionRow`。

### 4.3 逐步收敛散落硬编码 UI 常量

- **现状**：`core/ui/` 已定义 `AppShapes`、`AppSpacing`、`AppSizes`、`AppTextStyles`，但各业务页面中仍大量直接书写 `smoothCornerShape(24.dp)`、`padding(16.dp)`、`FontWeight.Bold` 等常量。
- **方案**：
  - 不做无意义的全仓机械批量替换，避免制造海量无实质收益的 diff。
  - 约定在 Phase 1–4 实际触达的文件以及所有后续新建/改版页面中，强制优先引用 `AppShapes`、`AppSpacing`、`AppSizes`、`AppTextStyles`；必要时向 `core/ui` 补充缺失的标准 Token（例如标准化顶栏图标按钮尺寸、标准列表内边距）。

---

## 建议执行顺序与提交策略

1. **前置收尾（建议优先处理）**：先确认并提交或分批整理工作区现有已验证的未提交改动，避免新重构与旧改版混杂在同一个大 diff 中。
2. **第一批（Phase 1）**：导航菜单按钮、下载页双重 Snackbar、悬浮工具栏避让与恢复任务卡片化。完成后单独跑 `:androidApp:compileDebugKotlin`、`:androidApp:testDebugUnitTest`、`:androidApp:lintDebug`。
3. **第二批（Phase 2）**：顶栏规范收敛（先处理 `ToolsHomeScreen`、`SettingsScreen`、`StorageSettingsScreen` 及各二级页 `TopAppBar`）与公共弹层/滚动页 `navigationBarsPadding()`。
4. **第三批（Phase 3）**：按模块分批补齐 10 个遗留页面的骨架屏（先做高频的 `GameModeScreen`、`BgmScreen`、`StoryScreen`、`NavigationMenuScreen`，再补其余 6 页）。
5. **第四批（Phase 4）**：`StorageSettingsScreen` 主题色图表、`SettingsScreen` 间距与单选交互，以及触达文件内的 Token 对齐。

---

## 验证与真机验收清单

每批改动完成后需完成以下检查：

1. **构建与静态检查**：
   - `.\gradlew.bat :androidApp:compileDebugKotlin`
   - 变更模块及受影响区域的单元测试（`:androidApp:testDebugUnitTest`）
   - `.\gradlew.bat :androidApp:lintDebug`（保持 0 errors，不新增 warnings）
   - `git diff --check`
2. **真机 / 模拟器手检项**（当前工作环境无连接设备，合入或发版前实测）：
   - **宽屏 / 折叠屏展开态**：`PermanentNavigationDrawer` 下首页和下载页顶栏是否隐藏多余菜单按钮。
   - **下载页两种底栏模式**：悬浮工具栏 + 搜索结果 + 多选下载栏 + 进度条同时存在时是否互相遮挡；触发搜索报错时是否只弹出一个带重试的 Snackbar。
   - **文件管理与素材工具**：文件管理操作弹层与画廊预览的底部安全区、音频播放迷你控制条；素材工具展开卡片内点击空白不误收起、`ToolCard` 展开动效与底部滚动留白。
   - **Edge-to-Edge 安全区**：三键导航栏与全面屏手势条设备上，`WikiContentSheet`、`WikiScrollableSheet`、`SettingsScreen`、`StorageSettingsScreen` 底部交互区是否可正常点击且不被遮挡。
   - **大字号与矮屏**：系统字体调大后，`TopAppBar` 标题、设置单选弹窗与未完成任务恢复条不发生文字截断或按钮溢出。
   - **动态取色与深浅色**：`StorageSettingsScreen` 图表在默认配色、壁纸取色、AMOLED 深色与高对比度模式下均清晰可辨。
