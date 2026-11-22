package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.api.WorkshopApi
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl

internal enum class WorkshopTab(val title: String) { RECIPES("配方"), ROOMS("舱室"), STAFF("派驻"), DECOR("装修与收藏") }
private enum class WorkshopPanel { NOTES, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionWorkshopScreen(onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit, isActive: () -> Boolean = { true }) {
    val state = rememberLoadState(initial = WorkshopPage(), cachedPrefetchDelayMs = 300,
        cachedFetch = { WorkshopApi.fetch(cacheOnly = true) },
        fetch = { WorkshopApi.fetch(forceRefresh = it, allowMemoryCache = false) })
    var tab by rememberSaveable { mutableStateOf(WorkshopTab.RECIPES) }
    val tabStates = rememberSaveableStateHolder()
    val panelStates = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val active = isActive()
    var query by rememberSaveable { mutableStateOf("") }
    // The Web overlay removes the modal window, not its selected tier or reading position.
    var panel by rememberSaveable(tab) { mutableStateOf<WorkshopPanel?>(null) }
    var selectedId by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    var quality by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    val currentActive by rememberUpdatedState(isActive)
    // Invalidate callbacks captured by the old modal even if the same detail is shown again.
    val displayedPanel = panel
    val displayedId = selectedId
    val inputOwner = remember(active, tab, displayedPanel, displayedId) { mutableStateOf(active) }
    DisposableEffect(inputOwner) { onDispose { inputOwner.value = false } }
    fun acceptsInput() = inputOwner.value && currentActive() && panel == displayedPanel && selectedId == displayedId
    val wikiUrl = remember { extractionArticleUrl(WORKSHOP_PAGE) }
    fun openOriginal() { if (acceptsInput()) { focus.clearFocus(); onOpenWikiUrl(wikiUrl) } }
    fun dismissPanel() { if (acceptsInput()) { panelStates.removeState("panel"); panel = null } }
    fun openPanel(target: WorkshopPanel, id: String? = null, tier: String? = null) {
        if (!acceptsInput()) return
        panelStates.removeState("panel")
        focus.clearFocus(); selectedId = id; quality = tier; panel = target
    }
    // Toggling pull-to-refresh for the web overlay changes the content's parent. Move the
    // live tab with it: remounting the same saveable key in a new branch resets its controls.
    val keepTab = remember { movableContentOf<@Composable () -> Unit> { content -> content() } }
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("工坊", fontWeight = FontWeight.Bold) }, navigationIcon = { BackNavButton(onClick = {
                if (acceptsInput()) { focus.clearFocus(); onBack() }
            }) },
                actions = {
                    RefreshActionButton(onClick = { if (acceptsInput()) state.reload(forceRefresh = true) }, enabled = active && !state.isLoading)
                    OpenWikiActionButton(wikiUrl = wikiUrl, onOpenWikiUrl = { openOriginal() }, contentDescription = "查看 Wiki 原文")
                })
            SecondaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 4.dp) {
                WorkshopTab.entries.forEach { choice -> Tab(selected = tab == choice,
                    onClick = { if (acceptsInput()) { focus.clearFocus(); tab = choice } }, text = { Text(choice.title) }) }
            }
        }
    }) { padding ->
        ApiResourceContent(state, Modifier.padding(padding).consumeWindowInsets(padding).imePadding(), isDataEmpty = { it.recipes.isEmpty() },
            enablePullToRefresh = active, loading = { WorkshopSkeleton(tab, it) }) { page ->
            keepTab {
                tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        WorkshopTab.RECIPES -> WorkshopRecipes(page, query, acceptsInput = ::acceptsInput, onQuery = { query = it },
                            onNotes = { openPanel(WorkshopPanel.NOTES) }, onRecipe = { match ->
                                openPanel(WorkshopPanel.DETAIL, match.recipe.id, match.materialTiers.firstOrNull()?.quality)
                            })
                        WorkshopTab.ROOMS -> WorkshopRooms(page, onNotes = { openPanel(WorkshopPanel.NOTES) },
                            onRoom = { openPanel(WorkshopPanel.DETAIL, it.overview.name) })
                        WorkshopTab.STAFF -> WorkshopStaff(page, ::acceptsInput)
                        WorkshopTab.DECOR -> WorkshopDecorations(page, ::acceptsInput)
                    }
                }
            }
        }
    }
    val page = state.data
    val recipe = if (panel == WorkshopPanel.DETAIL && tab == WorkshopTab.RECIPES) page.recipes.find { it.id == selectedId } else null
    val room = if (panel == WorkshopPanel.DETAIL && tab == WorkshopTab.ROOMS) page.rooms.find { it.overview.name == selectedId } else null
    // A successful refresh may remove an entry while its source article is open. An empty
    // prefetch is not authoritative and must not erase a restored selection on rotation.
    val removed = panel == WorkshopPanel.DETAIL && recipe == null && room == null && page.recipes.isNotEmpty() &&
        !state.isLoading && !state.isShowingCachedPrefetch && state.error == null
    LaunchedEffect(active, removed, selectedId) { if (removed && active) dismissPanel() }
    if (active && panel != null && page.recipes.isNotEmpty()) panelStates.SaveableStateProvider("panel") {
        when (panel) {
            WorkshopPanel.NOTES -> WikiScrollableSheet(onDismiss = ::dismissPanel) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == WorkshopTab.RECIPES) {
                        WorkshopTitle("配方说明"); Text(page.recipeNotes)
                    } else {
                        WorkshopTitle("舱室说明"); Text(page.intro)
                        DetailField(label = "舱室用途", value = page.roomNotes)
                        DetailField(label = "升级规则", value = page.upgradeNotes)
                    }
                    WorkshopSheetActions(::openOriginal, ::dismissPanel)
                }
            }
            WorkshopPanel.DETAIL -> when {
                recipe != null -> {
                    val tier = recipe.tiers.find { it.quality == quality } ?: recipe.tiers.first()
                    WorkshopRecipeSheet(recipe, tier, query, onQuality = { if (acceptsInput()) quality = it },
                        onOriginal = ::openOriginal, onDismiss = ::dismissPanel)
                }
                room != null -> WorkshopRoomSheet(room, ::openOriginal, ::dismissPanel)
                else -> Unit
            }
            null -> Unit
        }
    }
}
