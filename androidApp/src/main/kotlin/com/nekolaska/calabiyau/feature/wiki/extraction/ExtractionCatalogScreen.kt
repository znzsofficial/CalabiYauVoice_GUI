package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import util.wikiPathEncode

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
    LaunchedEffect(entries) {
        if (entries.isNotEmpty()) {
            if (category !in categories) category = null
            if (qualities.none { it.first == quality }) quality = null
            if (sort !in sorts) sort = ExtractionSort.DEFAULT
        }
    }
    val filtered = remember(entries, query, category, quality, valuableOnly, sort, descending) {
        filterExtractionEntries(entries, query, category, quality, valuableOnly, sort, descending)
    }
    val wikiUrl = "https://wiki.biligame.com/klbq/${kind.pageName.wikiPathEncode()}"
    Scaffold(topBar = {
        TopAppBar(title = { Text(kind.title, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) },
            actions = {
                RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(wikiUrl = wikiUrl, onOpenWikiUrl = onOpenWikiUrl)
            })
    }) { padding ->
        ApiResourceContent(state, modifier = Modifier.padding(padding),
            loading = { ExtractionCatalogSkeleton(kind = kind, modifier = it) }) {
            val header: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("弦溯战线专用图鉴", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                        isSearching = false, placeholder = "搜索名称、描述或效果…")
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { showFilters = true }) {
                            Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("筛选与排序")
                        }
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                            Text("${filtered.size} / ${entries.size} 项", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(listOfNotNull(category, qualities.find { it.first == quality }?.second,
                        if (valuableOnly) "贵重" else null, sort.takeIf { it != ExtractionSort.DEFAULT }?.let { "${it.label} · ${if (descending) "降序" else "升序"}" })
                        .joinToString(" · ").ifBlank { "全部条目 · 默认顺序" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "controls", span = { GridItemSpan(maxLineSpan) }) { header() }
                    if (filtered.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyCatalogResults { query = ""; category = null; quality = null; valuableOnly = false }
                    }
                    gridItems(filtered, key = { it.id }) { entry ->
                        CatalogGridCard(name = entry.name, imageUrl = entry.imageUrl,
                            qualityLabel = entry.qualityLabel, qualityLevel = entry.quality,
                            fallbackIcon = Icons.Outlined.Inventory2, contentScale = ContentScale.Fit, imagePadding = 8.dp,
                            onClick = { selectedId = entry.id })
                    }
            }
        }
    }
    if (showFilters) ExtractionFilterSheet(kind, categories, qualities, sorts, category, quality, valuableOnly, sort, descending,
        onCategory = { category = it }, onQuality = { quality = it }, onValuable = { valuableOnly = it }, onSort = { sort = it },
        onDescending = { descending = it }, onDismiss = { showFilters = false })
    entries.firstOrNull { it.id == selectedId }?.let { entry -> ExtractionDetailSheet(entry, query) { selectedId = null } }
}

@Composable
private fun EmptyCatalogResults(onClear: () -> Unit) {
    CatalogEmptyResults(onClear = onClear)
}
