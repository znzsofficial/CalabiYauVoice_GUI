package com.nekolaska.calabiyau.feature.wiki.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogDetailRow
import com.nekolaska.calabiyau.core.ui.CatalogDetailSheet
import com.nekolaska.calabiyau.core.ui.CatalogGridCard
import com.nekolaska.calabiyau.core.ui.CatalogGridSkeleton
import com.nekolaska.calabiyau.core.ui.QualityFilterChips
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.feature.wiki.item.api.ItemCatalogApi
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemCatalogPlatform
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import androidx.compose.foundation.lazy.items as lazyItems

@Composable
fun ItemCatalogScreen(platform: ItemCatalogPlatform, onBack: () -> Unit) {
    val state = rememberLoadState(
        initial = emptyList<ItemInfo>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { ItemCatalogApi.fetch(platform, cacheOnly = true) },
        fetch = { force -> ItemCatalogApi.fetch(platform, forceRefresh = force, allowMemoryCache = false) }
    )
    val allItems = state.data
    val itemKeys = remember(allItems) {
        val keys = com.nekolaska.calabiyau.core.ui.catalogKeys(allItems) { "${it.category}:${it.name}:${it.qualityName}" }
        allItems.zip(keys).toMap()
    }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedQualityLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedQuality = selectedQualityLevel?.let(Quality::fromLevel)

    val categories = remember(allItems) { allItems.map { it.category }.distinct().sorted() }
    val filteredItems = remember(allItems, selectedCategory, selectedQuality, searchQuery) {
        allItems.filter { item ->
            (selectedCategory == null || item.category == selectedCategory) &&
                    (selectedQuality == null || item.quality == selectedQuality) &&
                    (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) ||
                            item.category.contains(searchQuery, ignoreCase = true) ||
                            item.description.contains(searchQuery, ignoreCase = true))
        }.sortedWith(
            compareByDescending<ItemInfo> { it.quality?.level ?: 0 }
                .thenBy { it.category }
                .thenBy { it.name }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(platform.title, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    if (allItems.isNotEmpty()) {
                        Text(
                            text = filteredItems.size.toString(),
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
            loading = { mod -> ItemCatalogSkeleton(platform, mod) }
        ) {
            var selectedItem by remember { mutableStateOf<ItemInfo?>(null) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        ItemCatalogNotice(platform)
                        SearchBar(
                            keyword = searchQuery,
                            onKeywordChange = { searchQuery = it },
                            onSearch = {},
                            onClear = { searchQuery = "" },
                            isSearching = false,
                            placeholder = "搜索道具名称、分类或描述…",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ItemFilterBar(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },
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
                    itemsIndexed(
                        filteredItems,
                        key = { _, item -> checkNotNull(itemKeys[item]) }
                    ) { _, item ->
                        ItemCard(item = item, onClick = { selectedItem = item })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
                }
            }

            selectedItem?.let { item ->
                ItemDetailSheet(item = item, onDismiss = { selectedItem = null })
            }
        }
    }
}

@Composable
private fun ItemCatalogSkeleton(platform: ItemCatalogPlatform, modifier: Modifier = Modifier) {
    CatalogGridSkeleton(
        modifier = modifier,
        minCellSize = 108.dp,
        selectorLabel = "按分类筛选",
        qualityLabel = "按稀有度筛选",
        chipLabels = listOf("全部稀有度") + Quality.entries.sortedByDescending { it.level }.map { it.displayName },
        beforeSearch = { ItemCatalogNotice(platform) }
    )
}

@Composable
private fun ItemCatalogNotice(platform: ItemCatalogPlatform) {
    if (platform == ItemCatalogPlatform.MOBILE) {
        Text(
            "仅收录移动端独有道具；双端共有道具请查看 PC 道具图鉴。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ItemFilterBar(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("按分类筛选", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ItemCategorySelector(
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectedCategoryChange = onCategorySelected
        )

        Text("按稀有度筛选", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QualityFilterChips(
            selectedLevel = selectedQuality?.level,
            levels = Quality.entries.sortedByDescending { it.level }.map { it.level to it.displayName },
            onSelectedLevelChange = { level -> onQualitySelected(level?.let(Quality::fromLevel)) },
            allLabel = "全部稀有度",
            colorForLevel = { catalogQualityColor(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCategorySelector(
    categories: List<String>,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showSheet = true },
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = smoothCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedCategory ?: "全部分类",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSheet) {
        var query by remember { mutableStateOf("") }
        val filteredCategories = remember(categories, query) {
            val keyword = query.trim()
            categories.filter { keyword.isBlank() || it.contains(keyword, ignoreCase = true) }
        }

        WikiSelectionSheet(title = "选择分类", onDismiss = { showSheet = false }, query = query,
            onQueryChange = { query = it }, searchPlaceholder = "搜索分类") {
            item(key = "all") {
                SelectionOptionRow(label = "全部分类", selected = selectedCategory == null,
                    onClick = { onSelectedCategoryChange(null); showSheet = false })
            }
            lazyItems(filteredCategories, key = { "category:$it" }) { category ->
                SelectionOptionRow(label = category, selected = selectedCategory == category,
                    onClick = { onSelectedCategoryChange(category); showSheet = false })
            }
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
        imageContent = {
            if (!item.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(44.dp)
                )
            }
        }
    ) {
        CatalogDetailRow(icon = Icons.Outlined.Category, label = "分类", value = item.category)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        CatalogDetailRow(
            icon = Icons.Outlined.Star,
            label = "稀有度",
            value = item.quality?.displayName ?: item.qualityName,
            valueColor = qColor
        )
    }
}


