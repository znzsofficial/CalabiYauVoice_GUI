# Android WebView、Cookie 与文件选择

## 修复范围（2026-10-20）

本轮处理 WebView/登录链路审查的 8 项：旧 Cookie 回写、opaque intent 崩溃、导航规则旁路、
userinfo 临时错误不恢复、文件选择串回调、临时 WebView 生命周期、CSRF 缓存 hash 冲突和渲染恢复缩放失配。
实现纳入 2.1.21（51），最终交付与验证状态见 [发布记录](releases/2.1.21.md)。
`didCrash()` 本身不是本轮发现的运行时 bug；局部 SDK 判断仅保留给 Lint 识别。

## Cookie 会话边界

- `CookieSessionGuard` 的票据绑定请求 Cookie 与会话 revision。回写前读取 CookieManager 的当前内容，
  不使用 500ms memo 作授权判断；切号或退出后的旧响应不得覆盖当前 Cookie。
- `WikiAuthHelper` 的原生回写在同一锁内完成检查和发布；`httpGetWithCookies` 与投票回写都携带请求开始时的票据。
  不携带登录 Cookie 的公共 GET 不再把响应 Cookie 写进浏览器登录存储。
- 设置页退出统一调用 `clearLoginCookies()`：立即废止票据、清空 Cookie memo 与 CSRF cache，
  等 removeAllCookies 回调完成后才结束清理状态。在清理期间，旧 Cookie 请求失败关闭。
- CSRF 缓存按 API 地址 + **完整 Cookie 的 SHA-256** 分区，不再使用 String.hashCode；
  获取令牌期间会话变化时不缓存或继续复用旧票据的结果，需要用户重新确认后重试。
- UI 的 `hasWikiLoginCookie` 只是提示；服务端鉴权才是写入权限依据。
  此客户端保护不等于撤销已到达服务器的写请求，也不是服务器端注销。

## Wiki 身份解析与重试

- `WikiUserApi.decodeWikiQueryPayload` 拒绝 MediaWiki error、无 query 和无效 JSON；当前身份还必须存在 userinfo。
  不能把 maxlag 等错误变成 Success(null)，也不能把贡献/监视列表的接口错误当成空结果。
- `WikiAccountState` 对失败、null 身份及匿名身份使用 20 秒重试间隔，Cookie 不变时也可恢复；
  仍以 Cookie + revision 防止旧账号结果覆盖新账号，不移除此保护。

## 导航策略

- `WikiNavigationPolicy` 是初始地址、原生加载、主链接、弹窗、POST 主文档检查和提交前来源检查的共同入口。
  显式黑名单先于允许域名后缀，包括被拦截域名的子域和尾点写法。
- `documentUrl` 可以把 intent 的安全 fallback 解析成 HTTPS 地址；`mayLoadDocument` 只允许实际的 HTTPS 文档 URL。
  不能因为 fallback 合法，就直接允许 WebView 加载原始 intent URL。
- opaque `intent:scan/...` 及 Android 合法的 `intent:#Intent;...;end` 不使用 Android Uri.getQueryParameter。
  解析失败/非法 fallback 失败关闭；外部 Intent 不沿用来源传入的任意 component、flags 或 extras。
- `shouldOverrideUrlLoading` 不处理 POST，因此主框架请求还有 shouldInterceptRequest 校验；
  页面开始加载时再校验最终文档来源。不将被拦截的 POST 自动改成 GET 重新提交，界面提示用户返回 Wiki 或明确打开系统浏览器。
- 子框架不应自动把父页面导航到 iframe 地址；原生错误/恢复使用的 about:blank 只在内部路径放行。
- **这不是网络防火墙**：合法第三方登录子资源仍可联网，WebView 的请求拦截也不能提供完整重定向链审计。
  不应宣称它阻断了 WebView 的全部外站网络请求；真实登录跳转、验证码和跨站表单仍需设备回归。

## 实例与弹窗所有权

- `WikiWebViewLifetime` 管理当前主实例和临时窗口，拒绝旧实例的导航、标题、进度、下载与渲染恢复回调。
  AndroidView.onRelease 与页面销毁均释放所有权，释放操作幂等。
- 临时 WebView 最多 4 个，每个有 30 秒超时；导航接管、window.close、渲染退出、父实例释放或页面退出都会回收。
  定时器使用主 Handler，不使用未 attach 的临时 View.postDelayed；关闭时取消定时器。
- resultMsg/transport 校验在创建临时实例之前执行。临时窗口只做链接接管，不能把不可重建的 POST 请求偷偷改成父页面 GET；
  不支持的弹窗表单操作明确提示在当前页或系统浏览器完成。
- 渲染恢复及初始 URL 替换时，新实例创建参数和 update 都应用当前文字缩放，不回落到与 UI 不一致的 100%。

## 文件选择归属

- `WikiFileChooserState` 一次只允许一个系统选择器；新请求不能替换尚未返回的旧请求。
- 每个请求绑定原 WebView。换文档、网络失败、来源拦截、实例恢复/释放都会先取消旧 callback。
  取消后保留 pending 墓碑，直到旧系统选择结果到达，不能把它转交给新页面。
- pending 标志用 rememberSaveable 保存。重建后先消化旧 ActivityResult，再允许新选择；旧原生 callback 不跨重建恢复。
- 启动失败只清除匹配的 request ID；dispose/重复回调不会二次交付结果。单选结果最多向网页返回一个 URI。

## 回归入口与限制

- `CookieSessionGuardTest`：切号、退出、相同 Cookie 再出现、SHA-256 分区。
- `WikiUserApiContractTest` / `WikiAccountStateTest`：真实 error 结构、匿名与登录身份、20 秒重试。
- `WikiNavigationPolicyTest`：黑名单优先、伪后缀、opaque intent、fallback 解码、直接文档与 intent 的区别。
- `WikiFileChooserStateTest`：重叠请求、同实例换页、渲染恢复、重建墓碑、启动失败及 dispose。
- `WikiWebViewLifetimeTest`：父子释放、迟到实例、渲染退出、定时器清理和窗口数量上限。
- 外部临时 `webview-review-20261020/WebViewAuthRegressionTest` 用本机 HTTP 服务执行实际原生请求/回写链路，
  仅 CookieManager 和时钟使用测试替身；已验证旧响应不覆盖 B、退出 ABA 票据失效、hash 冲突分别取 token、maxlag 后可重试。
  这些 Android 包名替身**不入库、不随 App 打包**；旧异常证据用例不应当成修复后的回归断言。

没有线上 Wiki 请求，也没有连接的真机。设备回归至少覆盖登录/切号/退出交错、选择器打开时渲染退出、
外站表单和 intent fallback、弹窗关闭/超时，以及缩放后渲染恢复。

最终验证：Android 237 项（9 项可选测试跳过）、shared JVM 42 项、Desktop 28 项，均无失败或错误。
新增的 26 项正式回归及 3 项临时实际 HTTP 链路回归通过；assembleDebug 成功，lintDebug 0 errors / 20 warnings。
复审补齐了同实例换页取消、恢复墓碑、fallback 必须解析后再加载、非法 fallback 失败关闭，以及未 attach 弹窗的 Handler 超时清理。
