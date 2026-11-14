package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.extractionContainersApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.rememberDropArtwork
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.SharedJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import util.wikiPathEncode

private val DropNavigationSaver = Saver<DropNavigation, String>(
    save = { SharedJson.encodeToString(it) },
    restore = { encoded -> runCatching { SharedJson.decodeFromString<DropNavigation>(encoded) }
        .getOrNull()?.takeIf { it.history.isNotEmpty() } ?: DropNavigation() }
)

@Composable
internal fun ExtractionDropsScreen(
    onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit, isActive: () -> Boolean = { true }
) {
    var navigation by rememberSaveable(stateSaver = DropNavigationSaver) { mutableStateOf(DropNavigation()) }
    val displayedNavigation = navigation
    val location = displayedNavigation.current
    fun navigate(change: (DropNavigation) -> DropNavigation) {
        if (isActive() && navigation === displayedNavigation) navigation = change(navigation)
    }
    fun goBack() {
        // A dismiss animation or a second tap must not pop the next location in the same frame.
        if (!isActive() || navigation !== displayedNavigation) return
        if (navigation.canGoBack) navigation = navigation.back() else onBack()
    }

    // Declared before the sheets: a modal's own back handler retains priority.
    BackHandler(enabled = isActive() && navigation.canGoBack) { goBack() }
    val containers = rememberLoadState(initial = emptyList<ExtractionEntry>(),
        cachedFetch = { extractionContainersApi.fetch(cacheOnly = true) },
        fetch = { force -> extractionContainersApi.fetch(forceRefresh = force, allowMemoryCache = false) })
    val containersPending = containers.isLoading || containers.isShowingCachedPrefetch
    var catalogRequested by remember { mutableStateOf(false) }
    // Prefetch source notes once the first core request settles (success or failure), before any
    // optional image network work. A cached prefetch alone does not mean the request has settled.
    val catalogRequired = location.task == DropTask.SOURCES || location.reference != null || location.hasDetail || !containersPending
    val catalogEnabled = catalogRequested || catalogRequired
    LaunchedEffect(catalogRequired) { if (catalogRequired) catalogRequested = true }
    val catalog = rememberLoadState(initial = emptyList<ExtractionEntry>(), enabled = catalogEnabled,
        cachedFetch = { ExtractionCatalogApi.fetch(ExtractionCatalogKind.DROPS, cacheOnly = true) },
        fetch = { force -> ExtractionCatalogApi.fetch(ExtractionCatalogKind.DROPS, forceRefresh = force, allowMemoryCache = false) })
    val catalogPending = catalogEnabled && (catalog.isLoading || catalog.isShowingCachedPrefetch)
    // Disk artwork can appear early. LoadState starts loading=true even while disabled, so this
    // also closes the first enabled frame before its reload effect actually starts.
    val artwork = rememberDropArtwork(enabled = isActive() && containers.data.isNotEmpty(), catalogEntries = catalog.data,
        allowNetwork = !containersPending && catalogRequested && !catalog.isLoading && !catalog.isShowingCachedPrefetch)
    val active = if (location.reference != null) catalog else containers
    val page = if (location.reference != null) ExtractionCatalogKind.DROPS.pageName else ExtractionCatalogRemoteSource.CONTAINERS_PAGE
    val uiState = rememberSaveableStateHolder()
    val detailState = rememberSaveableStateHolder()
    val lookup = remember(containers.data) { buildDropLookup(containers.data) }
    val sourceRecords = remember(containers.data, catalog.data) { dropSourceRecords(containers.data, catalog.data) }
    val contentKey = if (location.reference == null && location.task == DropTask.ITEMS && location.itemName != null)
        SharedJson.encodeToString(navigation.contentStatePath) else location.contentKey
    var moreActions by remember(location.task, location.reference) { mutableStateOf(false) }
    // Animate the single active subtree: outgoing pages never retain sheets or duplicate saved-state owners.
    val entrance = remember { Animatable(1f) }
    LaunchedEffect(contentKey) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (location.reference != null) "规则与原始资料" else "掉落图鉴", fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = ::goBack) },
            actions = {
                RefreshActionButton(onClick = {
                    active.reload(forceRefresh = true)
                    if (location.task == DropTask.SOURCES && location.reference == null) catalog.reload(forceRefresh = true)
                }, enabled = !active.isLoading)
                Box {
                    IconButton(onClick = { moreActions = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                    DropdownMenu(expanded = moreActions && isActive(), onDismissRequest = { moreActions = false }) {
                        if (location.reference == null) DropdownMenuItem(text = { Text("规则与原始资料") },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            onClick = { moreActions = false; navigate { it.openReference() } })
                        DropdownMenuItem(text = { Text("查看 Wiki 原页") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = {
                                moreActions = false
                                onOpenWikiUrl("https://wiki.biligame.com/klbq/${page.wikiPathEncode()}")
                            })
                    }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (location.reference == null) PrimaryTabRow(selectedTabIndex = location.task.ordinal) {
                DropTask.entries.forEach { task ->
                    Tab(selected = location.task == task, onClick = {
                        if (location.task != task) navigate { it.selectTask(task) }
                    }, text = { Text(task.title) })
                }
            }
            Box(Modifier.weight(1f).graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 8.dp.toPx()
            }) {
                uiState.SaveableStateProvider(contentKey) {
                if (location.reference == null && location.task == DropTask.SOURCES) {
                    ExtractionDropBrowser(sourceRecords, artwork,
                        isActive = isActive(),
                         loading = containersPending || catalogPending,
                         loadFailed = containers.error != null || catalog.error != null,
                        filtersAuthoritative = !containersPending && !catalogPending && containers.error == null && catalog.error == null,
                        status = {
                            if (containers.isOffline && !containers.isShowingCachedPrefetch) OfflineBanner(ageMs = containers.cacheAgeMs)
                            if (catalog.isOffline && !catalog.isShowingCachedPrefetch) {
                                Text("来源说明使用离线记录", style = MaterialTheme.typography.labelMedium)
                                OfflineBanner(ageMs = catalog.cacheAgeMs)
                            }
                             if ((containersPending || catalogPending) && sourceRecords.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                            containers.error?.let { error ->
                                Text("物品列表更新失败：${error.message}", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { containers.reload(forceRefresh = true) }) { Text("重试物品列表") }
                            }
                            catalog.error?.let { error ->
                                Text("部分来源说明未能更新：${error.message}", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { catalog.reload(forceRefresh = true) }) { Text("重试来源说明") }
                            }
                        }, onSelect = { record, query ->
                            navigate { if (record.isContainer) it.openContainer(record.entry.id, query)
                                else it.openSource(record.entry.id, query) }
                        })
                } else PullToRefreshBox(isRefreshing = active.isLoading && active.data.isNotEmpty(),
                    onRefresh = { active.reload(forceRefresh = true) }, modifier = Modifier.fillMaxSize()) {
                    val status: @Composable () -> Unit = {
                        if (active.isOffline && !active.isShowingCachedPrefetch) OfflineBanner(ageMs = active.cacheAgeMs)
                        active.error?.let { error ->
                            Column {
                                Text(error.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { active.reload(forceRefresh = true) }) { Text("重新加载") }
                            }
                        }
                    }
                    when {
                        location.reference != null -> {
                            val reference = location.reference
                            val entries = extractionEntriesForView(catalog.data, reference)
                            ExtractionDropBrowser(entries.map { DropSourceRecord(it, false) }, artwork,
                                isActive = isActive(),
                                filtersAuthoritative = !catalogPending && catalog.error == null,
                                 reference = true, loading = catalogPending, loadFailed = catalog.error != null, status = {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(ExtractionDropView.POOLS, ExtractionDropView.STAGES).forEach { view ->
                                            FilterChip(selected = reference == view, onClick = { navigate { it.selectReference(view) } },
                                                label = { Text(if (view == ExtractionDropView.POOLS) "共用掉落池" else "Boss 阶段对照") })
                                        }
                                    }
                                    Text("保留 Wiki 的分组与说明；物品查询请使用“找物品”。", style = MaterialTheme.typography.bodySmall)
                                    status()
                                }, onSelect = { record, query -> navigate { it.openSource(record.entry.id, query) } })
                        }
                        else -> ExtractionDropLookupList(lookup, location.itemName, location.itemQuality,
                            onChooseItem = { item -> if (item == null) goBack() else navigate { it.openItem(item) } },
                            onSource = { source -> navigate { it.openContainer(source.id, location.itemName.orEmpty(), location.itemQuality) } },
                             artwork = artwork, isActive = isActive(),
                             loading = containersPending, loadFailed = containers.error != null, status = status,
                            filtersAuthoritative = !containersPending && containers.error == null,
                            backLabel = if (navigation.history.dropLast(1).lastOrNull()?.hasDetail == true) "返回来源详情" else "返回物品列表")
                    }
                }
                }
            }
        }
    }

    val source = catalog.data.firstOrNull { it.id == location.sourceId }
    val container = if (location.linkedPool && source != null) linkedDropContainer(source, containers.data)
        else containers.data.firstOrNull { it.id == location.containerId }
    val sharedPool = if (location.linkedPool && source != null && source.containerId == null)
        linkedDropSharedPool(source, catalog.data) else null
    val detailKey = SharedJson.encodeToString(navigation.history)
    // Even the unresolved/loading sheet belongs to one visit. Never reuse its modal/callback
    // instance for the next hop while an earlier dismiss animation is completing.
    if (isActive()) key(detailKey) {
        when {
            location.linkedPool && container == null && sharedPool == null -> WikiContentSheet(onDismiss = ::goBack) {
                item("title") { Text(source?.name ?: "来源物品", style = MaterialTheme.typography.headlineSmall) }
                when {
                    (source?.containerId != null && containersPending) || catalogPending -> {
                        items(4) { SkeletonTextLine(widthFraction = 0.8f) }
                        item("loading") { Text("正在读取该来源的物品…") }
                    }
                    source?.containerId != null && containers.error != null -> item("error") {
                        Text("物品列表加载失败，请重试。", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { containers.reload(forceRefresh = true) }) { Text("重试") }
                    }
                    catalog.error != null -> item("error") {
                        Text("来源说明加载失败，暂时无法恢复关联。", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { catalog.reload(forceRefresh = true) }) { Text("重试来源说明") }
                    }
                    else -> item("unlinked") { Text("现有资料没有唯一对应的物品列表。原来源说明仍可查看。") }
                }
                item("back") { TextButton(onClick = ::goBack) { Text("返回来源说明") } }
            }
            sharedPool != null -> detailState.SaveableStateProvider(detailKey) {
                ExtractionDetailSheet(sharedPool.copy(dropCountState = source!!.dropCountState), location.query,
                    artwork = artwork, onFindItem = { item -> navigate { it.openItem(item) } },
                    offlineAgeMs = catalog.cacheAgeMs.takeIf { catalog.isOffline && !catalog.isShowingCachedPrefetch },
                    poolError = catalog.error?.message, poolIsLoading = catalogPending,
                    onRefreshPool = { catalog.reload(forceRefresh = true) },
                    dismissLabel = "返回来源说明", onDismiss = ::goBack)
            }
            container != null -> detailState.SaveableStateProvider(detailKey) {
                val notes = sourceRecords.singleOrNull { it.isContainer && it.entry.id == container.id }?.sourceNotes
                ExtractionDetailSheet(container, location.query, containerDetails = true,
                    initialPoolQuality = location.poolQuality,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    offlineAgeMs = containers.cacheAgeMs.takeIf { containers.isOffline && !containers.isShowingCachedPrefetch },
                    poolError = containers.error?.message, poolIsLoading = containersPending,
                    onRefreshPool = { containers.reload(forceRefresh = true) }, artwork = artwork,
                    onViewSourceNotes = notes?.let { note -> { navigate { it.openSource(note.id) } } },
                    dismissLabel = "返回上一页", onDismiss = ::goBack)
            }
            source != null -> detailState.SaveableStateProvider(detailKey) {
                ExtractionDetailSheet(source, location.query, artwork = artwork,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    onViewCompletePool = if (source.type == ExtractionEntryType.DROP_SOURCE &&
                        (source.containerId != null || linkedDropSharedPool(source, catalog.data) != null))
                        ({ navigate { it.openLinkedPool() } }) else null,
                    dismissLabel = "返回上一页", onDismiss = ::goBack)
            }
            location.hasDetail -> WikiContentSheet(onDismiss = ::goBack) {
                item("missing") {
                    val pending = if (location.sourceId != null) catalogPending else containersPending
                    val resource = if (location.sourceId != null) catalog else containers
                    Text(if (pending) "正在恢复来源详情…" else "这条记录暂不可用，请返回列表重新选择。")
                    resource.error?.let { error ->
                        Text(error.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { resource.reload(forceRefresh = true) }) { Text("重试") }
                    }
                    TextButton(onClick = ::goBack) { Text("返回上一页") }
                }
            }
        }
    }
}

@Composable
private fun ExtractionDropBrowser(
    records: List<DropSourceRecord>, artwork: DropArtworkIndex,
    modifier: Modifier = Modifier, reference: Boolean = false,
    loading: Boolean = false, loadFailed: Boolean = false, filtersAuthoritative: Boolean = true, isActive: Boolean = true,
    status: @Composable () -> Unit = {}, onSelect: (DropSourceRecord, String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    val categories = remember(records) { records.map { it.entry.filterCategory }.distinct() }
    val filtered = remember(records, query, category) {
        records.filter { category == null || it.entry.filterCategory == category }.mapNotNull { it.matchingRecord(query) }
    }
    LaunchedEffect(categories, filtersAuthoritative) {
        if (filtersAuthoritative && categories.isNotEmpty() && category !in categories) category = null
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                    isSearching = false, placeholder = if (reference) "搜索资料名称或内容…" else "搜索箱子、Boss 或物品…")
                DropFilterBar(summary = if (loading && records.isEmpty()) "正在读取${if (reference) "资料" else "来源"}…"
                    else "${filtered.size} 条${if (reference) "资料" else "来源记录"}",
                    label = category ?: "筛选分类", onClick = { filters = true }, enabled = records.isNotEmpty(),
                    onReset = if (category != null) ({ category = null }) else null)
                status()
            }
        }
        if (filtered.isEmpty()) {
            if (loading && records.isEmpty()) items(4, key = { "source-skeleton:$it" }, contentType = { "skeleton" }) {
                DropSourceCardSkeleton(Modifier.dropItemMotion(this))
            }
            else if (!(loadFailed && records.isEmpty())) item("empty") { CatalogEmptyResults(onClear = { query = ""; category = null }) }
        }
        items(filtered, key = { "${it.isContainer}:${it.entry.id}" }, contentType = { it.entry.type }) { record ->
            ExtractionDropCard(modifier = Modifier.dropItemMotion(this), entry = record.entry, query = query, artwork = artwork, onClick = { onSelect(record, query) })
        }
    }
    if (filters && isActive) WikiSelectionSheet(title = "来源分类", onDismiss = { filters = false }) {
        item("all") { SelectionOptionRow(label = "全部来源", selected = category == null, onClick = { category = null; filters = false }) }
        items(categories, key = { it }) { value ->
            SelectionOptionRow(label = value, selected = category == value, onClick = { category = value; filters = false })
        }
    }
}
