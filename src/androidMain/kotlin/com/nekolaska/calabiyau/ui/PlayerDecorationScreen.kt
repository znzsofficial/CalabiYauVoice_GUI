package com.nekolaska.calabiyau.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.GalleryApi
import com.nekolaska.calabiyau.data.PlayerDecorationApi
import kotlinx.coroutines.launch
import data.ApiResult

// ════════════════════════════════════════════════════════
//  通用玩家装饰页 —— 封装/聊天气泡/头套/超弦体动作/头像框
// ════════════════════════════════════════════════════════

private fun qualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF8C00)
    5 -> Color(0xFFE040FB)
    4 -> Color(0xFFAB47BC)
    3 -> Color(0xFF42A5F5)
    2 -> Color(0xFF66BB6A)
    1 -> Color(0xFF90A4AE)
    else -> Color(0xFF90A4AE)
}

/**
 * 通用玩家装饰展示页面。
 * @param title 页面标题（如 "封装"、"聊天气泡"）
 * @param pageName Wiki 页面名（传给 PlayerDecorationApi.fetch）
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDecorationScreen(
    title: String,
    pageName: String = title,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<PlayerDecorationApi.DecorationSection>>(emptyList()) }
    var selectedSectionIndex by remember { mutableStateOf(0) }
    var previewItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }
    var saveTargetItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = PlayerDecorationApi.fetch(pageName, forceRefresh)) {
                is ApiResult.Success -> {
                    sections = result.value
                    selectedSectionIndex = 0
                }
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(pageName) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (sections.isNotEmpty()) {
                        IconButton(onClick = { loadData(forceRefresh = true) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            errorMessage != null && sections.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(errorMessage!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { loadData(forceRefresh = true) }) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().padding(innerPadding)) {
                    // Section 切换
                    if (sections.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sections.forEachIndexed { index, section ->
                                FilterChip(
                                    selected = selectedSectionIndex == index,
                                    onClick = { selectedSectionIndex = index },
                                    label = {
                                        Text(
                                            section.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = if (selectedSectionIndex == index) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check, null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    // 网格
                    val currentSection = sections.getOrNull(selectedSectionIndex)
                    if (currentSection != null) {
                        val gridState = rememberLazyGridState()
                        LaunchedEffect(selectedSectionIndex) { gridState.scrollToItem(0) }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp,
                                top = 8.dp, bottom = 16.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentSection.items, key = { it.id }) { item ->
                                DecorationCard(item = item, onClick = { previewItem = item })
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 全屏预览 ──
    previewItem?.let { item ->
        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                ZoomableImage(
                    model = item.imageUrl.ifEmpty { item.iconUrl },
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    onClick = { previewItem = null },
                    onLongPress = { saveTargetItem = item }
                )
                IconButton(
                    onClick = { previewItem = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
                IconButton(
                    onClick = { saveTargetItem = item },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) { Icon(Icons.Outlined.SaveAlt, contentDescription = "保存") }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(RoundedCornerShape(50))
                                .background(qualityColor(item.quality))
                        )
                        Text(
                            item.name, color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.description,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.source.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "获得方式: ${item.source}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ── 保存确认 ──
    saveTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { saveTargetItem = null },
            title = { Text("保存图片") },
            text = {
                Text(item.name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            shape = RoundedCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = item.imageUrl.ifEmpty { item.iconUrl }
                    saveTargetItem = null
                    try {
                        val fileName = URLUtil.guessFileName(url, null, null)
                        val dir = java.io.File(AppPrefs.savePath)
                        dir.mkdirs()
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setTitle(fileName)
                            setDescription("正在保存图片...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationUri(Uri.fromFile(java.io.File(dir, fileName)))
                        }
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(context, "已保存: $fileName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { saveTargetItem = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DecorationCard(
    item: PlayerDecorationApi.DecorationItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = smoothCornerShape(16.dp)

    ElevatedCard(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.iconUrl.ifEmpty { item.imageUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(50))
                        .background(qualityColor(item.quality))
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
