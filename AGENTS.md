# 项目协作指南

从[文档索引](docs/README.md)找到本次任务的专题。根文件只放跨模块约定、常用命令和生产操作边界；页面字段、迁移细节和测试样本由对应专题维护。

## 改哪里，先读什么

- `androidApp/` 是 Android 客户端，`desktopApp/` 是 Windows 客户端，`shared/` 是两端共用的 Kotlin 业务与媒体代码。模块归属见 [Android 架构](docs/android-architecture.md)和[桌面实现](docs/desktop-implementation-guide.md)。
- 改 Android Wiki 页面：先在[功能专题](docs/README.md#wiki-功能专题)找对应页面。新增解析或缓存看[分层约定](docs/android-wiki-feature-guide.md)；改弹层、加载状态或文案看[公共 UI 与状态](docs/android-wiki-ui-architecture.md)；核对上游变更优先用[已有快照](docs/live-wiki-tests.md)。
- 改登录、网络、文件或下载：对应入口在[架构与基础设施](docs/README.md#架构与基础设施)；下载的跨端契约见[资源下载](docs/resource-download-implementation.md)。
- `downloadPage/` 是独立的 Vite/Svelte 站点和 Pages Worker，不是 Gradle 子模块。账号、留言与 D1 迁移见[用户系统后端](docs/user-system-backend.md)；匿名安装统计见[使用统计](docs/app-usage.md)。[`webApp/`](webApp/README.md) 是归档的演示原型，默认不参与构建，不承载正式网页功能。

## 本地命令与验证

Gradle 命令在仓库根目录运行；下表使用 Windows 写法，macOS/Linux 换用 `./gradlew`。`pnpm` 命令在 `downloadPage/` 运行。

| 任务 | 命令 |
| --- | --- |
| 编译 Android Debug | `.\gradlew.bat :androidApp:compileDebugKotlin` |
| 启动 Windows 客户端 | `.\gradlew.bat run` |
| 启动网页开发服务 / 类型检查 | `pnpm dev` / `pnpm check` |
| 本地 Worker 集成测试 | `pnpm test:worker` |

优先用实际输入做一次能确认问题的定向验证，复用未受影响的通过结果。只有失败未解释、改动范围扩大或用户要求时再扩测。待真机确认的视觉、手势和系统行为记入[设备验收清单](docs/device-acceptance.md)；纯文档修改只检查内容、链接和 diff。

## 生产操作与仓库边界

- 发布遵循[版本发布清单](docs/release-version-checklist.md)。`webDist` 会构建签名 APK、改写 `latest.json` 并上传 R2；`webPush` 会构建并部署站点与 Worker；`webDeploy` 串起两者。它们不是本地验证命令。
- 不提交 APK、`local.properties`、签名密钥、生产凭据或 `downloadPage/wrangler.toml`。不手改 `androidApp/build/`、`downloadPage/dist/` 等生成物。
- `.agents/` 是忽略的本机目录，不作为项目文档来源。`.github/` 下跟踪的是 `.github/workflows/`，包含每日 Wiki smoke CI。
- 新规则写进负责该行为的专题。上线产物、部署与检查结果写进[发布记录](docs/releases/README.md)；带日期的旧计划、审计和交接不是当前待办。只有跨模块规则、命令或文档入口变动时才更新本文件。
