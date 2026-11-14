package com.nekolaska.calabiyau.feature.wiki.decoration

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.decoration.api.PlayerDecorationApi
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import kotlinx.coroutines.launch

private data class DecorationEntryUi(val key: String, val item: DecorationItem)
private data class DecorationGroupUi(val key: String, val title: String, val entries: List<DecorationEntryUi>)

/** A searchable catalog. Data source choices stay in the API; all entries retain their original groups. */
@Composable
fun PlayerDecorationScreen(title: String, pageName: String = title, onBack: () -> Unit, isActive: () -> Boolean = { true }) {
    // A page change must discard an earlier page's load state as well as its dialogs.
    key(pageName) { DecorationCatalogScreen(title, pageName, onBack, isActive) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecorationCatalogScreen(title: String, pageName: String, onBack: () -> Unit, isActive: () -> Boolean) {
    val state = rememberLoadState(
        initial = emptyList<DecorationSection>(), key = pageName, cachedPrefetchDelayMs = 300L,
        cachedFetch = { PlayerDecorationApi.fetchDecoration(pageName, cacheOnly = true) },
        fetch = { force -> PlayerDecorationApi.fetchDecoration(pageName, forceRefresh = force, allowMemoryCache = false) }
    )
    val groups = remember(state.data) {
        val sectionKeys = catalogKeys(state.data) { it.title }
        state.data.mapIndexed { index, section ->
            val itemKeys = catalogKeys(section.items) { it.stableKey }
            DecorationGroupUi(sectionKeys[index], section.title, section.items.mapIndexed { itemIndex, item ->
                DecorationEntryUi("${sectionKeys[index]}:${itemKeys[itemIndex]}", item)
            })
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PreviewImage?>(null) }
    var saveTarget by remember { mutableStateOf<PreviewImage?>(null) }
    val showMessage = rememberSnackbarLauncher()
    val gridStates = rememberSaveableStateHolder()
    val effectiveCategory = category?.takeIf { id -> groups.any { it.key == id } }
    LaunchedEffect(groups, state.isLoading, state.isShowingCachedPrefetch, state.error) {
        if (groups.isNotEmpty() && !state.isLoading && !state.isShowingCachedPrefetch && state.error == null) {
            category = effectiveCategory
        }
    }
    val filtered = remember(groups, query, effectiveCategory) {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        groups.filter { effectiveCategory == null || it.key == effectiveCategory }.mapNotNull { group ->
            val entries = group.entries.filter { entry ->
                val item = entry.item
                val text = "${group.title} ${item.name} ${item.description} ${item.specialDescription} ${item.source}"
                terms.all { text.contains(it, ignoreCase = true) }
            }
            group.copy(entries = entries).takeIf { entries.isNotEmpty() }
        }
    }
    val selectedGroup = groups.firstOrNull { it.key == effectiveCategory }
    val categoryLabel = selectedGroup?.title ?: "全部分类"
    val hasCategories = groups.size > 1 || pageName in setOf("基板", "喷漆", "勋章")
    val selectedItem = groups.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { it.key == detailKey }?.item
    val notices = state.data.mapNotNull { it.notice }.distinct()

    Scaffold(topBar = {
        TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) },
            actions = { RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading) })
    }) { padding ->
        gridStates.SaveableStateProvider(category ?: "all") {
            val gridState = rememberLazyGridState()
            val scope = rememberCoroutineScope()
            val keyboard = LocalSoftwareKeyboardController.current
            fun updateQuery(value: String) {
                query = value
                scope.launch { gridState.scrollToItem(0) }
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBar(modifier = Modifier.weight(1f), keyword = query, onKeywordChange = ::updateQuery,
                        onSearch = { keyboard?.hide() }, onClear = { updateQuery("") }, isSearching = false,
                        placeholder = "搜索名称或获取方式…")
                    if (hasCategories) FilledTonalIconButton(
                        onClick = { keyboard?.hide(); showCategories = true },
                        enabled = groups.size > 1,
                        modifier = Modifier.size(48.dp),
                        shape = AppShapes.compactCard,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (effectiveCategory != null)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        BadgedBox(badge = { if (effectiveCategory != null) Badge() }) {
                            Icon(Icons.Outlined.Tune, "筛选分类，当前$categoryLabel")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = AppShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            if (state.isLoading && groups.isEmpty()) "正在读取图鉴…"
                            else "$categoryLabel · ${filtered.sumOf { it.entries.size }} 条记录",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (query.isNotBlank() || effectiveCategory != null) TextButton(onClick = {
                        updateQuery(""); category = null
                    }) { Text("重置") }
                }
                ApiResourceContent(state, modifier = Modifier.weight(1f), loading = { DecorationCatalogSkeleton(it, hasCategories) }) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), state = gridState,
                        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.error != null) item("error", span = { GridItemSpan(maxLineSpan) }) {
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
                                        Icons.Outlined.ErrorOutline, contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "更新未成功，保留上次资料。${state.error?.message.orEmpty()}",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    TextButton(onClick = { state.reload(forceRefresh = true) }) { Text("重试") }
                                }
                            }
                        }
                        if (notices.isNotEmpty()) item("notice", span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                shape = AppShapes.compactCard,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Info, contentDescription = null,
                                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        notices.joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (filtered.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                            CatalogEmptyResults(message = "没有匹配的装饰", onClear = { updateQuery(""); category = null })
                        }
                        filtered.forEach { group ->
                            if (groups.size > 1) item("heading:${group.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).semantics { heading() },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        Modifier.width(4.dp).height(16.dp)
                                            .background(MaterialTheme.colorScheme.primary, smoothCornerShape(2.dp))
                                    )
                                    Text(
                                        group.title,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = AppShapes.chip,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            "${group.entries.size}",
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            items(group.entries, key = { it.key }, contentType = { "decoration" }) { entry ->
                                DecorationCatalogCard(entry.item, modifier = Modifier.animateItem(
                                    fadeInSpec = tween(160), placementSpec = tween(200), fadeOutSpec = tween(100)),
                                    onClick = { if (isActive()) { keyboard?.hide(); detailKey = entry.key } })
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCategories && isActive()) WikiSelectionSheet(title = "选择分类", onDismiss = { showCategories = false }) {
        item("all") { SelectionOptionRow(label = "全部分类", selected = effectiveCategory == null,
            onClick = { category = null; showCategories = false }) }
        items(groups, key = { it.key }) { group ->
            SelectionOptionRow(label = "${group.title} · ${group.entries.size}", selected = effectiveCategory == group.key,
                onClick = { category = group.key; showCategories = false })
        }
    }
    if (selectedItem != null && isActive()) DecorationDetailSheet(selectedItem, onDismiss = { detailKey = null },
        onPreview = { if (isActive() && it.url.isNotBlank()) preview = it })
    preview?.takeIf { isActive() }?.let { image ->
        ImagePreviewDialog(model = image.url, contentDescription = image.title, onDismiss = { preview = null },
            onSave = { if (isActive()) saveTarget = image })
    }
    saveTarget?.takeIf { isActive() }?.let { image ->
        ConfirmImageSaveDialog(url = image.url, label = image.title, onDismiss = { saveTarget = null }, showMessage = showMessage)
    }
}
