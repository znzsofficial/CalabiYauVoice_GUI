# 项目文档

## 从这里开始

- **安装或从源码运行**：[Android 下载站](https://wiki.nekolaska.vip/) · [中文构建说明](../README_ZH_CN.md#-构建与运行) · [English build instructions](../README.md#-build-and-run)。Windows 桌面端可从源码运行，本次发布只分发 Android APK。
- **接续开发**：常用命令和仓库边界见[根协作指南](../AGENTS.md)。从 [Android 当前架构](android-architecture.md) 找模块；改界面视觉与组件样式先读 [Android 视觉设计规范](android-ui-design-guide.md)；新增 Wiki 页面看[分层与缓存约定](android-wiki-feature-guide.md)、[公共 UI 与状态](android-wiki-ui-architecture.md)。
- **接续当前版本**：先看[发布记录](releases/2.3.2.md)与[设备验收清单](device-acceptance.md)。战术风与画册风的使用边界见[Android 视觉设计规范](android-ui-design-guide.md)；日常 Wiki 联网检查见[每日 CI 读法](live-wiki-tests.md#怎样判断每日-ci-是否真的联网)。[11 月 24 日交接](handoffs/2026-11-24-2.3.1.md)保留上一版基线。
- **排查线上问题**：Wiki 登录或投票看[账号会话](android-webview-auth.md#cookie-会话边界)，图鉴读不到数据看[快照索引](live-wiki-tests.md)，后台数字看[统计口径](app-usage.md#面板与口径)。
- **验证与发版**：[设备验收清单](device-acceptance.md)记录仍需实机操作的项目；[发布清单](release-version-checklist.md)列出签名、迁移和主站核验顺序。

## 架构与基础设施

- [Android 视觉与组件设计规范](android-ui-design-guide.md)：柔光画册（首页／图鉴／详情）与战术情报（设置／搜打撤）两套视觉语域、组件原语与设计红线。
- [Android 导航与页面状态](android-navigation-followup.md)：页面恢复、Hub 快照、图片预取和搜索边界。
- [Hub 转场](hub-transition-stability.md)：共享元素、预测返回和迟到回调保护。
- [缓存、网络与文件管理](android-core-file-manager.md)：取消、清理代数、文件路径边界和安装包清理。
- [资源下载](resource-download-implementation.md)：按需搜索、任务恢复、完整性清单及原子下载。
- [WebView 与登录](android-webview-auth.md)：Cookie 票据、导航规则、宿主生命周期、弹窗和文件选择。
- [桌面端指南](desktop-implementation-guide.md)：Windows 窗口、媒体处理及分发。
- [用户系统后端](user-system-backend.md)：账号、留言/通知、管理 API 和 D1 迁移。
- [Android 匿名使用统计](app-usage.md)：历史总数、近 30 天趋势、留存、45 天明细归档与 D1 容量。

## Wiki 功能专题

### 指南与玩法

- [新人入坑指南](beginner-guide.md)：玩法入门、版本与下载、装备及设置建议、常见问题。
- [PC 游戏设置](pc-game-settings.md)：设置、推荐和参考表的语义边界。
- [弦化、角色定位与增幅网络](native-mechanics-pages.md)：三页原生阅读和完整导航分流。
- [弦溯战线](extraction-mode-implementation.md)：专题入口、单件抽中率与候选容器、弦溯装备、工坊及玩法资料。
- [裂隙与生化卡池](rift-bio-pool-followup.md)：地图、双卡表、正式分享索引。

### 图鉴与资料

- [活动与往期奖励](android-wiki-ui-architecture.md#活动详情)：原生阅读、奖励目录、原文章节及旧版活动缓存。
- [角色详情](character-detail-presentation.md)：技能、设定、关系和呈现规则。
- [武器解析](weapon-parsing-audit.md)：详情数据来源、图片文件名回退、伏闪时间轴及模式增幅。
- [玩家装饰](player-decoration-catalogs.md)：Lua 双表/HTML 主源、获取方式、缺图和预览。
- [列表健壮性](wiki-list-resilience.md)：角色、武器和地图的身份与完整性；通用 HTML 解析与图片查询另见 [Wiki 开发指南](android-wiki-feature-guide.md)。

### 上游变更与验证

- [联网与本地快照测试](live-wiki-tests.md)：复现问题先找对应页面的快照；检查当日上游兼容性，按[每日 CI 读法](live-wiki-tests.md#怎样判断每日-ci-是否真的联网)核对实抓证据与失败原因。

## 历史审查与设计记录

- [导航覆盖审计](wiki-navigation-coverage.md)：当时的菜单矩阵和 2.2.1 增量，不是实时全站清单。
- [2.2.1 架构审查](android-architecture-fixes.md)、[2.2.2 二次复审](android-architecture-round-two.md)：当时修复了什么，以及哪些场景有回归证据。
- [UI 优化实施记录](android-ui-refactor-plan.md)：2.2.0已完成改动、2.2.1文案更新，以及明确标注的历史计划。
- [工具视觉回退](tools-mesh-tab-surfaces.md)：保留已确认的视觉选择，避免后续重新引入被否决设计。

## 发布记录

当前发布：[Android 2.3.2（69）](releases/2.3.2.md)，2026-11-28。该页保存本版变更、APK 指纹、生产部署与验证结果。查过去某次变更见[历版发布记录](releases/README.md)；客户端读取的版本、下载地址和完整 APK 归档以 [`latest.json`](../downloadPage/downloads/latest.json) 为准。

## 归档与维护口径

- **现行规则**：每条详细规则只在负责该行为的专题维护。跨模块引用时链接到该处，不复制字段清单和例外条件。根协作指南只在模块入口、通用命令或跨模块规则变化时更新。
- **验证证据**：修订号、快照数量和测试结果写入有日期的审查或发布记录；真机未验事项集中到[设备验收清单](device-acceptance.md)。
- **历史资料**：[旧会话交接](session-handoff.md)和旧计划用于追溯，不代表当前待办；[`webApp/`](../webApp/README.md) 是归档原型，生产网页在 `downloadPage/`。
