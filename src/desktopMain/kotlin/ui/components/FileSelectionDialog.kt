package ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFluentApi::class)
@Composable
fun FileSelectionDialog(
    title: String,
    files: List<Pair<String, String>>,
    initialSelection: List<String>,
    isLoading: Boolean,
    onClose: () -> Unit,
    onConfirm: (List<Pair<String, String>>) -> Unit
) {
    val selectedUrls = remember { mutableStateListOf<String>() }
    val selectedSet by remember { derivedStateOf { selectedUrls.toSet() } }
    var searchKeyword by remember { mutableStateOf("") }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var loadingUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val stoppedListener: (String) -> Unit = { url ->
            coroutineScope.launch(Dispatchers.Main) {
                if (playingUrl == url) playingUrl = null
            }
        }
        val loadingListener: (String, Boolean) -> Unit = { url, loading ->
            coroutineScope.launch(Dispatchers.Main) {
                loadingUrl = if (loading) url else if (loadingUrl == url) null else loadingUrl
            }
        }
        AudioPlayerManager.addOnPlaybackStopped(stoppedListener)
        AudioPlayerManager.addOnLoadingChanged(loadingListener)
        onDispose {
            AudioPlayerManager.removeOnPlaybackStopped(stoppedListener)
            AudioPlayerManager.removeOnLoadingChanged(loadingListener)
        }
    }

    LaunchedEffect(files, initialSelection) {
        if (files.isNotEmpty()) {
            selectedUrls.clear()
            selectedUrls.addAll(initialSelection)
        }
    }

    val filteredFiles = remember(files, searchKeyword) {
        if (searchKeyword.isBlank()) files
        else files.filter { (name, _) -> name.contains(searchKeyword, ignoreCase = true) }
    }

    if (previewImageUrl != null) {
        ImagePreviewDialog(
            url = previewImageUrl!!,
            name = previewImageName,
            onClose = { previewImageUrl = null }
        )
    }

    val windowState = rememberWindowState(
        width = 720.dp,
        height = 680.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "文件列表: $title",
        onCloseRequest = {
            AudioPlayerManager.stop()
            onClose()
        },
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@StyledWindow false
            when (keyEvent.key) {
                Key.Escape -> {
                    AudioPlayerManager.stop(); onClose(); true
                }

                Key.Enter if !isLoading -> {
                    AudioPlayerManager.stop()
                    onConfirm(files.filter { it.second in selectedSet })
                    true
                }

                else -> false
            }
        }
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        keyEvent.isCtrlPressed && !keyEvent.isShiftPressed && keyEvent.key == Key.A -> {
                            filteredFiles.map { it.second }
                                .forEach { if (it !in selectedSet) selectedUrls.add(it) }
                            true
                        }

                        keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.A -> {
                            selectedUrls.clear(); true
                        }

                        else -> false
                    }
                }
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))

            // 工具栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    filteredFiles.map { it.second }
                        .forEach { if (it !in selectedSet) selectedUrls.add(it) }
                }) { Text("全选可见") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { selectedUrls.clear() }) { Text("清空") }
                Spacer(Modifier.width(16.dp))
                Text("选择后缀:", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.width(4.dp))
                listOf("CN", "JP", "EN").forEach { lang ->
                    val targets = files
                        .filter { (n, _) -> n.uppercase().let { it.endsWith(lang) || it.contains("$lang.") } }
                        .map { it.second }
                    val isSelected = targets.isNotEmpty() && targets.all { it in selectedSet }
                    PillButton(
                        selected = isSelected,
                        onSelectedChanged = {
                            if (!isSelected) {
                                val toAdd = targets.filterNot { it in selectedSet }
                                selectedUrls.addAll(toAdd)
                            } else {
                                selectedUrls.removeAll(targets.toSet())
                            }
                        },
                        content = { Text(lang) }
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // 搜索框
            TextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("搜索文件名...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Image(
                        painter = rememberVectorPainter(Icons.Regular.Search),
                        contentDescription = "Search",
                        colorFilter = ColorFilter.tint(FluentTheme.colors.text.text.secondary),
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            // 文件列表
            SubtleBox(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRing(size = 48.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("正在加载列表...")
                    }

                    filteredFiles.isEmpty() -> Text(
                        if (files.isEmpty()) "无文件" else "无搜索结果",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray
                    )

                    else -> {
                        val listState = rememberLazyListState()
                        ScrollbarContainer(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                items(filteredFiles) { (name, url) ->
                                    FileListItem(
                                        name = name,
                                        url = url,
                                        isSelected = url in selectedSet,
                                        onToggle = {
                                            if (url in selectedSet) selectedUrls.remove(url)
                                            else selectedUrls.add(url)
                                        },
                                        playingUrl = playingUrl,
                                        loadingUrl = loadingUrl,
                                        onPlayToggle = { u, isActive ->
                                            if (isActive) {
                                                AudioPlayerManager.stop()
                                                playingUrl = null
                                            } else {
                                                AudioPlayerManager.play(u)
                                                playingUrl = u
                                            }
                                        },
                                        onImageClick = { u, n ->
                                            previewImageUrl = u
                                            previewImageName = n
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 底部操作栏
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "已选 ${selectedUrls.size} / ${files.size}",
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Button(onClick = { AudioPlayerManager.stop(); onClose() }) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        AudioPlayerManager.stop()
                        onConfirm(files.filter { it.second in selectedSet })
                    },
                    disabled = isLoading
                ) { Text("确认选择", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
