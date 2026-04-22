package com.nekolaska.calabiyau.ui.download

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.EmptyState
import com.nekolaska.calabiyau.core.ui.ZoomableImage
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.launch
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume

@Composable
fun PortraitGrid(
    characters: List<String>,
    characterAvatars: Map<String, String>,
    hasSearched: Boolean,
    favorites: Set<String> = emptySet(),
    searchError: String? = null,
    onRetry: (() -> Unit)? = null,
    onToggleFavorite: (String) -> Unit = {},
    onSelectCharacter: (String) -> Unit
) {
    if (characters.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Image,
            message = if (hasSearched) "未找到相关角色" else "输入关键词搜索角色立绘",
            errorMessage = if (hasSearched) searchError else null,
            onRetry = onRetry
        )
        return
    }

    val sorted = remember(characters, favorites) {
        characters.sortedByDescending { it in favorites }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sorted) { name ->
            val avatarUrl = characterAvatars[name]
            val isFavorite = name in favorites
            Card(
                onClick = { onSelectCharacter(name) },
                shape = smoothCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(1),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        // 收藏图标
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shadowElevation = 2.dp
                        ) {
                            IconButton(
                                onClick = { onToggleFavorite(name) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitDetailContent(
    characterName: String,
    catalog: CharacterPortraitCatalog?,
    isLoading: Boolean,
    selectedCostume: PortraitCostume?,
    isDownloading: Boolean,
    onSelectCostume: (PortraitCostume) -> Unit,
    onDownload: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope() // Add scope

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Limit height
            .padding(bottom = 16.dp)
    ) {
        // Header
        Text(
            text = characterName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (catalog == null || catalog.costumes.isEmpty()) {
            EmptyState(icon = Icons.Outlined.HideImage, message = "该角色暂无立绘数据")
            return
        }

        val costumes = catalog.costumes
        val pagerState = rememberPagerState(pageCount = { costumes.size })

        // Sync selected costume with pager
        LaunchedEffect(pagerState.currentPage) {
            onSelectCostume(costumes[pagerState.currentPage])
        }

        // Costume Tabs
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 24.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(pagerState.currentPage),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            costumes.forEachIndexed { index, costume ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(costume.name) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content Pager
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            overscrollEffect = null,
            modifier = Modifier.weight(1f)
        ) { page ->
            val costume = costumes[page]
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                CostumeCard(costume = costume)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Download Action
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            enabled = !isDownloading,
            shape = smoothCapsuleShape()
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("下载此装扮资产", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CostumeCard(costume: PortraitCostume) {
    // Collect all available images with labels
    val allImages = remember(costume) {
        buildList {
            costume.illustration?.let { add("立绘" to it) }
            costume.frontPreview?.let { add("正面预览" to it) }
            costume.backPreview?.let { add("背面预览" to it) }
            costume.extraAssets.forEachIndexed { i, asset ->
                add("附加 ${i + 1}" to asset)
            }
        }
    }

    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (allImages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.HideImage, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("无预览图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (allImages.size == 1) {
                // Single image — no pager needed
                ZoomableImage(
                    model = allImages[0].second.url,
                    contentDescription = allImages[0].first,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Multiple images — use vertical pager to avoid gesture conflict with outer costume HorizontalPager
                val imagePagerState = rememberPagerState(pageCount = { allImages.size })

                VerticalPager(
                    state = imagePagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val (_, asset) = allImages[page]
                    ZoomableImage(
                        model = asset.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Image label badge (top-start)
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                    shape = smoothCapsuleShape(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = allImages[imagePagerState.currentPage].first,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Page indicator dots (right-center, vertical)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(allImages.size) { index ->
                        val isSelected = imagePagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                        )
                    }
                }
            }

            // Info Badge — pill style (bottom-end)
            val fileCount = allImages.size
            if (fileCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                    shape = smoothCapsuleShape(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Collections, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        Text(
                            text = "$fileCount 个文件",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
