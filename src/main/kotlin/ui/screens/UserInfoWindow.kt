package ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import ui.components.StyledWindow
import viewmodel.UserInfoViewModel

@Composable
fun UserInfoWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(
        width = 900.dp,
        height = 800.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "用户信息",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        },
        useLayer = false
    ) { insetModifier ->
        val coroutineScope = rememberCoroutineScope()
        val viewModel = remember { UserInfoViewModel(coroutineScope) }

        UserInfoContent(
            viewModel = viewModel,
            modifier = insetModifier
        )
    }
}
