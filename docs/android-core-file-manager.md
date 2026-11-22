# Android 基础设施与文件管理约束

当前契约包含 [2.2.2](releases/2.2.2.md) 的缓存写入和文件管理根目录修复。总体职责见[当前架构](android-architecture.md)；下方带日期的回归记录保留当时的验证范围。

## 缓存与网络

- `CachedWikiApi` / `KeyedCachedWikiApi` 使用 `VersionedMemoryCache`：最新启动的请求拥有写入权；
  清缓存会同时废止进行中的写入，不能仅把值设为 null 后任由旧请求回填。
- 离线回退结果照常返回，但不进入永久内存缓存。网络恢复后普通读取必须有机会重新联网。
- Hub跨页面快照也只保留完整在线列表，绑定清理代数及同类请求序号；搜索没有独立永久列表缓存。离线/不完整内容可显示，但不能阻止重入验证。
- `OfflineCache` 强刷更新写入代数、使用独立 inflight，但**不先删除旧磁盘副本**。
  网络错误或有效载荷校验失败时，仍可回退旧的有效副本；显式 invalidate/clear 才删除文件。
- 排队的强刷不跨清理代数接管。对立绘、留言首屏这类派生结果，在读取依赖之前 `captureWrite(type, key)`，完成后 `put(ticket, payload)`；取票与写入之间发生清理、失效或同键新预约时，旧写入被丢弃。
- `ReadSingleFlight`负责读请求去重：owner取消时，仍active的follower最多接管一次；当前读者取消直接退出，连续接管失败给出可重试错误。`LoadState`按请求版本执行finally，旧请求不能关掉新请求的加载状态。
- `WikiParseSource` 在发布缓存前验证 `parse`、所需 text/wikitext 字符串字段和 MediaWiki `error`。
  联网与离线读取共用校验规则；无效 JSON/错误 JSON/缺字段响应不得覆盖已有可用缓存。
  `WikiEngine.safeGet` 也拒绝无效 JSON 和 MediaWiki error，不只是检查首字符。
- `safeGet`为suspend，使用shared `awaitGetString`，取消一直绑定到body读取和Response关闭。仅切换到IO不代表阻塞网络已可取消。纯Parser不能发请求，补图由API/Source承担。
- `SafeReadRetryInterceptor` 只自动重试 GET/HEAD；POST/PUT/DELETE 等写请求不自动重放。
  共用客户端关闭透明连接重试，写入 body 标记 one-shot，防止 OkHttp 自身的 503 Retry-After: 0 等 follow-up 再发请求。
  退避每 100ms 检查 Call 的取消状态。业务写入若要重试，必须由业务端保证幂等性。
- `NetworkMonitor` 监听默认网络；只有其能力确认 INTERNET + VALIDATED 后才报告在线。
  已被替换的网络发出的迟到能力/断开回调不能影响新默认网络的状态。
  对 Wiki 缓存读取，区分无路由与已有默认网络但尚未通过验证：后者优先用有效磁盘副本，没有副本时直接尝试读取；前者仍只读磁盘。公开 Wiki JSON GET 可恢复失效连接，写入不共用这一传输策略。

## 下载适配器

- Android 与 Desktop 统一调用共享 `util.downloadResourceAtomically`。
  **是否跳过文件只能由共享调度层的长度/SHA-256 校验决定**，平台适配器不得再按非零长度提前返回。
- 同目录临时文件下载完成后调用平台原子替换，允许替换已存在的损坏/零字节目标；失败或取消保留旧目标、清理临时文件。
  调度层不得先删除目标再调用适配器，否则失败路径无法保留旧文件。
- shared JVM 整链测试同时执行“调度→实际公共适配器→发布→清单”，不能只用直接写目标的假下载函数测试哈希。

## 文件管理

- 文件系统逻辑位于 `feature/tools/FileManagerFileSystem.kt`，UI 在 `FileManagerScreen.kt`。
  `AndroidFileTools.isAndroidFileLink` 用原生 lstat 判断链接；JVM 测试注入对应的本机链接判断。
- 重命名只接受单个文件名，拒绝空名、`.`、`..`、斜线和控制字符；确认原文件/父目录/目标均在允许范围内，且不覆盖已有目标。
- 路径访问与修改使用 canonical 边界检查；字符串前缀只用于已验证路径的界面显示，不是访问控制。
- 删除只在管理根目录下执行，禁止删除根本身；递归时不跟随目录链接。
  不使用 chmod、shell rm 或权限绕过兜底，权限不足如实报错；部分失败也必须刷新列表。
- 文件选择模式不显示破坏性的管理操作。单选/多选限制同时覆盖点击、长按、全选和最终确认；
  只能确认存在、属于根目录内的普通文件，不能回调空列表或把目录计入所选文件。
- 目录选择栏独立于列表内容：空目录仍可确认；文件夹为空时也保留系统选择器入口。
  加载中、读取错误、提交中和目录快照不匹配时禁止确认。
- 根目录/子目录不存在或 listFiles 失败显示读取错误，不作为空目录，也不在浏览时悄悄重建被删除的子目录。
- 手动刷新和 ON_RESUME 都重新读取；外部新增、改名、删除可更新快照，并剔除已失效选择。
- 重命名、列表读取、APK 信息读取、文件夹计数、大小/时间等元数据读取在 IO 执行。
  行组件使用已加载的元数据，不在重组中反复 listFiles。
- 设置的「存储空间」在后台一次遍历下载树，同时求总大小、文件数和直属子目录用量。允许所选根目录本身为系统路径别名，但不跟随下层符号链接；离页取消后不继续统计后续条目。
- 根目录列举成功且确实为空时显示 `0 B · 0 个文件`；目录不存在或无法列举时显示「无法读取下载目录」。子目录列举失败或条目属性读取失败时保留已统计的文件数与字节数，标明统计不完整；受影响的子目录也标注已知用量或「用量未知」。主动跳过的链接和特殊文件不计为失败。
- 统计不完整时可直接重试；总览区分已统计用量和未知用量，隐藏需要完整分母的占比图，缓存明细仍可查看。
- 设置页将壁纸保存到可写的公共图片目录时，先写同目录、同扩展名的隐藏暂存文件，成功后原子替换；失败或取消保留原文件。保存提示跟随发起保存的壁纸，不复用于新壁纸。
- 两个图片画廊入口共用 `managedGalleryFiles`，既检查图片扩展名也排除目录。
- 资源下载页不再提供运行日志按钮/弹窗；底层仍保留最多 1000 条诊断消息，进度、失败重试与下载历史不受影响。
- 更新 APK 保存在应用私有外部目录。安装前将目标 versionCode、版本名和 APK 路径同步写入 SharedPreferences；下次启动由 PackageManager 确认已安装版本达到目标，且 DownloadManager 能排除该路径仍在下载时，才清理记录中的 APK 并移除对应任务记录。
  启动时还迁移清理旧版本遗留的 app-owned updater APK：必须满足文件名、APK 包名和 archive versionCode 均匹配；活跃下载、未来版本、外部/嵌套路径、非 updater 文件、其他包 APK，以及 DownloadManager 状态未知时保留。
  未安装（包括取消或安装失败）时保留 APK 以便重试。外部私有目录不可用时不再把内部 `filesDir` 错传给 DownloadManager。

## 回归与复审（2026-10-20）

本节数字是2026-10-20阶段结果，不是当前测试总数或Lint警告数。2.2.1的新增测试与结果见[架构修复记录](android-architecture-fixes.md#验证与边界)。

- `CacheConsistencyTest`：迟到刷新、清缓存、按键隔离、离线恢复、错误载荷保留有效副本。
- `NetworkInfrastructureTest`：默认网络交接、未验证网络、写请求不重放、GET 有界重试和取消。
- `AtomicResourceDownloadTest` + shared 的整链用例：损坏/零字节替换、失败保留、取消清理、有效文件跳过。
- `FileManagerFileSystemTest`：路径边界、非法重命名、空目录/读取失败、单多选、目录排除、外部刷新、删除与符号链接。
- `UpdateApkCleanupTest`：安装目标版本门槛、精确私有路径、包名/版本验证、活跃下载保护和删除失败留待下次启动。
- 复审另外补齐了多选后的目录点击旁路、目录切换时快照确认保护，以及 Desktop 下载适配器的同类遗漏。
- WebView 渲染退出回调本来只在 API 26+ 执行；加局部 SDK 判断是让 Lint 识别 lambda 的调用条件，
  不是将 didCrash 判定为 API 25 的真实运行时故障，也没有改变 API 26+ 的恢复逻辑。
- 运行 `:androidApp:testDebugUnitTest :shared:jvmTest :desktopApp:test`；不需要线上 Wiki。

最终验证：Android 211 项（9 项可选测试跳过）、shared JVM 42 项、Desktop 28 项，均无失败或错误。
`:androidApp:assembleDebug` 成功；`:androidApp:lintDebug` 成功，0 errors / 20 warnings。
现存告警包括其他 WebViewClient 的渲染退出处理提示、可更新依赖、Modifier 参数顺序及有意保留 commit 返回值的 SharedPreferences 写法；本轮没有通过 baseline 或全局 suppress 隐藏它们。
写请求另以本机 HTTP 服务返回 503 + Retry-After: 0，验证 POST 与无 body 的 DELETE 都只到达服务器一次。

目前没有连接的 Android 设备，尚未验证真机上的存储权限、默认网络切换、空目录选择器布局及系统文件分享。
后续设备回归应至少覆盖这四项，不能把 JVM 单测通过视为设备端行为已实测。

## 2026-11-17 架构审查后续

图片工具的`ToolOutputTransaction`与资源下载适配器用途不同：图片工具占用唯一新名称，临时写出后使用`Os.rename`发布，返回实际新路径；资源下载可根据清单替换已知损坏文件。不可互换两者的覆盖策略。

GIF只有成功finish才发布，自有流失败/abort仍关闭；预览和导出均绑定取消检查。图片解码/输出有像素预算，拼图逐张绘制，极端宽高比GIF不创建巨型缩放中间图。细节见[架构修复记录](android-architecture-fixes.md)。设备验证统一见[清单](device-acceptance.md)。
