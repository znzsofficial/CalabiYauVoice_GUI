package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.extractionContainersApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.rememberDropArtwork
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.SharedJson
import data.ErrorKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private const val dropReferenceTitle = "掉落池与首领阶段"

private fun dropLoadMessage(subject: String, kind: ErrorKind) = when (kind) {
    ErrorKind.PARSE -> "暂时无法显示完整的$subject"
    ErrorKind.TIMEOUT -> "${subject}加载超时"
    ErrorKind.CDN_BLOCKED -> "Wiki 暂时限制访问，无法加载$subject"
    else -> "${subject}加载失败"
}

private fun dropNavigationSaver(initial: DropNavigation) = Saver<DropNavigation, String>(
    save = { SharedJson.encodeToString(it) },
    restore = { encoded -> runCatching { SharedJson.decodeFromString<DropNavigation>(encoded) }
        .getOrNull()?.takeIf { it.history.isNotEmpty() } ?: initial }
)

@Composable
internal fun ExtractionDropsScreen(
    onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit, isActive: () -> Boolean = { true },
    initialTask: DropTask = DropTask.ITEMS
) {
    val initial = remember(initialTask) { DropNavigation(initialTask) }
    var navigation by rememberSaveable(initialTask, stateSaver = remember(initial) { dropNavigationSaver(initial) }) {
        mutableStateOf(initial)
    }
    val displayedNavigation = navigation
    val location = displayedNavigation.current
    val focus = LocalFocusManager.current
    fun navigate(change: (DropNavigation) -> DropNavigation) {
        if (isActive() && navigation === displayedNavigation) {
            focus.clearFocus()
            navigation = change(navigation)
        }
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
    var lastContentKey by remember { mutableStateOf(contentKey) }
    LaunchedEffect(contentKey) {
        if (lastContentKey == contentKey) return@LaunchedEffect
        lastContentKey = contentKey
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(DropMotion.page, easing = FastOutSlowInEasing))
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (location.reference != null) dropReferenceTitle else "掉落查询", fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = ::goBack) },
            actions = {
                RefreshActionButton(onClick = {
                    active.reload(forceRefresh = true)
                    if (location.task == DropTask.SOURCES && location.reference == null) catalog.reload(forceRefresh = true)
                }, enabled = !active.isLoading)
                Box {
                    IconButton(onClick = { moreActions = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                    DropdownMenu(expanded = moreActions && isActive(), onDismissRequest = { moreActions = false }) {
                        if (location.reference == null) DropdownMenuItem(text = { Text(dropReferenceTitle) },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            onClick = { moreActions = false; navigate { it.openReference() } })
                        DropdownMenuItem(text = { Text("Wiki 原文") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = {
                                moreActions = false
                                onOpenWikiUrl(extractionArticleUrl(page))
                            })
                    }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (location.reference == null) PrimaryTabRow(selectedTabIndex = location.task.ordinal) {
                DropTask.entries.forEach { task ->
                    Tab(selected = location.task == task, onClick = {
                        if (location.task != task) navigate { it.selectTask(task) }
                    }, text = { Text(task.title) })
                }
            }
            Box(Modifier.weight(1f).graphicsLayer {
                alpha = 0.55f + 0.45f * entrance.value
                translationY = (1f - entrance.value) * 8.dp.toPx()
            }) {
                uiState.SaveableStateProvider(contentKey) {
                if (location.reference == null && location.task == DropTask.SOURCES) {
                    ExtractionDropBrowser(sourceRecords, artwork, catalogEntries = catalog.data,
                        isActive = isActive(),
                        partialSources = containers.data.isEmpty() != catalog.data.isEmpty(),
                         loading = containersPending || catalogPending,
                         loadFailed = containers.error != null || catalog.error != null,
                        filtersAuthoritative = !containersPending && !catalogPending && containers.error == null && catalog.error == null,
                        status = {
                            if (containers.isOffline && !containers.isShowingCachedPrefetch) OfflineBanner(ageMs = containers.cacheAgeMs)
                            if (catalog.isOffline && !catalog.isShowingCachedPrefetch) {
                                Text("来源说明", style = MaterialTheme.typography.labelMedium)
                                OfflineBanner(ageMs = catalog.cacheAgeMs)
                            }
                            if ((containersPending || catalogPending) && sourceRecords.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                            containers.error?.let { error ->
                                DropErrorBanner(dropLoadMessage("容器掉落表", error.kind), "重试") {
                                    containers.reload(forceRefresh = true)
                                }
                            }
                            catalog.error?.let { error ->
                                DropErrorBanner(dropLoadMessage("来源说明", error.kind), "重试") {
                                    catalog.reload(forceRefresh = true)
                                }
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
                            DropErrorBanner(dropLoadMessage(if (location.reference != null) "掉落图鉴" else "容器掉落表", error.kind), "重试") {
                                active.reload(forceRefresh = true)
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
                                 view = reference, loading = catalogPending, loadFailed = catalog.error != null, status = {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(ExtractionDropView.POOLS, ExtractionDropView.STAGES).forEach { view ->
                                            FilterChip(selected = reference == view, onClick = { navigate { it.selectReference(view) } },
                                                label = { Text(if (view == ExtractionDropView.POOLS) "掉落池" else "首领阶段对照") })
                                        }
                                    }
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
                item("title") { Text(source?.name ?: "候选物品", style = MaterialTheme.typography.headlineSmall) }
                when {
                    (source?.containerId != null && containersPending) || catalogPending -> {
                         items(4) { DropLookupRowSkeleton() }
                         item("loading") { Text("正在加载候选物品…") }
                    }
                    source?.containerId != null && containers.error != null -> item("error") {
                        Text("容器掉落表加载失败", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { containers.reload(forceRefresh = true) }) { Text("重试") }
                    }
                    catalog.error != null -> item("error") {
                        Text("来源说明加载失败", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { catalog.reload(forceRefresh = true) }) { Text("重试") }
                    }
                    else -> item("unlinked") { Text("暂无对应的掉落池") }
                }
                item("back") { TextButton(onClick = ::goBack) { Text("返回来源说明") } }
            }
            sharedPool != null -> detailState.SaveableStateProvider(detailKey) {
                ExtractionDetailSheet(sourcePoolDetail(source!!, sharedPool), location.query, sharedSourceList = true,
                    artwork = artwork, onFindItem = { item -> navigate { it.openItem(item) } },
                    offlineAgeMs = catalog.cacheAgeMs.takeIf { catalog.isOffline && !catalog.isShowingCachedPrefetch },
                    poolError = catalog.error?.let { dropLoadMessage("掉落池", it.kind) }, poolIsLoading = catalogPending,
                    onRefreshPool = { catalog.reload(forceRefresh = true) },
                    dismissLabel = "返回来源说明", onDismiss = ::goBack)
            }
            container != null -> detailState.SaveableStateProvider(detailKey) {
                val notes = sourceRecords.singleOrNull { it.isContainer && it.entry.id == container.id }?.sourceNotes
                ExtractionDetailSheet(container, location.query, containerDetails = true,
                    initialPoolQuality = location.poolQuality,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    offlineAgeMs = containers.cacheAgeMs.takeIf { containers.isOffline && !containers.isShowingCachedPrefetch },
                    poolError = containers.error?.let { dropLoadMessage("容器掉落表", it.kind) }, poolIsLoading = containersPending,
                    onRefreshPool = { containers.reload(forceRefresh = true) }, artwork = artwork,
                    onViewSourceNotes = notes?.let { note -> { navigate { it.openSource(note.id) } } },
                    dismissLabel = "返回上一页", onDismiss = ::goBack)
            }
            source != null -> detailState.SaveableStateProvider(detailKey) {
                val sourcePool = linkedDropSharedPool(source, catalog.data)
                ExtractionDetailSheet(source, location.query, artwork = artwork,
                    evidenceGroups = sourcePool?.dropPoolGroups ?: source.dropPoolGroups,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    onViewCompletePool = if (source.type == ExtractionEntryType.DROP_SOURCE &&
                        (source.containerId != null || sourcePool != null))
                        ({ navigate { it.openLinkedPool() } }) else null,
                    dismissLabel = "返回上一页", onDismiss = ::goBack)
            }
            location.hasDetail -> WikiContentSheet(onDismiss = ::goBack) {
                item("missing") {
                    val pending = if (location.sourceId != null) catalogPending else containersPending
                    val resource = if (location.sourceId != null) catalog else containers
                    if (pending || resource.error == null) Text(if (pending) "正在加载来源详情…" else "暂未找到此来源")
                    resource.error?.let { error ->
                        Text(dropLoadMessage("来源详情", error.kind), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { resource.reload(forceRefresh = true) }) { Text("重试") }
                    }
                    TextButton(onClick = ::goBack) { Text("返回上一页") }
                }
            }
        }
    }
}

/** A shared pool proves membership only, even when two sources are in the same section. */
internal fun sourcePoolDetail(source: ExtractionEntry, pool: ExtractionEntry): ExtractionEntry =
    pool.copy(name = "${source.name}的物品清单", category = source.category, sourceCategory = source.category,
        description = source.description, sectionIntroduction = source.sectionIntroduction,
        containerId = null, dropCountState = source.dropCountState,
        fields = pool.fields.filter { it.label == "池规模" } +
            source.fields.filter { it.label in setOf("掉落件数", "本节掉落说明", "抽中率最高") })

@Composable
private fun ExtractionDropBrowser(
    records: List<DropSourceRecord>, artwork: DropArtworkIndex,
    modifier: Modifier = Modifier, view: ExtractionDropView = ExtractionDropView.SOURCES,
    catalogEntries: List<ExtractionEntry> = emptyList(),
    partialSources: Boolean = false,
    loading: Boolean = false, loadFailed: Boolean = false, filtersAuthoritative: Boolean = true, isActive: Boolean = true,
    status: @Composable () -> Unit = {}, onSelect: (DropSourceRecord, String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val subject = when (view) {
        ExtractionDropView.SOURCES -> "来源"
        ExtractionDropView.POOLS -> "掉落池"
        ExtractionDropView.STAGES -> "首领阶段"
    }
    val categories = remember(records) { records.map { it.entry.filterCategory }.distinct() }
    val filtered = remember(records, query, category, catalogEntries) {
        records.filter { category == null || it.entry.filterCategory == category }.mapNotNull { it.matchingRecord(query, catalogEntries) }
    }
    LaunchedEffect(categories, filtersAuthoritative) {
        if (filtersAuthoritative && categories.isNotEmpty() && category !in categories) category = null
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(keyword = query, onKeywordChange = { query = it }, onClear = { query = "" },
                    isSearching = false, placeholder = when (view) {
                        ExtractionDropView.SOURCES -> "搜索来源或物品"
                        ExtractionDropView.POOLS -> "搜索掉落池或物品"
                        ExtractionDropView.STAGES -> "搜索阶段或掉落内容"
                    })
                DropFilterBar(summary = if (loading && records.isEmpty()) "正在加载${subject}…"
                    else if (loadFailed && records.isEmpty()) subject
                    else if (partialSources) "已加载的来源中匹配 ${filtered.size} 个" else "${filtered.size} 个$subject",
                    label = category ?: "分类", onClick = { focus.clearFocus(); filters = true }, enabled = records.isNotEmpty(),
                    activeFilter = category != null)
                status()
            }
        }
        if (filtered.isEmpty()) {
            if (loading && records.isEmpty()) items(4, key = { "source-skeleton:$it" }, contentType = { "skeleton" }) {
                DropSourceCardSkeleton(Modifier.dropItemMotion(this))
            }
            else if (!(loadFailed && records.isEmpty())) item("empty") {
                ExtractionEmptyResults(message = if (partialSources) "已加载的来源中没有匹配项"
                    else if (query.isNotBlank() || category != null) "没有符合条件的$subject" else "暂无$subject",
                    onClearQuery = if (query.isNotEmpty()) ({ query = "" }) else null,
                    onClearFilters = if (category != null) ({ category = null }) else null)
            }
        }
        items(filtered, key = { "${it.isContainer}:${it.entry.id}" }, contentType = { it.entry.type }) { record ->
            val sharedPool = remember(record.entry, catalogEntries) { linkedDropSharedPool(record.entry, catalogEntries) }
            ExtractionDropCard(modifier = Modifier.dropItemMotion(this), entry = record.entry, query = query, isContainer = record.isContainer,
                sharedPool = sharedPool, artwork = artwork, onClick = { onSelect(record, query) })
        }
    }
    if (filters && isActive) WikiSelectionSheet(title = "${subject}分类", onDismiss = { filters = false }) {
        item("all") { SelectionOptionRow(label = "全部分类", selected = category == null, onClick = { category = null; filters = false }) }
        items(categories, key = { it }) { value ->
            SelectionOptionRow(label = value, selected = category == value, onClick = { category = value; filters = false })
        }
    }
}

@Composable
private fun DropErrorBanner(message: String, retryLabel: String, onRetry: () -> Unit) {
    Surface(
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}
