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

// ════════════════════════════════════════════════════════
//  基板页 —— 展示所有基板（名片/横幅）
// ════════════════════════════════════════════════════════

/** 品质等级对应颜色 */
private fun qualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF8C00)  // 橙色 - 传说
    5 -> Color(0xFFE040FB)  // 紫色 - 史诗
    4 -> Color(0xFFAB47BC)  // 紫色
    3 -> Color(0xFF42A5F5)  // 蓝色 - 稀有
    2 -> Color(0xFF66BB6A)  // 绿色 - 精良
    1 -> Color(0xFF90A4AE)  // 灰色 - 初始
    else -> Color(0xFF90A4AE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseplateScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<PlayerDecorationApi.DecorationSection>>(emptyList()) }
    var selectedSectionIndex by remember { mutableStateOf(0) }

    // 全屏预览
    var previewItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }
    // 长按保存
    var saveTargetItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = PlayerDecorationApi.fetch("基板", forceRefresh)) {
                is GalleryApi.ApiResult.Success -> {
                    sections = result.value
                    selectedSectionIndex = 0
                }
                is GalleryApi.ApiResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基板", fontWeight = FontWeight.Bold) },
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
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // ── Section 切换 FilterChip ──
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
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    // ── 基板网格 ──
                    val currentSection = sections.getOrNull(selectedSectionIndex)
                    if (currentSection != null) {
                        val gridState = rememberLazyGridState()

                        LaunchedEffect(selectedSectionIndex) {
                            gridState.scrollToItem(0)
                        }

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
                            items(
                                currentSection.items,
                                key = { it.id }
                            ) { item ->
                                BaseplateCard(
                                    item = item,
                                    onClick = { previewItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 全屏预览 Dialog ──
    previewItem?.let { item ->
        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                ZoomableImage(
                    model = item.imageUrl.ifEmpty { item.iconUrl },
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onClick = { previewItem = null },
                    onLongPress = { saveTargetItem = item }
                )

                // 关闭按钮
                IconButton(
                    onClick = { previewItem = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }

                // 保存按钮
                IconButton(
                    onClick = { saveTargetItem = item },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = "保存")
                }

                // 底部信息
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
                        // 品质色点
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(qualityColor(item.quality))
                        )
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.source.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "获得方式: ${item.source}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ── 保存图片确认对话框 ──
    saveTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { saveTargetItem = null },
            title = { Text("保存图片") },
            text = {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

// ────────────────────────────────────────────
//  基板卡片
// ────────────────────────────────────────────

@Composable
private fun BaseplateCard(
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
            // 图片（优先使用缩略图 icon，加载更快）
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

            // 名称 + 品质色条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 品质色点
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
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
