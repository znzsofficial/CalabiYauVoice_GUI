# Android 当前架构

基线：[2.2.4（65）](releases/2.2.4.md)，2026-11-21。本文描述模块和状态归属；首次修复见[架构修复记录](android-architecture-fixes.md)，生产调用路径及故障回归见[二次复审](android-architecture-round-two.md)，设备验证见[验收清单](device-acceptance.md)。

## 模块边界

- `androidApp`：Android入口、Compose界面、Wiki解析/缓存、WebView、文件与素材工具。
- `shared`：JVM/Android共享的Wiki查询/下载、DTO、请求工具及媒体算法。没有Compose UI依赖；部分代码使用Java IO，并非iOS/JS实现。
- `desktopApp`：桌面UI、平台媒体适配、窗口与分发，调用shared公共业务。
- `downloadPage`：独立Svelte站点与Pages Worker，不属于Gradle子模块。根构建任务只是编排其构建/发布。
- `webApp`：归档原型，默认不参与Gradle构建。

依赖版本统一在 `gradle/libs.versions.toml`；网页依赖由 `downloadPage/package.json` 与锁文件维护。

`core/usage` 只接收固定类别的计数，使用独立客户端异步上传。主界面记录前台活跃，业务页面只报告结果；统计与 Wiki 身份、Cookie 和操作内容分离。数据保留及管理页见[使用统计](app-usage.md)。

## 导航与状态所有权

`MainActivity → core/navigation/MainScreen → WikiHubScreen / 下载 / 工具 / 设置 / 留言板`。

- MainScreen管理主目的地、抽屉及覆盖在Hub上的Wiki浏览器；主页面退出组合，不让所有页面常驻。
- Hub使用 `WikiRoute`、`WikiRouteCodec` 和页面保存域维护栈、筛选与滚动。转场中的旧页面回调须核验当前栈顶，预测返回由现有协调器负责。
- 简单只读页使用 `LoadState`；投票、卡组、留言等复杂业务使用独立状态类；下载由ViewModel和StateFlow承接。没有“所有页面必须改成ViewModel”的要求。
- 完整导航可通过 `WikiNativeLinks` 分流到明确匹配的原生页面；原文按钮、未知/带锚点/编辑/旧版本链接继续网页。

## Wiki 数据链路

`Screen → API → Source → OfflineCache → WikiEngine`，纯 `Parser` 将HTML/wikitext转成model。

- Screen负责展示和本地选择；API负责IO、组合依赖、错误分类和内存缓存；Source负责请求及磁盘载荷；Parser不联网。
- 图鉴条目介绍由解析器保留原文换行和段间空行。详情弹窗只负责展开与显示；名称、分类和搜索标识继续用紧凑文本。见[解析约定](android-wiki-feature-guide.md#parser)。
- `CachedWikiApi` / `KeyedCachedWikiApi` 使用版本票据：清理或新请求使旧发布失效，离线成功不成为永久内存命中。
- `OfflineCache`保留好磁盘副本，校验失败/网络失败可回退。通用JSON envelope校验不能代替业务完整性校验；各图鉴/阅读页自行审核完整结构。
- `ReadSingleFlight`合并相同读请求。旧owner取消时仍活跃的读者有界接管；当前读者自身取消必须透传。
- `LoadState`将有效网络结果置于磁盘预取之上，包括成功空结果；异常/上游取消可重试，finally只收尾当前请求。
- `WikiEngine.safeGet`是suspend，底层 `awaitGetString`的取消绑定覆盖body消费和关闭。只对GET/HEAD自动重试，不重放业务写请求。
- 投票与卡组写入绑定发起时的账号会话，每次新请求前核验会话和任务状态；取消不代表已发送请求在服务器端回滚。
- Hub跨页面快照只接收完整在线结果，绑定清理代数和请求序号。搜索复用当前快照，不维护另一份永久索引；离线/部分结果重入后仍有验证机会。

## 文件与媒体

- 资源下载是固定请求快照、目录锁、长度/SHA-256验证和临时文件发布。Android清单位于内部filesDir，Desktop保留目录sidecar。
- 即使目标文件尚未写出，清单记录的名称也属于原资源。系统 APK 更新通过版本及 URL 查找 DownloadManager 中的同一任务，弹窗重新进入后先核对任务状态。
- 下载搜索首次进入才初始化；进程死亡后可明确恢复原任务，不保证后台持续执行或字节级续传。
- 图片工具使用 `launchToolJob` / `loadToolPreview` 绑定取消检查。输出通过 `ToolOutputTransaction` 保留旧同名文件，返回实际新路径；GIF只有finish成功才发布。
- 原图与输出先检查像素预算，拼图逐张解码/绘制；极端宽高比GIF直接绘制目标画布。预算是工程限额，不是真机峰值测量。
- 文件管理仍受canonical根目录和lstat约束；工具输出事务与资源下载完整性清单是两种职责，不互相替代。

## WebView 与账号

- 登录入口是Wiki网页右上角的**通知按钮**。Cookie回写必须匹配原请求票据和当前会话，退出登录废止在途写入。
- `WikiNavigationPolicy`统一检查主文档入口；弹窗、文件选择、崩溃恢复由对应owner管理。
- 主窗口与弹窗同步宿主RESUMED状态；不调用影响所有WebView的全局pauseTimers。不能据此宣称JavaScript在后台完全停止。
- 留言板身份轮询限于STARTED，草稿和待确认发送按身份隔离；不可为简化文案改变重试身份。

## 原生覆盖边界

角色/武器/地图详情、装饰及弦溯图鉴、PC设置、新人指南、弦化/定位/增幅网络等已有原生实现。公告、活动、剧情、历史、投稿等有原生目录，但部分正文或外站作品仍走网页。

特别行动、赫尔墨斯、赛事系统仍是网页入口；视频交给外部播放入口。登录、编辑、投稿表单不因原生阅读页新增而被替代。具体名单和历史证据见[导航覆盖](wiki-navigation-coverage.md)。

## 验证和后续演进

已有纯解析、请求时序、缓存清理、输出失败、WebView owner及路由单测；完整Wiki捕获通过环境变量接入离线审计，未配置可选捕获时允许跳过。测试数量/跳过数以各次发布记录为准。

设备项集中在[验收清单](device-acceptance.md)。仅在测量证明收益时考虑复用同次解析结果、提取更多导出服务或拆分Gradle模块；现有完整性、取消、身份和导航保护必须保留。
