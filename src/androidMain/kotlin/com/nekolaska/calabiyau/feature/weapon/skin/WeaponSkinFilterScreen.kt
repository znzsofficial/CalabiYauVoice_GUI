package com.nekolaska.calabiyau.feature.weapon.skin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.weapon.components.WeaponSelector
import com.nekolaska.calabiyau.feature.weapon.components.WeaponSelectorOption
import com.nekolaska.calabiyau.feature.weapon.components.buildWeaponSelectorOptions
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi.Quality
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi.WeaponSkinInfo
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

// ════════════════════════════════════════════════════════
//  武器外观筛选页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private const val CRYSTAL_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_3.png"
private const val CRYSTAL_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%B7%B4%E5%B8%83%E6%B4%9B%E6%99%B6%E6%A0%B8.png"
private const val BASE_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_6.png"
private const val BASE_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%9F%BA%E5%BC%A6.png"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponSkinFilterScreen(
    initialWeapon: String? = null,
    onBack: () -> Unit
) {
    val state = rememberLoadState(
        initial = emptyList<WeaponSkinInfo>(),
        cachedFetch = { WeaponSkinFilterApi.fetchAllWeaponSkins(cacheOnly = true) },
        fetch = { force -> WeaponSkinFilterApi.fetchAllWeaponSkins(force) }
    )
    val allSkins = state.data

    // 筛选状态
    var selectedWeapon by remember { mutableStateOf(initialWeapon) }
    var selectedWeaponCategory by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<Quality?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 筛选后的列表
    val filteredSkins = remember(allSkins, selectedWeapon, selectedWeaponCategory, selectedQuality, searchQuery) {
        allSkins.filter { skin ->
            (selectedWeapon == null || skin.weapon == selectedWeapon) &&
                    (selectedWeaponCategory == null || skin.weaponCategory == selectedWeaponCategory) &&
                    (selectedQuality == null || skin.quality == selectedQuality) &&
                    (searchQuery.isBlank() || skin.name.contains(searchQuery, ignoreCase = true)
                            || skin.weapon.contains(searchQuery, ignoreCase = true)
                            || skin.weaponCategory.contains(searchQuery, ignoreCase = true)
                            || skin.weaponType.contains(searchQuery, ignoreCase = true))
        }
    }

    val weaponOptions = remember(allSkins) { buildWeaponSelectorOptions(allSkins) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("武器外观", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (allSkins.isNotEmpty()) {
                        Text(
                            text = filteredSkins.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> WeaponSkinFilterSkeleton(mod) }
        ) {
            var selectedSkin by remember { mutableStateOf<WeaponSkinInfo?>(null) }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SearchBar(
                            keyword = searchQuery,
                            onKeywordChange = { searchQuery = it },
                            onSearch = {},
                            onClear = { searchQuery = "" },
                            isSearching = false,
                            placeholder = "搜索外观名称或武器…",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // ── 筛选栏 ──
                        WeaponSkinFilterBar(
                            weaponOptions = weaponOptions,
                            selectedWeaponCategory = selectedWeaponCategory,
                            selectedWeapon = selectedWeapon,
                            onWeaponSelected = { category, weapon ->
                                selectedWeaponCategory = category
                                selectedWeapon = weapon
                            },
                            selectedQuality = selectedQuality,
                            onQualitySelected = { selectedQuality = it }
                        )
                    }
                }

                // ── 外观网格 ──
                if (filteredSkins.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的武器外观", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
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

@Composable
private fun WeaponSkinFilterSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(52.dp),
            shape = smoothCornerShape(28.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(72.dp)
                    .height(14.dp),
                shape = smoothCornerShape(6.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = smoothCornerShape(20.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .width(72.dp)
                    .height(14.dp),
                shape = smoothCornerShape(6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        ShimmerBox(
                            Modifier.fillMaxWidth(0.75f).height(12.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(
                            Modifier.fillMaxWidth(0.5f).height(10.dp)
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeaponSkinFilterBar(
    weaponOptions: List<WeaponSelectorOption>,
    selectedWeaponCategory: String?,
    selectedWeapon: String?,
    onWeaponSelected: (category: String?, weapon: String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "按武器筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WeaponSelector(
            label = "武器",
            weapons = weaponOptions,
            selectedCategory = selectedWeaponCategory,
            selectedWeapon = selectedWeapon,
            onSelected = onWeaponSelected,
            allLabel = "全部武器"
        )

        // 品质筛选
        Text(
            text = "按品质筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        model = skin.thumbnailUrl,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
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
    val displayModes = buildList {
        if (!skin.fullImageUrl.isNullOrBlank() || !skin.thumbnailUrl.isNullOrBlank()) add("立绘")
        if (!skin.screenshotUrl.isNullOrBlank()) add("游戏截图")
    }
    var selectedMode by remember(skin.name, skin.weapon) {
        mutableStateOf(if (displayModes.contains("立绘")) "立绘" else displayModes.firstOrNull().orEmpty())
    }
    val displayedImage = when (selectedMode) {
        "游戏截图" -> skin.screenshotUrl ?: skin.fullImageUrl ?: skin.thumbnailUrl
        else -> skin.fullImageUrl ?: skin.thumbnailUrl ?: skin.screenshotUrl
    }

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
                if (displayedImage != null) {
                    AsyncImage(
                        model = displayedImage,
                        contentDescription = skin.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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

                if (displayModes.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayModes.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode) },
                                shape = smoothCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (skin.description.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = smoothCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    val scrollState = rememberScrollState()
                    Text(
                        text = skin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isDescriptionExpanded) 240.dp else Dp.Unspecified)
                            .then(if (isDescriptionExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isDescriptionExpanded = !isDescriptionExpanded }
                            .animateContentSize()
                            .padding(20.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

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
                            imageUrl = CRYSTAL_ICON_URL,
                            fallbackImageUrl = CRYSTAL_ICON_FALLBACK_URL,
                            label = "巴布洛晶核",
                            value = skin.crystalCost
                        )
                    }

                    // 基弦价格
                    if (skin.baseCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SkinDetailRow(
                            imageUrl = BASE_ICON_URL,
                            fallbackImageUrl = BASE_ICON_FALLBACK_URL,
                            label = "基弦",
                            value = skin.baseCost
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
    icon: ImageVector? = null,
    imageUrl: String? = null,
    fallbackImageUrl: String? = null,
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
                if (imageUrl != null) {
                    var useFallback by remember(imageUrl, fallbackImageUrl) { mutableStateOf(false) }
                    AsyncImage(
                        model = if (useFallback) fallbackImageUrl ?: imageUrl else imageUrl,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        onError = {
                            if (!useFallback && !fallbackImageUrl.isNullOrBlank()) {
                                useFallback = true
                            }
                        },
                        modifier = Modifier.size(22.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
