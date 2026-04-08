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
import kotlinx.coroutines.launch
import data.ApiResult

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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<GalleryApi.GallerySection>>(emptyList()) }
    var selectedSectionIndex by remember { mutableStateOf(0) }

    // 全屏预览
    var previewImage by remember { mutableStateOf<GalleryApi.GalleryImage?>(null) }
    // 长按保存
    var saveTargetImage by remember { mutableStateOf<GalleryApi.GalleryImage?>(null) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = GalleryApi.fetchGallery(pageName, forceRefresh)) {
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
                ErrorState(
                    message = errorMessage!!,
                    onRetry = { loadData(forceRefresh = true) },
                    modifier = Modifier.padding(innerPadding)
                )
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
            shape = RoundedCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = image.imageUrl
                    saveTargetImage = null
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
                TextButton(onClick = { saveTargetImage = null }) { Text("取消") }
            }
        )
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
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
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
