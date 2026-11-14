# PC 游戏设置：原生实用指南

纳入 [2.1.30（60）](releases/2.1.30.md)，最终交付状态与产物以发布记录为准。

## 入口与分层

原生入口由 Hub 的「游戏延伸 → 实用指南 → 游戏设置（PC）」接入。页面资料来自 BWiki **游戏设置PC端**，只读展示。

实现位于 `androidApp/src/main/kotlin/com/nekolaska/calabiyau/feature/wiki/gamesettings/`：

- `PcGameSettingsScreen(onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit)`：公开 Composable。
- `PcGameSettingsComponents.kt`：逐项列表、文本骨架、公共详情弹层适配。
- `model/PcGameSettingsModels.kt`：章节、段落、字段、设置条目与纯本地筛选。
- `parser/PcGameSettingsParser.kt`：HTML 语义解析、合并单元格展开与完整性诊断。
- `source/PcGameSettingsRemoteSource.kt`：MediaWiki parse 请求与校验后磁盘缓存。
- `api/PcGameSettingsApi.kt`：`CachedWikiApi<PcGameSettingsPage>` 单例；可供 bootstrap 注册。

## 阅读与交互

常驻标题、主 Tab、搜索与分类按钮位于加载／错误容器外，使用现有 Material 主题。三种阅读模式分别保存搜索、分类和滚动位置：

- **设置详解**：按原文 H2/H3 组织，列表显示 Wiki 记录的原厂默认值，点按查看作用、可选值、帧数影响及完整说明。
- **推荐速查**：独立展示 31 条推荐资料及其分类；显示模式等建议中的多行条件原样保留。
- **原理科普**：保留启动模式、DX11/DX12、DLSS/XESS 的段落、定义和嵌套列表；“卡拉彼丘没有此功能仅作科普”不丢失。

搜索只在当前模式／分类内本地执行，可匹配设置名、分类上下文、默认值和说明等所有字段，空格分词取交集。详情使用 `WikiContentSheet`，分类使用 `WikiSelectionSheet`；条目及详情字段逐项 lazy，文字可选择复制。刷新失败保留已有内容与错误提示，离线状态由公共资源容器展示。骨架匹配无图片的文字正文，列表状态变化使用短时 `animateItem`，没有装饰性动效。

### UI 整理（2026-11-14）

- 模式由筛选 chips 改为三个固定主 Tab，先选阅读任务再搜索。分类入口与搜索框同行，选中分类显示标记；分类名、命中数量与重置集中在结果摘要，避免常驻三排筛选控件。
- 设置／推荐模式只统计设置项，科普统计段落，不将说明段落混入设置计数。摘要提示使用短句，完整字段来源与限定保留在详情及原文。
- 列表突出设置名与值：默认值用正文色，Wiki 建议用主题强调色，说明降为次级文本；推荐的条件换行保留，全文在详情可读。
- 详情优先展示独立的默认／建议值区域，其余字段逐项排列，避免重复同一主值。正文使用主题中等圆角，卡片和骨架共享 16dp 内边距与 8dp 槽间距，文字占位高度随主题字号变化。
- 结果摘要在骨架与正文中复用；切换模式仅对活动内容树做 180ms 淡入，保留各模式滚动状态；筛选继续使用短时列表位移动画。使用 Compose 系统动画时长倍率，无额外循环装饰。

声音表格自身带有的“推荐”仍作为独立 **Wiki 建议** 字段展示，不当成默认值；它们不混入原文的 31 条画质速查。推荐不被宣传为官方最佳配置。PC 鼠标／键盘和手柄的同名设置保留各自章节和身份，不自动关联或合并。

## 解析与缓存保护

基准是本地捕获的 **revision 120159**：10 个 H2、33 张 `klbqtable`、323 条资料行，其中 31 条为推荐速查。

解析先展开有界 `rowspan`／`colspan`，再按表头名称映射字段，不使用全页固定列号。支持五套字段语义及列换序：

- 设置项 / 默认 / 可选值或说明（原文也写作 `可选值 / 说明`）
- 设置项 / 说明 / 可选值 / 默认
- 设置项 / 作用 / 帧数影响 / 可选值 / 默认
- 设置项 / 说明 / 可选值 / 默认 / 推荐
- 分类 / 设置项 / 推荐 / 说明

条目 ID 在过滤前根据 H2、H3、推荐分类、名称和同上下文出现号建立；不按名称跨章节去重。`br`、段落及列表保留换行，目录和编辑按钮排除。

`canCache` 要求全部预期表格上下文存在、每表资料行数达到当前完整快照基线、字段集符合所属章节、跨行单元格完整结束、两段科普保有至少 8/5 个正文块。缺少整列、缺少尾表、缺少资料行、重复顶级章节、异常合并单元格都产生可诊断错误。当前没有上游声明总数，因此采用**按语义上下文的保守完整性基线**；若 Wiki 合法删除条目或调整章节／字段，需要核验新快照后更新基线，不能随意放松检查以接受半页结果。新增行可通过，不把全页固定 323 行作为生产上限。

独立缓存键为 **`pc_game_settings_v1`**，使用既有 `OfflineCache.Type.GAME_TIPS` 目录（与 Tips 自己的键隔离）。MediaWiki JSON、可选页面标题及页面结构在 `OfflineCache.fetchWithCache(validatePayload=...)` 写盘前校验；异常网络响应回退到原有好缓存。读出的磁盘数据还会经过 API 完整性校验，错误／残缺页不显示为成功结果。

`PcGameSettingsApi.fetch(forceRefresh, cacheOnly, allowMemoryCache)` 继承公共缓存行为：

- `cacheOnly=true` 只读磁盘和解析，没有网络、图片或模块请求。
- 缓存预取后的正常验证显式传 `allowMemoryCache=false`。
- `forceRefresh=true` 绕过内存并交给磁盘层强制刷新，保留好缓存以供失败回退。
- `isOffline` / `cacheAgeMs` 原样透传；离线回退不发布成永久内存命中。
- 内存失效／并发发布保护由 `CachedWikiApi` 及 `MemoryCacheRegistry` 提供。

## 回归与本次验证

Tracked 小 fixture：`androidApp/src/test/resources/fixtures/gamesettings/pc_settings_variants.html`。

- `PcGameSettingsParserTest`：五类表头、换列、跨行分类与跨列字段、同名上下文、段落／嵌套列表、科普限定、筛选身份、缺行／缺列／未结束 rowspan。
- `PcGameSettingsSourceTest`：CDN HTML、错误 JSON、非字符串正文、错误页面标题和残缺 parse payload 不可写盘。
- `PcGameSettingsFullPageAuditTest`：可选全量快照，核对 10/33/323/31、两条未支持功能声明、离线年龄透传，并对整表／整列／单行／单格／科普块删除等破坏执行拒绝缓存断言。

设置 `PC_GAME_SETTINGS_AUDIT_FILE` 为 revision 120159 的完整 HTML 路径后运行该全量测试；未设置时使用 JUnit Assume 跳过。快照只读本地，测试不抓取 BWiki。当前会话快照位于 `C:/Users/NekoLaska/.local/share/opencode/tool-output/tool_1d825ae18001AvouuQ02dE4bZw`。

子任务先使用本机 Kotlin 编译器和 JUnit 在临时目录验证模型／解析器及真实快照，随后父会话完成 Android 集成检查：

- `:androidApp:compileDebugKotlin`、`:androidApp:lintDebug` 通过，Lint 0 errors / 5 个既有 warnings（1 ModifierParameter、4 UseKtx）。
- `*.feature.wiki.gamesettings.*` 与 `*.feature.wiki.hub.*` 共 **32 项，0 failures/errors/skips**；已配置全页快照，33 表／323 行／31 推荐及破坏载荷检查实际执行。
- 界面调整已纳入上述编译与检查；暂无连接设备，窄屏、大字体、筛选／切换状态、关闭系统动画和实际视觉观感尚未设备验收。提交与发布见2.1.30记录。

## 2.1.30 发布后：PC 设置页“无法连接网络”修复（待发布）

用户报告首次打开失败。按生产同参数请求 `action=parse&page=游戏设置PC端&prop=text&format=json`，实际收到HTTP200与有效JSON（131980字节），不是网络断开。响应相较之前网页快照新增了“DLSS、FSR、XESS是什么”标题及4行超分辨率渲染比例表；原有33张设置表、323个设置行和31个推荐项均保留。

### 根因

- 原实现只识别精确的“DLSS、XESS是什么”科普标题，并把所有 `klbqtable` 都按带“设置项”列的设置表校验。新科普表只有“质量档位 (命名)／DLSS渲染比例／XeSS 渲染比例”，因此整个有效响应被拒绝缓存。
- 拒绝校验后无可用磁盘缓存，`OfflineCache` 返回null，API将它一律归为NETWORK，导致界面误报“无法连接网络”。
- 初版全页测试只使用较早的完整网页HTML，没有覆盖应用实际调用的当前parse响应，未暴露此次差异。

### 修复与验证

- 科普章节按DLSS与XeSS语义识别，保留源标题（含FSR）。知识表进入独立的 `PcSettingsBlock.Reference`，按表头保存比例与名称、逐行展示和搜索，不伪装成游戏默认值／推荐设置。普通设置表的列、行、跨行及完整性校验继续保留。
- Source在请求内保留载荷拒绝原因；有有效旧缓存仍返回离线成功，无有效缓存时明确返回PARSE。只有没有响应且没有可用缓存才归NETWORK，不改变公共缓存层行为。
- 小fixture `upscaling_reference.html` 覆盖真实新标题和比例表。`PC_GAME_SETTINGS_API_AUDIT_FILE` 指向真实JSON响应，验证完整取数→校验→缓存提交→API链路，核对 **34表／323设置／31推荐／4参考行**；另验证坏新响应保留好缓存、无缓存时报告PARSE。
- 同时配置旧 `PC_GAME_SETTINGS_AUDIT_FILE` 网页快照及新API快照，PC设置／Hub专项 **35项，0 failures/errors/skips**，Android编译和Lint通过（0 errors / 5既有warnings）。构建使用排查时工作区已有的AGP9.5.1／serialization1.13.0升级；首次离线构建缺AAPT2，下载依赖后的完整检查通过。
- 本机证据：`C:/Users/NekoLaska/AppData/Local/Temp/opencode/pc-settings-load-fix/response.json`、`api.html`。本轮只读取一次Wiki接口，回归均离线读取捕获。修复尚未发布，线上2.1.30安装包未被替换。
