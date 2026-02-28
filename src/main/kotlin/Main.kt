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
    val windowState = rememberWindowState(width = 1000.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "卡拉彼丘 WiKi 语音下载器",
        icon = painterResource("icon.png"),
        state = windowState
    ) {
        val windowFrameState = rememberWindowsWindowFrameState(window)
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

        CompositionLocalProvider(LocalThemeState provides darkMode) {
            FluentTheme(colors = if (darkMode.value) darkColors() else lightColors()) {
                WindowsWindowFrame(
                    title = "卡拉彼丘 WiKi 语音下载器",
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    frameState = windowFrameState,
                    isDarkTheme = darkMode.value,
                    captionBarHeight = 48.dp,
                ) { windowInset, _ ->
                    val contentModifier = Modifier
                        .windowInsetsPadding(windowFrameState.paddingInset)
                        .windowInsetsPadding(windowInset)
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
