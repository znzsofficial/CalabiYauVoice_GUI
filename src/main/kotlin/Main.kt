import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.zIndex
import com.mayakapps.compose.windowstyler.WindowBackdrop
import com.mayakapps.compose.windowstyler.WindowStyle
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import jna.windows.ComposeWindowProcedure
import jna.windows.rememberLayoutHitTestOwner
import jna.windows.structure.WinUserConst.HTCAPTION
import jna.windows.structure.WinUserConst.HTCLIENT
import jna.windows.structure.WinUserConst.HTCLOSE
import jna.windows.structure.WinUserConst.HTMAXBUTTON
import jna.windows.structure.WinUserConst.HTMINBUTTON
import jna.windows.structure.isWindows11OrLater
import ui.components.CaptionButtonRow
import ui.components.contains
import ui.screens.NewDownloaderContent
import util.findSkiaLayer
import java.awt.Button
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.Label

val LocalThemeState = compositionLocalOf { mutableStateOf(false) }

// 创建非Win11系统的背景渐变
private fun getNonWin11BackgroundGradient(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            colors = listOf(Color(0xff1A212C), Color(0xff2C343C)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xffCCD7E8), Color(0xffDAE9F7)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
}

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
fun main() = application {
    setupGlobalExceptionHandler()
    val darkMode = mutableStateOf(isSystemInDarkTheme())
    val windowState = rememberWindowState(width = 1000.dp, height = 750.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "卡拉彼丘 WiKi 语音下载器",
        icon = painterResource("icon.png"),
        state = windowState
    ) {
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }

        // Win11 + SkiaLayer 透明化逻辑
        if (skiaLayerExists && isWin11) {
            LaunchedEffect(Unit) {
                window.findSkiaLayer()?.transparency = true
            }
            WindowStyle(
                isDarkTheme = darkMode.value,
                backdropType = WindowBackdrop.Tabbed
            )
        }

        // 初始化 WindowsWindowFrame 所需状态
        val maxButtonRect = remember { mutableStateOf(Rect.Zero) }
        val minButtonRect = remember { mutableStateOf(Rect.Zero) }
        val closeButtonRect = remember { mutableStateOf(Rect.Zero) }
        val captionBarRect = remember { mutableStateOf(Rect.Zero) }
        val paddingInset = remember { MutableWindowInsets() }
        val layoutHitTestOwner = rememberLayoutHitTestOwner()

        // 构建 Window 命中测试逻辑
        val hitTest = remember(darkMode) {
            { x: Float, y: Float ->
                when {
                    maxButtonRect.value.contains(x, y) -> HTMAXBUTTON
                    minButtonRect.value.contains(x, y) -> HTMINBUTTON
                    closeButtonRect.value.contains(x, y) -> HTCLOSE
                    captionBarRect.value.contains(x, y) && !layoutHitTestOwner.hitTest(x, y) -> HTCAPTION
                    else -> HTCLIENT
                }
            }
        }

        // 构建 ComposeWindowProcedure（窗口过程回调）
        val windowProcedure = remember(window) {
            ComposeWindowProcedure(
                window = window,
                hitTest = hitTest,
                onWindowInsetUpdate = { paddingInset.insets = it } // 接收系统边框inset
            )
        }

        CompositionLocalProvider(LocalThemeState provides darkMode) {
            FluentTheme(colors = if (darkMode.value) darkColors() else lightColors()) {
                WindowsWindowFrame(
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    captionBarHeight = 48.dp,
                    onMaxButtonRectUpdate = { maxButtonRect.value = it },
                    onMinButtonRectUpdate = { minButtonRect.value = it },
                    onCloseButtonRectUpdate = { closeButtonRect.value = it },
                    onCaptionBarRectUpdate = { captionBarRect.value = it },
                    windowProcedure = windowProcedure,
                ) { windowInset, captionBarInset ->
                    val contentModifier = Modifier
                        .windowInsetsPadding(paddingInset) // 应用系统边框inset（windowInset）
                        .windowInsetsPadding(windowInset) // 应用标题栏高度inset
                    Box(
                        modifier = contentModifier.then(
                            if (skiaLayerExists && isWin11) Modifier
                            else Modifier.background(getNonWin11BackgroundGradient(darkMode.value))
                        )
                    ) {
                        // 你的业务内容：NewDownloaderContent 现在会自动避开标题栏/边框/按钮
                        NewDownloaderContent()
                    }
                }
            }
        }
    }
}

/**
 * 扩展 WindowsWindowFrame 定义（补充必要参数，需和你的 gallery 模块保持一致）
 */
@Composable
@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
fun FrameWindowScope.WindowsWindowFrame(
    onCloseRequest: () -> Unit,
    state: androidx.compose.ui.window.WindowState,
    captionBarHeight: Dp = 48.dp,
    onMaxButtonRectUpdate: (Rect) -> Unit,
    onMinButtonRectUpdate: (Rect) -> Unit,
    onCloseButtonRectUpdate: (Rect) -> Unit,
    onCaptionBarRectUpdate: (Rect) -> Unit,
    windowProcedure: ComposeWindowProcedure,
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    val paddingInset = remember { MutableWindowInsets() }
    val contentPaddingInset = remember { MutableWindowInsets() }

    Box(
        modifier = Modifier.windowInsetsPadding(paddingInset) // 先应用系统计算的边框inset
    ) {
        // 1. 传递两个关键inset给业务内容：
        // - windowInset：标题栏高度（避免内容顶到标题栏）
        // - captionBarInset：标题栏按钮区域（避免内容和按钮重叠）
        content(
            WindowInsets(top = captionBarHeight),
            contentPaddingInset
        )

        // 2. 标题栏布局（悬浮在内容上方，不占用内容区域）
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .height(captionBarHeight)
                .zIndex(10f)
                .onGloballyPositioned { onCaptionBarRectUpdate(it.boundsInWindow()) }
        ) {
            // 标题栏左侧：可添加返回按钮/图标/标题（根据你的需求）
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "卡拉彼丘 WiKi 语音下载器",
                color = if (LocalThemeState.current.value) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))

            // 标题栏右侧：最小化/最大化/关闭按钮
            CaptionButtonRow(
                windowHandle = windowProcedure.windowHandle,
                isMaximize = state.placement == WindowPlacement.Maximized,
                onCloseRequest = onCloseRequest,
                onMaximizeButtonRectUpdate = onMaxButtonRectUpdate,
                onMinimizeButtonRectUpdate = onMinButtonRectUpdate,
                onCloseButtonRectUpdate = onCloseButtonRectUpdate,
                accentColor = windowProcedure.windowFrameColor,
                frameColorEnabled = windowProcedure.isWindowFrameAccentColorEnabled,
                isActive = windowProcedure.isWindowActive,
                modifier = Modifier.align(Alignment.Top)
                    .onSizeChanged {
                        // 更新标题栏按钮区域的inset
                        contentPaddingInset.insets = WindowInsets(
                            right = it.width,
                            top = it.height
                        )
                    }
            )
        }
    }
}

/**
 * 全局异常处理器
 */
private fun setupGlobalExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        java.awt.Dialog(Frame(), e.message ?: "程序异常").apply {
            layout = FlowLayout()
            add(Label("异常信息：${e.message ?: "未知异常"}"))
            add(Label("堆栈信息：\n${e.stackTraceToString().take(500)}")) // 限制长度避免窗口过大
            add(Button("确认").apply {
                addActionListener { dispose() }
            })
            size = java.awt.Dimension(600, 400)
            isVisible = true
        }
    }
}
