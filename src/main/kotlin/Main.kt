import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalFluentApi::class)
fun main() = application {
    setupGlobalExceptionHandler()
    val darkMode = mutableStateOf(isSystemInDarkTheme())

    Window(
        onCloseRequest = ::exitApplication,
        title = "卡拉彼丘 WiKi 语音下载器",
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1000.dp, height = 750.dp)
    ) {
        val skiaLayerExists = remember { window.findSkiaLayer() != null }

        // 只有当 Skia Layer 存在并且是 Win11 系统时，才执行透明化和设置 WindowStyle 的逻辑
        if (skiaLayerExists && isWindows11OrLater()) {
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
                if (skiaLayerExists && isWindows11OrLater()) {
                    // 对于支持透明的 Skia Layer，可以直接渲染内容
                    // Mica/Tabbed 效果会由 WindowStyle 负责
                    NewDownloaderContent()
                } else {
                    // 对于不支持透明的 fallback 路径，使用渐变背景
                    Box(modifier = Modifier.background(getNonWin11BackgroundGradient(darkMode.value))) {
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