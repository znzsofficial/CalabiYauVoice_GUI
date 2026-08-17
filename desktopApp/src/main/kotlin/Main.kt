import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.fluent.FluentDecoratedWindow
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.FluentThemeConfiguration
import io.github.composefluent.component.ContentDialogHostState
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import org.jetbrains.skiko.hostOs
import ui.components.AppWindowChrome
import ui.screens.NewDownloaderContent
import java.awt.Button
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.Label

@OptIn(ExperimentalFluentApi::class)
fun main() {
    data.PortraitRepository.init(
        fetchFilesInCategory = { cat, audio -> data.WikiEngine.fetchFilesInCategory(cat, audio) },
        searchFilesFn = { kw, audio -> data.WikiEngine.searchFiles(kw, audio) },
        getAllCharacterNames = { data.WikiEngine.getAllCharacterNames() }
    )

    nucleusApplication(
        backend = NucleusBackend.Tao,
        enableSingleInstance = false,
    ) {
        setupGlobalExceptionHandler()
        val windowState = rememberWindowState(
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            size = DpSize(1280.dp, 900.dp),
        )
        val systemDark = isSystemInDarkTheme()
        val darkMode = remember { mutableStateOf(systemDark) }
        val isWindows = hostOs.isWindows
        val backdropType = remember {
            mutableStateOf(if (isWindows) AppBackdrop.MicaAlt else AppBackdrop.None)
        }
        val appState = remember(isWindows) {
            AppState(
                darkMode = darkMode,
                backdropType = backdropType,
                isWindows = isWindows,
            )
        }
        val icon = painterResource("icon.png")
        val title = "卡拉彼丘 WiKi 资源下载器"

        CompositionLocalProvider(
            LocalAppStore provides appState,
            LocalNucleusAppScope provides this,
        ) {
            FluentThemeConfiguration(
                colors = if (darkMode.value) darkColors() else lightColors(),
                contentDialogHostState = remember { ContentDialogHostState() },
            ) {
                FluentDecoratedWindow(
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    title = title,
                    icon = icon,
                    minimumSize = DpSize(800.dp, 600.dp),
                ) {
                    FluentTheme(colors = if (darkMode.value) darkColors() else lightColors(), useAcrylicPopup = true) {
                        AppWindowChrome(title = title) {
                            NewDownloaderContent()
                        }
                    }
                }
            }
        }
    }
}

private fun setupGlobalExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        java.awt.Dialog(Frame(), e.message ?: "程序异常").apply {
            layout = FlowLayout()
            add(Label("异常信息：${e.message ?: "未知异常"}"))
            add(Label("堆栈信息：\n${e.stackTraceToString().take(500)}"))
            add(Button("确认").apply {
                addActionListener { dispose() }
            })
            size = java.awt.Dimension(600, 400)
            isVisible = true
        }
    }
}
