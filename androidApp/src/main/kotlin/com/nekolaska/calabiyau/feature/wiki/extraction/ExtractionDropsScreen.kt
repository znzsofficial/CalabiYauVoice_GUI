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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionCatalogApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.extractionContainersApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.extractionDropRateApi
import com.nekolaska.calabiyau.feature.wiki.extraction.api.rememberDropArtwork
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.ErrorKind
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

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
    // Typing, changing quality and browsing sources do not request probability shards. A different
    // name owns a separate LoadState so a late response cannot appear under the new item's header.
    val rates = location.itemName?.takeIf { location.showItemRates && location.task == DropTask.ITEMS && location.reference == null }?.let { name ->
        key(name) {
            rememberLoadState(initial = emptyList<DropRate>(), enabled = isActive() && !location.hasDetail,
                cachedFetch = { extractionDropRateApi.fetch(name, cacheOnly = true) },
                fetch = { force -> extractionDropRateApi.fetch(name, forceRefresh = force) })
        }
    }
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
    fun refreshCurrent() {
        if (rates != null) { rates.reload(forceRefresh = true); return }
        active.reload(forceRefresh = true)
        if (location.task == DropTask.SOURCES && location.reference == null) catalog.reload(forceRefresh = true)
    }
    val page = if (location.reference != null || rates != null) ExtractionCatalogKind.DROPS.pageName else ExtractionCatalogRemoteSource.CONTAINERS_PAGE
    val uiState = rememberSaveableStateHolder()
    val detailState = rememberSaveableStateHolder()
    val containerEntries = containers.data
    val lookup = remember(containerEntries) { lazy(LazyThreadSafetyMode.NONE) { buildDropLookup(containerEntries) } }
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
        TopAppBar(
            title = { Text(if (location.reference != null) dropReferenceTitle else "掉落查询", fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = ::goBack) },
            actions = {
                RefreshActionButton(onClick = ::refreshCurrent, enabled = !(rates?.isLoading ?: active.isLoading))
                Box {
                    IconButton(onClick = { moreActions = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                    DropdownMenu(expanded = moreActions && isActive(), onDismissRequest = { moreActions = false }) {
                        if (location.reference == null) DropdownMenuItem(
                            text = { Text(dropReferenceTitle) },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            onClick = { moreActions = false; navigate { it.openReference() } }
                        )
                        DropdownMenuItem(
                            text = { Text("Wiki 原文") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = {
                                moreActions = false
                                onOpenWikiUrl(extractionArticleUrl(page))
                            }
                        )
                    }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (location.reference == null) {
                DropTaskSwitcher(
                    selectedTask = location.task,
                    onSelect = { task -> if (location.task != task) navigate { it.selectTask(task) } }
                )
            }
            Box(Modifier.weight(1f).graphicsLayer {
                alpha = 0.55f + 0.45f * entrance.value
                translationY = (1f - entrance.value) * 8.dp.toPx()
            }) {
                uiState.SaveableStateProvider(contentKey) {
                    if (location.reference == null && location.task == DropTask.SOURCES) {
                        ExtractionDropBrowser(
                            sourceRecords, artwork, catalogEntries = catalog.data,
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
                                navigate {
                                    if (record.isContainer) it.openContainer(record.entry.id, query)
                                    else it.openSource(record.entry.id, query)
                                }
                            }
                        )
                    } else PullToRefreshBox(
                        isRefreshing = if (rates != null) rates.isLoading && rates.data.isNotEmpty() else active.isLoading && active.data.isNotEmpty(),
                        onRefresh = ::refreshCurrent,
                        modifier = Modifier.fillMaxSize()
                    ) {
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
                                ExtractionDropBrowser(
                                    entries.map { DropSourceRecord(it, false) }, artwork,
                                    isActive = isActive(),
                                    filtersAuthoritative = !catalogPending && catalog.error == null,
                                    view = reference, loading = catalogPending, loadFailed = catalog.error != null,
                                    status = {
                                        val colors = MaterialTheme.colorScheme
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(ExtractionDropView.POOLS, ExtractionDropView.STAGES).forEach { view ->
                                                val isChosen = reference == view
                                                FilterChip(
                                                    selected = isChosen,
                                                    onClick = { navigate { it.selectReference(view) } },
                                                    shape = smoothCornerShape(10.dp),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isChosen) colors.primary.copy(alpha = 0.55f)
                                                        else colors.outlineVariant.copy(alpha = 0.55f)
                                                    ),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
                                                        selectedLabelColor = colors.onPrimaryContainer,
                                                        containerColor = colors.surfaceContainerLow,
                                                        labelColor = colors.onSurface
                                                    ),
                                                    label = { Text(if (view == ExtractionDropView.POOLS) "掉落池" else "首领阶段对照") }
                                                )
                                            }
                                        }
                                        status()
                                    }, onSelect = { record, query -> navigate { it.openSource(record.entry.id, query) } }
                                )
                            }
                            else -> ExtractionDropLookupList(
                                lookup.value, location.itemName, location.itemQuality,
                                onChooseItem = { item -> if (item == null) goBack() else navigate { it.openItem(item) } },
                                onSource = { source -> navigate { it.openContainer(source.id, location.itemName.orEmpty(), location.itemQuality) } },
                                artwork = artwork, isActive = isActive(), rates = rates, onShowRates = { show -> navigate { it.showItemRates(show) } },
                                loading = containersPending, loadFailed = containers.error != null, status = status,
                                filtersAuthoritative = !containersPending && containers.error == null,
                                backLabel = if (navigation.history.dropLast(1).lastOrNull()?.hasDetail == true) "返回来源详情" else "返回物品列表"
                            )
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
                item("title") { Text(source?.name ?: "候选物品", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                when {
                    (source?.containerId != null && containersPending) || catalogPending -> {
                        items(4) { DropLookupRowSkeleton() }
                        item("loading") { Text("正在加载候选物品…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    source?.containerId != null && containers.error != null -> item("error") {
                        DropErrorBanner("容器掉落表加载失败", "重试") { containers.reload(forceRefresh = true) }
                    }
                    catalog.error != null -> item("error") {
                        DropErrorBanner("来源说明加载失败", "重试") { catalog.reload(forceRefresh = true) }
                    }
                    else -> item("unlinked") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = smoothCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
                        ) {
                            Text("暂无对应的掉落池", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item("back") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(onClick = ::goBack, shape = smoothCornerShape(10.dp)) { Text("返回来源说明") }
                    }
                }
            }
            sharedPool != null -> detailState.SaveableStateProvider(detailKey) {
                ExtractionDetailSheet(
                    sourcePoolDetail(source!!, sharedPool), location.query, resource = catalog, sharedSourceList = true,
                    artwork = artwork, onFindItem = { item -> navigate { it.openItem(item) } },
                    dismissLabel = "返回来源说明", onDismiss = ::goBack
                )
            }
            container != null -> detailState.SaveableStateProvider(detailKey) {
                val notes = sourceRecords.singleOrNull { it.isContainer && it.entry.id == container.id }?.sourceNotes
                ExtractionDetailSheet(
                    container, location.query, resource = containers, containerDetails = true,
                    initialPoolQuality = location.poolQuality,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    artwork = artwork,
                    onViewSourceNotes = notes?.let { note -> { navigate { it.openSource(note.id) } } },
                    dismissLabel = "返回上一页", onDismiss = ::goBack
                )
            }
            source != null -> detailState.SaveableStateProvider(detailKey) {
                val sourcePool = linkedDropSharedPool(source, catalog.data)
                ExtractionDetailSheet(
                    source, location.query, resource = catalog, artwork = artwork,
                    evidenceGroups = sourcePool?.dropPoolGroups ?: source.dropPoolGroups,
                    onFindItem = { item -> navigate { it.openItem(item) } },
                    onViewCompletePool = if (source.type == ExtractionEntryType.DROP_SOURCE &&
                        (source.containerId != null || sourcePool != null))
                        ({ navigate { it.openLinkedPool() } }) else null,
                    dismissLabel = "返回上一页", onDismiss = ::goBack
                )
            }
            location.hasDetail -> WikiContentSheet(onDismiss = ::goBack) {
                item("missing") {
                    val pending = if (location.sourceId != null) catalogPending else containersPending
                    val resource = if (location.sourceId != null) catalog else containers
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (pending || resource.error == null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = smoothCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
                            ) {
                                Text(
                                    if (pending) "正在加载来源详情…" else "暂未找到此来源",
                                    Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        resource.error?.let { error ->
                            DropErrorBanner(dropLoadMessage("来源详情", error.kind), "重试") {
                                resource.reload(forceRefresh = true)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            FilledTonalButton(onClick = ::goBack, shape = smoothCornerShape(10.dp)) { Text("返回上一页") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropTaskSwitcher(selectedTask: DropTask, onSelect: (DropTask) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = smoothCornerShape(10.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            DropTask.entries.forEach { task ->
                val isSelected = selectedTask == task
                Surface(
                    onClick = { onSelect(task) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        },
                    shape = smoothCornerShape(8.dp),
                    color = if (isSelected) colors.primaryContainer.copy(alpha = 0.65f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.55f)) else null
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (task == DropTask.ITEMS) Icons.Outlined.Search else Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) colors.primary else colors.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            task.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) colors.primary else colors.onSurfaceVariant
                        )
                    }
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
    val search = remember(records, catalogEntries) { DropSourceSearch(records, catalogEntries) }
    if (query.isNotBlank()) LaunchedEffect(search) { withContext(Dispatchers.Default) { search.prepare() } }
    val filtered = remember(search, query, category) { search.filter(query, category) }
    LaunchedEffect(categories, filtersAuthoritative) {
        if (filtersAuthoritative && categories.isNotEmpty() && category !in categories) category = null
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    keyword = query,
                    onKeywordChange = { query = it },
                    onClear = { query = "" },
                    isSearching = false,
                    placeholder = when (view) {
                        ExtractionDropView.SOURCES -> "搜索来源或物品"
                        ExtractionDropView.POOLS -> "搜索掉落池或物品"
                        ExtractionDropView.STAGES -> "搜索阶段或掉落内容"
                    }
                )
                DropFilterBar(
                    summary = if (loading && records.isEmpty()) "正在加载${subject}…"
                    else if (loadFailed && records.isEmpty()) subject
                    else if (partialSources) "已加载的来源中匹配 ${filtered.size} 个" else "${filtered.size} 个$subject",
                    label = category ?: "分类",
                    onClick = { focus.clearFocus(); filters = true },
                    enabled = records.isNotEmpty(),
                    activeFilter = category != null,
                    activeCount = if (category != null) 1 else 0
                )
                status()
            }
        }
        if (filtered.isEmpty()) {
            if (loading && records.isEmpty()) items(4, key = { "source-skeleton:$it" }, contentType = { "skeleton" }) {
                DropSourceCardSkeleton(Modifier.dropItemMotion(this))
            }
            else if (!(loadFailed && records.isEmpty())) item("empty") {
                ExtractionEmptyResults(
                    message = if (partialSources) "已加载的来源中没有匹配项"
                    else if (query.isNotBlank() || category != null) "没有符合条件的$subject" else "暂无$subject",
                    onClearQuery = if (query.isNotEmpty()) ({ query = "" }) else null,
                    onClearFilters = if (category != null) ({ category = null }) else null
                )
            }
        }
        items(filtered, key = { "${it.isContainer}:${it.entry.id}" }, contentType = { it.entry.type }) { record ->
            val sharedPool = remember(record.entry, catalogEntries) { linkedDropSharedPool(record.entry, catalogEntries) }
            ExtractionDropCard(
                modifier = Modifier.dropItemMotion(this),
                entry = record.entry,
                query = query,
                isContainer = record.isContainer,
                sharedPool = sharedPool,
                artwork = artwork,
                onClick = { onSelect(record, query) }
            )
        }
    }
    if (filters && isActive) WikiSelectionSheet(title = "${subject}分类", onDismiss = { filters = false }) {
        item("all") {
            SelectionOptionRow(label = "全部分类", selected = category == null, onClick = { category = null; filters = false })
        }
        items(categories, key = { it }) { value ->
            SelectionOptionRow(label = value, selected = category == value, onClick = { category = value; filters = false })
        }
    }
}

@Composable
internal fun DropErrorBanner(message: String, retryLabel: String, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = smoothCornerShape(12.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer
            )
            TextButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}
