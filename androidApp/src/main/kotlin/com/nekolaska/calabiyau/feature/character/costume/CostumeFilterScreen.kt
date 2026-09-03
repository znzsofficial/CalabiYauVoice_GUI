package com.nekolaska.calabiyau.feature.character.costume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogDetailRow
import com.nekolaska.calabiyau.core.ui.CatalogDetailSheet
import com.nekolaska.calabiyau.core.ui.CatalogGridCard
import com.nekolaska.calabiyau.core.ui.CatalogGridSkeleton
import com.nekolaska.calabiyau.core.ui.CatalogPreviewImage
import com.nekolaska.calabiyau.core.ui.QualityFilterChips
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.feature.character.components.CharacterSelector
import com.nekolaska.calabiyau.feature.character.components.rememberCharacterSelectorOptions
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.CostumeInfo
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.Quality

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

@Composable
private fun CostumeDetailSheet(
    costume: CostumeInfo,
    onDismiss: () -> Unit
) {
    val qColor = costume.quality?.let { catalogQualityColor(it.level) } ?: MaterialTheme.colorScheme.outline
    val quality = costume.quality?.takeIf { it != Quality.INITIAL }
    val screenshotBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val images = buildList {
        val portrait = costume.fullImageUrl ?: costume.thumbnailUrl
        if (!portrait.isNullOrBlank()) {
            add(CatalogPreviewImage("立绘", portrait))
        }
        if (!costume.screenshotUrl.isNullOrBlank()) {
            add(
                CatalogPreviewImage(
                    label = "游戏截图",
                    url = costume.screenshotUrl,
                    contentScale = ContentScale.Fit,
                    background = screenshotBackground
                )
            )
        }
    }
    CatalogDetailSheet(
        title = costume.name,
        subtitle = costume.character,
        onDismiss = onDismiss,
        images = images,
        qualityLabel = quality?.displayName,
        qualityLevel = quality?.level,
        description = costume.description
    ) {
        if (costume.sources.isNotEmpty()) {
            CatalogDetailRow(
                icon = Icons.Outlined.ShoppingBag,
                label = "获取方式",
                value = costume.sources.joinToString("、")
            )
        }
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
        if (quality != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            CatalogDetailRow(
                icon = Icons.Outlined.Star,
                label = "品质",
                value = quality.displayName,
                valueColor = qColor
            )
        }
    }
}


