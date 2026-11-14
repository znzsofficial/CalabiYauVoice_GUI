package com.nekolaska.calabiyau.feature.wiki.gamesettings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.feature.wiki.gamesettings.api.PcGameSettingsApi
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PC_GAME_SETTINGS_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcGameSettingsPage
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.filteredSections
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcGameSettingsScreen(onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit) {
    val state = rememberLoadState(
        initial = PcGameSettingsPage(),
        cachedFetch = { PcGameSettingsApi.fetch(cacheOnly = true) },
        fetch = { force -> PcGameSettingsApi.fetch(forceRefresh = force, allowMemoryCache = false) }
    )
    var view by rememberSaveable { mutableStateOf(PcSettingsView.SETTINGS) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val viewStates = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        title = { Text("游戏设置（PC）", fontWeight = FontWeight.Bold) },
                        navigationIcon = { BackNavButton(onClick = onBack) },
                        actions = {
                            RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                            OpenWikiActionButton(wikiUrl = PC_GAME_SETTINGS_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                        }
                    )
                    PrimaryTabRow(selectedTabIndex = view.ordinal, containerColor = Color.Transparent) {
                        PcSettingsView.entries.forEach { option ->
                            Tab(selected = view == option, onClick = {
                                keyboard?.hide()
                                view = option
                            }, text = { Text(option.label, fontWeight = if (view == option) FontWeight.SemiBold else FontWeight.Normal) })
                        }
                    }
                }
            }
        }
    ) { padding ->
        viewStates.SaveableStateProvider(view.name) {
            var query by rememberSaveable { mutableStateOf("") }
            var sectionId by rememberSaveable { mutableStateOf("") }
            var showSections by rememberSaveable { mutableStateOf(false) }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val sections = remember(state.data, view) { state.data.sections.filter { it.view == view } }
            // Empty prefetch never erases a restored category. Only the effective filter falls back.
            val effectiveSectionId = sectionId.takeIf { id -> sections.any { it.id == id } }.orEmpty()
            val visible = remember(state.data, view, effectiveSectionId, query) {
                state.data.filteredSections(view, effectiveSectionId, query)
            }
            val categoryTitle = sections.firstOrNull { it.id == effectiveSectionId }?.title ?: "全部分类"
            val entryCount = if (view == PcSettingsView.KNOWLEDGE) visible.sumOf { it.blocks.size }
                else visible.sumOf { section -> section.blocks.count { it is PcSettingsBlock.Setting } }
            val loading = state.isLoading && state.data.isEmpty()
            val entrance = remember { Animatable(1f) }
            LaunchedEffect(view) {
                entrance.snapTo(0f)
                entrance.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            }
            fun resetScroll() { scope.launch { listState.scrollToItem(0) } }
            val listHeader: @Composable () -> Unit = {
                PcSettingsListHeader(view, categoryTitle,
                    countLabel = if (loading) "正在读取…" else "$entryCount ${if (view == PcSettingsView.KNOWLEDGE) "条资料" else "个设置"}",
                    onReset = if (query.isNotBlank() || effectiveSectionId.isNotEmpty()) ({
                        query = ""; sectionId = ""; resetScroll()
                    }) else null)
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchBar(
                        modifier = Modifier.weight(1f), keyword = query,
                        onKeywordChange = { query = it; resetScroll() },
                        onSearch = { keyboard?.hide() }, onClear = { query = ""; resetScroll() },
                        isSearching = false,
                        placeholder = when (view) {
                            PcSettingsView.SETTINGS -> "搜索设置、按键…"
                            PcSettingsView.RECOMMENDATIONS -> "搜索画质推荐…"
                            PcSettingsView.KNOWLEDGE -> "搜索 DX12、DLSS…"
                        }
                    )
                    FilledTonalIconButton(
                        onClick = { keyboard?.hide(); showSections = true },
                        enabled = sections.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                        shape = AppShapes.compactCard,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (effectiveSectionId.isNotEmpty())
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        BadgedBox(badge = { if (effectiveSectionId.isNotEmpty()) Badge() }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "筛选分类，当前$categoryTitle")
                        }
                    }
                }
                ApiResourceContent(
                    state = state, modifier = Modifier.weight(1f).graphicsLayer { alpha = entrance.value },
                    isDataEmpty = PcGameSettingsPage::isEmpty,
                    loading = { PcSettingsSkeleton(modifier = it, knowledge = view == PcSettingsView.KNOWLEDGE, header = listHeader) }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.error != null) item(key = "refresh-error") {
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
                                        "更新失败，正在显示上次资料。${state.error?.message.orEmpty()}",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    TextButton(onClick = { state.reload(forceRefresh = true) }) { Text("重试") }
                                }
                            }
                        }
                        item(key = "source-note") { listHeader() }
                        if (visible.isEmpty()) item(key = "empty") {
                            CatalogEmptyResults(message = "当前${view.label}中没有匹配内容", onClear = {
                                query = ""; sectionId = ""; resetScroll()
                            })
                        }
                        pcSettingsItems(visible, query, onSelect = { detailId = it.id })
                    }
                }
            }
            if (showSections) WikiSelectionSheet(title = "选择${view.label}分类", onDismiss = { showSections = false }) {
                item(key = "all") {
                    SelectionOptionRow(label = "全部分类", selected = effectiveSectionId.isEmpty(), onClick = {
                        sectionId = ""; showSections = false; resetScroll()
                    })
                }
                items(sections, key = { it.id }) { section ->
                    SelectionOptionRow(label = section.title, selected = effectiveSectionId == section.id, onClick = {
                        sectionId = section.id; showSections = false; resetScroll()
                    })
                }
            }
        }
    }
    val detailSection = state.data.sections.firstOrNull { section -> section.blocks.any { it.id == detailId } }
    val detail = detailSection?.blocks?.filterIsInstance<PcSettingsBlock.Setting>()?.firstOrNull { it.id == detailId }
    if (detail != null) PcSettingsDetailSheet(
        setting = detail, section = detailSection, onDismiss = { detailId = null }
    )
}
