package ui.screens

import LocalAppStore
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import viewmodel.UserInfoViewModel

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
@Composable
fun UserInfoWindow(onCloseRequest: () -> Unit) {
    val darkModeState = LocalAppStore.current.darkMode
    val windowState = rememberWindowState(
        width = 800.dp,
        height = 700.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = onCloseRequest,
        title = "用户信息",
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) {
        val darkMode = darkModeState.value
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }

        if (skiaLayerExists && isWin11) {
            LaunchedEffect(Unit) { window.findSkiaLayer()?.transparency = true }
            WindowStyle(isDarkTheme = darkMode, backdropType = WindowBackdrop.Tabbed)
        }

        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            WindowsWindowFrame(
                title = "用户信息",
                onCloseRequest = onCloseRequest,
                state = windowState,
                frameState = windowFrameState,
                isDarkTheme = darkMode,
                captionBarHeight = 36.dp
            ) { windowInset, _ ->
                val coroutineScope = rememberCoroutineScope()
                val viewModel = remember { UserInfoViewModel(coroutineScope) }

                UserInfoContent(
                    viewModel = viewModel,
                    modifier = Modifier
                        .windowInsetsPadding(windowFrameState.paddingInset)
                        .windowInsetsPadding(windowInset)
                )
            }
        }
    }
}

