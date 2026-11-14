# 玩家装饰图鉴：数据职责与来源策略

纳入 [2.1.30（60）](releases/2.1.30.md)，最终交付状态与产物以发布记录为准。

## 职责边界

`feature/wiki/decoration/` 保持以下小型、单向依赖结构：

```text
PlayerDecorationScreen.kt                 搜索、分类、按组懒网格与页面内状态
PlayerDecorationComponents.kt             图文卡片、资料弹层、图片占位与匹配骨架
model/
  DecorationModels.kt                     屏幕领域模型：DecorationItem / Section / Preview
  DecorationSourceModels.kt               统一 raw section/item/file/preview；Lua 条目、双表快照与诊断
  DecorationCatalogs.kt                   每页 Html / Lua 主源策略，双表页名、图片规则与完整性基线
parser/
  DecorationHtmlParser.kt                 HTML → raw，保留分组、顺序、真实 ID、文件身份
  DecorationModuleParser.kt               安全 Lua 数组 → 完整条目记录与诊断
  DecorationWaysParser.kt                 ID 键控途径表 → 来源/备注，区分缺记录与显式空记录
  DecorationLuaCatalogParser.kt           条目 + 途径联合校验，按已核验 renderer 规则形成目录
  DecorationText.kt                       两种来源共用的可读标记处理，保留换行和删除线失效语义
api/
  DecorationAssembler.kt                  raw + imageinfo → 领域模型、稳定展示 key、降级 notice
  PlayerDecorationApi.kt                  公共入口 + 可注入 source 的缓存编排实现
source/
  PlayerDecorationRemoteSource.kt         显式磁盘/网络读取、成对模块的单次缓存提交、imageinfo
  DecorationLuaCachePayload.kt            成对缓存格式；提交前调用联合完整性校验
```

原 `PlayerDecorationParsers` 已拆除。Parser 不发请求，HTML parser 不读取 Lua；模块 parser 与途径 parser 各自只认一种数据格式，Lua 目录 parser 负责双表完整性与已验证图片规则。组装器不解析文本、不读缓存，也不再把“可选模块字段”混入 HTML 目录；两种目录各自形成 raw 后统一映射。Source 在发布原始成对缓存前调用同一联合校验，不维护另一套领域规则。`DecorationSource` 六个明确操作分别读取 HTML、Lua 双表、imageinfo 的网络或磁盘版本，没有额外 Repository/Loader 框架。

`LoadedDecorationPage` 仅是 API 内部缓存封套（领域 sections + `canCache`），不暴露给屏幕。`PlayerDecorationApi.fetchDecoration(...)` 的参数和 `ApiResult<List<DecorationSection>>` 返回类型不变。实际编排仍继承 `KeyedCachedWikiApi`，沿用同名 `PlayerDecorationApi` 内存清理注册、请求版本 token、强制刷新和缓存绕过规则。

## 每页真值策略

可读注册表在 `DecorationCatalogs.policies`；12 个现存页面加一个历史不存在页面。以下九页已根据本文件后半部分的完整实查证据改为 **条目 Lua + 获得方式 Lua 主源**，不是继续“HTML 集合 + 同 ID 文本覆盖”。表中条目路径除标注移动端外均位于 `模块:玩家装饰/`，途径均位于 `模块:获得方式/`。

| 页面 | 条目表 | 途径表 | 图片身份规则 | 已核验数量 |
| --- | --- | --- | --- | --- |
| 封装 | FrameData | Frame | 行内 `file`（不再推测无 icon 后缀的另一文件） | 52 |
| 聊天气泡 | ChatBubblesData | ChatBubbles | `聊天气泡_<id>.png` | 37 |
| 头套 | MascotHeadData | MascotHead | `头套_<id>.png` | 9 |
| 超弦体动作 | RoleActionData | RoleAction | `超弦体动作图标_<id>.png`；附属 `name.gif` | 16 |
| 房间外观 | ChangeBgData | ChangeBg | `房间外观_<id>.png`；`_FriendTitleBg.png`、`_BtnBg.png` | 7 |
| 头像框 | `模块:玩家装饰移动端/HeadFrameData` | HeadFrameMobile | `头像框移动端_<id>.png` | 71 |
| 极限推进模式载具外观 | `模块:玩家装饰移动端/VehicleSkinData` | VehicleSkinMobile | `载具外观_<id>.png`，独立 `spdesc` | 15 |
| 休息室手办 | GarageKitsData | GarageKits | `道具图标_<id>.png` | 14 |
| 个性名片 | ProfileCardData | ProfileCard | `个性名片_<id>.png` | 5 |

PC 按 ID 升序；移动端按原数组顺序。数量作为结构完整性最低基线，合法的上游删项需要审核后更新基线；新增项无需变更基线。图片仅形成已证实的 Wiki **文件名**，真实 URL 批量查询 imageinfo（含 redirects），不拼 CDN 地址。

**基板、喷漆、勋章继续以 HTML 为目录主源**：保留按获取方式筛选的分组、混合平台、手写成就与印迹最高级 gallerybox，绝不把整个 Data 表直接塞进目录。未知 pageName 与历史 `登场特效` 使用 HTML 兼容路径，不猜测新模块。Hub 分类与路由不是这个注册表的职责。

### 字段优先级

1. **目录来源先选完整的一方**：九个简单页完整且新鲜的 Lua 双表优先；失败时整体降级 HTML，不拿部分 Lua 空字段覆盖 HTML。复杂三页的身份、顺序和分组来自 HTML。
2. **名称/描述/品质**：Lua `id/name/quality/desc` 必须有正确类型，真实 ID 为正整数，品质 **0 合法**（默认房间 93001）；缺品质的 HTML 手写勋章使用 `-1` 表示未提供，名称来自真实 caption，不显示“未知”。
3. **获得方式**：独立的 ID 键控途径表支持字符串、字符串数组及 `{get, remark}`。缺 ID 是不完整依赖，显式空数组是已给出的空记录，二者不同；备注带 `备注：` 保留。数组顺序、链接可读文字与换行保留；`<del>/<s>/<strike>` 转成 `【已失效：…】`，旧价格不会变成当前价格。
4. **特殊说明**：Lua 的载具 `spdesc` 与 HTML 的“外观效果”都进入独立 `specialDescription`；HTML 不把效果、父级获得方式标签与重复子 `li` 混入 `source`。
5. **图片**：HTML fallback 的原图只绑定本文件；`/thumb/` 必须解析 imageinfo。Lua 用已证实文件名批量解析。缺图不删除条目，也保留有标签的附属预览（URL 可暂空，UI 不应对空 URL 开预览/保存）。

`LuaTableParser.parseReturnTable` 是新增的安全严格入口：仅返回数组/对象表，拒绝冲突键、混合数组/对象、尾部可执行代码。其他 feature 用的旧 `parseReturnArray` 行为不变。条目冲突、缺字段、缺途径、未知途径结构和数量不足均有诊断；相同内容重复 ID 可折叠，冲突使整对数据无效。

### 稳定展示身份（供 UI）

- `DecorationItem.stableKey: String`、`platform: String`、`fileName: String` 均有兼容默认值；生产组装器保证生成 key。Key 由页面＋平台＋分组路径＋真实 ID＋规范化文件＋名称组成，使用长度前缀避免分隔符冲突，不依赖图片 URL。
- `DecorationSection.notice: String? = null`：Lua 主源降级时仅首组带一次说明。现有构造调用保持兼容。
- 喷漆 PC/移动端相同数值 ID 不混合；同组完全相同行只保留一次。不同组仍各自保留，同组同身份但内容冲突的行保留并加内容限定 key，避免 Lazy key crash。未知平台不从 ID 推测：已验证 Lua policy 给平台；HTML 依照明确文件前缀区分。

## 原生入口契约

两页均复用 `feature/wiki/decoration/PlayerDecorationScreen`，通过同一 API 的 `pageName` 分开缓存：

- 个性名片：`PlayerDecorationScreen(title = "个性名片", pageName = "个性名片", onBack = …)`；归入 **身份展示**。
- 休息室手办：`PlayerDecorationScreen(title = "休息室手办", pageName = "休息室手办", onBack = …)`；与房间外观一起归入 **外观扩展**。

`pageName` 默认等于 `title`，调用方也可只传标题。路由、保存编码、入口和全局搜索由 Hub 接入负责。

## 2026-11-14 真实页面核对

- 个性名片 revision **117403**：5 张，真实图片文件 `个性名片 34000001.png` 至 `个性名片 34000005.png`，名称依次为序列证、弦辉名笺、肥肥鲨的见面礼、妄破之证、入梦星音。
- 休息室手办 revision **112816**：14 张，真实图片文件 `道具图标 70001.png` 至 `道具图标 70014.png`；首项星绘-星佑手办，末项肥肥鲨绯绯莎。品质 3/4，来源包含赛季段位奖励、手办特典与活动奖励。
- 两页均为 `.player-decoration.gallerygrid` → `.gallerygrid-item.klbq-decoration-card`；名称与品质来自 `span[data-quality]`，获得方式来自列表，描述来自 `.klbq-decoration-card__subtitle`。实际 HTML 的 class 包含 `&#95;` 转义，Jsoup 解码后匹配双下划线。
- 文件名实际在前缀与数字之间有空格；沿用既有空格/下划线 ID 提取。未生成序号 ID，未猜测文件名、图片路径或 Lua 模块映射。

## 解析与缓存

- 新版 subtitle 描述与旧版 `small` 均兼容；房间兼容 `.roomgrid` / `.roomgrid__*` 及旧 `.roomgrid-item` / 连字符子类，完整提取两个 UI 预览。
- 优先使用 HTML 中的原图 URL；支持 `data-src`、`src`、`srcset` 与协议相对/站内相对地址。`/thumb/` 不当成原图；仍通过已有批量 imageinfo（含文件重定向映射）解析未解决的文件名。
- 原图 URL 与具体卡片文件名绑定，不通过移除 icon 后缀发明高清文件。imageinfo 请求规范化空格/下划线，并把返回地址映回调用方文件名。
- 缺少图片解析结果时保留真实条目及文本，不把完整目录悄悄裁成部分列表。
- `cacheOnly` 的 HTML、模块、imageinfo 均只调用 `loadCached*`；即使同时传 `forceRefresh=true` 也绝不调用网络操作。离线模块不存在时回退 HTML；原图齐全时不调用 imageinfo source。图片控件沿用既有图片加载/缓存机制，这与 API 数据读取是不同的职责。
- 九个简单页的新鲜完整 Lua 双表足够时不请求 HTML；只请求尚未解析的图片。复杂 HTML 页面若原图齐全，模块/imageinfo 都是零补请求。
- 新成对缓存键 `decoration_lua_pair_v1_<pageName>` 在一次原子写入中保留条目表与途径表，只有联合 schema/ID/覆盖率/基线校验通过才能替换好磁盘快照；不拼接独立过期模块缓存。保留既有 HTML 键 `decoration_<pageName>` 与 imageinfo 键公式。旧的单模块缓存不作为主源依赖对使用。
- 超过 DECORATIONS 三天寿命的双表即使用户允许磁盘长期保留，也不作为当前主源覆盖 HTML；文件不删除。在线HTML成功时可用于兼容；两方均是缓存时按完整性与年龄择新，同龄优先Lua。cacheOnly也采用同一规则且仅查磁盘，详见末节复审维护。
- Lua 失败降级、过期、结构不完整或图片缺失时不发布永久 memo；完整离线数据同样由基类阻止内存发布。强制刷新不删除好磁盘；失败不清空原成功内存，旧请求不能覆盖新请求，取消异常透传。两种目录都不可用才报错。
- 界面默认全部分组，可搜索名称、说明、效果及获取方式；复杂目录保留分组标题，分类间分别保存滚动位置。卡片图使用 Fit、名称两行、来源一行；先打开资料详情，再点击主图／附属图进入全屏预览和保存，长文字不再遮盖大图。页面按pageName隔离LoadState与弹层，退出中的旧Hub页移除弹层；布局详见 [UI架构](android-wiki-ui-architecture.md)。
- `光之轮舞.gif`、`独幕曲.gif` 在捕获中为尚未上传的红链接。HTML与Lua均保留其声明文件与标签；imageinfo缺失时附属URL为空，UI显示“图片暂不可用”并禁用点击／保存，不把声明当成已存在图片。

## 回归与可选全页审计

测试按职责拆分（旧 `PlayerDecorationParsersTest` 已替换）：

- `parser.DecorationHtmlParserTest`：真实名片/手办及新增房间/载具/手写勋章/gallerybox；旧结构、换行与文件身份。
- `parser.DecorationModuleParserTest`：真实名片双表、零品质房间、动作 GIF、封装 file、缺依赖/冲突/畸形/基线不足。
- `parser.DecorationWaysParserTest`：字符串/数组/get+remark、真实过期头套价格与可读 Wiki 标记。
- `model.DecorationCatalogsTest`：九种 Lua 双表主源、复杂 HTML 目录、移动端原顺序及基线。
- `api.DecorationAssemblerTest`：跨平台同 ID、同组去重/跨组保留、冲突 key 唯一、notice 与缺图预览。
- `api.DecorationCatalogApiTest`：Lua 成功不请求 HTML、复杂目录不请求 Lua、cacheOnly 零网络、失败恢复/过期、缺图、取消、force 和请求版本。
- `source.DecorationLuaCachePayloadTest`：使用与生产相同的缓存校验器证明坏依赖对不能覆盖好磁盘。
- `core.wiki.LuaTableParserTest`：严格对象表入口与原数组入口兼容性。

小型 fixture 位于 `androidApp/src/test/resources/fixtures/decoration/`，真实 fixture 注释记录来源修订版；旧结构的合成 fixture 明确标为 synthetic。全页捕获不提交。

可选 `PlayerDecorationRealPageAuditTest` 读取 `PLAYER_DECORATION_AUDIT_DIR` 下的 UTF-8 **渲染 HTML**：`个性名片.html`、`休息室手办.html`（可为完整网页或含 `.mw-parser-output` 的正文）。未配置目录时跳过，不联网。针对上述修订版检查 5/14 数量、完整 ID/名称顺序、每卡品质/完整描述/获得方式/真实原图与源 DOM 一致；页面新增内容时应先核对源，再更新审计期望。

新增 `DecorationSourceAuditTest`：设置 `DECORATION_SOURCE_AUDIT_DIR=C:/Users/NekoLaska/AppData/Local/Temp/opencode/decoration-source-audit`，实际读取全部 13 份条目表、13 份途径表及12页 HTML；名片/手办 HTML 缺省从相邻 `player-decoration-audit` 目录读取。对照九种主源共 **226** 条的集合、顺序、名称、品质、描述、途径、特殊效果和附属预览文件（包括已声明未上传的动作GIF）；核对复杂三页全部源图片身份与 key 唯一性。基板途径表重复键的真实冲突作为**预期诊断**验证，不能放松校验来使它被当成有效主源。保留 `PLAYER_DECORATION_AUDIT_DIR` 的原两页审计。所有审计只读本地，无网络；缺少显式配置时跳过。

父会话最终统一运行 `*.feature.wiki.decoration.*`、`*.core.wiki.LuaTableParserTest`、`*.core.cache.*`、`*.feature.wiki.hub.*`：**84 项，0 failures/errors/skips**，上述完整快照全部实际参与。Android 编译、Lint通过（0 errors / 5个既有warnings）。首轮全页回归发现基板源表冲突与动作红链接两个边界，已分别建立预期诊断和保留缺图声明的回归后通过；未追加Wiki抓取。此前48项结果仅为旧HTML/enrichment阶段记录。

Hub 已接入独立入口、保存编码和搜索，提交与发布见2.1.30记录；暂无连接设备，图片 Fit、长说明滚动与小屏表现尚未设备验收。

## 2026-11-14：HTML 与 Lua 主数据源实查

用户要求实际探索稳定性后再下结论。本节保留**迁移前调查证据**；其后用户授权，现已按前述方案实施九页 Lua 主源和 HTML 修复。调查检查了 12 个现存目录页的源码、13 份条目表（含移动端喷漆）、13 份获得方式表、PC/移动端展示模块、Gallerygrid 及四个子模板。10 个渲染页面在调查阶段获取；名片／手办复用完整捕获。调查串行请求后间隔至少 6.5 秒，未遇 567；实施阶段未再联网。

原始证据与脚本保存在本机 `C:/Users/NekoLaska/AppData/Local/Temp/opencode/decoration-source-audit/`。`comparison.json` 是按 ID 对照结果；Lua 数据使用现有已编译 `LuaTableParser` 的受限解析器离线转成 JSON，未执行上游 Lua。渲染 DOM 对照使用 lxml；`HtmlAudit.java` 直接调用当前已编译 `DecorationHtmlParser` 复现现存展示缺口。没有重建、提交或发布应用。

### 核心事实

- PC 展示模块 [`模块:玩家装饰`](https://wiki.biligame.com/klbq/模块:玩家装饰) revision **117402**；移动端展示模块 revision **112218**。页面模板先读条目表，再按 `itemType + id` 调用 [`模块:获得方式`](https://wiki.biligame.com/klbq/模块:获得方式)（111973）读取独立途径表。绝大多数 Data 行已无 `get` 字段；仅获取目前配置的 Data 表不等于获得完整资料。
- 途径表为数值 ID 键控表，不是条目数组。当前捕获的 1956 条途径记录中，1942 条为数组、14 条（GarageKits）为字符串；获得方式模块还明确兼容 `{get=..., remark=...}` 形式用于筛选。迁移不能仅复用当前 `parseReturnArray` 或假定每个值都是字符串列表。途径字符串含 `<del>` 等标记，需要保留“已失效”语义，不能把旧价与现价并列当作同时有效。
- **简单 9 页**的 Lua ID 集合与渲染卡片一一相同，名称／品质／描述无差异：封装52、聊天气泡37、头套9、超弦体动作16、房间外观7、头像框71、载具15、手办14、名片5；对应每个 ID 都存在途径表记录。当前足以支持后续切为“条目 Lua + 获得方式 Lua”主源。
- 图片身份有可核验规则：封装由 `file` 提供；其余简单目录由展示模块显式规定文件格式。动作 GIF 是展示模块中的 `name + .gif`；房间背景及好友列表／按钮预览由 `Gallerygrid/ChangeBg` 显式拼接 `.png`、`_FriendTitleBg.png`、`_BtnBg.png`。这些属于已证实源规则，URL仍应由 imageinfo（含 redirects）解析，不拼 CDN 哈希路径。

### 复杂目录不能只遍历一张 Data 表

| 目录 | 当前实查 | 迁移约束 |
| --- | --- | --- |
| 基板 | 473 行／471 个 Lua ID，网页492张卡／469个ID；31001242、31001243为完全相同的重复行 | 页源码逐标题传入获取方式关键词筛选；同一条目可属于多组。31000002途径为空、31001279途径“失落音律-随星重鸣”未匹配当前章节，不能擅自解释为未公开或补进现有分类 |
| 喷漆 | PC623个ID；页面同时调用30条移动端表，673张卡／646个数值ID | 必须使用平台＋类型＋ID身份。30002237在PC为“默契考验”、移动端为“马踏飞弦”；30002250分别为“赏春荧”／“甜蜜心愿”，不能按裸ID覆盖。原页还含喷漆音效 |
| 勋章 | BadgeData606行；网页337张gallerygrid卡，另有成就印迹生成的gallerybox | 页面混用按途径筛选的模块、手写成就列表、`模块:成就印迹.main2`。后者按角色和ID前7位分组只展示最高等级。不能把606行直接当页面目录，手写说明也可能不同于Data |

`登场特效` 只是原策略表中的历史配置，本次 query 返回该页面不存在，当前Hub也无对应入口；不将13条配置数冒充13个已核验页面。

### 迁移前确认的 HTML 适配缺口（本次已补修并通过回归）

1. **房间附属预览漏失**：模板 `Gallerygrid/ChangeBg` revision **119870** 当前使用 `.gallerygrid-item.roomgrid`、`.roomgrid__image-bg`、`.roomgrid__ui-item`；旧 parser 判断 `.roomgrid-item` 并查询连字符子类。实跑仍得到7个主条目，但 `extraPreviews=0`，源 DOM 有14个附属预览。此前合成旧fixture通过不能代表当前网页兼容。
2. **载具字段混淆**：模板 `Gallerygrid/VehicleSkin` revision **119876** 在 HTML 显示 `spdesc` 为“外观效果”；15张卡均可对应。旧实现全选 `li` 将效果、父级获得方式标签和嵌套途径重复拼入 `source`；默认载具旧解析实跑为“外观效果：、获得方式： 默认、默认”，修复后仅“默认”。
3. **源表重复途径冲突**：`模块:获得方式/IDCard` 对31001242与31001243先声明“未实装”，随后又声明“特别行动26SP3”。本轮严格解析拒绝冲突键，不将其静默覆盖成某个值；基板继续以网页实际分组为准。该问题不影响九个已迁移主源。

### 结论

按调查建议实施**结构化 Lua 双表主源**的九个简单目录，HTML 用于对照与受控降级；复杂目录继续保留原 HTML 的分组、平台与手写/印迹目录政策。Lua 仍非稳定版本化 API，联合完整性校验、冲突诊断、图片文件规则与全量快照回归共同约束发布，不因少发请求就默认任一来源完整。

## 2026-11-14：复审维护——缓存择新与 HTML 发布边界

本段更新前述「缓存 Lua 一律先让位 HTML」策略：

- 正常请求得到完整、未过期的在线 Lua 双表时直接使用，不额外读取 HTML；Lua 更新失败后，成功在线取得且通过完整性检查的 HTML 可优先。
- 双方都是磁盘快照时，只比较完整资料，以 `ageMs` 较小者为准；同龄优先 Lua。`cacheOnly` 同样读取两种磁盘快照并使用这条规则，避免预取与正常离线读取选择不同版本。过期 Lua 始终排除；它不会因 HTML 更旧而重新取得主源资格。缺少另一方时使用现有的有效一方。
- 选择缓存时保留其实际离线标记、年龄和资料说明；Lua 更新失败的结果明确提示下次刷新重试。缓存或 Lua 降级 HTML 不发布永久内存命中；force、取消及成对缓存原有约束保留。

`DecorationHtmlCachePayload` 是 decoration 专用的 JSON/目录校验边界：要求正常 parse envelope、与请求一致的字符串页面标题、字符串 HTML 正文，以及 `DecorationHtmlParser.parseResult().isComplete`。网络原始响应在 `OfflineCache.fetchWithCache(validatePayload=...)` 发布前验证，合法 JSON 中的坏正文也不得替换好磁盘；磁盘读取复用同一校验，API 对返回的 HTML 再做完整性检查。继续使用原 HTML cache key，不改变通用缓存行为。

HTML 完整性按正文实际识别出的最外层 `.gallerygrid-item` / `li.gallerybox` / 新旧 roomgrid 卡片计数，要求每张都有真实正 ID、主图文件身份和非空名称，并拒绝目录中的模板/模块错误。计数发生在同组去重之前，合法重复卡片不误报。没有写死复杂目录最新数量，旧的小型完整目录仍有效；这种结构校验不能凭空识别上游已整段移除、且没有留下任何容器或错误标记的内容。`parse()` 保留兼容入口，返回可解析 sections；缓存和 API 必须使用带诊断的完整性结果，不能仅判断列表非空。

主图缺失仍可保留资料：只在明确主图容器读取 `title="文件:…"` / `File:…`、文件页 href 或 Wiki 上传链接的 `wpDestFile`。支持现代卡片、gallerybox thumb、新旧 roomgrid；旧卡片的直接 img 或含 img 的 a 子节点仍兼容，容器外孤立红链接不作主图。文件声明必须含真实 ID，URL 可为空；不会扫描说明、获取方式、动作预览或房间附属预览来冒充主图，也不会生成序号身份。无 img 时存在多个不同主图声明、缺身份或缺有意义名称的卡片使整页不适合发布。

新增回归：`DecorationHtmlCompletenessTest`、`DecorationHtmlCachePayloadTest` 与 `DecorationCatalogApiTest` 的来源/年龄矩阵。`missing-main-artwork.html` 基于已捕获结构缩减，明确标记为缺文件的合成变体；覆盖无 img 的完整资料、主图声明边界、非空部分坏卡、重复卡片、合法 JSON 坏正文保留旧缓存、旧目录可读、在线 HTML 优先、过期 Lua 排除、cacheOnly/正常离线一致以及取消传播。

父会话已统一验证本段变更，复用 `DECORATION_SOURCE_AUDIT_DIR` / `PLAYER_DECORATION_AUDIT_DIR` 全页捕获，连同实时列表快照、分享索引、缓存和Hub回归共 **102项，0 failures/errors/skips**。Android编译与Lint通过（0 errors / 5个既有warnings）。过期但结构有效的Lua仅走过期回退，不再误记为结构解析异常。设备视觉验收仍独立待办；本段随2.1.30交付。
