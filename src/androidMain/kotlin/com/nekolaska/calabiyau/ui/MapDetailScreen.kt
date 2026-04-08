package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.MapDetailApi
import com.nekolaska.calabiyau.data.MapDetailApi.MapDetail
import data.ApiResult

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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val enc = java.net.URLEncoder.encode(mapName, "UTF-8").replace("+", "%20")
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
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载地图信息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ────────────────────────────────────────────
//  头部卡片
// ────────────────────────────────────────────

@Composable
private fun MapHeaderCard(detail: MapDetail, previewImageUrl: String?) {
    Card(
        shape = RoundedCornerShape(24.dp),
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
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            MapSectionTitle(Icons.Outlined.Info, "地图信息")
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
                                shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            MapSectionTitle(Icons.Outlined.Layers, "地形图")
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = terrainMapUrl,
                contentDescription = "地形图",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            MapSectionTitle(Icons.Outlined.PhotoLibrary, "地图概览")
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(galleryUrls) { url ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
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
private fun MapSectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
