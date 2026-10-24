# 会话交接快照（2026-10-20）

这是会话压缩前的状态记录，不是实时发布状态。续接先检查 `git status` / `git log`，再查相关专题；
不要按本文自动提交、发版、执行数据库迁移或修改其他会话工作。

**2.1.21 / 51 已于 2026-10-20 发布。** 源码准备提交 `c55aec6`、最终构建元数据提交 `ccc5a90`；
Pages 生产部署 `20ea53d6-9cf6-46db-9eb0-5334473092e3`，APK SHA-256 为
`b5b835fcaa2c8b4f323b2c3b7f75d54ff2d11c02ef21acb99c560d923ea23358`。
发布收尾现以 [2.1.21 发布记录](releases/2.1.21.md) 为准。下方旧快照保留用于追溯。

**2.1.22 / 52 已于 2026-10-24 发布。** 源码准备提交 `1162258`、最终构建元数据提交 `15d37ee`；
Pages 生产部署 `664ad548-74b2-4b8a-a80d-eb2e3fe81513`，APK SHA-256 为
`1fe2951e532b890889b36f082db2d9d3d2388105b52d321fd178c83b27a782f3`。
发布收尾以 [2.1.22 发布记录](releases/2.1.22.md) 为准。下方“尚未发布”段落是发布前快照，不再代表线上状态。

## 同日后续：底层类与文件管理修复

### 2026-10-24：Android 下载清单迁入私有存储（尚未发布）

用户报告 `Could not publish download manifest`，已授权移除公共下载目录的清单写入。
Android `WikiEngine.downloadSpecificFiles` 统一传入内部 `filesDir/download_manifests/<canonical 保存目录 SHA-256>.tsv`。
shared 支持指定独立清单文件；Android 首次仅尝试只读导入旧 sidecar，不能读取/被同名目录占用也不阻断，不修改旧清单。
Desktop 默认保留 sidecar。文件名去重、旧映射迁移、重试与完整性检查保留。新增 3 项针对性回归已通过，
Android/shared 测试及桌面编译通过。没有真机复现原报告，不把公共目录音频文件本身的权限问题也宣称为已解决。
这些更改与下方更新 APK 清理、下载门槛清理均已包含在 2.1.22/52，不能回溯说成 2.1.21 的内容。

### 2.1.21 发布后更新 APK 清理改进（尚未发布）

用户发现更新后 `Android/data/.../files` 安装包残留并授权启动时清理。实现位于 `UpdateApkCleanup.kt`：
安装前持久记录目标 versionCode/版本名/精确 APK 路径；下次启动确认已达目标版本后，才删除受 canonical 根目录保护的目标并移除对应 DownloadManager 记录。
另外按 APK 文件名、应用包名和 versionCode 清理旧 updater 包；活跃/状态不明任务、未来版本、非应用包和路径越界均保留。
若外部私有目录不可用，更新下载明确失败，不再使用不适合 DownloadManager 的内部 filesDir。回归在 `UpdateApkCleanupTest.kt`。
此改进纳入 2.1.22/52，不能说已包含在 2.1.21 中。最终测试与部署结果以 2.1.22 发布记录为准。

### 更晚的 WebView / 登录链路修复

用户随后授权修复 WebView 审查 8 项。Cookie 回写已绑定请求会话票据，设置页退出会废止旧票据；
CSRF 改 SHA-256 分区，userinfo 错误按错误处理并允许 20 秒后重试；导航策略覆盖 intent、显式黑名单及主框架 POST；
系统文件选择器串行化并保留迟到结果墓碑；临时 WebView 有统一所有权、数量/超时限制，恢复时重新应用缩放。
详情和测试入口见 `docs/android-webview-auth.md`。下面的底层测试数量是此轮之前的历史快照；仍未提交或发版。
本轮最终 Android 237 项（9 项跳过）、shared JVM 42 项、Desktop 28 项全部无失败；另有 3 项临时本地 HTTP 链路回归通过。
assembleDebug 与 lintDebug 成功，Lint 0 errors / 20 warnings；未做真机登录/渲染退出/选择器回归。

用户随后授权修复底层 7 项和文件管理 8 项问题，并要求修完复审；资源下载页日志入口也已移除。
修改仍未提交/推送/发版。详细实现与回归入口见 `docs/android-core-file-manager.md`。
下载适配器曾遗漏按非零文件短路，本轮已修复 Android/Desktop 两个调用端并增加共享整链测试；
此前“下载 8 项全部修好”的判断应以本轮整链修复后的状态为准。
后续读取下方测试数量等旧快照时注意：它们是本轮后续修复前的状态，不代表最新测试统计。
本轮最终 Android 211 项（9 项跳过）、shared JVM 42 项、Desktop 28 项均无失败；
assembleDebug 成功，lintDebug 0 errors / 20 warnings。没有真机设备，尚未提交或发版。

## 当前最重要的状态

- 当前分支 `master`，最新提交 `bdbe351`（平衡数据的历史赛季恢复与展示更新，不是本轮下载修复）。
- 最近发布版本 **2.1.20 / versionCode 50**，已于 2026-10-13 上传 R2、部署 Pages、验证并推送。
- **资源下载审查的 8 项修复已完成，但全部仍在工作区，尚未提交、推送或发版。**
  包含新文件，续接提交时不可只 `git add -u`。
- 本轮没有修改网站业务、数据库结构或发布元数据，没有遗留正在运行的测试。
- 最终验证：`:androidApp:testDebugUnitTest :shared:jvmTest :desktopApp:test` 成功。
  XML 快照：Android 189 项（9 项跳过，0 失败/错误），shared JVM 41 项（0 跳过/失败/错误），
  Desktop 28 项（0 跳过/失败/错误）。Android 可选联网/本地夹具测试未启用时会跳过；本轮没有线上 Wiki 请求。
- 未进行真机回归，不能把单元测试通过表述为后台、通知或系统媒体库行为已实测。

## 本轮资源下载修复

1. 分类文件弹窗：提取 `CategoryFileSelectionState`，独立 Job + 代数，关闭/切换模式/分类时失效旧请求；
   错误不是空列表，加载/失败时不能确认手动选择，支持原分类重试。
2. 分类分组：同一官方角色的多搜索家族合并，官方名匹配最长优先且顺序稳定；列表 key 用根分类而不是显示名。
3. 日志：允许相同文案多次出现，不以文本作 LazyColumn key；并发追加原子更新，保留最近 1000 行。
4. 分类下载：新增 `scanCategoryTrees`，从根 + 全部家族种子展开，循环/跨种子去重，并发上限 4。
   Android 和 Desktop 两个调用端均已接线，避免改共享分组后桌面仍漏兄弟分类。
5. 搜索错误：删除 Android 的吞错双路径副本，委托共享实现；校验空响应、HTML、无效 JSON、MediaWiki `error`；
   合法空 `batchcomplete` 仍可返回空集合，不把失败/部分结果当成功。
6. 重试任务：`ResourceDownloadRequest` 深拷贝并固定任务、保存目录、并发数和 ID，错误事件直接携带原请求。
   不再在 Snackbar 点击时从当前 UI 重建另一份任务。
7. 媒体扫描：仅扫描本批结果文件，移至 IO dispatcher，不再主线程遍历整个历史目录。
8. 文件完整性：下载清单由两列扩为 `文件名\tURL\t长度\tSHA-256`，目录锁 + 原子替换；
   每项成功保存指纹，只有长度/哈希均正确才跳过；覆盖等长损坏、非零损坏、部分失败重试、取消释放锁。

附加：Android 保存下载 Job 并提供取消按钮。网络前用 SharedPreferences `commit` 写任务日志，
取消/失败/进程中断后保留恢复槽，页面可手动继续原任务或放弃（保留文件）。
**不是 WorkManager/前台服务，没有系统保证的后台自动下载或字节级续传。**
旧两列清单首次继续会重下已归属文件以建立指纹；未归属的同名用户文件仍保留。
SHA-256 是本地已确认版本的完整性校验，不是上游真实性验证，不检测远端同 URL 更新。

## 续接文件指针

- 长期设计与兼容约束：`docs/resource-download-implementation.md`。
- Android 新源码：`feature/download/CategoryFileSelectionState.kt`、`DownloadTask.kt`。
- Android 修改：`SearchViewModel.kt`、`DownloadViewModel.kt`、`DownloaderScreen.kt`、`FileSearchComponents.kt`、
  `CategoryComponents.kt`、`core/wiki/WikiEngine.kt`、`core/ui/FeedbackComponents.kt`、`core/preferences/AppPrefs.kt`。
- 新测试：`androidApp/src/test/.../feature/download/CategoryFileSelectionStateTest.kt`、`ResourceDownloadRequestTest.kt`。
- shared：`data/WikiEngineCore.kt`、`data/WikiModels.kt`、`portrait/PortraitLogic.kt`；
  `jvmTest/.../WikiEngineCoreQueryTest.kt`、`WikiEngineCoreDownloadTest.kt`。
- Desktop：`data/WikiEngine.kt`、`viewmodel/MainViewModel.kt` 的多种子分类扫描调用。
- 审查时的临时证据用例在 `%LOCALAPPDATA%/Temp/opencode/download-review-20261020/`，
  不入库、不可当成正式回归套件；正式替代用例已经在仓库测试源码中。

## 此前已完成并推送的工作

- 2.1.19/49：壁纸即时刷新、骨架屏对齐、留言板和用户系统 8 项审查修复。
  D1 `0010_account_identity.sql` 已在 2026-10-05 备份/演练后应用，别名、头像/点赞稳定归属及旧通知兼容已上线。
  发布提交 `72219f5`，实现提交 `a8726d9`；详情见 `docs/user-system-backend.md`。
- 道具图鉴拆分：旧合并页 404，改为 `功能道具图鉴PC端` / `功能道具图鉴移动端`；
  移动端只含独有道具，共有道具保留在 PC。入口、缓存、真实夹具与 CI 已分开。
  提交 `1f80626`；GitHub 冒烟运行 `38384450432` 成功，issue #3 已关闭并关联本会话。
- 2.1.20/50：上述拆分 + 用户的自动清理/依赖更新一并发布，提交链 `6b75761` → `51b4e89` → `3d07e47`。
  线上 APK 大小 3611708 字节，SHA-256 `c8e20fb8065486e251b3acbe618b008aa8156e7c12e9a38ac48b1ce37ab3d51e`。
  证书与 2.1.19 一致，下载/Range/元数据均已验证。
- Compose Web：JS/Wasm 可编译但只是写死样例/42% 假进度的 UI 原型，未接 shared。
  已归档、从默认 Gradle settings 移出、清理旧部署头与误导文案，源码/字体保留。
  实验需 `-PincludeArchivedWebApp=true`；正式 Web 功能继续放在 Svelte `downloadPage/`。
  提交 `85c40f1` 已推送，默认原生编译及显式启用的 JS/Wasm 编译均通过。

## 后续操作边界

- 若用户要求提交：核对新增文件和工作区后，只提交当前相关改动；本轮尚未获指令提交。
- 若用户要求发版：当前仍为 2.1.20/50，下一版通常为 2.1.21/51，须重新核实所有版本字段和更新日志。
  本轮不需要新 D1 迁移，不能无故重做或回滚 0010。
- Cloudflare 直连可能不稳定，先前 `HTTP_PROXY` / `HTTPS_PROXY=http://127.0.0.1:7897` 可用；
  Git 推送也曾通过该代理。不要将代理或凭据写入仓库。
