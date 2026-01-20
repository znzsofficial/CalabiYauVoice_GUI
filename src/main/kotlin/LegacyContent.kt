import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Button
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val characterWikiMap = mapOf(
    "米雪儿·李" to "https://wiki.biligame.com/klbq/%E7%B1%B3%E9%9B%AA%E5%84%BF%C2%B7%E6%9D%8E/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "信" to "https://wiki.biligame.com/klbq/%E4%BF%A1/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "心夏" to "https://wiki.biligame.com/klbq/%E5%BF%83%E5%A4%8F/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "伊薇特" to "https://wiki.biligame.com/klbq/%E4%BC%8A%E8%96%87%E7%89%B9/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "芙拉薇娅" to "https://wiki.biligame.com/klbq/%E8%8A%99%E6%8B%89%E8%96%87%E5%A8%85/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "忧雾" to "https://wiki.biligame.com/klbq/%E5%BF%A7%E9%9B%BE/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "蕾欧娜" to "https://wiki.biligame.com/klbq/%E8%95%BE%E6%AC%A7%E5%A8%9C/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "千代" to "https://wiki.biligame.com/klbq/%E5%8D%83%E4%BB%A3/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "明" to "https://wiki.biligame.com/klbq/%E6%98%8E/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "拉薇" to "https://wiki.biligame.com/klbq/%E6%8B%89%E8%96%87/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "梅瑞狄斯" to "https://wiki.biligame.com/klbq/%E6%A2%85%E7%91%9E%E7%8B%84%E6%96%AF/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "香奈美" to "https://wiki.biligame.com/klbq/%E9%A6%99%E5%A5%88%E7%BE%8E/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "令" to "https://wiki.biligame.com/klbq/%E4%BB%A4/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "艾卡" to "https://wiki.biligame.com/klbq/%E8%89%BE%E5%8D%A1/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "珐格兰丝" to "https://wiki.biligame.com/klbq/%E7%8F%90%E6%A0%BC%E5%85%B0%E4%B8%9D/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "玛拉" to "https://wiki.biligame.com/klbq/%E7%8E%9B%E6%8B%89/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "星绘" to "https://wiki.biligame.com/klbq/%E6%98%9F%E7%BB%98/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "奥黛丽" to "https://wiki.biligame.com/klbq/%E5%A5%A5%E9%BB%9B%E4%B8%BD%C2%B7%E6%A0%BC%E7%BD%97%E5%A4%AB/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "白墨" to "https://wiki.biligame.com/klbq/%E7%99%BD%E5%A2%A8/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "玛德蕾娜" to "https://wiki.biligame.com/klbq/%E7%8E%9B%E5%BE%B7%E8%95%BE%E5%A8%9C%C2%B7%E5%88%A9%E9%87%8C/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "绯莎" to "https://wiki.biligame.com/klbq/%E7%BB%AF%E8%8E%8E/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "加拉蒂亚" to "https://wiki.biligame.com/klbq/%E5%8A%A0%E6%8B%89%E8%92%82%E4%BA%9A%C2%B7%E5%88%A9%E9%87%8C/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D",
    "汐" to "https://wiki.biligame.com/klbq/%E6%B1%90/%E8%AF%AD%E9%9F%B3%E5%8F%B0%E8%AF%8D"
)

@Preview
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LegacyContent() {
    val itemsList = characterWikiMap.keys.toList()
    val languageOptions = listOf("CN", "JP", "EN")
    var selectedOption by remember { mutableStateOf(languageOptions[0]) } // 默认选中第一个

    // 1. URL 输入状态，使用 TextFieldValue
    var urlState by remember { mutableStateOf(TextFieldValue("")) }

    var maxNumState by remember { mutableStateOf(TextFieldValue("16")) }

    var selected by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            ComboBox(
                header = "预设",
                placeholder = "选择角色",
                selected = selected,
                items = itemsList,
                onSelectionChange = { i, s ->
                    selected = i
                    urlState = TextFieldValue(characterWikiMap[s] ?: "")
                }
            )
            Spacer(Modifier.width(16.dp))
            TextField(
                value = urlState, // 绑定 value
                onValueChange = { urlState = it }, // 更新状态
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                readOnly = false,
                placeholder = { Text("请输入卡拉彼丘角色Wiki页链接") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri
                ),
                // 将标签移入 header
                header = { Text("Wiki链接") },
                singleLine = true // 通常 URL 输入是单行
            )
        }
        space()

        // --- 最大并发数输入框 ---
        TextField(
            value = maxNumState, // 绑定 value
            onValueChange = { maxNumState = it }, // 更新状态
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            // 将标签移入 header
            header = {
                Text("最大并发下载数:")
            },
            singleLine = true,
        )

        space()
        Text(
            text = "要下载的语音语言",
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            //verticalAlignment = Alignment.CenterVertically, // 使所有选项在 Row 内垂直居中对齐
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languageOptions.forEach { optionText ->
                RadioButton(
                    label = optionText,
                    selected = (optionText == selectedOption),
                    onClick = { selectedOption = optionText }
                )
            }
        }
        space()
        val coroutineScope = rememberCoroutineScope() // 获取与 Composable 绑定的 CoroutineScope
        val terminalOutputLines = remember { mutableStateListOf("--- Terminal Initialized ---") }
        var clearDir by remember { mutableStateOf(false) }
        var savePath by remember { mutableStateOf("$home\\output") }
        var progress by remember { mutableStateOf(0f) }
        var currentDownloadFile by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth() // 让 Row 占据整个可用宽度
                .height(IntrinsicSize.Min), // 让 Row 的高度适应内容，并让 CenterVertically 生效
            verticalAlignment = Alignment.CenterVertically // 垂直居中对齐所有子项
        ) {
            Text("语音文件保存路径: $savePath")
            Spacer(Modifier.weight(1f))
            CheckBox(
                checked = clearDir,
                label = "清空保存目录",
                onCheckStateChange = { clearDir = it })
            Spacer(Modifier.width(20.dp))
            Button(onClick = {
                jChoose {
                    savePath = it.absolutePath
                }
            }) {
                Text("选择保存路径")
            }
        }
        space()
        Button(onClick = {
            progress = 0f
            coroutineScope.launch(Dispatchers.IO) {
                Downloader.start(
                    urlState.text,
                    savePath,
                    selectedOption,
                    clearDir,
                    maxNumState.text.toIntOrNull() ?: 10,
                    log = {
                        terminalOutputLines.add(it)
                    },
                    onUpdate = { complete, total, name ->
                        progress = (complete.toFloat() / total.toFloat())
                        currentDownloadFile = "正在下载：$name"
                        if (complete == total) currentDownloadFile = "下载完成"
                    }
                )
            }
        }) {
            Text("开始下载", Modifier.padding(4.dp))
        }
        space()
        Text(currentDownloadFile)
        space()
        ProgressBar(
            modifier = Modifier.fillMaxWidth(),
            progress = progress
        )
        TerminalOutputView(terminalOutputLines)
    }
}

@Composable
fun space() {
    Spacer(Modifier.height(8.dp))
}