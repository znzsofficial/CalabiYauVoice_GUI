package ui.screens

import LocalThemeState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
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
import util.findSkiaLayer

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
@Composable
fun AboutWindow(onCloseRequest: () -> Unit) {
    // 读 State 对象，不在此处读 .value，让 Window 内部订阅
    val darkModeState = LocalThemeState.current
    val windowState = rememberWindowState(
        width = 450.dp,
        height = 320.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = onCloseRequest,
        title = "关于",
        state = windowState,
        resizable = false
    ) {
        // 在 Window Composable 作用域内读 .value，Compose 能正确追踪主题变化
        val darkMode = darkModeState.value
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }

        if (skiaLayerExists && isWin11) {
            LaunchedEffect(Unit) {
                window.findSkiaLayer()?.transparency = true
            }
            WindowStyle(
                isDarkTheme = darkMode,
                backdropType = WindowBackdrop.Tabbed
            )
        }

        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            WindowsWindowFrame(
                title = "关于",
                onCloseRequest = onCloseRequest,
                state = windowState,
                frameState = windowFrameState,
                isDarkTheme = darkMode,
                captionBarHeight = 36.dp
            ) { windowInset, _ ->
                AboutContent(
                    modifier = Modifier
                        .windowInsetsPadding(windowFrameState.paddingInset)
                        .windowInsetsPadding(windowInset)
                )
            }
        }
    }
}
