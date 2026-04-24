package com.nekolaska.calabiyau.feature.wiki.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.wiki.map.api.MapDetailApi
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.UpdateEntry
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ErrorState
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.SectionTitle
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import data.ApiResult
import java.net.URLEncoder

// ════════════════════════════════════════════════════════
//  地图详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDetailScreen(
    mapName: String,
    mapImageUrl: String? = null,  // 从列表传入的预览图
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<MapDetail?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(mapName, retryTrigger) {
        isLoading = true
        errorMessage = null
        when (val result = MapDetailApi.fetchMapDetail(mapName)) {
            is ApiResult.Success -> detail = result.value
            is ApiResult.Error -> errorMessage = result.message
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(mapName, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    FilledTonalIconButton(onClick = {
                        val enc = URLEncoder.encode(mapName, "UTF-8").replace("+", "%20")
                        onOpenWikiUrl("https://wiki.biligame.com/klbq/$enc")
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器中打开")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                LoadingState("正在加载地图信息…", Modifier.padding(innerPadding))
            }
            errorMessage != null && detail == null -> {
                ErrorState(
                    message = errorMessage!!,
                    onRetry = { retryTrigger++ },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            detail != null -> {
                MapDetailContent(
                    detail = detail!!,
                    previewImageUrl = mapImageUrl,
                    onOpenWikiUrl = onOpenWikiUrl,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun MapDetailContent(
    detail: MapDetail,
    previewImageUrl: String?,
    onOpenWikiUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 头部：地图名 + 简介 ──
        item(key = "header") {
            MapHeaderCard(detail, previewImageUrl)
        }

        // ── 支持模式 + 平台 ──
        item(key = "info") {
            MapInfoCard(detail)
        }

        // ── 地形图 ──
        if (detail.terrainMapUrl != null) {
            item(key = "terrain") {
                MapTerrainCard(detail.terrainMapUrl)
            }
        }

        // ── 地图概览（横向滚动图片） ──
        if (detail.galleryUrls.isNotEmpty()) {
            item(key = "gallery") {
                MapGalleryCard(detail.galleryUrls)
            }
        }

        // ── 更新改动历史 ──
        if (detail.updateHistory.isNotEmpty()) {
            item(key = "update_history") {
                MapUpdateHistoryCard(detail.updateHistory)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ────────────────────────────────────────────
//  头部卡片
// ────────────────────────────────────────────

@Composable
private fun MapHeaderCard(detail: MapDetail, previewImageUrl: String?) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 预览图
            val imageUrl = previewImageUrl ?: detail.galleryUrls.firstOrNull()
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = detail.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(smoothCornerShape(24.dp))
                )
            }

            Column(Modifier.padding(20.dp)) {
                Text(
                    detail.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (detail.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        detail.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  信息卡片（支持模式 + 平台）
// ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapInfoCard(detail: MapDetail) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Info, "地图信息")
            Spacer(Modifier.height(12.dp))

            // 支持模式
            if (detail.supportedModes.isNotBlank()) {
                Text("支持模式", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.supportedModes.split("、", "，", ",").forEach { mode ->
                        val trimmed = mode.trim()
                        if (trimmed.isNotBlank()) {
                            Surface(
                                shape = smoothCapsuleShape(),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    trimmed,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 上线平台
            if (detail.platforms.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))
                Text("上线平台", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.platforms.split("、", "，", ",").forEach { platform ->
                        val trimmed = platform.trim()
                        if (trimmed.isNotBlank()) {
                            Surface(
                                shape = smoothCapsuleShape(),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    trimmed,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  地形图卡片
// ────────────────────────────────────────────

@Composable
private fun MapTerrainCard(terrainMapUrl: String) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Layers, "地形图")
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = terrainMapUrl,
                contentDescription = "地形图",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(smoothCornerShape(16.dp))
            )
        }
    }
}

// ────────────────────────────────────────────
//  地图概览（横向滚动图片）
// ────────────────────────────────────────────

@Composable
private fun MapGalleryCard(galleryUrls: List<String>) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.PhotoLibrary, "地图概览")
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(galleryUrls) { url ->
                    Card(
                        shape = smoothCornerShape(16.dp),
                        modifier = Modifier.width(280.dp)
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "地图概览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapUpdateHistoryCard(updateHistory: List<UpdateEntry>) {
    var expanded by remember { mutableStateOf(false) }
    val visibleHistory = if (expanded) updateHistory else updateHistory.take(3)

    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Update, "更新改动历史")
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleHistory.forEach { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = smoothCapsuleShape(),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = entry.date,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    entry.changes.forEach { change ->
                        Row(
                            Modifier.padding(start = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                            )
                            Text(
                                text = change,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            if (updateHistory.size > 3) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (expanded) "收起" else "查看全部 ${updateHistory.size} 条更新")
                }
            }
        }
    }
}


