package com.nekolaska.calabiyau.feature.character.costume

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
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.CostumeInfo
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.Quality
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
import com.nekolaska.calabiyau.feature.character.components.CharacterSelector
import com.nekolaska.calabiyau.feature.character.components.rememberCharacterSelectorOptions

// ════════════════════════════════════════════════════════
//  角色时装筛选页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private const val CRYSTAL_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_3.png"
private const val CRYSTAL_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%B7%B4%E5%B8%83%E6%B4%9B%E6%99%B6%E6%A0%B8.png"
private const val BASE_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_6.png"
private const val BASE_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%9F%BA%E5%BC%A6.png"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostumeFilterScreen(
    initialCharacter: String? = null,
    onBack: () -> Unit
) {
    val state = rememberLoadState(
        initial = emptyList<CostumeInfo>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { CostumeFilterApi.fetchAllCostumes(cacheOnly = true) },
        fetch = { force -> CostumeFilterApi.fetchAllCostumes(forceRefresh = force) }
    )
    val allCostumes = state.data

    // 筛选状态
    var selectedCharacter by rememberSaveable(initialCharacter) { mutableStateOf(initialCharacter) }
    var selectedQualityLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedQuality = selectedQualityLevel?.let(Quality::fromLevel)

    // 筛选后的列表
    val filteredCostumes = remember(allCostumes, selectedCharacter, selectedQuality, searchQuery) {
        allCostumes.filter { costume ->
            (selectedCharacter == null || costume.character == selectedCharacter) &&
                    (selectedQuality == null || costume.quality == selectedQuality) &&
                    (searchQuery.isBlank() || costume.name.contains(searchQuery, ignoreCase = true)
                            || costume.character.contains(searchQuery, ignoreCase = true))
        }.let { filtered ->
            if (selectedQuality == null) {
                filtered.sortedWith(
                    compareByDescending<CostumeInfo> { it.quality?.level ?: 0 }
                        .thenBy { it.character }
                        .thenBy { it.name }
                )
            } else {
                filtered
            }
        }
    }

    // 角色列表（去重）
    val characters = remember(allCostumes) {
        allCostumes.map { it.character }.distinct().sorted()
    }
    val characterOptions = rememberCharacterSelectorOptions(characters)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色时装", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (allCostumes.isNotEmpty()) {
                        Text(
                            text = filteredCostumes.size.toString(),
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
            loading = { mod -> CostumeFilterSkeleton(mod) }
        ) {
            var selectedCostume by remember { mutableStateOf<CostumeInfo?>(null) }

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
                            placeholder = "搜索时装名称或角色…",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // ── 筛选栏 ──
                        CostumeFilterBar(
                            characterOptions = characterOptions,
                            selectedCharacter = selectedCharacter,
                            onCharacterSelected = { selectedCharacter = it },
                            selectedQuality = selectedQuality,
                            onQualitySelected = { selectedQualityLevel = it?.level }
                        )
                    }
                }

                // ── 时装网格 ──
                if (filteredCostumes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的时装", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredCostumes, key = { "${it.character}|${it.name}|${it.thumbnailUrl.orEmpty()}" }) { costume ->
                        CostumeCard(
                            costume = costume,
                            onClick = { selectedCostume = costume }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(16.dp))
                    }
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

@Composable
private fun CostumeFilterSkeleton(modifier: Modifier = Modifier) {
    CatalogGridSkeleton(modifier = modifier)
}

// ────────────────────────────────────────────
//  筛选栏
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumeFilterBar(
    characterOptions: List<com.nekolaska.calabiyau.feature.character.components.CharacterSelectorOption>,
    selectedCharacter: String?,
    onCharacterSelected: (String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "按角色筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CharacterSelector(
            options = characterOptions,
            selectedName = selectedCharacter,
            onSelectedNameChange = onCharacterSelected,
            label = "角色",
            allLabel = "全部角色"
        )

        // 品质筛选（跳过“初始”品质）
        Text(
            text = "按品质筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        QualityFilterChips(
            selectedLevel = selectedQuality?.level,
            levels = Quality.entries
                .filter { it != Quality.INITIAL }
                .sortedByDescending { it.level }
                .map { it.level to it.displayName },
            onSelectedLevelChange = { level -> onQualitySelected(level?.let(Quality::fromLevel)) },
            colorForLevel = { catalogQualityColor(it) }
        )
    }
}


// ────────────────────────────────────────────
//  时装卡片
// ────────────────────────────────────────────

@Composable
private fun CostumeCard(costume: CostumeInfo, onClick: () -> Unit) {
    val quality = costume.quality?.takeIf { it != Quality.INITIAL }
    CatalogGridCard(
        name = costume.name,
        onClick = onClick,
        imageUrl = costume.thumbnailUrl,
        qualityLabel = quality?.displayName,
        qualityLevel = quality?.level,
        fallbackIcon = Icons.Outlined.Checkroom
    )
}

// ────────────────────────────────────────────
//  品质颜色
// ────────────────────────────────────────────

// ────────────────────────────────────────────
//  时装详情底部弹窗
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumeDetailSheet(
    costume: CostumeInfo,
    onDismiss: () -> Unit
) {
    val qColor = costume.quality?.let { catalogQualityColor(it.level) } ?: MaterialTheme.colorScheme.outline
    val displayModes = buildList {
        if (!costume.fullImageUrl.isNullOrBlank() || !costume.thumbnailUrl.isNullOrBlank()) add("立绘")
        if (!costume.screenshotUrl.isNullOrBlank()) add("游戏截图")
    }
    var selectedMode by remember(costume.name, costume.character) {
        mutableStateOf(if (displayModes.contains("立绘")) "立绘" else displayModes.firstOrNull().orEmpty())
    }
    val displayedImage = when (selectedMode) {
        "游戏截图" -> costume.screenshotUrl ?: costume.fullImageUrl ?: costume.thumbnailUrl
        else -> costume.fullImageUrl ?: costume.thumbnailUrl ?: costume.screenshotUrl
    }
    val imageContentScale = if (selectedMode == "游戏截图") ContentScale.Fit else ContentScale.Crop
    val imageBackgroundColor = if (selectedMode == "游戏截图") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        Color.Transparent
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
                    .background(imageBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (displayedImage != null) {
                    AsyncImage(
                        model = displayedImage,
                        contentDescription = costume.name,
                        contentScale = imageContentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                CatalogQualityBadge(
                    label = costume.quality?.takeIf { it != Quality.INITIAL }?.displayName,
                    level = costume.quality?.takeIf { it != Quality.INITIAL }?.level,
                    compact = false
                )
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

            if (costume.description.isNotBlank()) {
                ExpandableCatalogDescription(
                    text = costume.description,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
                    if (costume.sources.isNotEmpty()) {
                        CatalogDetailRow(
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
                        CatalogDetailRow(
                            imageUrl = CRYSTAL_ICON_URL,
                            fallbackImageUrl = CRYSTAL_ICON_FALLBACK_URL,
                            label = "巴布洛晶核",
                            value = costume.crystalCost
                        )
                    }

                    // 基弦价格
                    if (costume.baseCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        CatalogDetailRow(
                            imageUrl = BASE_ICON_URL,
                            fallbackImageUrl = BASE_ICON_FALLBACK_URL,
                            label = "基弦",
                            value = costume.baseCost
                        )
                    }

                    // 品质
                    if (costume.quality != null && costume.quality != Quality.INITIAL) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        CatalogDetailRow(
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


