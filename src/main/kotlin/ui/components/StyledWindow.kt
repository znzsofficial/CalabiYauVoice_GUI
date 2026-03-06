package ui.components

import LocalAppStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.mayakapps.compose.windowstyler.WindowStyle
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import util.findSkiaLayer
import util.getNonWin11BackgroundGradient

/**
 * 统一的子窗口容器，封装了 Backdrop + FluentTheme + WindowsWindowFrame 的重复模板。
 * Backdrop 行为与主窗口保持一致：读取 [LocalAppStore] 中的 backdropType，
 * 无 backdrop 时回退到渐变背景。
 *
 * @param title             窗口标题
 * @param onCloseRequest    关闭回调
 * @param state             窗口状态（大小/位置）
 * @param resizable         是否可调整大小，默认 true
 * @param captionBarHeight  标题栏高度，默认 36.dp
 * @param onKeyEvent        键盘事件回调，默认无操作
 * @param useLayer          是否在 WindowsWindowFrame 内部包裹一个透明 Layer（用于设置 contentColor），默认 true
 * @param content           内容，接收一个已组合好的 inset Modifier
 */
@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
@Composable
fun StyledWindow(
    title: String,
    onCloseRequest: () -> Unit,
    state: WindowState,
    resizable: Boolean = true,
    captionBarHeight: Dp = 36.dp,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    useLayer: Boolean = true,
    content: @Composable (insetModifier: Modifier) -> Unit
) {
    val appStore = LocalAppStore.current
    val darkModeState = appStore.darkMode
    val backdropTypeState = appStore.backdropType

    Window(
        onCloseRequest = onCloseRequest,
        title = title,
        state = state,
        resizable = resizable,
        onKeyEvent = onKeyEvent
    ) {
        val darkMode = darkModeState.value
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }

        // Win11 始终需要透明图层；非Win11有Skia时也可能应用backdrop，统一设置一次
        if (skiaLayerExists) {
            LaunchedEffect(Unit) {
                window.findSkiaLayer()?.transparency = true
            }
        }

        val backgroundBrush = remember(darkMode) {
            getNonWin11BackgroundGradient(darkMode)
        }

        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            val currentBackdrop = backdropTypeState.value
            // 只要选了非 null backdrop 且 skia 可用就应用 WindowStyle
            if (skiaLayerExists && currentBackdrop != null) {
                WindowStyle(isDarkTheme = darkMode, backdropType = currentBackdrop)
            }
            val useBackdropEffect = skiaLayerExists && currentBackdrop != null

            WindowsWindowFrame(
                title = title,
                onCloseRequest = onCloseRequest,
                state = state,
                frameState = windowFrameState,
                isDarkTheme = darkMode,
                captionBarHeight = captionBarHeight,
                captionBarBackground = if (useBackdropEffect) null else backgroundBrush,
            ) { windowInset, _ ->
                val insetModifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowFrameState.paddingInset)
                    .windowInsetsPadding(windowInset)

                val bgModifier = if (useBackdropEffect) Modifier else Modifier.background(backgroundBrush)

                if (useLayer) {
                    Layer(
                        modifier = insetModifier.then(bgModifier),
                        color = Color.Transparent,
                        contentColor = FluentTheme.colors.text.text.primary,
                        border = null,
                    ) {
                        content(Modifier)
                    }
                } else {
                    Box(modifier = insetModifier.then(bgModifier)) {
                        content(Modifier)
                    }
                }
            }
        }
    }
}
