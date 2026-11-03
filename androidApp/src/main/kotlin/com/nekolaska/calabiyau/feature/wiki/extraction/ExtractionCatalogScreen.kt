package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import util.wikiPathEncode

@Composable
fun ExtractionCatalogScreen(kind: ExtractionCatalogKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit) {
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
    var dropView by rememberSaveable(kind) { mutableStateOf(ExtractionDropView.SOURCES) }
    val viewEntries = remember(entries, kind, dropView) {
        if (kind == ExtractionCatalogKind.DROPS) extractionEntriesForView(entries, dropView) else entries
    }
    val categories = remember(viewEntries) { viewEntries.map { it.filterCategory }.distinct() }
    val qualities = remember(entries) { entries.mapNotNull { e -> e.quality?.let { it to e.qualityLabel } }.distinctBy { it.first }.sortedBy { it.first } }
    val sorts = remember(entries) { ExtractionSort.entries.filter { s -> s == ExtractionSort.DEFAULT || entries.any { s in it.numericValues } } }
    LaunchedEffect(entries, dropView) {
        if (entries.isNotEmpty()) {
            if (category !in categories) category = null
            if (qualities.none { it.first == quality }) quality = null
            if (sort !in sorts) sort = ExtractionSort.DEFAULT
        }
    }
    val filtered = remember(viewEntries, query, category, quality, valuableOnly, sort, descending) {
        filterExtractionEntries(viewEntries, query, category, quality, valuableOnly, sort, descending)
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
                    if (kind == ExtractionCatalogKind.DROPS) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExtractionDropView.entries.forEach { view ->
                                FilterChip(
                                    selected = dropView == view,
                                    onClick = { if (dropView != view) { dropView = view; category = null } },
                                    label = { Text("${view.title} ${extractionEntriesForView(entries, view).size}") }
                                )
                            }
                        }
                    }
                    SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                        isSearching = false, placeholder = if (kind == ExtractionCatalogKind.DROPS) "搜索晶源体、容器或掉落物…" else "搜索名称、描述或效果…")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { showFilters = true }) {
                            Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("筛选与排序")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${filtered.size} / ${viewEntries.size} 项", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(listOfNotNull(category, qualities.find { it.first == quality }?.second,
                        if (valuableOnly) "贵重" else null, sort.takeIf { it != ExtractionSort.DEFAULT }?.let { "${it.label} · ${if (descending) "降序" else "升序"}" })
                        .joinToString(" · ").ifBlank { "全部条目 · 默认顺序" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (kind == ExtractionCatalogKind.DROPS) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "controls") { header() }
                    if (filtered.isEmpty()) item(key = "empty") { EmptyCatalogResults { query = ""; category = null; quality = null; valuableOnly = false } }
                    items(filtered, key = { it.id }) { entry ->
                        Card(onClick = { selectedId = entry.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text((listOf(if (entry.isDropPool) "完整掉落池" else entry.category) + entry.tags).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                entry.fields.firstOrNull { it.label in setOf("掉落件数", "件数概率") }?.let {
                                    Text(it.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val preview = if (entry.dropPoolGroups.isNotEmpty()) {
                                    entry.dropPoolGroups.joinToString(" · ") { it.label }
                                } else {
                                    (entry.fields.firstOrNull { it.label == "必掉物" }
                                        ?: entry.fields.firstOrNull { it.label == "掉落池条目（完整）" })?.value
                                }
                                preview?.let {
                                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("查看掉落详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
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
    }
    if (showFilters) ExtractionFilterSheet(kind, categories, qualities, sorts, category, quality, valuableOnly, sort, descending,
        onCategory = { category = it }, onQuality = { quality = it }, onValuable = { valuableOnly = it }, onSort = { sort = it },
        onDescending = { descending = it }, onDismiss = { showFilters = false })
    entries.firstOrNull { it.id == selectedId }?.let { entry -> ExtractionDetailSheet(entry, query) { selectedId = null } }
}

@Composable
private fun EmptyCatalogResults(onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("没有匹配的条目")
        TextButton(onClick = onClear) { Text("清除搜索与筛选") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractionFilterSheet(
    kind: ExtractionCatalogKind, categories: List<String>, qualities: List<Pair<Int, String>>, sorts: List<ExtractionSort>,
    category: String?, quality: Int?, valuable: Boolean, sort: ExtractionSort, descending: Boolean,
    onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onValuable: (Boolean) -> Unit,
    onSort: (ExtractionSort) -> Unit, onDescending: (Boolean) -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("筛选与排序", style = MaterialTheme.typography.headlineSmall) }
            item {
                SimpleDropdownSelector(label = if (kind == ExtractionCatalogKind.ATTACHMENTS) "槽位" else "分类",
                    options = listOf("全部") + categories, selected = category ?: "全部", onSelected = { onCategory(it.takeUnless { it == "全部" }) })
            }
            if (qualities.isNotEmpty()) item {
                SimpleDropdownSelector(label = "稀有度", options = listOf("全部") + qualities.map { it.second },
                    selected = qualities.find { it.first == quality }?.second ?: "全部",
                    onSelected = { label -> onQuality(qualities.find { it.second == label }?.first) })
            }
            if (kind == ExtractionCatalogKind.LOOT) item {
                FilterChip(selected = valuable, onClick = { onValuable(!valuable) }, label = { Text("仅贵重物品") })
            }
            if (sorts.size > 1) item {
                SimpleDropdownSelector(label = "排序", options = sorts.map { it.label }, selected = sort.label,
                    onSelected = { label -> sorts.find { it.label == label }?.let(onSort) })
                if (sort != ExtractionSort.DEFAULT) FilterChip(selected = descending, onClick = { onDescending(!descending) }, label = { Text(if (descending) "降序" else "升序") })
            }
            item { FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractionDetailSheet(entry: ExtractionEntry, query: String, onDismiss: () -> Unit) {
    var poolQuery by rememberSaveable(entry.id) {
        mutableStateOf(query.takeIf { keyword ->
            entry.dropPoolGroups.any { group -> group.items.any { it.contains(keyword.trim(), true) } }
        }.orEmpty())
    }
    val poolGroups = remember(entry, poolQuery) { filterDropPoolGroups(entry.dropPoolGroups, poolQuery) }
    val fields = remember(entry) {
        if (entry.dropPoolGroups.isNotEmpty()) entry.fields.filterNot { it.label == "掉落池条目（完整）" } else entry.fields
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 680.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (entry.imageUrl != null) item {
                AsyncImage(model = entry.imageUrl, contentDescription = entry.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            }
            item {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text((listOf(if (entry.isDropPool) "完整掉落池" else entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.description.isNotBlank()) item { Text(entry.description, style = MaterialTheme.typography.bodyLarge) }
            items(fields.size, key = { index -> "field-$index" }) { index ->
                val field = fields[index]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(field.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(field.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (entry.dropPoolGroups.isNotEmpty()) {
                item(key = "pool-search") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("完整掉落池", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SearchBar(keyword = poolQuery, onKeywordChange = { poolQuery = it }, onSearch = {},
                            onClear = { poolQuery = "" }, isSearching = false, placeholder = "在掉落池中搜索物品…")
                    }
                }
                if (poolGroups.isEmpty()) item(key = "pool-empty") { Text("没有匹配的掉落物") }
                items(poolGroups.size, key = { "pool-group-$it" }) { index ->
                    ExtractionDropPoolSection(entryId = entry.id, group = poolGroups[index], query = poolQuery)
                }
            }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") } }
        }
    }
}
