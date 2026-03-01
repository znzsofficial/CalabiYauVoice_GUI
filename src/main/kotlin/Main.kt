import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mayakapps.compose.windowstyler.WindowBackdrop
import com.mayakapps.compose.windowstyler.WindowStyle
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import jna.windows.structure.isWindows11OrLater
import ui.components.WindowsWindowFrame
import ui.components.rememberWindowsWindowFrameState
import ui.screens.NewDownloaderContent
import util.findSkiaLayer
import java.awt.Button
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.Label

// All app-wide UI state is stored in AppStore.kt — access via LocalAppStore

// 创建非Win11系统的背景渐变，由调用方用 remember 缓存
private fun getNonWin11BackgroundGradient(isDarkMode: Boolean): Brush =
    if (isDarkMode) {
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

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
fun main() = application {
    setupGlobalExceptionHandler()
    val windowState = rememberWindowState(width = 1200.dp, height = 850.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "卡拉彼丘 WiKi 语音下载器",
        icon = painterResource("icon.png"),
        state = windowState
    ) {
        val systemDark = isSystemInDarkTheme()
        val darkMode = remember { mutableStateOf(systemDark) }
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }
        // 非Win11但支持 skia：可以应用 Acrylic/Aero/Transparent，null 表示使用渐变背景
        val canUseNonWin11Backdrop = skiaLayerExists && !isWin11
        // Win11 默认 Tabbed；非Win11 默认 null（渐变背景）
        val backdropType = remember { mutableStateOf<WindowBackdrop?>(if (isWin11) WindowBackdrop.Tabbed else null) }

        // Win11 始终需要透明图层；非Win11有Skia时也可能应用backdrop，统一在此设置一次
        if (skiaLayerExists) {
            LaunchedEffect(Unit) {
                window.findSkiaLayer()?.transparency = true
            }
        }

        val backgroundBrush = remember(darkMode.value) {
            getNonWin11BackgroundGradient(darkMode.value)
        }

        val appState = remember {
            AppState(
                darkMode = darkMode,
                backdropType = backdropType,
                isWin11 = isWin11,
                canUseNonWin11Backdrop = canUseNonWin11Backdrop,
            )
        }
        CompositionLocalProvider(LocalAppStore provides appState) {
            FluentTheme(colors = if (darkMode.value) darkColors() else lightColors(), useAcrylicPopup = true) {
                val currentBackdrop = backdropType.value
                // skiaLayerExists 已是前提，只要选了非 null backdrop 就应用 WindowStyle
                if (skiaLayerExists && currentBackdrop != null) {
                    WindowStyle(
                        isDarkTheme = darkMode.value,
                        backdropType = currentBackdrop
                    )
                }
                // 是否使用任何 Backdrop 效果（非默认渐变）
                val useBackdropEffect = skiaLayerExists && currentBackdrop != null
                WindowsWindowFrame(
                    title = "卡拉彼丘 WiKi 语音下载器",
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    frameState = windowFrameState,
                    isDarkTheme = darkMode.value,
                    captionBarHeight = 48.dp,
                    captionBarBackground = if (useBackdropEffect) null else backgroundBrush,
                ) { windowInset, _ ->
                    val contentModifier = remember(windowFrameState.paddingInset, windowInset) {
                        Modifier
                            .windowInsetsPadding(windowFrameState.paddingInset)
                            .windowInsetsPadding(windowInset)
                    }
                    // useBackdropEffect 变化时重算；backgroundBrush 已在外层按 darkMode 缓存
                    val bgModifier = if (useBackdropEffect) Modifier else Modifier.background(backgroundBrush)
                    Box(modifier = contentModifier.then(bgModifier)) {
                        NewDownloaderContent()
                    }
                }
            }
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
