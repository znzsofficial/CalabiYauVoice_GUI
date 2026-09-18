# Wiki 实际 HTML 联网测试

在项目根目录运行（PowerShell）：

```powershell
$env:LIVE_WIKI_TEST = '1'
.\gradlew.bat :androidApp:testDebugUnitTest --tests 'com.nekolaska.calabiyau.feature.wiki.LiveWikiSnapshotTest' --rerun-tasks
Remove-Item Env:LIVE_WIKI_TEST
```

`--rerun-tasks` 避免 Gradle 将上次结果视为最新而跳过联网请求。
未启用环境变量时，联网用例显示为 skipped，而不是通过。

实际抓取下方列出的 16 个公开 HTML 页面，校验 HTTP 状态、HTML Content-Type 和 MediaWiki 内容标记。其中道具、Tips、历史会调用现有 parser 检查解析结果非空；其余 13 个页面仅检查可达性和文档结构，不代表对应 parser 已通过验证。武器外观另经 parse API 获取渲染 HTML 并验证 parser。启用的联网检查失败会报错，不回退到缓存。

本次抓取的 HTML 位于 `androidApp/build/live-wiki/`；测试报告位于 `androidApp/build/reports/tests/testDebugUnitTest/`。

这是公开 HTML 到 parser 的联网冒烟测试，不覆盖全部 Wiki 页面、登录接口或 Android UI。原有 `parsesLocalLiveSnapshotsWhenPresent` 仍是读取本地可选快照的离线测试。

## 回归处理

- `fetchesAndParsesWeaponSkinModuleWhenEnabled`：2026-09-18 联网验证发现武器外观 Lua 模块的输出从
  `|- class="divsort" data-param*` 表格行改为 `div.klbq-skin-card[data-param*]` 卡片结构，
  parser 已兼容新结构。已删除根据解析结果自动跳过的临时豁免；启用联网测试后，解析为空必须失败。
  抓取的现场数据保存在 `androidApp/build/live-wiki/weapon_skins.json`，可直接用于修复后的离线回归。

页面列表与模板渲染检查：

- 普通页面：功能道具筛选表、游戏Tips、游戏历史、成就、活动、BGM、联动、印迹、喵言喵语、梗百科、誓约、玩家等级、剧情故事、投稿作品、好友、战斗模式（16 个）。
- Lua 模板：`{{#invoke:武器|武器外观筛选}}` 经 `action=parse` 渲染后交给 `parseWeaponSkinHtml`。
- 地图列表与时装投票等其余功能分别依赖模式模板与登录 Cookie，暂不在通用联网冒烟范围内。
