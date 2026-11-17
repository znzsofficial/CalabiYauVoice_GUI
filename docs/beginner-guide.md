# 新人入坑指南原生页

## 入口与阅读

“游戏延伸 → 实用指南”中紧邻“游戏设置（PC）”，路由为 `WikiRoute.BeginnerGuide` / `beginnerGuide`。全局搜索支持新人、新手、入坑、配置需求、最低/推荐配置、腾讯服、创梦服、国际服和 Steam。已有 PC 设置及 Tips 路由编码不变。

`feature/wiki/beginner/` 按 model / parser / source / api / Screen 划分。直接读取 `action=parse&page=新人入坑指南&prop=text`，没有复制成静态攻略。原生显示四个章节，沿用原文顺序、加粗、嵌套列表、下载平台标题与链接。配置表转换为逐行资料块，仍明确区分最低/推荐配置；合并字段显示联合标签，不将最低配置当推荐，也不将 Wiki 入坑观点改写成应用结论。SMW 隐藏注释（如 Steam 不再支持 Win7/8）显式保留且只出现一次。

每章使用独立保存域记录滚动位置，空缓存预取不清掉章节选择。每条段落/配置行独立 lazy item；加载骨架匹配章节选择器和正文。共享顶栏提供刷新和 Wiki 原文，刷新失败保留原资料，来源标签明确为 Wiki。外部官网/Steam 链接用系统浏览器打开，Wiki 链接沿用站内打开方式；已验证的 Bilibili linkfilter URL 解包后只接受 HTTP(S)，拒绝可执行协议及带认证信息的 URL。不会自动下载安装游戏。

## 数据与缓存契约

2026-11-17 后续玩法页接入时，将富文本模型/解析和 Text 展示提取到 `core/wiki/reading/WikiRichText.kt` / `core/ui/WikiRichText.kt`，与三个玩法页复用；新人指南结构、缓存及导航不变，原10项测试继续参与联合回归。见 [玩法页专题](native-mechanics-pages.md)。

- 主源：`新人入坑指南`，独立 `beginner_guide_v1` 键，复用 `GAME_TIPS` 存储类型。
- HTML parser 先移除目录、面包屑、编辑按钮、CSS/脚本和装饰图；正文链接/粗体以轻量文本 span 保存。
- 下载标签页必须把所有 pane 读完，不能只读网页默认可见平台；内联节点与列表交错时按顺序输出，不重复嵌套列表的父子文本。
- 以修订 **120197** 的四章节、下载三平台、版本表4行/PC表8行/移动表5行为完整性基线；表头按含义映射，支持重排及有界 rowspan/colspan，异常跨度/缺字段/空字段拒绝发布。入坑正文、互通提示及下载说明另有最低完整性检查。新增内容可读取，合法删除/改名需先审核基线。
- JSON envelope 校验兼容 formatversion 1/2，要求标题匹配；**缓存写入前和读取后**检查正文结构。有效 HTTP 响应但结构错误且无好缓存时为 PARSE；无法获取且无缓存为 NETWORK。错误新数据不会替换好磁盘数据，回退保留离线标识及年龄。
- API 沿用 `CachedWikiApi` 的版本票据、清理注册与离线不永久 memo 规则；页面后台验证绕过内存缓存。cacheOnly 只有磁盘读，无图标/模块等补请求。

## 证据与测试

- 2026-11-17 实际 API HTML：`C:/Users/NekoLaska/AppData/Local/Temp/opencode/wiki-changes-20261117/beginner.html`。只发一次页面请求，沿用6.6秒节流。
- 小型跟踪 fixture：`androidApp/src/test/resources/fixtures/beginner/guide.html`，来自上述捕获，移除 CSS、目录、编辑链接和装饰图，保留完整阅读正文。
- `BeginnerGuideTest` 覆盖章节/17条表格记录、8条二级列表、4个下载链接、强调、配置注释、合并/重排字段、破坏载荷拒绝、缓存保全/离线元数据、PARSE分类、取消、原生路由保存及全页快照。
- `BEGINNER_GUIDE_AUDIT_FILE` 指向完整 HTML，可逐块比较真实页面和跟踪 fixture；没有配置时仅此可选审计跳过，其余离线测试正常运行。
- 本轮 Android 编译、10项专项测试（真实快照参与、0失败/跳过）、Lint通过（0 errors / 5个既有 warnings）。未连接设备，章节滚动恢复、大字号排版和系统浏览器跳转仍待设备验收。

已随 [2.2.1（62）](releases/2.2.1.md) 发布，版本、签名APK与线上核验以发布记录为准。
