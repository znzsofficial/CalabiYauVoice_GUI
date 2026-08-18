package com.nekolaska.calabiyau.feature.weapon.skin

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.weapon.components.WeaponSelector
import com.nekolaska.calabiyau.feature.weapon.components.WeaponSelectorOption
import com.nekolaska.calabiyau.feature.weapon.components.buildWeaponSelectorOptions
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi.Quality
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi.WeaponSkinInfo
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogDetailRow
import com.nekolaska.calabiyau.core.ui.CatalogGridCard
import com.nekolaska.calabiyau.core.ui.CatalogGridSkeleton
import com.nekolaska.calabiyau.core.ui.CatalogQualityBadge
import com.nekolaska.calabiyau.core.ui.ExpandableCatalogDescription
import com.nekolaska.calabiyau.core.ui.QualityFilterChips
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
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
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { WeaponSkinFilterApi.fetchAllWeaponSkins(cacheOnly = true) },
        fetch = { force -> WeaponSkinFilterApi.fetchAllWeaponSkins(forceRefresh = force) }
    )
    val allSkins = state.data

    // 筛选状态
    var selectedWeapon by rememberSaveable(initialWeapon) { mutableStateOf(initialWeapon) }
    var selectedWeaponCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedQualityLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedQuality = selectedQualityLevel?.let(Quality::fromLevel)

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
        }.let { filtered ->
            if (selectedQuality == null) {
                filtered.sortedWith(
                    compareByDescending<WeaponSkinInfo> { it.quality?.level ?: 0 }
                        .thenBy { it.weaponCategory }
                        .thenBy { it.weaponType }
                        .thenBy { it.weapon }
                        .thenBy { it.name }
                )
            } else {
                filtered
            }
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
                            onQualitySelected = { selectedQualityLevel = it?.level }
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
                    items(filteredSkins, key = { "${it.weapon}|${it.name}|${it.thumbnailUrl.orEmpty()}" }) { skin ->
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
    CatalogGridSkeleton(modifier = modifier)
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
        QualityFilterChips(
            selectedLevel = selectedQuality?.level,
            levels = Quality.entries.sortedByDescending { it.level }.map { it.level to it.displayName },
            onSelectedLevelChange = { level -> onQualitySelected(level?.let(Quality::fromLevel)) },
            colorForLevel = { catalogQualityColor(it) }
        )
    }
}


// ────────────────────────────────────────────
//  外观卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponSkinCard(skin: WeaponSkinInfo, onClick: () -> Unit) {
    CatalogGridCard(
        name = skin.name,
        onClick = onClick,
        imageUrl = skin.thumbnailUrl,
        qualityLabel = skin.quality?.displayName,
        qualityLevel = skin.quality?.level,
        fallbackIcon = Icons.Outlined.Palette
    )
}

// ────────────────────────────────────────────
//  品质颜色
// ────────────────────────────────────────────

// ────────────────────────────────────────────
//  外观详情底部弹窗
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeaponSkinDetailSheet(
    skin: WeaponSkinInfo,
    onDismiss: () -> Unit
) {
    val qColor = skin.quality?.let { catalogQualityColor(it.level) } ?: MaterialTheme.colorScheme.outline
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
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
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
                CatalogQualityBadge(
                    label = skin.quality?.displayName,
                    level = skin.quality?.level,
                    compact = false
                )
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
                ExpandableCatalogDescription(
                    text = skin.description,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    collapsedLines = 5
                )
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
                        CatalogDetailRow(
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
                        CatalogDetailRow(
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
                        CatalogDetailRow(
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
                        CatalogDetailRow(
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


