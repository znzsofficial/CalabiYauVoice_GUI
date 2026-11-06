package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.extractionContainersApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import util.wikiPathEncode

private enum class DropTab(val label: String) {
    SOURCES("掉落来源"), CONTAINERS("完整池"), ITEMS("物品反查"), REFERENCE("参考资料")
}

@Composable
internal fun ExtractionDropsScreen(onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(DropTab.SOURCES) }
    var selectedSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedContainerId by rememberSaveable { mutableStateOf<String?>(null) }
    var linkedPool by rememberSaveable { mutableStateOf(false) }
    var itemName by rememberSaveable { mutableStateOf<String?>(null) }
    var itemQuality by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogRequested by remember { mutableStateOf(false) }
    var containersRequested by remember { mutableStateOf(false) }
    LaunchedEffect(tab, linkedPool) {
        if (tab == DropTab.SOURCES || tab == DropTab.REFERENCE) catalogRequested = true
        if (tab == DropTab.CONTAINERS || tab == DropTab.ITEMS || linkedPool) containersRequested = true
    }
    // Requests stay mounted after first use. Search/filter/tab edits never become request keys.
    val catalog = rememberLoadState(initial = emptyList<ExtractionEntry>(), enabled = catalogRequested,
        cachedFetch = { ExtractionCatalogApi.fetch(ExtractionCatalogKind.DROPS, cacheOnly = true) },
        fetch = { force -> ExtractionCatalogApi.fetch(ExtractionCatalogKind.DROPS, forceRefresh = force, allowMemoryCache = false) })
    val containers = rememberLoadState(initial = emptyList<ExtractionEntry>(), enabled = containersRequested,
        cachedFetch = { extractionContainersApi.fetch(cacheOnly = true) },
        fetch = { force -> extractionContainersApi.fetch(forceRefresh = force, allowMemoryCache = false) })
    val tableActive = tab == DropTab.CONTAINERS || tab == DropTab.ITEMS
    val active = if (tableActive) containers else catalog
    val page = if (tableActive) ExtractionCatalogRemoteSource.CONTAINERS_PAGE else ExtractionCatalogKind.DROPS.pageName
    val uiState = rememberSaveableStateHolder()
    val detailState = rememberSaveableStateHolder()
    val lookup = remember(containers.data) { buildDropLookup(containers.data) }
    fun findItem(item: ExtractionDropItem) {
        selectedContainerId = null
        selectedSourceId = null
        linkedPool = false
        itemName = item.name
        itemQuality = item.qualityLabel
        tab = DropTab.ITEMS
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("掉落图鉴", fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) },
            actions = {
                RefreshActionButton(onClick = { active.reload(forceRefresh = true) }, enabled = !active.isLoading)
                OpenWikiActionButton(wikiUrl = "https://wiki.biligame.com/klbq/${page.wikiPathEncode()}", onOpenWikiUrl = onOpenWikiUrl)
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Kept outside loading/error content: a failed secondary page must not trap navigation.
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropTab.entries.forEach { value ->
                    FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(value.label) })
                }
            }
            uiState.SaveableStateProvider(tab.name) {
                ApiResourceContent(active, modifier = Modifier.weight(1f),
                    loading = { ExtractionCatalogSkeleton(ExtractionCatalogKind.DROPS, it) }) {
                    if (tab == DropTab.ITEMS) {
                        val lookupState = rememberSaveableStateHolder()
                        val selectionKey = itemName?.let { "${it.length}:$it:${itemQuality.orEmpty()}" } ?: "items"
                        lookupState.SaveableStateProvider(selectionKey) {
                            ExtractionDropLookupList(lookup, itemName, itemQuality,
                                onChooseItem = { itemName = it?.name; itemQuality = it?.qualityLabel },
                                onSource = { selectedContainerId = it.id },
                                errorMessage = active.error?.message)
                        }
                    } else {
                        var reference by rememberSaveable { mutableStateOf(ExtractionDropView.POOLS) }
                        LaunchedEffect(catalog.data) {
                            if (catalog.data.isNotEmpty() && extractionEntriesForView(catalog.data, reference).isEmpty())
                                reference = ExtractionDropView.POOLS
                        }
                        val entries = when (tab) {
                            DropTab.SOURCES -> extractionEntriesForView(catalog.data, ExtractionDropView.SOURCES)
                            DropTab.CONTAINERS -> containers.data
                            else -> extractionEntriesForView(catalog.data, reference)
                        }
                        val sectionState = rememberSaveableStateHolder()
                        sectionState.SaveableStateProvider(if (tab == DropTab.REFERENCE) reference.name else "list") {
                            ExtractionDropBrowser(entries, containerMode = tab == DropTab.CONTAINERS,
                                errorMessage = active.error?.message,
                                extraControls = {
                                    if (tab == DropTab.REFERENCE) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(ExtractionDropView.POOLS, ExtractionDropView.STAGES).forEach { view ->
                                            if (extractionEntriesForView(catalog.data, view).isNotEmpty()) FilterChip(
                                                selected = reference == view, onClick = { reference = view }, label = { Text(view.title) })
                                        }
                                    }
                                }, onSelect = { entry ->
                                    if (tab == DropTab.CONTAINERS) selectedContainerId = entry.id else selectedSourceId = entry.id
                                })
                        }
                    }
                }
            }
        }
    }
    val source = catalog.data.firstOrNull { it.id == selectedSourceId }
    val container = if (linkedPool && source != null) linkedDropContainer(source, containers.data)
        else containers.data.firstOrNull { it.id == selectedContainerId }
    when {
        linkedPool && container == null -> WikiContentSheet(onDismiss = { linkedPool = false }) {
            item("linked-title") {
                Text(source?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("容器编号 ${source?.containerId.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
            }
            if (containers.isLoading) {
                items(5) { SkeletonTextLine(widthFraction = 0.8f) }
                item("loading") { Text("正在读取完整掉落表…", style = MaterialTheme.typography.bodyMedium) }
            } else {
                item("failure") {
                    Text(containers.error?.message ?: "当前完整表没有唯一对应的容器记录。可浏览完整表或查看 Wiki。")
                    TextButton(onClick = { containers.reload(forceRefresh = true) }) { Text("重试") }
                    TextButton(onClick = { linkedPool = false; selectedSourceId = null; tab = DropTab.CONTAINERS }) { Text("浏览完整表") }
                }
            }
            item("back") { TextButton(onClick = { linkedPool = false }) { Text("返回来源详情") } }
        }
        container != null -> detailState.SaveableStateProvider(
            if (tab == DropTab.ITEMS && itemName != null) "${container.id}:lookup:${itemName!!.length}:$itemName:$itemQuality" else container.id
        ) {
            ExtractionDetailSheet(container, if (tab == DropTab.ITEMS) itemName.orEmpty() else "", containerDetails = true, onFindItem = ::findItem,
                initialPoolQuality = itemQuality.takeIf { tab == DropTab.ITEMS },
                offlineAgeMs = containers.cacheAgeMs.takeIf { containers.isOffline && !containers.isShowingCachedPrefetch },
                poolError = containers.error?.message, poolIsLoading = containers.isLoading || containers.isShowingCachedPrefetch,
                onRefreshPool = { containers.reload(forceRefresh = true) },
                onDismiss = { selectedContainerId = null; linkedPool = false })
        }
        source != null -> detailState.SaveableStateProvider(source.id) {
            ExtractionDetailSheet(source, "", onViewCompletePool = if (source.type == ExtractionEntryType.DROP_SOURCE) {
                {
                    if (source.containerId != null) linkedPool = true
                    else { selectedSourceId = null; tab = DropTab.CONTAINERS }
                }
            } else null, onDismiss = { selectedSourceId = null })
        }
    }
}

@Composable
private fun ExtractionDropBrowser(
    entries: List<ExtractionEntry>, containerMode: Boolean, errorMessage: String?,
    extraControls: @Composable () -> Unit, onSelect: (ExtractionEntry) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var hideZero by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    val categories = remember(entries) { entries.map { it.filterCategory }.distinct() }
    val filtered = remember(entries, query, category, hideZero) {
        entries.filter { it.matches(query) && (category == null || it.filterCategory == category) &&
            (!hideZero || it.dropCountState != DropCountState.ZERO) }
    }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && category !in categories) category = null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                extraControls()
                Text(if (containerMode) "逐来源完整池" else "浏览掉落资料", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                    isSearching = false, placeholder = if (containerMode) "搜索容器或池内物品…" else "搜索来源、材料或阶段…")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { filters = true }) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(category ?: "全部分类")
                    }
                    if (containerMode) FilterChip(selected = hideZero, onClick = { hideZero = !hideZero }, label = { Text("排除零件数") })
                }
                Text("${filtered.size} / ${entries.size} 项", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (filtered.isEmpty()) item("empty") {
            CatalogEmptyResults(onClear = { query = ""; category = null; hideZero = false })
        }
        items(filtered, key = { it.id }, contentType = { it.type }) { entry ->
            ExtractionDropCard(entry = entry, onClick = { onSelect(entry) })
        }
    }
    if (filters) WikiSelectionSheet(title = "来源分类", onDismiss = { filters = false }) {
        item("all") { SelectionOptionRow(label = "全部分类", selected = category == null, onClick = { category = null; filters = false }) }
        items(categories, key = { it }) { value ->
            SelectionOptionRow(label = value, selected = category == value, onClick = { category = value; filters = false })
        }
    }
}
