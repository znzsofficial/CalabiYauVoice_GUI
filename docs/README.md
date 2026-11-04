# 项目文档索引

按问题查阅专题文档；可复用约束集中在根目录 [`AGENTS.md`](../AGENTS.md)，不要把临时调查日志当作长期规范。

## 开发与实现

- [Android Wiki 页面与复用组件](android-wiki-ui-architecture.md)：统一弹层、选择／保存／分区状态、推进懒网格及生化／投票／平衡业务编排。
- [Android 导航与页面状态](android-navigation-followup.md)：主导航状态恢复、预取与玻璃开销、筛选草稿、渐变和网页请求限速。
- [弦溯战线简介与图鉴](extraction-mode-implementation.md)：模式简介、外观与图鉴中的三个原生入口、专用骨架及完整快照回归。
- [武器页面解析与巡检](weapon-parsing-audit.md)：42 页完整快照验证、10 个战术道具、武器机制和参考参数。
- [维里昂裂隙与生化卡池补全](rift-bio-pool-followup.md)：原生搜打撤地图、双卡表补全和“盗取”分享码索引缺失边界。
- [Android Wiki 功能约定](android-wiki-feature-guide.md)：model/source/parser/api/Screen 分层与缓存策略。
- [资源下载实现](resource-download-implementation.md)：搜索和分类、弹窗请求隔离、原任务重试、取消与恢复、文件完整性。
- [Android 基础设施与文件管理](android-core-file-manager.md)：缓存请求版本、载荷校验、网络监听、写请求重试限制和文件操作边界。
- [Android WebView 与登录链路](android-webview-auth.md)：Cookie 票据、统一导航规则、文件选择结果归属和临时窗口回收。
- [桌面端实现指南](desktop-implementation-guide.md)：Windows 窗口、业务状态、媒体管线和桌面分发；下载公共语义以资源下载专题为准。
- [用户系统后端](user-system-backend.md)：身份、档案、头像、留言/回复、通知、管理接口、D1 迁移与部署顺序。
- [Wiki 联网测试](live-wiki-tests.md)：真实页面巡检、独立 PC/移动端图鉴、快照和 EdgeOne 请求间隔。

## 发布与维护

- [2.1.26 发布记录](releases/2.1.26.md)：新版共用掉落池、阶段对照、页面组件与状态重构，以及发布前复审修复。
- [2.1.25 发布记录](releases/2.1.25.md)：地图详情修复、平衡页文案、掉落池新表兼容与骨架屏对齐。
- [2.1.24 发布记录](releases/2.1.24.md)：弦溯战线、武器解析、卡池补全和导航修复的本次交付与验证。
- [2.1.23 发布记录](releases/2.1.23.md)：生化卡组同名卡图片、平衡数据筛选弹层、关于页网格渐变与发布验证。
- [2.1.22 发布记录](releases/2.1.22.md)：私有下载记录、更新安装包清理、依赖升级与发布验证。
- [2.1.21 发布记录](releases/2.1.21.md)：历史赛季与上一版发布验证。

- [版本发布清单](release-version-checklist.md)：版本字段、更新日志、APK/R2/Pages 发布流程。
- [Compose Web 归档说明](../webApp/README.md)：不维护的演示原型；默认不参与构建，正式网页功能在 `downloadPage/`。
- [会话交接快照](session-handoff.md)：**2026-10-20 的一次性状态**，供压缩续接；不是实时任务看板，后续以 Git 与重新执行的测试为准。
