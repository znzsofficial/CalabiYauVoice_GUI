package ui.components

import LocalAppStore
import LocalNucleusAppScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.window.fluent.FluentDecoratedWindow
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors

@OptIn(ExperimentalFluentApi::class)
@Composable
fun StyledWindow(
    title: String,
    onCloseRequest: () -> Unit,
    state: WindowState,
    resizable: Boolean = true,
    @Suppress("UNUSED_PARAMETER") captionBarHeight: Dp = 36.dp,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    useLayer: Boolean = true,
    content: @Composable (insetModifier: Modifier) -> Unit
) {
    val appStore = LocalAppStore.current
    val darkMode = appStore.darkMode.value
    val icon = painterResource("icon.png")
    with(LocalNucleusAppScope.current) {
        FluentDecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            icon = icon,
            resizable = resizable,
            onKeyEvent = onKeyEvent,
            minimumSize = if (resizable) DpSize(320.dp, 240.dp) else null,
        ) {
            FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
                AppWindowChrome(title = title) {
                    if (useLayer) {
                        Layer(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            contentColor = FluentTheme.colors.text.text.primary,
                            border = null,
                        ) {
                            content(Modifier)
                        }
                    } else {
                        content(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
