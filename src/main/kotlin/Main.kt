import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
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

@OptIn(ExperimentalFluentApi::class)
fun main() = application {
    //FlatIntelliJLaf.setup()
    // 捕获异常
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        java.awt.Dialog(Frame(), e.message ?: "Error").apply {
            layout = FlowLayout()
            add(Label(e.message))
            add(Label(e.stackTraceToString()))
            add(Button("OK").apply {
                addActionListener { dispose() }
            })
            setSize(300, 300)
            isVisible = true
        }
    }
    val darkMode = mutableStateOf(isSystemInDarkTheme())

    Window(
        onCloseRequest = ::exitApplication,
        title = "卡拉彼丘 WiKi 语音下载器",
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1000.dp, height = 750.dp) // 新版界面需要宽一点
    ) {
        LaunchedEffect(window) {
            window.findSkiaLayer()?.transparency = true
        }
        WindowStyle(
            isDarkTheme = darkMode.value,
            backdropType =  WindowBackdrop.Mica
        )
        CompositionLocalProvider(LocalThemeState provides darkMode) {
            FluentTheme(
                colors = if (darkMode.value) darkColors() else lightColors(),
                useAcrylicPopup = true,
            ) {
//                Layer(
//                    shape = RoundedCornerShape(size = 4.dp),
//                    color = FluentTheme.colors.background.layer.default,
//                    contentColor = FluentTheme.colors.text.text.primary,
//                    border = BorderStroke(1.dp, FluentTheme.colors.stroke.card.default),
//                    backgroundSizing = BackgroundSizing.OuterBorderEdge
//                ) {
                NewDownloaderContent()
//                }
            }
        }
    }
}
