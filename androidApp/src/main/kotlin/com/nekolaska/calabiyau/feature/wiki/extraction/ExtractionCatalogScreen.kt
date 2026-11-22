package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
fun ExtractionCatalogScreen(
    kind: ExtractionCatalogKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit,
    isActive: () -> Boolean = { true }
) {
    if (kind == ExtractionCatalogKind.DROPS) {
        ExtractionDropsScreen(onBack, onOpenWikiUrl, isActive = isActive)
        return
    }
    val state = rememberLoadState(
        initial = emptyList<ExtractionEntry>(), key = kind,
        cachedFetch = { ExtractionCatalogApi.fetch(kind, cacheOnly = true) },
        cachedPrefetchDelayMs = 300L,
        fetch = { force -> ExtractionCatalogApi.fetch(kind, forceRefresh = force, allowMemoryCache = false) }
    )
    val entries = state.data
    var query by rememberSaveable(kind) { mutableStateOf("") }
    var category by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var quality by rememberSaveable(kind) { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable(kind) { mutableStateOf(ExtractionSort.DEFAULT) }
    var descending by rememberSaveable(kind) { mutableStateOf(true) }
    var valuableOnly by rememberSaveable(kind) { mutableStateOf(false) }
    var showFilters by rememberSaveable(kind) { mutableStateOf(false) }
    var selectedId by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val categories = remember(entries) { entries.map { it.filterCategory }.distinct() }
    val qualities = remember(entries) { entries.mapNotNull { e -> e.quality?.let { it to e.qualityLabel } }.distinctBy { it.first }.sortedBy { it.first } }
    val sorts = remember(entries) { ExtractionSort.entries.filter { s -> s == ExtractionSort.DEFAULT || entries.any { s in it.numericValues } } }
    LaunchedEffect(entries, state.isShowingCachedPrefetch) {
        if (entries.isNotEmpty() && !state.isShowingCachedPrefetch) {
            if (category !in categories) category = null
            if (qualities.none { it.first == quality }) quality = null
            if (sort !in sorts) sort = ExtractionSort.DEFAULT
        }
    }
    val filtered = remember(entries, query, category, quality, valuableOnly, sort, descending) {
        filterExtractionEntries(entries, query, category, quality, valuableOnly, sort, descending)
    }
    val focus = LocalFocusManager.current
    val conditions = listOfNotNull(
        category,
        qualities.find { it.first == quality }?.second,
        "仅贵重物品".takeIf { valuableOnly },
        sort.takeUnless { it == ExtractionSort.DEFAULT }?.let {
            "${it.label}${if (descending) "降序" else "升序"}"
        }
    )
    fun clearFilters() { category = null; quality = null; valuableOnly = false }
    fun resetOptions() { clearFilters(); sort = ExtractionSort.DEFAULT; descending = true }
    val wikiUrl = extractionArticleUrl(kind.pageName)
    val subject = if (kind == ExtractionCatalogKind.LOOT) "物资" else "配件"
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(kind.title, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) },
            actions = {
                RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(wikiUrl = wikiUrl, onOpenWikiUrl = onOpenWikiUrl, contentDescription = "查看 Wiki 原文")
            }
        )
    }) { padding ->
        ApiResourceContent(
            state,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            loading = { ExtractionCatalogSkeleton(kind = kind, modifier = it) }
        ) {
            LazyVerticalGrid(
                columns = extractionItemGridCells(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchBar(
                            keyword = query,
                            onKeywordChange = { query = it },
                            onClear = { query = "" },
                            isSearching = false,
                            placeholder = "搜索$subject"
                        )
                        DropFilterBar(
                            summary = if (filtered.size == entries.size) "${entries.size} 项$subject"
                                else "${filtered.size} / ${entries.size} 项$subject",
                            label = "筛选与排序",
                            activeFilter = conditions.isNotEmpty(),
                            activeCount = conditions.size,
                            onClick = { focus.clearFocus(); showFilters = true }
                        )
                        if (conditions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                conditions.forEach { condition ->
                                    Surface(
                                        shape = smoothCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            condition,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    ExtractionEmptyResults(
                        message = if (entries.isEmpty()) "暂无$subject" else "没有符合条件的$subject",
                        onClearQuery = if (query.isNotEmpty()) ({ query = "" }) else null,
                        onClearFilters = if (category != null || quality != null || valuableOnly) ::clearFilters else null
                    )
                }
                gridItems(filtered, key = { it.id }) { entry ->
                    ExtractionItemCard(kind, entry) { focus.clearFocus(); selectedId = entry.id }
                }
            }
        }
    }
    if (showFilters && isActive()) ExtractionFilterSheet(
        kind, categories, qualities, sorts, category, quality, valuableOnly, sort, descending,
        onCategory = { category = it }, onQuality = { quality = it }, onValuable = { valuableOnly = it },
        onSort = { sort = it }, onDescending = { descending = it },
        onClear = if (conditions.isNotEmpty()) ::resetOptions else null,
        onDismiss = { showFilters = false }
    )
    if (isActive()) entries.firstOrNull { it.id == selectedId }?.let { entry ->
        ExtractionItemDetailSheet(entry, kind) { selectedId = null }
    }
}

@Composable
internal fun extractionItemGridCells() = GridCells.Adaptive(120.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f))

/** Square in a compact cell; cap only the height if large text leaves the grid with one wide column. */
internal fun Modifier.extractionItemImageSlot() = fillMaxWidth().layout { measurable, constraints ->
    val height = minOf(constraints.maxWidth, 164.dp.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    val image = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(image.width, image.height) { image.placeRelative(0, 0) }
}

@Composable
internal fun ColumnScope.ExtractionItemImageHeader(
    accent: Color,
    artwork: @Composable () -> Unit,
    badge: @Composable () -> Unit
) {
    Spacer(Modifier.fillMaxWidth().height(2.dp).background(accent))
    Box(Modifier.extractionItemImageSlot().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        artwork()
        Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { badge() }
    }
}

@Composable
private fun ExtractionItemCard(kind: ExtractionCatalogKind, entry: ExtractionEntry, onClick: () -> Unit) {
    val quality = entry.quality?.takeIf { it in 1..5 } ?: -1
    val qualityColor = extractionDropQualityColor(quality)
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        ExtractionItemImageHeader(
            accent = qualityColor,
            artwork = { ExtractionCatalogArtwork(entry, Modifier.fillMaxSize().padding(10.dp)) }
        ) {
            if (entry.qualityLabel.isNotBlank()) {
                ExtractionDropQualityBadge(label = entry.qualityLabel, code = quality, surfaceBacked = true)
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (kind == ExtractionCatalogKind.ATTACHMENTS) {
                if (entry.category.isNotBlank()) {
                    Surface(
                        shape = smoothCornerShape(6.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Text(
                            entry.category,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                val valueText = entry.fields.firstOrNull { it.label == "价值" && it.value.isNotBlank() }?.value
                if (valueText != null) {
                    Surface(
                        shape = smoothCornerShape(6.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "价值",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                valueText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExtractionCatalogArtwork(entry: ExtractionEntry, modifier: Modifier = Modifier, onReady: ((Boolean) -> Unit)? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        var loaded by remember(entry.imageUrl) { mutableStateOf(false) }
        if (!loaded) Icon(Icons.Outlined.Inventory2, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!entry.imageUrl.isNullOrBlank()) AsyncImage(
            model = entry.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onLoading = { loaded = false; onReady?.invoke(false) },
            onSuccess = { loaded = true; onReady?.invoke(true) },
            onError = { loaded = false; onReady?.invoke(false) }
        )
    }
}
