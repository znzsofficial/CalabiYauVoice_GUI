# 项目文档索引

按问题查阅专题文档；可复用约束集中在根目录 [`AGENTS.md`](../AGENTS.md)，不要把临时调查日志当作长期规范。

## 开发与实现

- [Android Wiki 功能约定](android-wiki-feature-guide.md)：model/source/parser/api/Screen 分层与缓存策略。
- [资源下载实现](resource-download-implementation.md)：搜索和分类、弹窗请求隔离、原任务重试、取消与恢复、文件完整性。
- [Android 基础设施与文件管理](android-core-file-manager.md)：缓存请求版本、载荷校验、网络监听、写请求重试限制和文件操作边界。
- [Android WebView 与登录链路](android-webview-auth.md)：Cookie 票据、统一导航规则、文件选择结果归属和临时窗口回收。
- [桌面端实现指南](desktop-implementation-guide.md)：Windows 窗口、业务状态、媒体管线和桌面分发；下载公共语义以资源下载专题为准。
- [用户系统后端](user-system-backend.md)：身份、档案、头像、留言/回复、通知、管理接口、D1 迁移与部署顺序。
- [Wiki 联网测试](live-wiki-tests.md)：真实页面巡检、独立 PC/移动端图鉴、快照和 EdgeOne 请求间隔。

## 发布与维护

- [2.1.23 发布记录](releases/2.1.23.md)：生化卡组同名卡图片、平衡数据筛选弹层、关于页网格渐变与发布验证。
- [2.1.22 发布记录](releases/2.1.22.md)：私有下载记录、更新安装包清理、依赖升级与发布验证。
- [2.1.21 发布记录](releases/2.1.21.md)：历史赛季与上一版发布验证。

- [版本发布清单](release-version-checklist.md)：版本字段、更新日志、APK/R2/Pages 发布流程。
- [Compose Web 归档说明](../webApp/README.md)：不维护的演示原型；默认不参与构建，正式网页功能在 `downloadPage/`。
- [会话交接快照](session-handoff.md)：**2026-10-20 的一次性状态**，供压缩续接；不是实时任务看板，后续以 Git 与重新执行的测试为准。
