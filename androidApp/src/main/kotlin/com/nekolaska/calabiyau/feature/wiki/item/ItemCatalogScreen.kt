package com.nekolaska.calabiyau.feature.wiki.item

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogDetailRow
import com.nekolaska.calabiyau.core.ui.CatalogDetailSheet
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.CatalogGridCard
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.catalogKeys
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.item.api.ItemCatalogApi
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemCatalogPlatform
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import androidx.compose.foundation.lazy.items as lazyItems

internal fun keyedCatalogItems(items: List<ItemInfo>): Map<String, ItemInfo> =
    catalogKeys(items) { "${it.category}:${it.name}:${it.qualityName}" }.zip(items).toMap()

@Composable
fun ItemCatalogScreen(platform: ItemCatalogPlatform, onBack: () -> Unit) {
    key(platform.name) { ItemCatalogContent(platform, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCatalogContent(platform: ItemCatalogPlatform, onBack: () -> Unit) {
    val state = rememberLoadState(
        initial = emptyList<ItemInfo>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { ItemCatalogApi.fetch(platform, cacheOnly = true) },
        fetch = { force -> ItemCatalogApi.fetch(platform, forceRefresh = force, allowMemoryCache = false) }
    )
    val allItems = state.data
    val itemsByKey = remember(allItems) { keyedCatalogItems(allItems) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedQualityLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    val selectedQuality = selectedQualityLevel?.let(Quality::fromLevel)

    val categories = remember(allItems) { allItems.map { it.category }.distinct().sorted() }
    val filteredItems = remember(allItems, selectedCategory, selectedQuality, searchQuery) {
        itemsByKey.entries.filter { (_, item) ->
            (selectedCategory == null || item.category == selectedCategory) &&
                    (selectedQuality == null || item.quality == selectedQuality) &&
                    (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) ||
                            item.category.contains(searchQuery, ignoreCase = true) ||
                            item.description.contains(searchQuery, ignoreCase = true))
        }.sortedWith(
            compareByDescending<Map.Entry<String, ItemInfo>> { it.value.quality?.level ?: 0 }
                .thenBy { it.value.category }
                .thenBy { it.value.name }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(platform.title, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    if (allItems.isNotEmpty()) {
                        Surface(
                            shape = AppShapes.capsule,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                text = filteredItems.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> ItemCatalogSkeleton(platform, mod) }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ItemCatalogNotice(platform)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchBar(
                                modifier = Modifier.weight(1f),
                                keyword = searchQuery,
                                onKeywordChange = { searchQuery = it },
                                onClear = { searchQuery = "" },
                                isSearching = false,
                                placeholder = "搜索道具名称、分类或描述…"
                            )
                            FilledTonalIconButton(
                                onClick = { showCategorySheet = true },
                                enabled = categories.isNotEmpty(),
                                modifier = Modifier.size(48.dp),
                                shape = smoothCornerShape(16.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (selectedCategory != null) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (selectedCategory != null) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            ) {
                                BadgedBox(badge = { if (selectedCategory != null) Badge() }) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        contentDescription = "筛选分类，当前${selectedCategory ?: "全部分类"}"
                                    )
                                }
                            }
                        }
                        ItemFilterChipsRow(
                            selectedCategory = selectedCategory,
                            onClearCategory = { selectedCategory = null },
                            selectedQuality = selectedQuality,
                            onQualitySelected = { selectedQualityLevel = it?.level }
                        )
                    }
                }

                if (filteredItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogEmptyResults(message = "没有匹配的道具", onClear = {
                            searchQuery = ""; selectedCategory = null; selectedQualityLevel = null
                        })
                    }
                } else {
                    items(filteredItems, key = { it.key }) { (key, item) ->
                        ItemCard(item = item, onClick = { detailKey = key })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showCategorySheet) {
        var query by remember { mutableStateOf("") }
        val filteredCategories = remember(categories, query) {
            val keyword = query.trim()
            categories.filter { keyword.isBlank() || it.contains(keyword, ignoreCase = true) }
        }
        WikiSelectionSheet(
            title = "选择分类",
            onDismiss = { showCategorySheet = false },
            query = query,
            onQueryChange = { query = it },
            searchPlaceholder = "搜索分类"
        ) {
            item(key = "all") {
                SelectionOptionRow(
                    label = "全部分类",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null; showCategorySheet = false }
                )
            }
            lazyItems(filteredCategories, key = { "category:$it" }) { category ->
                SelectionOptionRow(
                    label = category,
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category; showCategorySheet = false }
                )
            }
        }
    }

    detailKey?.let { itemsByKey[it] }?.let { item ->
        ItemDetailSheet(item = item, onDismiss = { detailKey = null })
    }
}

@Composable
private fun ItemCatalogSkeleton(platform: ItemCatalogPlatform, modifier: Modifier = Modifier) = ShimmerGroup {
    val cardShape = smoothCornerShape(18.dp)
    val labelMinHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ItemCatalogNotice(platform)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShimmerBox(
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = smoothCornerShape(AppShapes.sheetRadius)
                    )
                    ShimmerBox(
                        Modifier.size(48.dp),
                        shape = smoothCornerShape(16.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val widths = listOf(88.dp, 64.dp, 64.dp, 64.dp, 64.dp)
                    widths.forEach { w ->
                        ShimmerBox(Modifier.width(w).height(32.dp), shape = AppShapes.capsule)
                    }
                }
            }
        }
        items(12) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = cardShape
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .heightIn(min = labelMinHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerBox(Modifier.fillMaxWidth(0.78f).height(12.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.52f).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCatalogNotice(platform: ItemCatalogPlatform) {
    if (platform == ItemCatalogPlatform.MOBILE) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = smoothCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "移动端独有道具，双端共有道具收录于 PC 图鉴",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ItemFilterChipsRow(
    selectedCategory: String?,
    onClearCategory: () -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedCategory != null) {
            InputChip(
                selected = true,
                onClick = onClearCategory,
                label = { Text(selectedCategory, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清除分类筛选",
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = AppShapes.capsule,
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        FilterChip(
            selected = selectedQuality == null,
            onClick = { onQualitySelected(null) },
            shape = AppShapes.capsule,
            label = { Text("全部稀有度", maxLines = 1) }
        )
        Quality.entries.sortedByDescending { it.level }.forEach { quality ->
            val qColor = catalogQualityColor(quality.level)
            FilterChip(
                selected = selectedQuality == quality,
                onClick = { onQualitySelected(if (selectedQuality == quality) null else quality) },
                shape = AppShapes.capsule,
                label = { Text(quality.displayName, maxLines = 1) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(qColor, CircleShape)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = qColor.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun ItemCard(item: ItemInfo, onClick: () -> Unit) {
    CatalogGridCard(
        name = item.name,
        onClick = onClick,
        imageUrl = item.iconUrl,
        qualityLabel = item.quality?.displayName,
        qualityLevel = item.quality?.level,
        fallbackIcon = Icons.Outlined.Inventory2,
        contentScale = ContentScale.Fit,
        imagePadding = 14.dp
    )
}

@Composable
private fun ItemDetailSheet(item: ItemInfo, onDismiss: () -> Unit) {
    val qColor = item.quality?.let { catalogQualityColor(it.level) } ?: MaterialTheme.colorScheme.outline
    CatalogDetailSheet(
        title = item.name,
        subtitle = item.category,
        onDismiss = onDismiss,
        qualityLabel = item.quality?.displayName,
        qualityLevel = item.quality?.level,
        description = item.description,
        descriptionCollapsedLines = 5,
        descriptionExpandedMaxHeight = 260.dp,
        heroHeight = 220.dp,
        imageContent = if (item.iconUrl.isNullOrBlank()) null else {
            {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                )
            }
        }
    ) {
        CatalogDetailRow(icon = Icons.Outlined.Category, label = "分类", value = item.category)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        CatalogDetailRow(
            icon = Icons.Outlined.Star,
            label = "稀有度",
            value = item.quality?.displayName ?: item.qualityName,
            valueColor = qColor
        )
    }
}


