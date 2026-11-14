package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.PreviewImage
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SimpleDropdownSelector
import com.nekolaska.calabiyau.core.ui.WikiScrollableSheet
import com.nekolaska.calabiyau.core.ui.catalogKeys
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioCardApi
import com.nekolaska.calabiyau.feature.wiki.bio.model.CardPageData
import com.nekolaska.calabiyau.feature.wiki.bio.model.CardRefreshProbability
import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.MobileCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.SharedDeck
import com.nekolaska.calabiyau.feature.wiki.bio.model.SubmitDeckPayload
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareParsers
import com.nekolaska.calabiyau.feature.wiki.hub.hasWikiLoginCookie
import data.ApiResult
import kotlinx.coroutines.launch
import com.nekolaska.calabiyau.feature.wiki.bio.model.BioCatalogTab as BioCardTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioCardScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    initialTab: Int = 0
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var selectedTab by rememberSaveable(initialTab) {
        mutableStateOf(when (initialTab) { 1 -> BioCardTab.MOBILE; 2 -> BioCardTab.DECK; else -> BioCardTab.PC })
    }
    val state = key(selectedTab) {
        rememberLoadState(
            initial = CardPageData(
                pcCards = emptyList(),
                mobileCards = emptyList(),
                decks = emptyList(),
                pcWikiUrl = "",
                mobileWikiUrl = "",
                deckWikiUrl = ""
            ),
            key = selectedTab
        ) { force ->
            BioCardApi.fetchTab(selectedTab, force)
        }
    }
    val showSnack = rememberSnackbarLauncher()
    val copyText = rememberPlainTextClipboardCopier { showSnack("已复制分享码") }
    var isWikiLoggedIn by remember { mutableStateOf(hasWikiLoginCookie()) }
    val submission = remember { BioDeckSubmissionState() }
    // Own the draft outside lazy/conditional content: tabs, loading and errors may remove that subtree.
    val deckEditor = rememberSaveable(saver = BioDeckEditorState.Saver) { BioDeckEditorState() }
    val refreshPublishedDeck by rememberUpdatedState(newValue = {
        if (selectedTab == BioCardTab.DECK) state.reload(forceRefresh = true)
    })
    val isSubmittingDeck = submission.isSubmitting

    var pcKeyword by rememberSaveable { mutableStateOf("") }
    var mobileKeyword by rememberSaveable { mutableStateOf("") }
    var deckKeyword by rememberSaveable { mutableStateOf("") }
    var pcFaction by rememberSaveable { mutableStateOf("全部阵营") }
    var pcCategory by rememberSaveable { mutableStateOf("全部分类") }
    var pcRarity by rememberSaveable { mutableIntStateOf(0) }
    var mobileFaction by rememberSaveable { mutableStateOf("全部阵营") }
    var mobileCategory by rememberSaveable { mutableStateOf("全部分类") }
    var mobileRarity by rememberSaveable { mutableIntStateOf(0) }
    var deckFaction by rememberSaveable { mutableStateOf("全部卡组") }

    var selectedPcCard by remember { mutableStateOf<PcCard?>(null) }
    var selectedMobileCard by remember { mutableStateOf<MobileCard?>(null) }
    var selectedDeck by remember { mutableStateOf<SharedDeck?>(null) }
    var previewImage by remember { mutableStateOf<PreviewImage?>(null) }
    var isComposerExpanded by rememberSaveable { mutableStateOf(false) }
    var showCardSelector by rememberSaveable { mutableStateOf(false) }
    val tabStates = rememberSaveableStateHolder()

    val data = state.data
    val pcKeys = remember(data.pcCards) {
        val cards = data.pcCards.distinct()
        cards.zip(catalogKeys(cards) { "${it.cardId}:${it.faction}:${it.category}:${it.name}" }).toMap()
    }
    val mobileKeys = remember(data.mobileCards) {
        val cards = data.mobileCards.distinct()
        cards.zip(catalogKeys(cards) { "${it.faction}:${it.category}:${it.name}" }).toMap()
    }
    val deckKeys = remember(data.decks) {
        val decks = data.decks.distinct()
        decks.zip(catalogKeys(decks) { "${it.faction}:${it.title}:${it.author}" }).toMap()
    }
    val completeDeckCardMap = remember(data.deckCardMap, data.pcCards) {
        BioDeckShareParsers.completeFromPcCatalog(data.deckCardMap, data.pcCards)
    }
    val pcFactions = remember(data.pcCards) { listOf("全部阵营") + data.pcCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val pcCategories = remember(data.pcCards) { listOf("全部分类") + data.pcCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val mobileFactions = remember(data.mobileCards) { listOf("全部阵营") + data.mobileCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val mobileCategories = remember(data.mobileCards) { listOf("全部分类") + data.mobileCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val deckFactions = remember(data.decks) { listOf("全部卡组") + data.decks.map { it.faction }.filter { it.isNotBlank() }.distinct() }

    val filteredPc = remember(data.pcCards, pcKeyword, pcFaction, pcCategory, pcRarity) {
        val term = pcKeyword.trim()
        data.pcCards.distinct().filter {
            (term.isEmpty() || it.name.contains(term, true) ||
                it.effect.contains(term, true) ||
                it.roles.any { role -> role.contains(term, true) }) &&
                (pcFaction == "全部阵营" || it.faction == pcFaction) &&
                (pcCategory == "全部分类" || it.category == pcCategory) &&
                (pcRarity == 0 || it.rarity == pcRarity)
        }
    }
    val filteredMobile = remember(data.mobileCards, mobileKeyword, mobileFaction, mobileCategory, mobileRarity) {
        val term = mobileKeyword.trim()
        data.mobileCards.distinct().filter {
            (term.isEmpty() || it.name.contains(term, true) ||
                it.effect.contains(term, true)) &&
                (mobileFaction == "全部阵营" || it.faction == mobileFaction) &&
                (mobileCategory == "全部分类" || it.category == mobileCategory) &&
                (mobileRarity == 0 || it.rarity == mobileRarity)
        }
    }
    val filteredDecks = remember(data.decks, deckKeyword, deckFaction) {
        val term = deckKeyword.trim()
        data.decks.distinct().filter {
            (term.isEmpty() || it.title.contains(term, true) ||
                it.author.contains(term, true) ||
                it.intro.contains(term, true) ||
                it.cardNames.any { card -> card.contains(term, true) }) &&
                (deckFaction == "全部卡组" || it.faction == deckFaction)
        }
    }

    var previousTab by remember { mutableStateOf(selectedTab) }
    val tabEntrance = remember { Animatable(1f) }
    LaunchedEffect(selectedTab) {
        if (previousTab != selectedTab) {
            previousTab = selectedTab
            tabEntrance.snapTo(0f)
            tabEntrance.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            // One shared backing layer: no opaque scaffold or stacked tints masking the Wiki wallpaper.
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        title = {
                            Text("生化卡牌", fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            BackNavButton(onClick = onBack)
                        },
                        actions = {
                            RefreshActionButton(
                                onClick = { state.reload(forceRefresh = true) },
                                enabled = !state.isLoading
                            )
                            OpenWikiActionButton(
                                wikiUrl = when (selectedTab) {
                                    BioCardTab.PC -> data.pcWikiUrl
                                    BioCardTab.MOBILE -> data.mobileWikiUrl
                                    BioCardTab.DECK -> data.deckWikiUrl
                                },
                                onOpenWikiUrl = onOpenWikiUrl
                            )
                        }
                    )
                    BioTabBar(selectedTab) { tab ->
                        keyboard?.hide()
                        selectedPcCard = null
                        selectedMobileCard = null
                        selectedDeck = null
                        selectedTab = tab
                    }
                }
            }
        }
    ) { innerPadding ->
        tabStates.SaveableStateProvider(selectedTab.name) {
            val listState = rememberLazyListState()
            var showFilterSheet by rememberSaveable { mutableStateOf(false) }

            val currentKeyword = when (selectedTab) {
                BioCardTab.PC -> pcKeyword
                BioCardTab.MOBILE -> mobileKeyword
                BioCardTab.DECK -> deckKeyword
            }
            val hasActiveFilter = when (selectedTab) {
                BioCardTab.PC -> pcFaction != "全部阵营" || pcCategory != "全部分类" || pcRarity != 0
                BioCardTab.MOBILE -> mobileFaction != "全部阵营" || mobileCategory != "全部分类" || mobileRarity != 0
                BioCardTab.DECK -> deckFaction != "全部卡组"
            }
            val canOpenFilter = when (selectedTab) {
                BioCardTab.PC -> data.pcCards.isNotEmpty()
                BioCardTab.MOBILE -> data.mobileCards.isNotEmpty()
                BioCardTab.DECK -> deckFactions.size > 1
            }
            val isTabEmpty = data.loadedTab != selectedTab || when (selectedTab) {
                BioCardTab.PC -> data.pcCards.isEmpty()
                BioCardTab.MOBILE -> data.mobileCards.isEmpty()
                BioCardTab.DECK -> data.deckWikiUrl.isBlank()
            }
            val summaryText = when {
                state.isLoading && isTabEmpty -> "正在读取…"
                state.error != null && isTabEmpty -> "加载失败"
                else -> when (selectedTab) {
                    BioCardTab.PC -> buildCardSummary(pcFaction, pcCategory, pcRarity, filteredPc.size)
                    BioCardTab.MOBILE -> buildCardSummary(mobileFaction, mobileCategory, mobileRarity, filteredMobile.size)
                    BioCardTab.DECK -> "$deckFaction · ${filteredDecks.size} 个卡组"
                }
            }

            fun scrollTop() {
                scope.launch { listState.scrollToItem(0) }
            }

            fun updateKeyword(value: String) {
                when (selectedTab) {
                    BioCardTab.PC -> pcKeyword = value
                    BioCardTab.MOBILE -> mobileKeyword = value
                    BioCardTab.DECK -> deckKeyword = value
                }
                scrollTop()
            }

            fun resetFilters() {
                when (selectedTab) {
                    BioCardTab.PC -> {
                        pcKeyword = ""
                        pcFaction = "全部阵营"
                        pcCategory = "全部分类"
                        pcRarity = 0
                    }
                    BioCardTab.MOBILE -> {
                        mobileKeyword = ""
                        mobileFaction = "全部阵营"
                        mobileCategory = "全部分类"
                        mobileRarity = 0
                    }
                    BioCardTab.DECK -> {
                        deckKeyword = ""
                        deckFaction = "全部卡组"
                    }
                }
                scrollTop()
            }

            val entranceOffsetPx = with(LocalDensity.current) { 10.dp.toPx() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    SearchBar(
                        modifier = Modifier.weight(1f),
                        keyword = currentKeyword,
                        onKeywordChange = ::updateKeyword,
                        onSearch = { keyboard?.hide() },
                        onClear = { updateKeyword("") },
                        isSearching = false,
                        placeholder = when (selectedTab) {
                            BioCardTab.PC -> "搜索卡牌、效果或角色"
                            BioCardTab.MOBILE -> "搜索卡牌或效果"
                            BioCardTab.DECK -> "搜索卡组、作者或卡牌"
                        }
                    )
                    FilledTonalIconButton(
                        onClick = {
                            keyboard?.hide()
                            showFilterSheet = true
                        },
                        enabled = canOpenFilter,
                        modifier = Modifier.size(48.dp),
                        shape = AppShapes.compactCard,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (hasActiveFilter) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                    ) {
                        BadgedBox(badge = { if (hasActiveFilter) Badge() }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "筛选")
                        }
                    }
                }

                BioFilterStatusRow(
                    summary = summaryText,
                    hasActiveFilter = hasActiveFilter || currentKeyword.isNotBlank(),
                    onReset = ::resetFilters,
                    modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = AppSpacing.xSmall)
                )

                ApiResourceContent(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = tabEntrance.value
                            translationY = (1f - tabEntrance.value) * entranceOffsetPx
                        },
                    isDataEmpty = {
                        it.loadedTab != selectedTab || when (selectedTab) {
                            BioCardTab.PC -> it.pcCards.isEmpty()
                            BioCardTab.MOBILE -> it.mobileCards.isEmpty()
                            BioCardTab.DECK -> it.deckWikiUrl.isBlank()
                        }
                    },
                    enablePullToRefresh = false,
                    loading = { mod -> BioCardSkeleton(selectedTab, mod) }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppSpacing.screen,
                            end = AppSpacing.screen,
                            top = AppSpacing.small,
                            bottom = AppSpacing.large
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedTab == BioCardTab.DECK) {
                            item(key = "deck-composer", contentType = "deck-composer") {
                                DeckShareComposerCard(
                                    editor = deckEditor,
                                    isExpanded = isComposerExpanded,
                                    onExpandedChange = { isComposerExpanded = it },
                                    showCardSelector = showCardSelector,
                                    onShowCardSelectorChange = { showCardSelector = it },
                                    isLoggedIn = isWikiLoggedIn,
                                    isSubmitting = isSubmittingDeck,
                                    cardMap = completeDeckCardMap,
                                    pcCards = data.pcCards,
                                    mobileCards = data.mobileCards,
                                    isLoadingCardMap = state.isLoading,
                                    cardMapError = data.deckEditorError,
                                    onReloadCardMap = { state.reload(forceRefresh = true) },
                                    onOpenLogin = {
                                        onOpenWikiUrl("https://wiki.biligame.com/wiki/Special:UserLogin?returnto=%E6%99%B6%E6%BA%90%E6%84%9F%E6%9F%93%E5%8D%A1%E7%BB%84%E5%88%86%E4%BA%AB")
                                    },
                                    onRefreshLoginState = { isWikiLoggedIn = hasWikiLoginCookie() },
                                    onShowMessage = showSnack,
                                    onSubmit = { payload ->
                                        scope.launch {
                                            when (val result = submission.publish(payload, completeDeckCardMap)) {
                                                is ApiResult.Success -> {
                                                    showSnack(
                                                        if (result.value.isPending) "发布成功，已进入审核队列"
                                                        else "发布成功"
                                                    )
                                                    refreshPublishedDeck()
                                                }
                                                is ApiResult.Error -> showSnack(result.message)
                                                null -> Unit
                                            }
                                            isWikiLoggedIn = hasWikiLoginCookie()
                                        }
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            BioCardTab.PC -> {
                                if (filteredPc.isEmpty()) {
                                    item(key = "empty-pc") {
                                        EmptyBioCardState(
                                            text = "没有匹配的 PC 卡牌",
                                            onClear = if (hasActiveFilter || currentKeyword.isNotBlank()) ::resetFilters else null
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        filteredPc,
                                        key = { _, card -> "pc:${checkNotNull(pcKeys[card])}" },
                                        contentType = { _, _ -> "bio-card" }
                                    ) { _, card ->
                                        val isTalent = card.category.contains("天赋")
                                        val accentColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)
                                        val badgeLabel = if (isTalent) card.category else rarityLabel(card.rarity)
                                        val meta = listOfNotNull(
                                            card.faction.takeIf { it.isNotBlank() },
                                            card.category.takeIf { it.isNotBlank() && !isTalent },
                                            card.maxLevel.takeIf { it.isNotBlank() }?.let { "满级 $it" },
                                            card.defaultTag.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ")
                                        BioCardItem(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = tween(180),
                                                placementSpec = tween(220, easing = FastOutSlowInEasing),
                                                fadeOutSpec = tween(120)
                                            ),
                                            imageUrl = card.imageUrl,
                                            title = card.name,
                                            meta = meta,
                                            effect = card.effect,
                                            badge = badgeLabel,
                                            accentColor = accentColor,
                                            fallbackIcon = Icons.Outlined.SportsEsports,
                                            onClick = { selectedPcCard = card }
                                        )
                                    }
                                }
                            }

                            BioCardTab.MOBILE -> {
                                if (filteredMobile.isEmpty()) {
                                    item(key = "empty-mobile") {
                                        EmptyBioCardState(
                                            text = "没有匹配的移动端卡牌",
                                            onClear = if (hasActiveFilter || currentKeyword.isNotBlank()) ::resetFilters else null
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        filteredMobile,
                                        key = { _, card -> "mobile:${checkNotNull(mobileKeys[card])}" },
                                        contentType = { _, _ -> "bio-card" }
                                    ) { _, card ->
                                        val isTalent = card.category.contains("天赋")
                                        val accentColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)
                                        val badgeLabel = if (isTalent) card.category else rarityLabel(card.rarity)
                                        val meta = listOfNotNull(
                                            card.faction.takeIf { it.isNotBlank() },
                                            card.category.takeIf { it.isNotBlank() && !isTalent },
                                            card.maxLevel.takeIf { it.isNotBlank() }?.let { "满级 $it" }
                                        ).joinToString(" · ")
                                        BioCardItem(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = tween(180),
                                                placementSpec = tween(220, easing = FastOutSlowInEasing),
                                                fadeOutSpec = tween(120)
                                            ),
                                            imageUrl = card.imageUrl,
                                            title = card.name,
                                            meta = meta,
                                            effect = card.effect,
                                            badge = badgeLabel,
                                            accentColor = accentColor,
                                            fallbackIcon = Icons.Outlined.PhoneAndroid,
                                            onClick = { selectedMobileCard = card }
                                        )
                                    }
                                }
                            }

                            BioCardTab.DECK -> {
                                if (filteredDecks.isEmpty()) {
                                    item(key = "empty-deck") {
                                        EmptyBioCardState(
                                            text = "没有匹配的卡组",
                                            onClear = if (hasActiveFilter || currentKeyword.isNotBlank()) ::resetFilters else null
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        filteredDecks,
                                        key = { _, deck -> "deck:${checkNotNull(deckKeys[deck])}" },
                                        contentType = { _, _ -> "bio-deck" }
                                    ) { _, deck ->
                                        BioDeckItem(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = tween(180),
                                                placementSpec = tween(220, easing = FastOutSlowInEasing),
                                                fadeOutSpec = tween(120)
                                            ),
                                            deck = deck,
                                            onClick = { selectedDeck = deck },
                                            onCopyShareId = { copyText("分享码", deck.shareId) }
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "bottom-nav-spacer") {
                            Spacer(Modifier.navigationBarsPadding().height(AppSpacing.medium))
                        }
                    }
                }
            }

            if (showFilterSheet) {
                when (selectedTab) {
                    BioCardTab.PC -> BioCardFilterSheet(
                        factions = pcFactions,
                        categories = pcCategories,
                        selectedFaction = pcFaction,
                        selectedCategory = pcCategory,
                        selectedRarity = pcRarity,
                        onFactionChange = { pcFaction = it; scrollTop() },
                        onCategoryChange = { pcCategory = it; scrollTop() },
                        onRarityChange = { pcRarity = it; scrollTop() },
                        onReset = {
                            pcFaction = "全部阵营"
                            pcCategory = "全部分类"
                            pcRarity = 0
                            scrollTop()
                        },
                        onDismiss = { showFilterSheet = false }
                    )

                    BioCardTab.MOBILE -> BioCardFilterSheet(
                        factions = mobileFactions,
                        categories = mobileCategories,
                        selectedFaction = mobileFaction,
                        selectedCategory = mobileCategory,
                        selectedRarity = mobileRarity,
                        onFactionChange = { mobileFaction = it; scrollTop() },
                        onCategoryChange = { mobileCategory = it; scrollTop() },
                        onRarityChange = { mobileRarity = it; scrollTop() },
                        onReset = {
                            mobileFaction = "全部阵营"
                            mobileCategory = "全部分类"
                            mobileRarity = 0
                            scrollTop()
                        },
                        onDismiss = { showFilterSheet = false }
                    )

                    BioCardTab.DECK -> BioDeckFilterSheet(
                        factions = deckFactions,
                        selectedFaction = deckFaction,
                        onFactionChange = { deckFaction = it; scrollTop() },
                        onReset = {
                            deckFaction = "全部卡组"
                            scrollTop()
                        },
                        onDismiss = { showFilterSheet = false }
                    )
                }
            }

            selectedPcCard?.let { card ->
                PcCardDetailSheet(
                    card = card,
                    onPreviewImage = { previewImage = it },
                    onDismiss = { selectedPcCard = null }
                )
            }
            selectedMobileCard?.let { card ->
                MobileCardDetailSheet(
                    card = card,
                    onPreviewImage = { previewImage = it },
                    onDismiss = { selectedMobileCard = null }
                )
            }
            selectedDeck?.let { deck ->
                DeckDetailSheet(
                    deck = deck,
                    onDismiss = { selectedDeck = null },
                    onCopyShareId = { copyText("分享码", deck.shareId) }
                )
            }
            previewImage?.let { preview ->
                ImagePreviewDialog(
                    model = preview.url,
                    contentDescription = preview.title,
                    onDismiss = { previewImage = null }
                )
            }
        }
    }
}

private fun buildCardSummary(faction: String, category: String, rarity: Int, count: Int): String {
    val activeParts = buildList {
        if (faction != "全部阵营") add(faction)
        if (category != "全部分类") add(category)
        if (rarity != 0) add(rarityLabel(rarity))
    }
    val prefix = if (activeParts.isEmpty()) "全部卡牌" else activeParts.joinToString(" · ")
    return "$prefix · $count 张"
}

@Composable
private fun BioTabBar(selected: BioCardTab, onSelect: (BioCardTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = Color.Transparent) {
        listOf(
            BioCardTab.PC to "PC 卡牌",
            BioCardTab.MOBILE to "移动端卡牌",
            BioCardTab.DECK to "卡组分享"
        ).forEach { (tab, label) ->
            val isSelected = selected == tab
            Tab(
                selected = isSelected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun BioCardSkeleton(tab: BioCardTab, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (tab == BioCardTab.DECK) {
            item(key = "deck-composer-skeleton") {
                Surface(
                    shape = AppShapes.card,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AppSpacing.cardContent, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShimmerBox(Modifier.size(22.dp), shape = CircleShape)
                        ShimmerBox(Modifier.width(120.dp).height(18.dp))
                        Spacer(Modifier.weight(1f))
                        ShimmerBox(Modifier.width(72.dp).height(14.dp))
                    }
                }
            }
            repeat(6) { index ->
                item(key = "bio-deck-skeleton-$index") {
                    BioDeckItemSkeleton()
                }
            }
        } else {
            repeat(6) { index ->
                item(key = "bio-card-skeleton-$index") {
                    BioCardItemSkeleton()
                }
            }
        }
    }
}

@Composable
private fun BioCardItemSkeleton() {
    Surface(
        shape = AppShapes.card,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.xLarge, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShimmerBox(Modifier.size(84.dp), shape = AppShapes.compactCard)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(18.dp))
                    ShimmerBox(Modifier.width(42.dp).height(20.dp), shape = AppShapes.chip)
                }
                ShimmerBox(Modifier.fillMaxWidth(0.45f).height(13.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.92f).height(14.dp))
            }
        }
    }
}

@Composable
private fun BioDeckItemSkeleton() {
    Surface(
        shape = AppShapes.card,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.xLarge, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.fillMaxWidth(0.58f).height(18.dp))
                ShimmerBox(Modifier.width(56.dp).height(22.dp), shape = AppShapes.chip)
            }
            ShimmerBox(Modifier.fillMaxWidth(0.88f).height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.width(110.dp).height(13.dp))
                ShimmerBox(Modifier.width(86.dp).height(26.dp), shape = AppShapes.chip)
            }
        }
    }
}

@Composable
private fun BioCardItem(
    modifier: Modifier = Modifier,
    imageUrl: String?,
    title: String,
    meta: String,
    effect: String,
    badge: String,
    accentColor: Color,
    fallbackIcon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.card,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.xLarge, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = AppShapes.compactCard,
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.34f)),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            fallbackIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotBlank()) {
                        Surface(
                            shape = AppShapes.chip,
                            color = accentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(accentColor, CircleShape)
                                )
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (effect.isNotBlank()) {
                    Text(
                        text = effect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BioDeckItem(
    modifier: Modifier = Modifier,
    deck: SharedDeck,
    onClick: () -> Unit,
    onCopyShareId: () -> Unit
) {
    val factionColor = bioFactionColor(deck.faction)
    val metaText = listOfNotNull(
        deck.author.takeIf { it.isNotBlank() },
        "${deck.cardNames.size} 张卡牌".takeIf { deck.cardNames.isNotEmpty() }
    ).joinToString(" · ")

    Surface(
        onClick = onClick,
        shape = AppShapes.card,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.xLarge, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Text(
                    text = deck.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (deck.faction.isNotBlank()) {
                    Surface(
                        shape = AppShapes.chip,
                        color = factionColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, factionColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(factionColor, CircleShape)
                            )
                            Text(
                                text = deck.faction,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (deck.intro.isNotBlank()) {
                Text(
                    text = deck.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Text(
                    text = metaText.ifBlank { "卡组详情" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (deck.shareId.isNotBlank()) {
                    Surface(
                        onClick = onCopyShareId,
                        shape = AppShapes.chip,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "复制分享码",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeckShareComposerCard(
    editor: BioDeckEditorState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showCardSelector: Boolean,
    onShowCardSelectorChange: (Boolean) -> Unit,
    isLoggedIn: Boolean,
    isSubmitting: Boolean,
    cardMap: Map<String, List<DeckCardOption>>,
    pcCards: List<PcCard>,
    mobileCards: List<MobileCard>,
    isLoadingCardMap: Boolean,
    cardMapError: String?,
    onReloadCardMap: () -> Unit,
    onOpenLogin: () -> Unit,
    onRefreshLoginState: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSubmit: (SubmitDeckPayload) -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "composerChevron"
    )

    with(editor) {
        val factionOptions = remember(cardMap) {
            listOf("超弦体", "晶源体")
                .filter { cardMap[it]?.isNotEmpty() == true }
                .ifEmpty { cardMap.keys.sorted() }
        }
        LaunchedEffect(factionOptions) { reconcileFactions(factionOptions) }
        val cards = remember(cardMap, selectedFaction) { cardMap[selectedFaction].orEmpty() }
        val defaultCards = remember(cards) { cards.filter { it.isDefault } }
        val userSelectableCards = remember(cards) { cards.filter { !it.isDefault } }
        val cardById = remember(cards) { cards.filter { it.cardId.isNotBlank() }.associateBy { it.cardId } }

        val imageIndex = remember(pcCards, mobileCards) { BioDeckCardImageIndex(pcCards, mobileCards) }
        val cardImages = remember(cards, imageIndex, selectedFaction) {
            cards.associate { it.cardId to imageIndex.imageUrl(it, selectedFaction) }
        }

        val mergedSelectedIds = editor.selection(cards)
        val unindexedCards = mergedSelectedIds.mapNotNull { cardById[it] }.filter { it.index < 0 }
        var allowCardListOnly by rememberSaveable(selectedFaction, mergedSelectedIds) { mutableStateOf(false) }

        val rarityCounts = remember(mergedSelectedIds, cardById) {
            editor.rarityCounts(cards)
        }
        val threshold = remember(selectedFaction) { DECK_RARITY_THRESHOLDS[selectedFaction].orEmpty() }
        val customCount = (mergedSelectedIds.size - defaultCards.size).coerceAtLeast(0)

        Surface(
            shape = AppShapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.card),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.cardContent),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.chip)
                        .clickable { onExpandedChange(!isExpanded) }
                        .padding(vertical = 2.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Publish,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(AppSpacing.large))
                    Column(Modifier.weight(1f)) {
                        Text("卡组发布（Beta）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = when {
                                !isLoggedIn -> "未登录 Wiki，展开登录并编辑"
                                customCount > 0 -> "已选 $customCount 张自定义卡牌"
                                else -> "展开导入分享码或编辑卡组"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        if (!isLoggedIn) {
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "未检测到 Wiki 登录状态",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(onClick = onOpenLogin, shape = AppShapes.capsule) { Text("去登录") }
                                        OutlinedButton(onClick = onRefreshLoginState, shape = AppShapes.capsule) { Text("刷新") }
                                    }
                                }
                            }
                        }

                        if (isLoadingCardMap) {
                            Text("正在加载卡牌数据…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (!cardMapError.isNullOrBlank()) {
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        cardMapError,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(onClick = onReloadCardMap, shape = AppShapes.capsule) { Text("重试") }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = deckName,
                            onValueChange = { deckName = it },
                            label = { Text("卡组名称") },
                            singleLine = true,
                            shape = AppShapes.compactCard,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("分享作者") },
                            singleLine = true,
                            shape = AppShapes.compactCard,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (factionOptions.isNotEmpty()) {
                            SimpleDropdownSelector(
                                label = "阵营",
                                options = factionOptions,
                                selected = selectedFaction,
                                onSelected = { if (it in factionOptions) selectFaction(it) }
                            )
                        }

                        OutlinedTextField(
                            value = intro,
                            onValueChange = { intro = it },
                            label = { Text("卡组介绍") },
                            shape = AppShapes.compactCard,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = shareCodeInput,
                                onValueChange = { shareCodeInput = it },
                                label = { Text("导入分享码", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("粘贴分享码", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = AppShapes.compactCard,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    runCatching {
                                        importCode(cards)
                                    }.onFailure {
                                        statusText = it.message ?: "导入失败"
                                    }
                                },
                                shape = AppShapes.capsule
                            ) { Text("导入") }
                        }

                        if (defaultCards.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "默认携带",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    defaultCards.forEach { card ->
                                        Surface(
                                            shape = AppShapes.chip,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = card.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = AppShapes.compactCard,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                            ) {
                                Text(
                                    "稀有度统计（含默认卡 · 单项上限 $MAX_PER_RARITY）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                                ) {
                                    listOf("完美" to 4, "卓越" to 3, "精致" to 2).forEach { (key, rarityInt) ->
                                        val now = rarityCounts[key] ?: 0
                                        val need = threshold[key] ?: 0
                                        val ok = now in need..MAX_PER_RARITY
                                        val rarityColor = bioRarityColor(rarityInt)
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.chip,
                                            color = if (ok) rarityColor.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                1.dp,
                                                if (ok) rarityColor.copy(alpha = 0.35f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(rarityColor, CircleShape)
                                                    )
                                                    Text(
                                                        key,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (ok) {
                                                        Icon(
                                                            Icons.Outlined.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "$now / $need",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (ok) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onShowCardSelectorChange(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.compactCard
                        ) {
                            Text("选择自定义卡牌（已选 $customCount 张）")
                        }

                        if (statusText.isNotBlank()) {
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (unindexedCards.isNotEmpty()) {
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "${unindexedCards.joinToString("、") { it.name }}：Wiki 尚未提供分享码索引，不能生成包含这些卡牌的分享码。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.chip)
                                            .clickable { allowCardListOnly = !allowCardListOnly },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = allowCardListOnly,
                                            onCheckedChange = { allowCardListOnly = it }
                                        )
                                        Text(
                                            "仅发布完整卡牌列表，不生成分享码",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                runCatching { payload(cards, isLoggedIn, allowCardListOnly) }
                                    .onSuccess(onSubmit)
                                    .onFailure { statusText = it.message ?: "无法发布卡组" }
                            },
                            enabled = !isSubmitting && !isLoadingCardMap && cardMapError.isNullOrBlank() &&
                                (unindexedCards.isEmpty() || allowCardListOnly),
                            shape = AppShapes.capsule,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("发布中…")
                            } else {
                                Text(if (unindexedCards.isEmpty()) "发布卡组" else "发布卡牌列表（无分享码）")
                            }
                        }
                    }
                }
            }
        }

        if (showCardSelector) {
            BioDeckCardSelectorSheet(
                cards = userSelectableCards,
                selectedIds = selectableIds.toList(),
                images = cardImages,
                onDismiss = { onShowCardSelectorChange(false) },
                onToggle = { card ->
                    if (!isSubmitting) toggle(card, cards)?.let(onShowMessage)
                }
            )
        }
    }
}

@Composable
private fun PcCardDetailSheet(
    card: PcCard,
    onPreviewImage: (PreviewImage) -> Unit,
    onDismiss: () -> Unit
) {
    val isTalent = card.category.contains("天赋")
    val badge = if (isTalent) card.category else rarityLabel(card.rarity)
    val badgeColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)

    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = badge,
        badgeColor = badgeColor,
        onPreviewImage = onPreviewImage,
        onDismiss = onDismiss
    ) {
        DetailLine("最大等级", card.maxLevel)
        DetailLine("效果", card.effect)
        card.refreshProbability?.let { probability ->
            ProbabilitySection(probability)
        }
        if (card.roles.isNotEmpty()) ChipSection("适用角色", card.roles)
        val tags = listOfNotNull(
            card.defaultTag.takeIf { it.isNotBlank() },
            card.acquireType.takeIf { it.isNotBlank() },
            card.releaseDate.takeIf { it.isNotBlank() }
        )
        if (tags.isNotEmpty()) ChipSection("标签", tags)
    }
}

@Composable
private fun ProbabilitySection(probability: CardRefreshProbability) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(
            "刷新概率",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            listOf(
                listOf("第1阶段" to probability.stage1, "第2阶段" to probability.stage2),
                listOf("第3阶段" to probability.stage3, "第4阶段" to probability.stage4)
            ).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    rowItems.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.chip,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileCardDetailSheet(
    card: MobileCard,
    onPreviewImage: (PreviewImage) -> Unit,
    onDismiss: () -> Unit
) {
    val isTalent = card.category.contains("天赋")
    val badge = if (isTalent) card.category else rarityLabel(card.rarity)
    val badgeColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)

    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = badge,
        badgeColor = badgeColor,
        onPreviewImage = onPreviewImage,
        onDismiss = onDismiss
    ) {
        DetailLine("最大等级", card.maxLevel)
        DetailLine("效果", card.effect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckDetailSheet(
    deck: SharedDeck,
    onDismiss: () -> Unit,
    onCopyShareId: () -> Unit
) {
    val tooltipState = rememberTooltipState(isPersistent = true)

    CardDetailSheet(
        title = deck.title,
        subtitle = listOfNotNull(deck.faction, deck.author.takeIf { it.isNotBlank() }).joinToString(" · "),
        imageUrl = null,
        badge = "卡组",
        badgeColor = bioFactionColor(deck.faction),
        onDismiss = onDismiss,
        heroIcon = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            if (deck.author.isNotBlank()) DetailLine("作者", deck.author)
            if (deck.shareId.isNotBlank()) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        RichTooltip {
                            Text(
                                deck.shareId,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    state = tooltipState
                ) {
                    Surface(
                        shape = AppShapes.compactCard,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.compactCard)
                            .clickable(onClick = onCopyShareId)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacing.xLarge, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "分享码",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "点击复制，长按查看完整内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            if (deck.intro.isNotBlank()) DetailLine("简介", deck.intro)
            if (deck.cardNames.isNotEmpty()) ChipSection("卡牌（${deck.cardNames.size} 张）", deck.cardNames)
        }
    }
}

@Composable
private fun CardDetailSheet(
    title: String,
    subtitle: String,
    imageUrl: String?,
    badge: String,
    badgeColor: Color,
    onPreviewImage: ((PreviewImage) -> Unit)? = null,
    onDismiss: () -> Unit,
    heroIcon: ImageVector? = Icons.Outlined.AutoAwesome,
    content: @Composable ColumnScope.() -> Unit
) {
    val hasHero = !imageUrl.isNullOrBlank() || heroIcon != null

    WikiScrollableSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (hasHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .let { mod ->
                            if (!imageUrl.isNullOrBlank() && onPreviewImage != null) {
                                mod.clickable { onPreviewImage(PreviewImage(imageUrl, title)) }
                            } else mod
                        }
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow)
                                    )
                                )
                        )
                        if (onPreviewImage != null) {
                            Surface(
                                shape = smoothCapsuleShape(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "查看大图",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else if (heroIcon != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(heroIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(56.dp))
                        }
                    }

                    if (badge.isNotBlank()) {
                        DetailBadge(
                            text = badge,
                            color = badgeColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = AppSpacing.cardContent, vertical = if (hasHero) 0.dp else 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasHero && badge.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        DetailBadge(text = badge, color = badgeColor)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(AppSpacing.xLarge))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.cardContent),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = smoothCapsuleShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    DetailField(label = label, value = value)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            items.forEach { item ->
                Surface(
                    shape = AppShapes.chip,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        item,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBioCardState(text: String, onClear: (() -> Unit)? = null) {
    CatalogEmptyResults(message = text, onClear = onClear)
}

private fun rarityLabel(rarity: Int): String = bioRarityLabel(rarity)

@Composable
private fun talentColor(faction: String): Color = bioFactionColor(faction)

@Composable
private fun rarityColor(rarity: Int): Color = bioRarityColor(rarity)
