package com.nekolaska.calabiyau.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
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
import com.nekolaska.calabiyau.data.PlayerDecorationApi

// ════════════════════════════════════════════════════════
//  基板页 —— 展示所有基板（名片/横幅）
// ════════════════════════════════════════════════════════

/** 品质等级对应颜色 */
private fun qualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF6B2C)  // 橙色 - 私服
    5 -> Color(0xFFEF4444)  // 红色 - 传说
    4 -> Color(0xFFF59E0B)  // 金色 - 完美
    3 -> Color(0xFFAB47BC)  // 紫色
    2 -> Color(0xFF42A5F5)  // 蓝色
    1 -> Color(0xFF90A4AE)  // 灰色 - 初始
    else -> Color(0xFF90A4AE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseplateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()

    val state = rememberLoadState(emptyList<PlayerDecorationApi.DecorationSection>()) { force ->
        PlayerDecorationApi.fetch("基板", force)
    }
    val sections = state.data
    var selectedSectionIndex by remember { mutableStateOf(0) }

    // 全屏预览
    var previewItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }
    // 长按保存
    var saveTargetItem by remember { mutableStateOf<PlayerDecorationApi.DecorationItem?>(null) }

    LaunchedEffect(state.data) { selectedSectionIndex = 0 }

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
                        IconButton(onClick = { state.reload(forceRefresh = true) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> BaseplateSkeleton(mod) }
        ) {
            Column(Modifier.fillMaxSize()) {
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
                        showSnack("已保存: $fileName")
                    } catch (e: Exception) {
                        showSnack("保存失败: ${e.message}")
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
private fun BaseplateSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                ShimmerBox(
                    modifier = Modifier
                        .width(if (it == 0) 80.dp else 68.dp)
                        .height(32.dp),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false
        ) {
            items(10) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShimmerBox(
                                modifier = Modifier.size(8.dp),
                                shape = RoundedCornerShape(50)
                            )
                            Spacer(Modifier.width(6.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(11.dp))
                        }
                    }
                }
            }
        }
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

    Card(
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
