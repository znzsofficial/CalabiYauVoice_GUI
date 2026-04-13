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
import com.nekolaska.calabiyau.data.WeaponSkinFilterApi
import com.nekolaska.calabiyau.data.WeaponSkinFilterApi.Quality
import com.nekolaska.calabiyau.data.WeaponSkinFilterApi.WeaponSkinInfo

// ════════════════════════════════════════════════════════
//  武器外观筛选页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponSkinFilterScreen(
    initialWeapon: String? = null,
    onBack: () -> Unit
) {
    val state = rememberLoadState(emptyList<WeaponSkinInfo>()) { force ->
        WeaponSkinFilterApi.fetchAllWeaponSkins(force)
    }
    val allSkins = state.data

    // 筛选状态
    var selectedWeapon by remember { mutableStateOf(initialWeapon) }
    var selectedQuality by remember { mutableStateOf<Quality?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // 筛选后的列表
    val filteredSkins = remember(allSkins, selectedWeapon, selectedQuality, searchQuery) {
        allSkins.filter { skin ->
            (selectedWeapon == null || skin.weapon == selectedWeapon) &&
                    (selectedQuality == null || skin.quality == selectedQuality) &&
                    (searchQuery.isBlank() || skin.name.contains(searchQuery, ignoreCase = true)
                            || skin.weapon.contains(searchQuery, ignoreCase = true))
        }
    }

    // 武器列表（去重）
    val weapons = remember(allSkins) {
        allSkins.map { it.weapon }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索外观名称…") },
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
                    title = { Text("武器外观", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        if (allSkins.isNotEmpty()) {
                            Text(
                                "${filteredSkins.size}/${allSkins.size}",
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
            loading = { mod -> WeaponSkinFilterSkeleton(mod) }
        ) {
            Column {
                // ── 筛选栏 ──
                WeaponSkinFilterBar(
                    weapons = weapons,
                    selectedWeapon = selectedWeapon,
                    onWeaponSelected = { selectedWeapon = it },
                    selectedQuality = selectedQuality,
                    onQualitySelected = { selectedQuality = it }
                )

                // ── 外观网格 ──
                if (filteredSkins.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有匹配的武器外观", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    var selectedSkin by remember { mutableStateOf<WeaponSkinInfo?>(null) }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSkins, key = { it.name + it.weapon }) { skin ->
                            WeaponSkinCard(
                                skin = skin,
                                onClick = { selectedSkin = skin }
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ── 外观详情底部弹窗 ──
                    if (selectedSkin != null) {
                        WeaponSkinDetailSheet(
                            skin = selectedSkin!!,
                            onDismiss = { selectedSkin = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponSkinFilterSkeleton(modifier: Modifier = Modifier) {
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
private fun WeaponSkinFilterBar(
    weapons: List<String>,
    selectedWeapon: String?,
    onWeaponSelected: (String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 武器筛选
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedWeapon == null,
                onClick = { onWeaponSelected(null) },
                shape = smoothCornerShape(12.dp),
                label = { Text("全部武器", maxLines = 1) }
            )
            weapons.forEach { weapon ->
                FilterChip(
                    selected = selectedWeapon == weapon,
                    onClick = {
                        onWeaponSelected(if (selectedWeapon == weapon) null else weapon)
                    },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(weapon, maxLines = 1) }
                )
            }
        }

        // 品质筛选
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
            Quality.entries.forEach { quality ->
                FilterChip(
                    selected = selectedQuality == quality,
                    onClick = {
                        onQualitySelected(if (selectedQuality == quality) null else quality)
                    },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(quality.displayName, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = skinQualityColor(quality).copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  外观卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponSkinCard(skin: WeaponSkinInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = skin.quality?.let { skinQualityColor(it).copy(alpha = 0.4f) }
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 外观图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(smoothCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (skin.thumbnailUrl != null) {
                    AsyncImage(
                        model = skin.fullImageUrl ?: skin.thumbnailUrl,
                        contentDescription = skin.name,
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
                                Icons.Outlined.Palette, null,
                                Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // 品质角标
                if (skin.quality != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = smoothCapsuleShape(),
                        color = skinQualityColor(skin.quality).copy(alpha = 0.85f)
                    ) {
                        Text(
                            skin.quality.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 外观名
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    skin.name,
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
private fun skinQualityColor(quality: Quality): Color {
    return when (quality) {
        Quality.EXQUISITE -> Color(0xFF3B82F6)   // 蓝
        Quality.SUPERIOR -> Color(0xFFA855F7)    // 紫
        Quality.PERFECT -> Color(0xFFF59E0B)     // 金
        Quality.LEGENDARY -> Color(0xFFEF4444)   // 红
        Quality.COLLECTION -> Color(0xFFFF6B2C)  // 橙
    }
}

// ────────────────────────────────────────────
//  外观详情底部弹窗
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeaponSkinDetailSheet(
    skin: WeaponSkinInfo,
    onDismiss: () -> Unit
) {
    val qColor = skin.quality?.let { skinQualityColor(it) } ?: MaterialTheme.colorScheme.outline

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
                    model = skin.fullImageUrl ?: skin.thumbnailUrl,
                    contentDescription = skin.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                if (skin.quality != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = smoothCapsuleShape(),
                        color = qColor.copy(alpha = 0.9f)
                    ) {
                        Text(
                            skin.quality.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── 名称 & 武器 ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    skin.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    skin.weapon,
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
                    if (skin.sources.isNotEmpty()) {
                        SkinDetailRow(
                            icon = Icons.Outlined.ShoppingBag,
                            label = "获取方式",
                            value = skin.sources.joinToString("、")
                        )
                    }

                    // 巴布洛晶核价格
                    if (skin.crystalCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SkinDetailRow(
                            icon = Icons.Outlined.Diamond,
                            label = "巴布洛晶核",
                            value = skin.crystalCost,
                            valueColor = Color(0xFFFFC107)
                        )
                    }

                    // 基弦价格
                    if (skin.baseCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SkinDetailRow(
                            icon = Icons.Outlined.Paid,
                            label = "基弦",
                            value = skin.baseCost,
                            valueColor = Color(0xFFE040FB)
                        )
                    }

                    // 品质
                    if (skin.quality != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SkinDetailRow(
                            icon = Icons.Outlined.Star,
                            label = "品质",
                            value = skin.quality.displayName,
                            valueColor = qColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinDetailRow(
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
