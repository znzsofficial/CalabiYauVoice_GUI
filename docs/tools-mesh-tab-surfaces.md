# 工具页视觉回退与 Tab 背景巡检

2026-11-06，纳入 [2.1.27（57）](releases/2.1.27.md)，以发布记录为最终交付状态。下文明确区分已撤销设计与最终保留修改。

## 工具页

**按用户最终要求，工具页视觉改动已全部回退。** 素材工具首页及图片、文本、音频、视频子页恢复原有标题栏、内容顺序、间距与滚动布局；无 Mesh、无新增顶部横幅。`ToolPageIntro` 和标题栏遮罩均已删除。

工具页仅随共用预测返回协调器修复更新 `rememberTransition` 的状态引用；这是转场竞态修复，不是视觉改版。文件选择、处理与文本页 IME 避让保持原逻辑。

## Android Tab 巡检

- 生化卡牌：首轮仅按默认滚动 token 推断问题不准确，用户实际看到的是全程深色。代码确认该页的不透明 Scaffold 及标题区挡住 Hub 壁纸；现改为透明 Scaffold，标题与 Tab 合并到一个 86% surface 底板，二者自身透明，只叠加一次背景色；移除不再需要的 pinned 滚动变色逻辑。Tab 在加载／错误时依然可用。
- 角色列表、武器列表：标题栏显式使用与 Tab 外层 Surface 相同的背景；实际 Hub 壁纸背景下统一为 `surface` 的 86% 不透明度。骨架 Tab 原已使用同一颜色；代码里无壁纸 URL 的回退不是用户可切换的“非壁纸模式”。
- 誓约：标题栏与 Tab 显式统一为 `surface`，避免默认 token 变化带来色差。
- 用户信息弹层：Tab 与 ModalBottomSheet 同为 `surfaceContainerLow`，已一致。
- 下载立绘选择：SecondaryScrollableTabRow 为透明背景，继承所在容器，无独立色带。

检查包含所有 Android `PrimaryTabRow` / `SecondaryScrollableTabRow` 调用点；不把筛选 chip 的选中态颜色当作 Tab 背景问题。

已移除仅针对废弃标题栏遮罩的 `ToolsHeaderContrastTest`。当前无连接设备，Tab 壁纸下真实合成仍待设备确认。

首版验证：Android 编译与 `lintDebug` 通过（0 errors / 5 个既有 warnings）；当时工具标题对比度及既有 Mesh 顶点、动画策略专项共 6 项全部通过，无跳过。该数值检查不代表真机视觉效果，当前修订验证另记。

反馈修订后：再次运行 `compileDebugKotlin`、`lintDebug` 通过，0 errors / 5 个既有 warnings；`git diff --check` 通过。没有将先前的截图缺失或真机体验判定为自动验证成功。

最终发布已完成，包含工具视觉回退和 Tab 背景修正，最终检查以 [2.1.27 发布记录](releases/2.1.27.md) 为准；以上已删除设计的测试仅为历史过程。
