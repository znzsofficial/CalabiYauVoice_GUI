package com.nekolaska.calabiyau.feature.wiki.decoration

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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import androidx.core.net.toUri
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.HorizontalFilterChips
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.decoration.api.PlayerDecorationApi
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import java.io.File

// ════════════════════════════════════════════════════════
//  通用玩家装饰页 —— 封装/聊天气泡/头套/超弦体动作/头像框
// ════════════════════════════════════════════════════════

private fun qualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF6B2C)
    5 -> Color(0xFFEF4444)
    4 -> Color(0xFFF59E0B)
    3 -> Color(0xFFAB47BC)
    2 -> Color(0xFF42A5F5)
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
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()

    val state = rememberLoadState(
        initial = emptyList<DecorationSection>(),
        key = pageName
    ) { force ->
        PlayerDecorationApi.fetch(pageName, force)
    }
    val sections = state.data
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    var previewItem by remember { mutableStateOf<DecorationItem?>(null) }
    var saveTargetItem by remember { mutableStateOf<DecorationItem?>(null) }

    LaunchedEffect(state.data) { selectedSectionIndex = 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (sections.isNotEmpty()) {
                        RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> PlayerDecorationSkeleton(mod) }
        ) {
            Column(Modifier.fillMaxSize()) {
                // Section 切换
                if (sections.size > 1) {
                    HorizontalFilterChips(
                        items = sections.indices.toList(),
                        selected = selectedSectionIndex,
                        label = { index -> sections[index].title },
                        onSelected = { selectedSectionIndex = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        showCheckIcon = true
                    )
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

    // ── 全屏预览 ──
    previewItem?.let { item ->
        ImagePreviewDialog(
            model = item.imageUrl.ifEmpty { item.iconUrl },
            contentDescription = item.name,
            onDismiss = { previewItem = null },
            onSave = { saveTargetItem = item }
        ) {
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
                    if (item.specialDescription.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.specialDescription,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                    if (item.extraPreviews.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "附属界面预览",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item.extraPreviews.forEach { preview ->
                                Column(
                                    modifier = Modifier.width(132.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = smoothCornerShape(16.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AsyncImage(
                                            model = preview.imageUrl,
                                            contentDescription = preview.label,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = preview.label,
                                        color = Color.White.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = item.imageUrl.ifEmpty { item.iconUrl }
                    saveTargetItem = null
                    try {
                        val fileName = URLUtil.guessFileName(url, null, null)
                        val dir = File(AppPrefs.savePath)
                        dir.mkdirs()
                        val request = DownloadManager.Request(url.toUri()).apply {
                            setTitle(fileName)
                            setDescription("正在保存图片...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationUri(Uri.fromFile(File(dir, fileName)))
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
private fun PlayerDecorationSkeleton(modifier: Modifier = Modifier) {
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
                    shape = smoothCapsuleShape()
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
                    shape = smoothCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = smoothCornerShape(16.dp)
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

@Composable
private fun DecorationCard(
    item: DecorationItem,
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
                    .data(item.iconUrl.ifEmpty { item.imageUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(smoothCornerShape(16.dp)),
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
