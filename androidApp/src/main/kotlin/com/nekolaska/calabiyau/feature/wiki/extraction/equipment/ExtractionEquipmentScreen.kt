package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.api.EquipmentApi
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl

@Composable
internal fun ExtractionEquipmentScreen(kind: EquipmentKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit,
                                      isActive: () -> Boolean = { true }) {
    key(kind) { EquipmentBrowser(kind, onBack, onOpenWikiUrl, isActive) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentBrowser(kind: EquipmentKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit, isActive: () -> Boolean) {
    val state = rememberLoadState(initial = EquipmentPage.EMPTY, cachedPrefetchDelayMs = 300L,
        cachedFetch = { EquipmentApi.fetch(kind, cacheOnly = true) },
        fetch = { EquipmentApi.fetch(kind, forceRefresh = it, allowMemoryCache = false) })
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var quality by rememberSaveable { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable { mutableStateOf(EquipmentSort.DEFAULT) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var selectedSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableIntStateOf(0) }
    val url = extractionArticleUrl(kind.pageName)
    val page = state.data
    val active = rememberActiveCallbacks(isActive)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun leaveSearch() { focus.clearFocus(); keyboard?.hide() }
    val clear: () -> Unit = { if (active()) { query = ""; category = null; quality = null; sort = EquipmentSort.DEFAULT } }
    val listState = rememberLazyListState()
    val modalState = rememberSaveableStateHolder()
    LaunchedEffect(page, state.isShowingCachedPrefetch) {
        if (page.series.isNotEmpty() && !state.isShowingCachedPrefetch) {
            if (category !in page.categories) category = null
            if (page.qualities.none { it.code == quality }) quality = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(kind.title, fontWeight = FontWeight.Bold) }, navigationIcon = { BackNavButton(onClick = { if (active()) onBack() }) },
            actions = {
                RefreshActionButton(onClick = { if (active()) state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(wikiUrl = url, onOpenWikiUrl = { if (active()) { leaveSearch(); onOpenWikiUrl(it) } }, contentDescription = "查看 Wiki 原文")
            })
    }) { padding ->
        ApiResourceContent(state, modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            enablePullToRefresh = active(), isDataEmpty = { it.series.isEmpty() }, loading = { EquipmentSkeleton(kind, it) }) {
            val rows = remember(page, query, category, quality, sort) { page.rows(query, category, quality, sort) }
            val chosenQuality = page.qualities.find { it.code == quality }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                item(key = "controls", contentType = "controls") {
                    Column {
                        SearchBar(keyword = query, onKeywordChange = { if (active()) query = it }, onClear = { if (active()) query = "" },
                            isSearching = false, placeholder = if (kind == EquipmentKind.GUNS) "搜索枪械、弹药或来源" else "搜索防具或来源")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (kind == EquipmentKind.GUNS) "${rows.size} 种枪械" else "${rows.size} 款防具",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { if (active()) { leaveSearch(); filtersOpen = true } }) {
                                Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("筛选与排序")
                            }
                        }
                        if (category != null || chosenQuality != null || sort != EquipmentSort.DEFAULT) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                category?.let { ActiveCondition(it, onRemove = { if (active()) category = null }) }
                                chosenQuality?.let { ActiveCondition(it.label, onRemove = { if (active()) quality = null }) }
                                if (sort != EquipmentSort.DEFAULT) ActiveCondition("${sort.label}降序", onRemove = { if (active()) sort = EquipmentSort.DEFAULT })
                            }
                        }
                    }
                }
                if (rows.isEmpty()) item(key = "empty") {
                    CatalogEmptyResults(message = if (kind == EquipmentKind.GUNS) "没有匹配的枪械" else "没有匹配的防具", onClear = clear)
                }
                items(rows, key = { it.series.key }, contentType = { "series" }) { row ->
                    SeriesRow(kind, row, sort) { if (active()) { leaveSearch(); selectedSeries = row.series.key; selectedId = row.variant.id } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
    if (active()) modalState.SaveableStateProvider("equipment-dialogs") {
        if (filtersOpen && page.series.isNotEmpty()) {
            val filterActive = rememberActiveCallbacks { active() && filtersOpen }
            EquipmentFilters(kind, page, category, quality, sort,
                onCategory = { if (filterActive()) category = it }, onQuality = { if (filterActive()) quality = it },
                onSort = { if (filterActive()) sort = it }, onDismiss = { if (filterActive()) filtersOpen = false })
        }
        page.series.find { it.key == selectedSeries }?.let { series -> key(series.key) {
            val item = series.variants.find { it.id == selectedId } ?: series.variants.first()
            val detailActive = rememberActiveCallbacks { active() && selectedSeries == series.key }
            EquipmentDetail(kind, series, item, isActive = detailActive,
                onSelect = { if (detailActive()) selectedId = it.id }, onOriginal = { if (detailActive()) onOpenWikiUrl(url) },
                onDismiss = { if (detailActive()) selectedSeries = null })
        } }
    }
}

/** An outgoing sheet's delayed dismiss must not close the restored sheet after returning from the web overlay. */
@Composable
private fun rememberActiveCallbacks(isActive: () -> Boolean): () -> Boolean {
    val latest by rememberUpdatedState(isActive)
    val mounted = remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { mounted.value = false }
    }
    return remember { { mounted.value && latest() } }
}

@Composable
private fun ActiveCondition(label: String, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, label = { Text(label) },
        trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) },
        modifier = Modifier.semantics { onClick(label = "清除$label", action = null) })
}

@Composable
private fun SeriesRow(kind: EquipmentKind, row: EquipmentRow, sort: EquipmentSort, onClick: () -> Unit) {
    val (series, variant) = row
    val summary = when (sort) {
        EquipmentSort.VALUE -> "价值 ${variant.value}"
        EquipmentSort.RATE -> variant.lootText.substringBefore('·').trim().ifBlank { "出现率未给出" }
        else -> when (val stats = variant.stats) {
            is EquipmentStats.Gun -> "伤害 ${stats.damage}   弹匣 ${stats.magazine}"
            is EquipmentStats.Armor -> "护甲 ${stats.armor}   生命 ${stats.health}"
        }
    }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EquipmentThumbnail(kind, variant, Modifier.size(width = 74.dp, height = 58.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(series.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EquipmentQualityLabel(variant.quality)
                    Text(listOfNotNull(series.category.takeIf { kind == EquipmentKind.GUNS }, "共 ${series.variants.size} 档").joinToString("  "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EquipmentFilters(kind: EquipmentKind, page: EquipmentPage, category: String?, quality: Int?, sort: EquipmentSort,
                             onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onSort: (EquipmentSort) -> Unit,
                             onDismiss: () -> Unit) {
    WikiScrollableSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("筛选与排序", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OptionGroup(if (kind == EquipmentKind.GUNS) "枪种" else "套装") {
                FilterChip(category == null, onClick = { onCategory(null) }, label = { Text(if (kind == EquipmentKind.GUNS) "全部枪种" else "全部套装") })
                page.categories.forEach { FilterChip(category == it, onClick = { onCategory(it) }, label = { Text(it) }) }
            }
            OptionGroup("品质") {
                FilterChip(quality == null, onClick = { onQuality(null) }, label = { Text("全部品质") })
                page.qualities.forEach { FilterChip(quality == it.code, onClick = { onQuality(it.code) }, label = { EquipmentQualityLabel(it) }) }
            }
            OptionGroup("排序") {
                EquipmentSort.entries.filter { when (it) {
                    EquipmentSort.DAMAGE -> kind == EquipmentKind.GUNS
                    EquipmentSort.ARMOR, EquipmentSort.HP -> kind == EquipmentKind.ARMOR
                    else -> true
                } }.forEach { FilterChip(sort == it, onClick = { onSort(it) }, label = { Text(if (it == EquipmentSort.DEFAULT) it.label else "${it.label}降序") }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onCategory(null); onQuality(null); onSort(EquipmentSort.DEFAULT) }) { Text("重置") }
                Button(onClick = onDismiss) { Text("完成") }
            }
        }
    }
}

@Composable
private fun OptionGroup(label: String, options: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options() }
}
