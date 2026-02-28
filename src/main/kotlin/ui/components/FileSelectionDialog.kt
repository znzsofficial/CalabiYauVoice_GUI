package ui.components

import LocalThemeState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Play
import io.github.composefluent.icons.regular.Stop
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.lightColors
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
    var searchKeyword by remember { mutableStateOf("") }
    val darkMode = LocalThemeState.current.value
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var loadingUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // 注册播放结束 & 加载状态回调
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

    // 图片预览弹窗
    if (previewImageUrl != null) {
        ImagePreviewDialog(
            url = previewImageUrl!!,
            name = previewImageName,
            onClose = { previewImageUrl = null }
        )
    }

    DialogWindow(
        onCloseRequest = {
            AudioPlayerManager.stop()
            onClose()
        },
        title = "文件列表: $title",
        state = rememberDialogState(width = 720.dp, height = 680.dp)
    ) {
        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            Mica(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            val newUrls = filteredFiles.map { it.second }
                            newUrls.forEach { if (!selectedUrls.contains(it)) selectedUrls.add(it) }
                        }) { Text("全选可见") }

                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { selectedUrls.clear() }) { Text("清空") }

                        Spacer(Modifier.width(16.dp))
                        Text("选择后缀:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(4.dp))

                        listOf("CN", "JP", "EN").forEach { lang ->
                            val targets = files.map { (name, url) -> name.uppercase() to url }
                                .filter { (n, _) -> n.endsWith(lang) || n.contains("$lang.") }
                                .map { it.second }
                            val isChecked = targets.isNotEmpty() && targets.all { it in selectedUrls }

                            CheckBox(label = lang, checked = isChecked) { checked ->
                                if (checked) {
                                    val toAdd = targets.filterNot { it in selectedUrls }
                                    selectedUrls.addAll(toAdd)
                                } else {
                                    selectedUrls.removeAll { it in targets }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

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
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(4.dp))
                            .background(FluentTheme.colors.control.secondary)
                    ) {
                        if (isLoading) {
                            Column(
                                Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ProgressRing(size = 48.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("正在加载列表...")
                            }
                        } else if (filteredFiles.isEmpty()) {
                            val emptyText = if (files.isEmpty()) "无文件" else "无搜索结果"
                            Text(emptyText, modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                        } else {
                            val listState = rememberLazyListState()
                            val adapter = rememberScrollbarAdapter(listState)

                            ScrollbarContainer(
                                adapter = adapter,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    items(filteredFiles) { (name, url) ->
                                        val isSelected = selectedUrls.contains(url)
                                        val canPreview = isImageFile(name, url)
                                        val canPlay = isAudioFile(name, url)
                                        val isPlaying = playingUrl == url && AudioPlayerManager.isPlaying(url)
                                        val isThisLoading = loadingUrl == url
                                        // 正在加载或正在播放时都显示 Stop 图标
                                        val isActive = isPlaying || isThisLoading


                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    if (isSelected) selectedUrls.remove(url) else selectedUrls.add(url)
                                                }
                                                .padding(vertical = 4.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CheckBox(checked = isSelected, onCheckStateChange = {
                                                if (it) selectedUrls.add(url) else selectedUrls.remove(url)
                                            })
                                            Spacer(Modifier.width(8.dp))

                                            if (canPreview) {
                                                NetworkImage(
                                                    url = url,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            previewImageUrl = url
                                                            previewImageName = name
                                                        },
                                                    contentScale = ContentScale.Crop,
                                                    placeholder = {
                                                        Box(
                                                            Modifier
                                                                .size(36.dp)
                                                                .background(FluentTheme.colors.control.secondary)
                                                        )
                                                    }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            // 文件名
                                            Column(Modifier.weight(1f)) {
                                                Text(name, fontSize = 13.sp)
                                            }

                                            // 播放按钮（图标）
                                            if (canPlay) {
                                                Spacer(Modifier.width(8.dp))
                                                Button(
                                                    iconOnly = true,
                                                    onClick = {
                                                        if (isActive) {
                                                            AudioPlayerManager.stop()
                                                            playingUrl = null
                                                        } else {
                                                            AudioPlayerManager.play(url)
                                                            playingUrl = url
                                                        }
                                                    }
                                                ) {
                                                    if (isThisLoading) {
                                                        ProgressRing(size = 16.dp)
                                                    } else {
                                                        val icon = if (isActive) Icons.Regular.Stop else Icons.Regular.Play
                                                        Image(
                                                            painter = rememberVectorPainter(icon),
                                                            contentDescription = if (isActive) "停止" else "播放",
                                                            colorFilter = ColorFilter.tint(
                                                                if (isActive) FluentTheme.colors.fillAccent.default
                                                                else FluentTheme.colors.text.text.primary
                                                            ),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "已选 ${selectedUrls.size} / ${files.size}",
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Button(onClick = {
                            AudioPlayerManager.stop()
                            onClose()
                        }) { Text("取消") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val finalSelection = files.filter { selectedUrls.contains(it.second) }
                                AudioPlayerManager.stop()
                                onConfirm(finalSelection)
                            },
                            disabled = isLoading
                        ) {
                            Text("确认选择", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
