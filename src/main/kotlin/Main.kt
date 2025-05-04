import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.formdev.flatlaf.FlatIntelliJLaf
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.background.BackgroundSizing
import com.konyaco.fluent.background.Layer
import com.konyaco.fluent.background.Mica
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.CheckBox
import com.konyaco.fluent.component.ComboBox
import com.konyaco.fluent.component.ProgressBar
import com.konyaco.fluent.component.RadioButton
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.component.TextField
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Button
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.Label

fun main() = application {
    FlatIntelliJLaf.setup()
    // 捕获异常
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        java.awt.Dialog(Frame(), e.message ?: "Error").apply {
            layout = FlowLayout()
            val label = Label(e.message)
            add(label)
            val label2 = Label(e.stackTraceToString())
            add(label2)
            val button = Button("OK").apply {
                addActionListener { dispose() }
            }
            add(button)
            setSize(300, 300)
            isVisible = true
        }
    }
    var darkMode by mutableStateOf(isSystemInDarkTheme())

    Window(
        onCloseRequest = { exitApplication() },
        title = "卡拉彼丘Wiki语音下载器",
        content = {
            MenuBar {
                Menu(text = "关于") {
                    AboutItem()
                }
            }

            FluentTheme(colors = if (darkMode) darkColors() else lightColors()) {
                Mica(Modifier.fillMaxSize()) {
                    Layer(
                        shape = RoundedCornerShape(size = 4.dp),
                        color = FluentTheme.colors.background.layer.default,
                        contentColor = FluentTheme.colors.text.text.primary,
                        border = BorderStroke(1.dp, FluentTheme.colors.stroke.card.default),
                        backgroundSizing = BackgroundSizing.OuterBorderEdge
                    ) {
                        MyAppContent()
                    }
                }
            }
        }
    )
}

val characterWikiMap = mapOf(
    "米雪儿·李" to "https://wiki.biligame.com/klbq/%E7%B1%B3%E9%9B%AA%E5%84%BF%C2%B7%E6%9D%8E",
    "信" to "https://wiki.biligame.com/klbq/%E4%BF%A1",
    "心夏" to "https://wiki.biligame.com/klbq/%E5%BF%83%E5%A4%8F",
    "伊薇特" to "https://wiki.biligame.com/klbq/%E4%BC%8A%E8%96%87%E7%89%B9",
    "芙拉薇娅" to "https://wiki.biligame.com/klbq/%E8%8A%99%E6%8B%89%E8%96%87%E5%A8%85",
    "忧雾" to "https://wiki.biligame.com/klbq/%E5%BF%A7%E9%9B%BE",
    "蕾欧娜" to "https://wiki.biligame.com/klbq/%E8%95%BE%E6%AC%A7%E5%A8%9C",
    "明" to "https://wiki.biligame.com/klbq/%E6%98%8E",
    "拉薇" to "https://wiki.biligame.com/klbq/%E6%8B%89%E8%96%87",
    "梅瑞狄斯" to "https://wiki.biligame.com/klbq/%E6%A2%85%E7%91%9E%E7%8B%84%E6%96%AF",
    "香奈美" to "https://wiki.biligame.com/klbq/%E9%A6%99%E5%A5%88%E7%BE%8E",
    "令" to "https://wiki.biligame.com/klbq/%E4%BB%A4",
    "艾卡" to "https://wiki.biligame.com/klbq/%E8%89%BE%E5%8D%A1",
    "珐格兰丝" to "https://wiki.biligame.com/klbq/%E7%8F%90%E6%A0%BC%E5%85%B0%E4%B8%9D",
    "玛拉" to "https://wiki.biligame.com/klbq/%E7%8E%9B%E6%8B%89",
    "星绘" to "https://wiki.biligame.com/klbq/%E6%98%9F%E7%BB%98",
    "奥黛丽" to "https://wiki.biligame.com/klbq/%E5%A5%A5%E9%BB%9B%E4%B8%BD%C2%B7%E6%A0%BC%E7%BD%97%E5%A4%AB",
    "白墨" to "https://wiki.biligame.com/klbq/%E7%99%BD%E5%A2%A8",
    "玛德蕾娜" to "https://wiki.biligame.com/klbq/%E7%8E%9B%E5%BE%B7%E8%95%BE%E5%A8%9C%C2%B7%E5%88%A9%E9%87%8C",
    "绯莎" to "https://wiki.biligame.com/klbq/%E7%BB%AF%E8%8E%8E",
    "加拉蒂亚" to "https://wiki.biligame.com/klbq/%E5%8A%A0%E6%8B%89%E8%92%82%E4%BA%9A%C2%B7%E5%88%A9%E9%87%8C",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyAppContent() {
    val itemsList = characterWikiMap.keys.toList()
    val languageOptions = listOf("CN", "JP", "EN")
    var selectedOption by remember { mutableStateOf(languageOptions[0]) } // 默认选中第一个

    // 1. URL 输入状态，使用 TextFieldValue
    var urlState by remember { mutableStateOf(TextFieldValue("")) }

    var maxNumState by remember { mutableStateOf(TextFieldValue("10")) }

    var selected by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row() {
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
                .fillMaxWidth(), // 可选：让 Row 占据可用宽度
            //verticalAlignment = Alignment.CenterVertically, // 使所有选项在 Row 内垂直居中对齐
            horizontalArrangement = Arrangement.spacedBy(8.dp) // 在每个选项之间添加 16dp 的水平间距
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
        var savePath by remember { mutableStateOf("") }
        var progress by remember { mutableStateOf(0f) }
        var currentDownloadFile by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth() // 让 Row 占据整个可用宽度
                .height(IntrinsicSize.Min), // 让 Row 的高度适应内容，并让 CenterVertically 生效
            verticalAlignment = Alignment.CenterVertically // 关键：垂直居中对齐所有子项
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
fun TerminalOutputView(
    outputLines: List<String>, // 接收输出行列表 (SnapshotStateList 也是 List)
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState() // 创建 LazyListState

    // 监听列表大小变化，自动滚动到底部
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            // 滚动到最新添加的项（列表的最后一项）
            listState.animateScrollToItem(index = outputLines.size - 1)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF2B2B2B)) // 设置类似终端的深色背景
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize() // 填满 Box
        ) {
            itemsIndexed(outputLines) { index, line ->
                Text(
                    text = line,
                    style = TextStyle(
                        color = Color(0xFFA9B7C6), // 设置类似终端的文本颜色
                        fontFamily = FontFamily.Monospace, // 使用等宽字体
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.fillMaxWidth() // 每行文本填满宽度
                )
            }
        }
    }
}


@Composable
fun space() {
    Spacer(Modifier.height(8.dp))
}

@Composable
fun MenuScope.AboutItem() {
    var darkMode by mutableStateOf(isSystemInDarkTheme())
    var showDialog by remember { mutableStateOf(false) } // 控制对话框显示/隐藏的状态
    Item(
        text = "About",
        onClick = {
            showDialog = true
        },
    )
    if (showDialog) {
        DialogWindow(
            { showDialog = false },
            rememberDialogState(position = WindowPosition(Alignment.Center)),
            true,
            "关于",
            null,
            undecorated = false,
            transparent = false,
            resizable = false,
            enabled = true,
            focusable = true,
            onPreviewKeyEvent = { false },
            onKeyEvent = { false },
            content = {
                FluentTheme(colors = if (darkMode) darkColors() else lightColors()) {
                    Mica(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).width(IntrinsicSize.Max), // 设置内边距和宽度
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("这是一个使用 Compose Desktop 和 Fluent-UI 组件构建的软件。")
                            TextWithLinks("GitHub：https://github.com/znzsofficial/CalabiyauWikiVoice")
                        }
                    }
                }
            })
    }
}