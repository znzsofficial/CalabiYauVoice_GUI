package com.nekolaska.calabiyau.feature.wiki.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nekolaska.calabiyau.feature.wiki.item.api.ItemCatalogApi
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import androidx.compose.foundation.lazy.items as lazyItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemCatalogScreen(onBack: () -> Unit) {
    val state = rememberLoadState(
        initial = emptyList<ItemInfo>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { ItemCatalogApi.fetch(cacheOnly = true) },
        fetch = { force -> ItemCatalogApi.fetch(forceRefresh = force) }
    )
    val allItems = state.data
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
                title = { Text("道具图鉴", fontWeight = FontWeight.Bold) },
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
            loading = { mod -> ItemCatalogSkeleton(mod) }
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
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的道具", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    itemsIndexed(
                        filteredItems,
                        key = { _, item -> "${item.category}|${item.name}|${item.iconUrl.orEmpty()}" }
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
private fun ItemCatalogSkeleton(modifier: Modifier = Modifier) {
    CatalogGridSkeleton(modifier = modifier, showSelector = false, chipCount = 7)
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

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
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
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("选择分类", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = smoothCornerShape(20.dp),
                    placeholder = { Text("搜索分类") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item(key = "__all") {
                        ItemCategoryRow(
                            label = "全部分类",
                            selected = selectedCategory == null,
                            onClick = {
                                onSelectedCategoryChange(null)
                                showSheet = false
                            }
                        )
                    }
                    lazyItems(filteredCategories, key = { it }) { category ->
                        ItemCategoryRow(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = {
                                onSelectedCategoryChange(category)
                                showSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCategoryRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(smoothCornerShape(16.dp)).clickable(onClick = onClick),
        shape = smoothCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
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


