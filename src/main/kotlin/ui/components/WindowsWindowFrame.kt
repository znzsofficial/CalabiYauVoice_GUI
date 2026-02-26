package ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.zIndex
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.Text
import jna.windows.ComposeWindowProcedure
import jna.windows.rememberLayoutHitTestOwner
import jna.windows.structure.WinUserConst.HTCAPTION
import jna.windows.structure.WinUserConst.HTCLIENT
import jna.windows.structure.WinUserConst.HTCLOSE
import jna.windows.structure.WinUserConst.HTMAXBUTTON
import jna.windows.structure.WinUserConst.HTMINBUTTON

@OptIn(ExperimentalLayoutApi::class)
@Stable
class WindowsWindowFrameState internal constructor(
    val maxButtonRect: MutableState<Rect>,
    val minButtonRect: MutableState<Rect>,
    val closeButtonRect: MutableState<Rect>,
    val captionBarRect: MutableState<Rect>,
    val paddingInset: MutableWindowInsets,
    val windowProcedure: ComposeWindowProcedure,
) {
    fun updateMaxButtonRect(rect: Rect) {
        maxButtonRect.value = rect
    }

    fun updateMinButtonRect(rect: Rect) {
        minButtonRect.value = rect
    }

    fun updateCloseButtonRect(rect: Rect) {
        closeButtonRect.value = rect
    }

    fun updateCaptionBarRect(rect: Rect) {
        captionBarRect.value = rect
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberWindowsWindowFrameState(
    window: java.awt.Window,
): WindowsWindowFrameState {
    val maxButtonRect = remember { mutableStateOf(Rect.Zero) }
    val minButtonRect = remember { mutableStateOf(Rect.Zero) }
    val closeButtonRect = remember { mutableStateOf(Rect.Zero) }
    val captionBarRect = remember { mutableStateOf(Rect.Zero) }
    val paddingInset = remember { MutableWindowInsets() }
    val layoutHitTestOwner = rememberLayoutHitTestOwner()

    val hitTest = remember(layoutHitTestOwner) {
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

    val windowProcedure = remember(window, hitTest) {
        ComposeWindowProcedure(
            window = window,
            hitTest = hitTest,
            onWindowInsetUpdate = { paddingInset.insets = it }
        )
    }

    return remember(windowProcedure) {
        WindowsWindowFrameState(
            maxButtonRect = maxButtonRect,
            minButtonRect = minButtonRect,
            closeButtonRect = closeButtonRect,
            captionBarRect = captionBarRect,
            paddingInset = paddingInset,
            windowProcedure = windowProcedure
        )
    }
}

@Composable
@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
fun FrameWindowScope.WindowsWindowFrame(
    title: String,
    onCloseRequest: () -> Unit,
    state: androidx.compose.ui.window.WindowState,
    frameState: WindowsWindowFrameState,
    isDarkTheme: Boolean,
    captionBarHeight: Dp = 48.dp,
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    val contentPaddingInset = remember { MutableWindowInsets() }

    Box(
        modifier = Modifier.windowInsetsPadding(frameState.paddingInset)
    ) {
        content(
            WindowInsets(top = captionBarHeight),
            contentPaddingInset
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(captionBarHeight)
                .zIndex(10f)
                .onGloballyPositioned { frameState.updateCaptionBarRect(it.boundsInWindow()) }
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = if (isDarkTheme) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))

            CaptionButtonRow(
                windowHandle = frameState.windowProcedure.windowHandle,
                isMaximize = state.placement == WindowPlacement.Maximized,
                onCloseRequest = onCloseRequest,
                onMaximizeButtonRectUpdate = frameState::updateMaxButtonRect,
                onMinimizeButtonRectUpdate = frameState::updateMinButtonRect,
                onCloseButtonRectUpdate = frameState::updateCloseButtonRect,
                accentColor = frameState.windowProcedure.windowFrameColor,
                frameColorEnabled = frameState.windowProcedure.isWindowFrameAccentColorEnabled,
                isActive = frameState.windowProcedure.isWindowActive,
                modifier = Modifier.align(Alignment.Top)
                    .onSizeChanged {
                        contentPaddingInset.insets = WindowInsets(
                            right = it.width,
                            top = it.height
                        )
                    }
            )
        }
    }
}

