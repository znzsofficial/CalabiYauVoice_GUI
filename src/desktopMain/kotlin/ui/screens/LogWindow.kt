package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.component.*
import io.github.composefluent.surface.Card
import ui.components.StyledWindow
import ui.components.TerminalOutputView

/**
 * 独立的日志窗口，从主界面分离出来。
 *
 * @param logLines       日志行列表
 * @param isDownloading  是否正在下载
 * @param isScanningTree 是否正在扫描分类树
 * @param progress       下载进度 (0f..1f)
 * @param progressText   进度文本
 * @param onCloseRequest 关闭回调
 */
@Composable
fun LogWindow(
    logLines: List<String>,
    isDownloading: Boolean,
    isScanningTree: Boolean,
    progress: Float,
    progressText: String,
    onCloseRequest: () -> Unit
) {
    val windowState = rememberWindowState(
        width = 700.dp,
        height = 400.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "运行日志",
        onCloseRequest = onCloseRequest,
        state = windowState,
        resizable = true,
        useLayer = false,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) { insetModifier ->
        Card(modifier = insetModifier.fillMaxSize()) {
            Column {
                // 进度条
                if (isDownloading || isScanningTree) {
                    ProgressBar(progress = progress, modifier = Modifier.fillMaxWidth().height(2.dp))
                }

                // 日志内容
                TerminalOutputView(logLines, Modifier.fillMaxSize())
            }
        }
    }
}
