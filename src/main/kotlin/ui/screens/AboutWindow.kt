package ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import ui.components.StyledWindow

@Composable
fun AboutWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(
        width = 450.dp,
        height = 320.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "关于",
        onCloseRequest = onCloseRequest,
        state = windowState,
        resizable = false,
        useLayer = false
    ) { insetModifier ->
        AboutContent(modifier = insetModifier)
    }
}
