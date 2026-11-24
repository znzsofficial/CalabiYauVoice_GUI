# Wiki 实际 HTML 联网测试

## 选择验证方式

优先使用跟踪fixture和已有本地快照。只有明确需要核验当前上游时才启用联网测试；下面的强制重跑命令是**主动抓取**入口，不是普通发布步骤。当前验证结果以[发布记录](README.md#发布记录)为准，设备行为见[设备验收](device-acceptance.md)。

- `WikiGoldenFixtureTest` 使用随仓库提交的精简 API JSON（公告 ask、活动与武器 parse），新检出即可离线运行。
- `WikiRealPageGoldenTest` 读取 `androidApp/src/test/resources/fixtures/pages/` 中的本地整页样本；该目录不入库，缺文件时不验证对应页面。上游结构变动时，按原查询重抓（正文页用 `action=parse&page=<页名>&prop=text`），核对新页面中的具体字段，再更新样本和断言。

### 本地完整快照入口

这些环境变量只指定已有文件/目录，不自动抓取；未配置时对应的可选审计跳过，其余跟踪fixture仍可运行。文件格式与目录命名须遵照对应专题，不能混用完整网页、parse JSON及wikitext。

| 环境变量 | 资料范围 | 格式与说明 |
| --- | --- | --- |
| `BEGINNER_GUIDE_AUDIT_FILE` | 新人指南 | 完整parse HTML；[专题](beginner-guide.md) |
| `MECHANICS_AUDIT_DIR` | 弦化、定位、增幅网络 | `stringify.html`、`roles.html`、`augmentation.html`；[专题](native-mechanics-pages.md) |
| `PC_GAME_SETTINGS_AUDIT_FILE` / `PC_GAME_SETTINGS_API_AUDIT_FILE` | PC设置 | 旧网页与实际parse JSON两种审计；[专题](pc-game-settings.md) |
| `DECORATION_SOURCE_AUDIT_DIR` / `PLAYER_DECORATION_AUDIT_DIR` | 装饰Lua/HTML及名片手办 | [主源与文件说明](player-decoration-catalogs.md) |
| `CHARACTER_SETTINGS_AUDIT_FILE` | 超弦体补充设定 | [设定审计](character-detail-presentation.md) |
| `WIKI_LIST_AUDIT_DIR` | 角色、武器、地图列表 | [列表审计](wiki-list-resilience.md) |
| `EXTRACTION_AUDIT_DIR`、`EXTRACTION_DROPS_AUDIT_FILE`、`EXTRACTION_CONTAINERS_AUDIT_DIR`、`EXTRACTION_UPDATED_AUDIT_DIR` | 多代弦溯结构 | 各自对应不同上游结构，不相互替代；[专题](extraction-mode-implementation.md) |
| `EXTRACTION_SPLIT_AUDIT_DIR` | 11 月 21 日晚的弦溯物资、配件、装备和工坊 | 新旧物资入口与配件 Q8 使用迁页快照；同批 `guns`、`armor`、`workshop` 捕获供新栏目的离线验证。工坊保留完整 `.json`（含 HTML 与 wikitext）和 `.html`；不要覆盖白天的基线。 |
| `EXTRACTION_NOV24_CATALOG_DIR` | 11 月 24 日简写装备价值与新版来源说明 | 解析接口返回的正文分别存为 `guns.html`、`armor.html`、`loot.html`、`attachments.html`；由 `November24CatalogAuditTest` 离线核对，与 11 月 21 日快照共存。 |
| `WIKI_ACTIVITY_NOV24_AUDIT_DIR` | 「欢迎光临卡邦尼！」活动、奖励获取途径和三份武器增幅资料 | `November24ActivityAuditTest` 对照首页卡片、活动列表、详情、聊天气泡和喷漆；`WeaponDefaultAugmentTest` 核对影袭／雨晦／破晓的同名 `.html` 与 `.txt` 快照。 |
| `WIKI_ARCHIVED_ACTIVITY_DIR` | 2023–2026 往期活动 | `ArchivedActivityAuditTest` 用 `activity.html` 原生列表逐一匹配目录内 `item-*.json`（含 `parse.title`、`text.*`、`wikitext.*` 的原始响应），通过生产详情 API 重放首开、缓存读回，核对一级章节与原文章节锚点。样本不是固定的线上活动白名单。 |
| `EXTRACTION_RATES_NOV24_DIR` | 弦溯单件抽中率 | 名称索引、全部八个分片及容器表快照，供 `ExtractionDropRatesNovember24Test` 对照全部名称／品质、百分数与阶段标签。 |
| `WIKI_MAP_MODE_AUDIT_DIR` | 维里昂地图和模式阅读 | `ExtractionFieldGuideTest` 对照修订 120739／120758／120781／120798 的点位、测试页、原文与渲染表格。 |
| `WEAPON_AUDIT_DIR` / `MAP_BIO_AUDIT_DIR` | 武器、地图、生化 | [武器](weapon-parsing-audit.md)、[地图与卡池](rift-bio-pool-followup.md) |
| `WIKI_NOV21_AUDIT_DIR` | 11 月 21 日弦溯、指南、伏闪 | 弦溯读 `drops.html`、`table.html`、`loot.html`、`attachments.html`；指南读 `beginner.html`、`review-1121/120474.html`、`review-1121/historical-120474.json`；伏闪优先读 `fushan-current.json`，也可用 `fushan.html` 与 `120510.txt`。 |

**2026-11-21 白天（[2.2.3](releases/2.2.3.md)）：** 使用新版完整页面及旧快照，Android 武器详情 43 项、新人指南与容器表 34 项、其余弦溯专项 80 项通过，三组筛选不重叠，均无跳过。Debug 编译通过，Lint 0 错误，5 个既有警告（4 UseKtx、1 ModifierParameter）。此处验证的是源码对真实页面的解析和缓存行为。

同日二审修复后，重跑新人指南与全部弦溯专项：**122 项通过，0 失败、0 跳过**；Debug 编译和 Lint 通过，仍为上述 5 个警告。覆盖 120474 旧缓存、等价文章链接、逐个删除共用池、来源卡数量和池规模矛盾、同区来源搜索与排序说明、容器件数空白与明确未知的区别。该批与前一批有重叠，不累加计数。

**同日晚间（[2.2.4](releases/2.2.4.md)）：** `EXTRACTION_SPLIT_AUDIT_DIR` 的迁页回归核对物资新页、配件 Q8、旧缓存及错页拒收。两个版本的快照和结果分别保留。

## 主动联网抓取

在项目根目录运行（PowerShell）：

```powershell
$env:LIVE_WIKI_TEST = '1'
.\gradlew.bat :androidApp:testDebugUnitTest --tests 'com.nekolaska.calabiyau.feature.wiki.LiveWikiSnapshotTest' --rerun-tasks
Remove-Item Env:LIVE_WIKI_TEST
```

`--rerun-tasks` 避免 Gradle 将上次结果视为最新而跳过联网请求。
未启用环境变量时，联网用例显示为 skipped，而不是通过。

实际抓取下方列出的 15 个公开 HTML 页面，校验 HTTP 状态、HTML Content-Type 和 MediaWiki 内容标记。其中 Tips、历史会调用现有 parser 检查解析结果非空；其余 13 个页面仅检查可达性和文档结构，不代表对应 parser 已通过验证。PC/移动端道具图鉴分别经 parse API 获取真实页面并验证字段；武器外观另经 parse API 获取渲染 HTML 并验证 parser。启用的联网检查失败会报错，不回退到缓存。

本次抓取的 HTML 位于 `androidApp/build/live-wiki/`；测试报告位于 `androidApp/build/reports/tests/testDebugUnitTest/`。

这是公开 HTML 到 parser 的联网冒烟测试，不覆盖全部 Wiki 页面、登录接口或 Android UI。`parsesLocalLiveSnapshotsWhenPresent` 读取同一 `build/live-wiki/` 目录的本地快照做离线断言（抓取路径已与读取路径统一，抓完即可复跑）。

## 回归处理

- 2026-10-13：旧合并道具页已下线（404），改为 `功能道具图鉴PC端` / `功能道具图鉴移动端`。
  `fetchesAndParsesPcItemCatalogWhenEnabled` / `fetchesAndParsesMobileItemCatalogWhenEnabled`
  使用与生产 source 相同的页名独立验证两端（一端失败不跳过另一端），
  锁定 PC「理想币」/移动端「排位币」的分类、简介、原图 URL，并校验卡片数量下限。
  新快照为 `build/live-wiki/items_pc.html`、`items_mobile.html`；不再读取旧 `items.html`。
  `fixtures/items/` 的两个跟踪样本覆盖现行卡片解析，不再以旧合并页的表格作为回归基线。
  移动端仅收录独有道具，共有道具仍在 PC 图鉴。`ItemCatalogPlatform` 管理两端的页名和缓存键，内存与磁盘均按平台隔离；不再请求合并页或读取旧 `item_catalog` 缓存。旧快捷入口和保存的 `WikiRoute.Items` 仍打开 PC，移动端使用独立的 `MobileItems`。

- `fetchesAndParsesWeaponSkinModuleWhenEnabled`：2026-09-18 联网验证发现武器外观 Lua 模块的输出从
  `|- class="divsort" data-param*` 表格行改为 `div.klbq-skin-card[data-param*]` 卡片结构，
  parser 已兼容新结构。已删除根据解析结果自动跳过的临时豁免；启用联网测试后，解析为空必须失败。
  抓取的现场数据保存在 `androidApp/build/live-wiki/weapon_skins.json`，可直接用于修复后的离线回归。

页面列表与模板渲染检查：

- 普通页面：游戏Tips、游戏历史、成就、活动、BGM、联动、印迹、喵言喵语、梗百科、誓约、玩家等级、剧情故事、投稿作品、好友、战斗模式（15 个）。
- 道具页：功能道具图鉴PC端、功能道具图鉴移动端，使用 `action=parse&page=...&prop=text`，分别跑生产 parser。
- Lua 模板：`{{#invoke:武器|武器外观筛选}}` 经 `action=parse` 渲染后交给 `parseWeaponSkinHtml`；
  另有 `{{#invoke:角色|角色时装筛选}}`（时装筛选）与 `{{游戏地图|一般爆破}}`（地图列表卡片）两个模板的联网解析校验。
- 端到端（`fetchesAndParsesAnnouncementActivityWeaponWhenEnabled`）：公告 ask 新属性查询、活动卡片解析、
  静风/北极星/大剑/小蜜蜂四类武器详情 wikitext+HTML 解析，全部跑真实 parser 并按武器锁定关键结构
  （静风多使用者、北极星移动端距离行、大剑近战嵌套行、小蜜蜂上肢列），任一解析路径回归都能定位。
- 角色详情：`CharacterDetailLiveAuditTest` 读取 `build/char-audit/` 下的抓取
  （action=parse&page=<角色名>&prop=wikitext|text，超弦体×2 + 晶源体×1）跑完整 parser
  （`CharacterDetailApi.parseCharacterWikitext`，internal 供测试），
  断言技能/数值组/增幅模式（爆破+极限推进+弦区争夺克隆）/故事条目/声优拆分/定位等。此审计和 `parsesLocalLiveSnapshotsWhenPresent` 都只读本地，缺少相应捕获时跳过。
- 限流：`fetchBody` 每次请求后强制 6.5 秒间隔（成功与失败路径都等待），四把霰弹枪诊断时以墙钟 187 秒实测生效；新增请求时保持该节奏。遇到 EdgeOne HTTP 567 应停止抓取；同一 IP 的浏览器也可能受限，等数十分钟后再复核，不要连续重试。
