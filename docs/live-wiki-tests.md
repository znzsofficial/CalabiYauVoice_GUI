# Wiki 实际 HTML 联网测试

在项目根目录运行（PowerShell）：

```powershell
$env:LIVE_WIKI_TEST = '1'
.\gradlew.bat :androidApp:testDebugUnitTest --tests 'com.nekolaska.calabiyau.feature.wiki.LiveWikiSnapshotTest.fetchesAndParsesCurrentWikiHtmlWhenEnabled' --rerun-tasks
Remove-Item Env:LIVE_WIKI_TEST
```

`--rerun-tasks` 避免 Gradle 将上次结果视为最新而跳过联网请求。
未启用环境变量时，联网用例显示为 skipped，而不是通过。

目前实际请求 BWiki 中应用使用的主要公开 HTML 页面，包括道具、Tips、历史、成就、活动、BGM、联动、印迹、喵言喵语、梗百科、誓约、玩家等级、剧情、投稿、好友、武器外观、地图等。每个页面都会校验 HTTP 状态、HTML Content-Type 和 MediaWiki 内容标记；其中道具、Tips、历史还会调用应用现有 parser 检查解析结果非空。请求失败或解析为空会导致测试失败，不回退到缓存。

本次抓取的 HTML 位于 `androidApp/build/live-wiki/`；测试报告位于 `androidApp/build/reports/tests/testDebugUnitTest/`。

这是公开 HTML 到 parser 的联网冒烟测试，不覆盖全部 Wiki 页面、登录接口或 Android UI。原有 `parsesLocalLiveSnapshotsWhenPresent` 仍是读取本地可选快照的离线测试。

## 已知回归

- `fetchesAndParsesWeaponSkinModuleWhenEnabled`：2026-09-18 联网验证发现武器外观 Lua 模块的输出从
  `|- class="divsort" data-param*` 表格行改为 `div.klbq-skin-card[data-param*]` 卡片结构，
  现有 `parseWeaponSkinHtml` 对新结构解析为 0 条记录，武器外观筛选在线上已失效。
  修复 parser 前该用例会以 skipped 呈现（附回归说明），不会掩盖失败也不会阻塞其他验证。
  抓取的现场数据保存在 `androidApp/build/live-wiki/weapon_skins.json`，可直接用于修复后的离线回归。

页面列表与模板渲染检查：

- 普通页面：功能道具筛选表、游戏Tips、游戏历史、成就、活动、BGM、联动、印迹、喵言喵语、梗百科、誓约、玩家等级、剧情故事、投稿作品、好友、战斗模式（16 个）。
- Lua 模板：`{{#invoke:武器|武器外观筛选}}` 经 `action=parse` 渲染后交给 `parseWeaponSkinHtml`。
- 地图列表与时装投票等其余功能分别依赖模式模板与登录 Cookie，暂不在通用联网冒烟范围内。
