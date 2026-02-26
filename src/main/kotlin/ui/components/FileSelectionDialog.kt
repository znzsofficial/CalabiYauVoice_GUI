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
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.lightColors

private fun isImageFile(name: String, url: String): Boolean {
    val lowerName = name.lowercase()
    val lowerUrl = url.lowercase()
    return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(
        ".gif"
    ) || lowerName.endsWith(".webp") ||
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") || lowerUrl.endsWith(
        ".webp"
    )
}

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

    DialogWindow(
        onCloseRequest = onClose,
        title = "文件列表: $title",
        state = rememberDialogState(width = 720.dp, height = 680.dp)
    ) {
        FluentTheme(colors = if (darkMode) darkColors() else lightColors()) {
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
                                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
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

                                            Text(name, fontSize = 13.sp)
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

                        Button(onClick = onClose) { Text("取消") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val finalSelection = files.filter { selectedUrls.contains(it.second) }
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
