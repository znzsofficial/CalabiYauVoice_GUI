package com.nekolaska.calabiyau.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
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
import androidx.core.net.toUri
import com.nekolaska.calabiyau.ui.shared.ApiResourceContent
import com.nekolaska.calabiyau.ui.shared.ShimmerBox
import com.nekolaska.calabiyau.ui.shared.ZoomableImage
import com.nekolaska.calabiyau.ui.shared.rememberLoadState
import com.nekolaska.calabiyau.ui.shared.rememberSnackbarLauncher
import com.nekolaska.calabiyau.ui.shared.smoothCapsuleShape
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape

// ════════════════════════════════════════════════════════
//  画廊页 —— 壁纸 / 表情包 / 四格漫画 通用
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    title: String,
    pageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()

    val state = rememberLoadState(
        initial = emptyList<GalleryApi.GallerySection>(),
        key = pageName
    ) { force ->
        GalleryApi.fetchGallery(pageName, force)
    }
    var selectedSectionIndex by remember { mutableIntStateOf(0) }

    // 全屏预览
    var previewImage by remember { mutableStateOf<GalleryApi.GalleryImage?>(null) }
    // 长按保存
    var saveTargetImage by remember { mutableStateOf<GalleryApi.GalleryImage?>(null) }

    LaunchedEffect(state.data) { selectedSectionIndex = 0 }

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
                    if (state.data.isNotEmpty()) {
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
            loading = { mod -> GallerySkeleton(mod) }
        ) { sections ->
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
                                shape = smoothCornerShape(12.dp),
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

                // ── 图片网格 ──
                val currentSection = sections.getOrNull(selectedSectionIndex)
                if (currentSection != null) {
                    val gridState = rememberLazyGridState()

                    // section 切换时滚动到顶部
                    LaunchedEffect(selectedSectionIndex) {
                        gridState.scrollToItem(0)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
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
                            currentSection.images,
                            key = { it.fileName }
                        ) { image ->
                            GalleryImageCard(
                                image = image,
                                onClick = { previewImage = image }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 全屏预览 Dialog（支持缩放 + 长按保存） ──
    previewImage?.let { image ->
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                ZoomableImage(
                    model = image.imageUrl,
                    contentDescription = image.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onClick = { previewImage = null },
                    onLongPress = { saveTargetImage = image }
                )

                // 关闭按钮
                IconButton(
                    onClick = { previewImage = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }

                // 保存按钮
                IconButton(
                    onClick = { saveTargetImage = image },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = "保存")
                }

                // 底部 caption
                if (image.caption.isNotBlank()) {
                    Text(
                        text = image.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    // ── 保存图片确认对话框 ──
    saveTargetImage?.let { image ->
        AlertDialog(
            onDismissRequest = { saveTargetImage = null },
            title = { Text("保存图片") },
            text = {
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = image.imageUrl
                    saveTargetImage = null
                    try {
                        val fileName = URLUtil.guessFileName(url, null, null)
                        val dir = java.io.File(AppPrefs.savePath)
                        dir.mkdirs()
                        val request = DownloadManager.Request(url.toUri()).apply {
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
                TextButton(onClick = { saveTargetImage = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun GallerySkeleton(modifier: Modifier = Modifier) {
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
                        .width(if (it == 0) 76.dp else 64.dp)
                        .height(32.dp),
                    shape = smoothCapsuleShape()
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false
        ) {
            items(8) {
                Card(
                    shape = smoothCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f),
                            shape = smoothCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(11.dp)
                                .padding(start = 12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  图片卡片
// ────────────────────────────────────────────

@Composable
private fun GalleryImageCard(
    image: GalleryApi.GalleryImage,
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
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = image.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(smoothCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            if (image.caption.isNotBlank()) {
                Text(
                    text = image.caption,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
