# Hub 快速导航转场修复

2026-11-06，纳入 [2.1.27（57）](releases/2.1.27.md)，以发布记录为最终交付状态。

## 用户反馈

快速进入或返回时，转场偶发抽动；返回动画接近结束时又闪现上一页面。

## 代码中确认的时序问题

- `PredictiveBackSeek.kt` 原有两个独立动画发起方：路由 `LaunchedEffect` 与手势取消分支。取消分支直接 `animateTo(currentState)`，该值来自手势开始时；导航已改变后，仍可能尝试恢复旧路由。
- `rememberPredictiveBackTransition` 的 effect 只以当前路由为 key，resetKey 创建新 transition 对象但路由相同时，effect 仍可能持有旧对象。
- Hub 用一个全局可变 `isNavigatingBack` 参与 AnimatedContent transitionSpec。快速 push/pop 会改变尚在运行的转场方向条件。
- AnimatedContent 退出页面在动画期间仍处于 composition；其旧回调原来可以直接修改当前导航栈。

这些是代码路径问题，尚未通过设备录屏证明每一项与用户闪回现象的一一对应。

## 修复

- 共用 `PredictiveBackTransition` 协调器把路由动画交给一个、以协调器实例/当前路由/手势所有权为 key 的 effect。手势期间仅 seek，结束或取消只释放所有权，由 effect 对齐最新已提交路由；取消分支不再启动旧路由动画。
- 每次手势有独立 ticket。路由改变、更新手势、handler 禁用或组件销毁时，旧 ticket 不能继续 seek、commit 或撤销新手势。
- 明确的目标解析器返回 null 表示已无可返回目标，不再回退到捕获的旧目标；工具页的合法 null 首页目标仍受支持。
- 进入动画未完成时收到返回手势，不把动画进度强行改成手势零点；手势结束仍可提交正常返回，取消恢复最新已提交页面。
- Hub 转场状态包含不可变的页面与栈深度，使用这一段的起止深度决定方向；详情共享元素过渡与已有时长保留。
- 退出页面的 push/pop/地图导航回调先验证自己仍位于当前栈顶，避免落后的点击使刚返回的页面重新入栈。
- 发布复审进一步在手势提交时核对当前栈顶，覆盖标题栏返回已发生、composition 尚未更新的同帧窗口；handler 再次检查 enabled，onStart 同样受 finally 保护。
- 设置/工具页继续使用共用协调器，更新状态引用，无额外视觉修改。

## 回归

- `BackGestureOwnerTest`：外部导航、同页新手势、路由往返、取消、null 目标、禁用/销毁后的迟到事件。
- `PredictiveBackTransitionTest`：真实 Compose Recomposer、SeekableTransitionState、animateFloat 与手动帧时钟；覆盖未完成进入时返回、返回稳定后继续推进帧不反弹、取消预览恢复、导航抢占手势后迟到清理不能回旧页。
- `HubTransitionTargetTest`：进出方向绑定起止栈位置，后续导航不能改变已经建立的段。

上述不依赖 Android 设备或真实 Wiki 请求，不代表已验证真实 SharedTransitionLayout 的每一帧。仍需设备快速点击返回、系统手势取消/提交、角色/武器/地图共享元素及设置/工具子页返回验收。

最终验证：Android 431 项，11 项可选快照测试跳过，0 failures/errors；其中新增导航回归 8 项均实际执行。`compileDebugKotlin`、`lintDebug` 通过，Lint 0 errors / 5 个既有 warnings，`git diff --check` 通过。工具目录最终仅保留协调器状态引用的一行兼容修改，四个工具子页与原始版本无内容差异。
