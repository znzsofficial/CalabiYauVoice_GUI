package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.CostumeFilterApi
import com.nekolaska.calabiyau.data.CostumeFilterApi.CostumeInfo
import com.nekolaska.calabiyau.data.CostumeFilterApi.Quality

// ════════════════════════════════════════════════════════
//  角色时装筛选页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostumeFilterScreen(
    initialCharacter: String? = null,
    onBack: () -> Unit
) {
    val state = rememberLoadState(emptyList<CostumeInfo>()) { force ->
        CostumeFilterApi.fetchAllCostumes(force)
    }
    val allCostumes = state.data

    // 筛选状态
    var selectedCharacter by remember { mutableStateOf(initialCharacter) }
    var selectedQuality by remember { mutableStateOf<Quality?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // 筛选后的列表
    val filteredCostumes = remember(allCostumes, selectedCharacter, selectedQuality, searchQuery) {
        allCostumes.filter { costume ->
            (selectedCharacter == null || costume.character == selectedCharacter) &&
                    (selectedQuality == null || costume.quality == selectedQuality) &&
                    (searchQuery.isBlank() || costume.name.contains(searchQuery, ignoreCase = true)
                            || costume.character.contains(searchQuery, ignoreCase = true))
        }
    }

    // 角色列表（去重）
    val characters = remember(allCostumes) {
        allCostumes.map { it.character }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索时装名称…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭搜索")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("角色时装", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        if (allCostumes.isNotEmpty()) {
                            Text(
                                "${filteredCostumes.size}/${allCostumes.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> CostumeFilterSkeleton(mod) }
        ) {
            Column {
                // ── 筛选栏 ──
                CostumeFilterBar(
                    characters = characters,
                    selectedCharacter = selectedCharacter,
                    onCharacterSelected = { selectedCharacter = it },
                    selectedQuality = selectedQuality,
                    onQualitySelected = { selectedQuality = it }
                )

                // ── 时装网格 ──
                if (filteredCostumes.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有匹配的时装", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    var selectedCostume by remember { mutableStateOf<CostumeInfo?>(null) }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCostumes, key = { it.name + it.character }) { costume ->
                            CostumeCard(
                                costume = costume,
                                onClick = { selectedCostume = costume }
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ── 时装详情底部弹窗 ──
                    if (selectedCostume != null) {
                        CostumeDetailSheet(
                            costume = selectedCostume!!,
                            onDismiss = { selectedCostume = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CostumeFilterSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(6) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(if (it == 0) 72.dp else 64.dp)
                            .height(32.dp),
                        shape = smoothCapsuleShape()
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(6) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(if (it == 0) 72.dp else 60.dp)
                            .height(32.dp),
                        shape = smoothCapsuleShape()
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false
        ) {
            items(12) {
                Card(
                    shape = smoothCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = smoothCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.75f).height(12.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  筛选栏
// ────────────────────────────────────────────

@Composable
private fun CostumeFilterBar(
    characters: List<String>,
    selectedCharacter: String?,
    onCharacterSelected: (String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 角色筛选
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCharacter == null,
                onClick = { onCharacterSelected(null) },
                shape = smoothCornerShape(12.dp),
                label = { Text("全部角色", maxLines = 1) }
            )
            characters.forEach { char ->
                FilterChip(
                    selected = selectedCharacter == char,
                    onClick = {
                        onCharacterSelected(if (selectedCharacter == char) null else char)
                    },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(char, maxLines = 1) }
                )
            }
        }

        // 品质筛选（跳过“初始”品质）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedQuality == null,
                onClick = { onQualitySelected(null) },
                shape = smoothCornerShape(12.dp),
                label = { Text("全部品质", maxLines = 1) }
            )
            Quality.entries.filter { it != Quality.INITIAL }.forEach { quality ->
                FilterChip(
                    selected = selectedQuality == quality,
                    onClick = {
                        onQualitySelected(if (selectedQuality == quality) null else quality)
                    },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(quality.displayName, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = qualityColor(quality).copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  时装卡片
// ────────────────────────────────────────────

@Composable
private fun CostumeCard(costume: CostumeInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = costume.quality?.let { qualityColor(it).copy(alpha = 0.4f) }
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 时装图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(smoothCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (costume.thumbnailUrl != null) {
                    AsyncImage(
                        model = costume.fullImageUrl ?: costume.thumbnailUrl,
                        contentDescription = costume.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Checkroom, null,
                                Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // 品质角标（跳过“初始”品质）
                if (costume.quality != null && costume.quality != Quality.INITIAL) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = smoothCapsuleShape(),
                        color = qualityColor(costume.quality).copy(alpha = 0.85f)
                    ) {
                        Text(
                            costume.quality.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 时装名
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    costume.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  品质颜色
// ────────────────────────────────────────────

@Composable
private fun qualityColor(quality: Quality): Color {
    return when (quality) {
        Quality.INITIAL -> Color(0xFF94A3B8)    // 灰蓝
        Quality.EXQUISITE -> Color(0xFF3B82F6)  // 蓝
        Quality.SUPERIOR -> Color(0xFFA855F7)   // 紫
        Quality.PERFECT -> Color(0xFFF59E0B)    // 金
        Quality.LEGENDARY -> Color(0xFFEF4444)  // 红
        Quality.SECRET -> Color(0xFFFF6B2C)     // 橙
    }
}

// ────────────────────────────────────────────
//  时装详情底部弹窗
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumeDetailSheet(
    costume: CostumeInfo,
    onDismiss: () -> Unit
) {
    val qColor = costume.quality?.let { qualityColor(it) } ?: MaterialTheme.colorScheme.outline

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── 头部：图片 + 渐变 + 名称 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                AsyncImage(
                    model = costume.fullImageUrl ?: costume.thumbnailUrl,
                    contentDescription = costume.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 底部渐变
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
                // 品质角标
                if (costume.quality != null && costume.quality != Quality.INITIAL) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = smoothCapsuleShape(),
                        color = qColor.copy(alpha = 0.9f)
                    ) {
                        Text(
                            costume.quality.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── 名称 & 角色 ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    costume.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    costume.character,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 信息卡片 ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp)) {
                    // 获取方式
                    if (costume.sources.isNotEmpty()) {
                        DetailRow(
                            icon = Icons.Outlined.ShoppingBag,
                            label = "获取方式",
                            value = costume.sources.joinToString("、")
                        )
                    }

                    // 巴布洛晶核价格
                    if (costume.crystalCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            icon = Icons.Outlined.Diamond,
                            label = "巴布洛晶核",
                            value = costume.crystalCost,
                            valueColor = Color(0xFFFFC107)
                        )
                    }

                    // 基弦价格
                    if (costume.baseCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            icon = Icons.Outlined.Paid,
                            label = "基弦",
                            value = costume.baseCost,
                            valueColor = Color(0xFFE040FB)
                        )
                    }

                    // 品质
                    if (costume.quality != null && costume.quality != Quality.INITIAL) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            icon = Icons.Outlined.Star,
                            label = "品质",
                            value = costume.quality.displayName,
                            valueColor = qColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
