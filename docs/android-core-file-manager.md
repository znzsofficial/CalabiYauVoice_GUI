# Android 基础设施与文件管理约束

## 缓存与网络

- `CachedWikiApi` / `KeyedCachedWikiApi` 使用 `VersionedMemoryCache`：最新启动的请求拥有写入权；
  清缓存会同时废止进行中的写入，不能仅把值设为 null 后任由旧请求回填。
- 离线回退结果照常返回，但不进入永久内存缓存。网络恢复后普通读取必须有机会重新联网。
- `OfflineCache` 强刷更新写入代数、使用独立 inflight，但**不先删除旧磁盘副本**。
  网络错误或有效载荷校验失败时，仍可回退旧的有效副本；显式 invalidate/clear 才删除文件。
- `WikiParseSource` 在发布缓存前验证 `parse`、所需 text/wikitext 字符串字段和 MediaWiki `error`。
  联网与离线读取共用校验规则；无效 JSON/错误 JSON/缺字段响应不得覆盖已有可用缓存。
  `WikiEngine.safeGet` 也拒绝无效 JSON 和 MediaWiki error，不只是检查首字符。
- `SafeReadRetryInterceptor` 只自动重试 GET/HEAD；POST/PUT/DELETE 等写请求不自动重放。
  共用客户端关闭透明连接重试，写入 body 标记 one-shot，防止 OkHttp 自身的 503 Retry-After: 0 等 follow-up 再发请求。
  退避每 100ms 检查 Call 的取消状态。业务写入若要重试，必须由业务端保证幂等性。
- `NetworkMonitor` 监听默认网络；只有其能力确认 INTERNET + VALIDATED 后才报告在线。
  已被替换的网络发出的迟到能力/断开回调不能影响新默认网络的状态。

## 下载适配器

- Android 与 Desktop 统一调用共享 `util.downloadResourceAtomically`。
  **是否跳过文件只能由共享调度层的长度/SHA-256 校验决定**，平台适配器不得再按非零长度提前返回。
- 同目录临时文件下载完成后调用平台原子替换，允许替换已存在的损坏/零字节目标；失败或取消保留旧目标、清理临时文件。
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
- 两个图片画廊入口共用 `managedGalleryFiles`，既检查图片扩展名也排除目录。
- 资源下载页不再提供运行日志按钮/弹窗；底层仍保留最多 1000 条诊断消息，进度、失败重试与下载历史不受影响。

## 回归与复审（2026-10-20）

- `CacheConsistencyTest`：迟到刷新、清缓存、按键隔离、离线恢复、错误载荷保留有效副本。
- `NetworkInfrastructureTest`：默认网络交接、未验证网络、写请求不重放、GET 有界重试和取消。
- `AtomicResourceDownloadTest` + shared 的整链用例：损坏/零字节替换、失败保留、取消清理、有效文件跳过。
- `FileManagerFileSystemTest`：路径边界、非法重命名、空目录/读取失败、单多选、目录排除、外部刷新、删除与符号链接。
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
