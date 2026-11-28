package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.api.EquipmentApi
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl

@Composable
internal fun ExtractionEquipmentScreen(
    kind: EquipmentKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit,
    isActive: () -> Boolean = { true }
) {
    key(kind) { EquipmentBrowser(kind, onBack, onOpenWikiUrl, isActive) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentBrowser(kind: EquipmentKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit, isActive: () -> Boolean) {
    val state = rememberLoadState(
        initial = EquipmentPage.EMPTY,
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { EquipmentApi.fetch(kind, cacheOnly = true) },
        fetch = { EquipmentApi.fetch(kind, forceRefresh = it, allowMemoryCache = false) }
    )
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
        TopAppBar(
            title = { Text(kind.title, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = { if (active()) onBack() }) },
            actions = {
                RefreshActionButton(onClick = { if (active()) state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(
                    wikiUrl = url,
                    onOpenWikiUrl = { if (active()) { leaveSearch(); onOpenWikiUrl(it) } },
                    contentDescription = "查看 Wiki 原文"
                )
            }
        )
    }) { padding ->
        ApiResourceContent(
            state,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            enablePullToRefresh = active(),
            isDataEmpty = { it.series.isEmpty() },
            loading = { EquipmentSkeleton(kind, it) }
        ) {
            val rows = remember(page, query, category, quality, sort) { page.rows(query, category, quality, sort) }
            val chosenQuality = page.qualities.find { it.code == quality }
            val activeFilterCount = (if (category != null) 1 else 0) +
                (if (chosenQuality != null) 1 else 0) +
                (if (sort != EquipmentSort.DEFAULT) 1 else 0)
            val colors = MaterialTheme.colorScheme
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "controls", contentType = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchBar(
                            keyword = query,
                            onKeywordChange = { if (active()) query = it },
                            onClear = { if (active()) query = "" },
                            isSearching = false,
                            placeholder = if (kind == EquipmentKind.GUNS) "搜索枪械、弹药或来源" else "搜索防具或来源"
                        )
                        FlowRow(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (kind == EquipmentKind.GUNS) "${rows.size} 种枪械" else "${rows.size} 款防具",
                                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.onSurfaceVariant
                            )
                            Surface(
                                onClick = { if (active()) { leaveSearch(); filtersOpen = true } },
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .heightIn(min = 38.dp)
                                    .semantics { role = Role.Button },
                                shape = smoothCornerShape(10.dp),
                                color = if (activeFilterCount > 0) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (activeFilterCount > 0) colors.primary.copy(alpha = 0.65f) else colors.outlineVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = if (activeFilterCount > 0) colors.primary else colors.onSurfaceVariant
                                    )
                                    Text(
                                        "筛选与排序",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (activeFilterCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (activeFilterCount > 0) colors.primary else colors.onSurface
                                    )
                                    if (activeFilterCount > 0) {
                                        Surface(
                                            shape = smoothCornerShape(5.dp),
                                            color = colors.primary,
                                            contentColor = colors.onPrimary
                                        ) {
                                            Text(
                                                activeFilterCount.toString(),
                                                Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (activeFilterCount > 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                category?.let { ActiveCondition(it, onRemove = { if (active()) category = null }) }
                                chosenQuality?.let { ActiveCondition(it.label, onRemove = { if (active()) quality = null }) }
                                if (sort != EquipmentSort.DEFAULT) {
                                    ActiveCondition("${sort.label}降序", onRemove = { if (active()) sort = EquipmentSort.DEFAULT })
                                }
                            }
                        }
                    }
                }
                if (rows.isEmpty()) item(key = "empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        CatalogEmptyResults(message = if (kind == EquipmentKind.GUNS) "没有匹配的枪械" else "没有匹配的防具", onClear = clear)
                    }
                }
                items(rows, key = { it.series.key }, contentType = { "series" }) { row ->
                    SeriesRow(kind, row, sort) {
                        if (active()) {
                            leaveSearch()
                            selectedSeries = row.series.key
                            selectedId = row.variant.id
                        }
                    }
                }
            }
        }
    }
    if (active()) modalState.SaveableStateProvider("equipment-dialogs") {
        if (filtersOpen && page.series.isNotEmpty()) {
            val filterActive = rememberActiveCallbacks { active() && filtersOpen }
            EquipmentFilters(
                kind, page, category, quality, sort,
                onCategory = { if (filterActive()) category = it },
                onQuality = { if (filterActive()) quality = it },
                onSort = { if (filterActive()) sort = it },
                onDismiss = { if (filterActive()) filtersOpen = false }
            )
        }
        page.series.find { it.key == selectedSeries }?.let { series ->
            key(series.key) {
                val item = series.variants.find { it.id == selectedId } ?: series.variants.first()
                val detailActive = rememberActiveCallbacks { active() && selectedSeries == series.key }
                EquipmentDetail(
                    kind, series, item,
                    isActive = detailActive,
                    onSelect = { if (detailActive()) selectedId = it.id },
                    onOriginal = { if (detailActive()) onOpenWikiUrl(url) },
                    onDismiss = { if (detailActive()) selectedSeries = null }
                )
            }
        }
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
    val colors = MaterialTheme.colorScheme
    InputChip(
        selected = true,
        onClick = onRemove,
        shape = smoothCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(15.dp)) },
        modifier = Modifier.semantics { onClick(label = "清除$label", action = null) }
    )
}

@Composable
private fun SeriesRow(kind: EquipmentKind, row: EquipmentRow, sort: EquipmentSort, onClick: () -> Unit) {
    val (series, variant) = row
    val colors = MaterialTheme.colorScheme
    val qualityAccent = equipmentQualityColor(variant.quality)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TacticalStyle.compactCardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = TacticalStyle.cardBorder()
    ) {
        Box(Modifier.fillMaxWidth().height(TacticalStyle.accentBarHeight).background(qualityAccent))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EquipmentThumbnail(kind, variant, Modifier.size(width = 80.dp, height = 62.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EquipmentQualityLabel(variant.quality, asBadge = true)
                    if (kind == EquipmentKind.GUNS && series.category.isNotBlank()) {
                        Surface(
                            shape = smoothCornerShape(6.dp),
                            color = colors.surfaceContainerHigh
                        ) {
                            Text(
                                series.category,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = smoothCornerShape(6.dp),
                        color = colors.surfaceContainerHigh
                    ) {
                        Text(
                            "共 ${series.variants.size} 档",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.tacticalTabular(),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (sort) {
                        EquipmentSort.VALUE -> EquipmentStatPill("价值", variant.value)
                        EquipmentSort.RATE -> EquipmentStatPill(
                            label = null,
                            value = variant.lootText.substringBefore('·').trim().ifBlank { "出现率未给出" }
                        )
                        else -> when (val stats = variant.stats) {
                            is EquipmentStats.Gun -> {
                                EquipmentStatPill("伤害", stats.damage)
                                EquipmentStatPill("弹匣", stats.magazine)
                            }
                            is EquipmentStats.Armor -> {
                                EquipmentStatPill("护甲", stats.armor)
                                EquipmentStatPill("生命", stats.health)
                            }
                        }
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EquipmentStatPill(label: String?, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = smoothCornerShape(6.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.tacticalTabular(),
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EquipmentFilters(
    kind: EquipmentKind, page: EquipmentPage, category: String?, quality: Int?, sort: EquipmentSort,
    onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onSort: (EquipmentSort) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    WikiScrollableSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("筛选与排序", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OptionGroup(if (kind == EquipmentKind.GUNS) "枪种" else "套装") {
                EquipmentFilterOption(
                    selected = category == null,
                    onClick = { onCategory(null) },
                    label = { Text(if (kind == EquipmentKind.GUNS) "全部枪种" else "全部套装") }
                )
                page.categories.forEach {
                    EquipmentFilterOption(selected = category == it, onClick = { onCategory(it) }, label = { Text(it) })
                }
            }
            OptionGroup("品质") {
                EquipmentFilterOption(selected = quality == null, onClick = { onQuality(null) }, label = { Text("全部品质") })
                page.qualities.forEach {
                    EquipmentFilterOption(
                        selected = quality == it.code,
                        onClick = { onQuality(it.code) },
                        label = { EquipmentQualityLabel(it) }
                    )
                }
            }
            OptionGroup("排序") {
                EquipmentSort.entries.filter {
                    when (it) {
                        EquipmentSort.DAMAGE -> kind == EquipmentKind.GUNS
                        EquipmentSort.ARMOR, EquipmentSort.HP -> kind == EquipmentKind.ARMOR
                        else -> true
                    }
                }.forEach {
                    EquipmentFilterOption(
                        selected = sort == it,
                        onClick = { onSort(it) },
                        label = { Text(if (it == EquipmentSort.DEFAULT) it.label else "${it.label}降序") }
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.50f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onCategory(null); onQuality(null); onSort(EquipmentSort.DEFAULT) }) { Text("重置") }
                FilledTonalButton(onClick = onDismiss, shape = smoothCornerShape(10.dp)) { Text("完成") }
            }
        }
    }
}

@Composable
private fun OptionGroup(label: String, options: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { options() }
        }
    }
}

@Composable
private fun EquipmentFilterOption(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = smoothCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.55f)
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
            selectedLabelColor = colors.onPrimaryContainer,
            containerColor = colors.surfaceContainerLow,
            labelColor = colors.onSurface
        ),
        label = label
    )
}
