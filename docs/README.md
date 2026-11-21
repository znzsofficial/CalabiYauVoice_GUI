# 项目文档

## 从这里开始

- 使用与构建：[中文 README](../README_ZH_CN.md) / [English README](../README.md)
- 当前发布：[2.2.4（65）](releases/2.2.4.md)，2026-11-21；机器可读元数据以 [`latest.json`](../downloadPage/downloads/latest.json) 为准。
- 理解客户端：[Android 当前架构](android-architecture.md)
- 开发 Wiki 功能：[分层与缓存约定](android-wiki-feature-guide.md)、[公共 UI 与状态](android-wiki-ui-architecture.md)
- 发版：[版本发布清单](release-version-checklist.md)
- 未完成的设备验证：[设备验收清单](device-acceptance.md)

## 架构与基础设施

- [Android 导航与页面状态](android-navigation-followup.md)：页面恢复、Hub 快照、图片预取和搜索边界。
- [Hub 转场](hub-transition-stability.md)：共享元素、预测返回和迟到回调保护。
- [缓存、网络与文件管理](android-core-file-manager.md)：取消、清理代数、文件路径边界和安装包清理。
- [资源下载](resource-download-implementation.md)：按需搜索、任务恢复、完整性清单及原子下载。
- [WebView 与登录](android-webview-auth.md)：Cookie 票据、导航规则、宿主生命周期、弹窗和文件选择。
- [2.2.1 架构修复记录](android-architecture-fixes.md)：审查发现、修复范围、验证证据及未采用的重构建议。
- [2.2.2 架构二次复审修复](android-architecture-round-two.md)：Wiki 提交、离线副本、文件归属、媒体成品及系统任务恢复。
- [桌面端指南](desktop-implementation-guide.md)：Windows 窗口、媒体处理及分发。
- [用户系统后端](user-system-backend.md)：账号、留言/通知、管理 API 和 D1 迁移。
- [Android 匿名使用统计](app-usage.md)：历史总数、近 30 天趋势、留存、45 天明细归档与 D1 容量。

## Wiki 功能专题

### 指南与玩法

- [新人入坑指南](beginner-guide.md)：玩法入门、版本与下载、装备及设置建议、常见问题。
- [PC 游戏设置](pc-game-settings.md)：设置、推荐和参考表的语义边界。
- [弦化、角色定位与增幅网络](native-mechanics-pages.md)：三页原生阅读和完整导航分流。
- [弦溯战线](extraction-mode-implementation.md)：物品、配件、掉落查询及历次上游结构兼容。
- [裂隙与生化卡池](rift-bio-pool-followup.md)：地图、双卡表、正式分享索引。

### 图鉴与资料

- [角色详情](character-detail-presentation.md)：技能、设定、关系和呈现规则。
- [武器解析](weapon-parsing-audit.md)：伏闪时间轴、按模式展示的武器增幅、多使用者入口与参数审计。
- [玩家装饰](player-decoration-catalogs.md)：Lua 双表/HTML 主源、获取方式、缺图和预览。
- [列表健壮性](wiki-list-resilience.md)：角色、武器、地图的身份与完整性。
- [导航覆盖审计](wiki-navigation-coverage.md)：历史菜单矩阵和2.2.1增量，非实时全站清单。
- [联网与本地快照测试](live-wiki-tests.md)：完整快照环境变量索引、主动联网开关、证据来源和请求节流。

## UI 与设计记录

- [UI 优化实施记录](android-ui-refactor-plan.md)：2.2.0已完成改动、2.2.1文案更新，以及明确标注的历史计划。
- [工具视觉回退](tools-mesh-tab-surfaces.md)：保留已确认的视觉选择，避免后续重新引入被否决设计。

## 发布记录

发布记录保存**当次**版本、产物指纹、部署和检查结果，不作为每次发版必须重跑的测试清单。

- [2.2.4](releases/2.2.4.md)：投票会话修复、物资图鉴迁页、卡牌首开、全屏图片与匿名使用统计。
- [2.2.3](releases/2.2.3.md)：伏闪详情、弦溯掉落反查、新人指南八章、字幕导出与单选控件。
- [2.2.2](releases/2.2.2.md)：图鉴详情换行、下载与更新恢复、Wiki 写入和离线资料、媒体工具修复。
- [2.2.1](releases/2.2.1.md)：四个原生资料页、架构修复、全局文案。
- [2.2.0](releases/2.2.0.md)：UI统一、骨架、工具交互、PC设置修复。
- [2.1.30](releases/2.1.30.md)：PC设置和装饰主源。
- [2.1.29](releases/2.1.29.md)：上游适配、列表和角色设定。
- [2.1.28](releases/2.1.28.md)：掉落玩家任务与图片。
- [2.1.27](releases/2.1.27.md)：掉落反查与转场。
- [2.1.26](releases/2.1.26.md)：掉落池/阶段和公共UI。
- [2.1.25](releases/2.1.25.md)：地图、平衡、掉落表。
- [2.1.24](releases/2.1.24.md)：解析和导航修复。
- [2.1.23](releases/2.1.23.md)：生化、平衡和关于页。
- [2.1.22](releases/2.1.22.md)：下载清单与安装包清理。
- [2.1.21](releases/2.1.21.md)：早期基础设施与发布记录。

## 归档与维护口径

- [旧会话交接](session-handoff.md)仅用于追溯，里面的“待提交/待发布”不代表当前任务状态。
- [`webApp`](../webApp/README.md)是归档的Compose演示；生产网页在 `downloadPage/`。
- 当前行为写入对应专题；实施经过和测试数量写入发布/修复记录。不要在每个专题复制一份“最新版本”和全量测试总数。
- 上游快照的数量、修订号和外部文件路径保留其日期；不能把历史捕获描述成实时Wiki结果。
- 修改基础契约时同步对应专题与 [`AGENTS.md`](../AGENTS.md)。文档整理只做链接/内容/diff检查，不触发构建、上传或重新部署。
