package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.QualityFilterChips
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SimpleDropdownSelector
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioCardApi
import com.nekolaska.calabiyau.feature.wiki.bio.model.CardPageData
import com.nekolaska.calabiyau.feature.wiki.bio.model.BioCatalogTab as BioCardTab
import com.nekolaska.calabiyau.core.ui.WikiScrollableSheet
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
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

@Composable
fun BioCardScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    initialTab: Int = 0
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable(initialTab) {
        mutableStateOf(when (initialTab) { 1 -> BioCardTab.MOBILE; 2 -> BioCardTab.DECK; else -> BioCardTab.PC })
    }
    val state = key(selectedTab) { rememberLoadState(
        initial = CardPageData(
            pcCards = emptyList(),
            mobileCards = emptyList(),
            decks = emptyList(),
            pcWikiUrl = "",
            mobileWikiUrl = "",
            deckWikiUrl = ""
        ), key = selectedTab
    ) { force ->
        BioCardApi.fetchTab(selectedTab, force)
    } }
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
    var keyword by rememberSaveable { mutableStateOf("") }
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

    val data = state.data
    val pcKeys = remember(data.pcCards) {
        val cards = data.pcCards.distinct()
        cards.zip(com.nekolaska.calabiyau.core.ui.catalogKeys(cards) { "${it.cardId}:${it.faction}:${it.category}:${it.name}" }).toMap()
    }
    val mobileKeys = remember(data.mobileCards) {
        val cards = data.mobileCards.distinct()
        cards.zip(com.nekolaska.calabiyau.core.ui.catalogKeys(cards) { "${it.faction}:${it.category}:${it.name}" }).toMap()
    }
    val deckKeys = remember(data.decks) {
        val decks = data.decks.distinct()
        decks.zip(com.nekolaska.calabiyau.core.ui.catalogKeys(decks) { "${it.faction}:${it.title}:${it.author}" }).toMap()
    }
    val completeDeckCardMap = remember(data.deckCardMap, data.pcCards) {
        BioDeckShareParsers.completeFromPcCatalog(data.deckCardMap, data.pcCards)
    }
    val pcFactions = remember(data.pcCards) { listOf("全部阵营") + data.pcCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val pcCategories = remember(data.pcCards) { listOf("全部分类") + data.pcCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val mobileFactions = remember(data.mobileCards) { listOf("全部阵营") + data.mobileCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val mobileCategories = remember(data.mobileCards) { listOf("全部分类") + data.mobileCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val deckFactions = remember(data.decks) { listOf("全部卡组") + data.decks.map { it.faction }.filter { it.isNotBlank() }.distinct() }

    val filteredPc = remember(data.pcCards, keyword, pcFaction, pcCategory, pcRarity) {
        data.pcCards.distinct().filter {
            (keyword.isBlank() || it.name.contains(keyword, true) || it.roles.any { role -> role.contains(keyword, true) }) &&
                (pcFaction == "全部阵营" || it.faction == pcFaction) &&
                (pcCategory == "全部分类" || it.category == pcCategory) &&
                (pcRarity == 0 || it.rarity == pcRarity)
        }
    }
    val filteredMobile = remember(data.mobileCards, keyword, mobileFaction, mobileCategory, mobileRarity) {
        data.mobileCards.distinct().filter {
            (keyword.isBlank() || it.name.contains(keyword, true) || it.effect.contains(keyword, true)) &&
                (mobileFaction == "全部阵营" || it.faction == mobileFaction) &&
                (mobileCategory == "全部分类" || it.category == mobileCategory) &&
                (mobileRarity == 0 || it.rarity == mobileRarity)
        }
    }
    val filteredDecks = remember(data.decks, keyword, deckFaction) {
        data.decks.distinct().filter {
            (keyword.isBlank() || it.title.contains(keyword, true) || it.author.contains(keyword, true) || it.cardNames.any { card -> card.contains(keyword, true) }) &&
                (deckFaction == "全部卡组" || it.faction == deckFaction)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                    Text(
                        when (selectedTab) {
                            BioCardTab.PC -> "卡牌 ${filteredPc.size}"
                            BioCardTab.MOBILE -> "生化卡牌 ${filteredMobile.size}"
                            BioCardTab.DECK -> "卡组分享 ${filteredDecks.size}"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    RefreshActionButton(onClick = {
                        state.reload(forceRefresh = true)
                    })
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
                selectedPcCard = null
                selectedMobileCard = null
                selectedDeck = null
                selectedTab = tab
            }
            }
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
        ApiResourceContent(
            state = state,
            modifier = Modifier.weight(1f),
            isDataEmpty = { it.loadedTab != selectedTab || when (selectedTab) {
                BioCardTab.PC -> it.pcCards.isEmpty()
                BioCardTab.MOBILE -> it.mobileCards.isEmpty()
                BioCardTab.DECK -> it.deckWikiUrl.isBlank()
            } },
            enablePullToRefresh = false,
            loading = { mod -> BioCardSkeleton(mod) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "search", contentType = "search") {
                    SearchBar(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSearch = {},
                        onClear = { keyword = "" },
                        isSearching = state.isLoading,
                        placeholder = when (selectedTab) {
                            BioCardTab.PC -> "搜索卡牌名 / 适用角色"
                            BioCardTab.MOBILE -> "搜索卡牌名 / 效果"
                            BioCardTab.DECK -> "搜索卡组名 / 作者 / 卡牌"
                        }
                    )
                }
                item(key = "filters", contentType = "filters") {
                    when (selectedTab) {
                        BioCardTab.PC -> BioCardFilterBar(
                            factions = pcFactions,
                            categories = pcCategories,
                            selectedFaction = pcFaction,
                            selectedCategory = pcCategory,
                            selectedRarity = pcRarity,
                            onFactionChange = { pcFaction = it },
                            onCategoryChange = { pcCategory = it },
                            onRarityChange = { pcRarity = it }
                        )

                        BioCardTab.MOBILE -> BioCardFilterBar(
                            factions = mobileFactions,
                            categories = mobileCategories,
                            selectedFaction = mobileFaction,
                            selectedCategory = mobileCategory,
                            selectedRarity = mobileRarity,
                            onFactionChange = { mobileFaction = it },
                            onCategoryChange = { mobileCategory = it },
                            onRarityChange = { mobileRarity = it }
                        )

                        BioCardTab.DECK -> DeckFilterBar(
                            factions = deckFactions,
                            selectedFaction = deckFaction,
                            onFactionChange = { deckFaction = it }
                        )
                    }
                }
                if (selectedTab == BioCardTab.DECK) {
                    item(key = "deck-composer", contentType = "deck-composer") {
                        DeckShareComposerCard(
                            editor = deckEditor,
                            isLoggedIn = isWikiLoggedIn,
                            isSubmitting = isSubmittingDeck,
                            cardMap = completeDeckCardMap,
                            pcCards = data.pcCards,
                            mobileCards = data.mobileCards,
                            isLoadingCardMap = state.isLoading,
                            cardMapError = data.deckEditorError,
                            onReloadCardMap = {
                                state.reload(forceRefresh = true)
                            },
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
                                                if (result.value.isPending) {
                                                    "发布成功，已进入审核队列"
                                                } else {
                                                    "发布成功"
                                                }
                                            )
                                            refreshPublishedDeck()
                                        }

                                        is ApiResult.Error -> {
                                            showSnack(result.message)
                                        }
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
                            item { EmptyBioCardState("没有匹配的 PC 卡牌") }
                        } else {
                            itemsIndexed(
                                filteredPc,
                                key = { _, card -> "pc:${checkNotNull(pcKeys[card])}" }
                            ) { _, card ->
                                val isTalent = card.category.contains("天赋")
                                val accentColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)
                                
                                BioInfoCard(
                                    imageUrl = card.imageUrl,
                                    title = card.name,
                                    subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity).takeIf { !isTalent } ?: "")
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    accentColor = accentColor,
                                    icon = Icons.Outlined.SportsEsports,
                                    onClick = { selectedPcCard = card }
                                )
                            }
                        }
                    }

                    BioCardTab.MOBILE -> {
                        if (filteredMobile.isEmpty()) {
                            item { EmptyBioCardState("没有匹配的移动端卡牌") }
                        } else {
                            itemsIndexed(
                                filteredMobile,
                                key = { _, card -> "mobile:${checkNotNull(mobileKeys[card])}" }
                            ) { _, card ->
                                val isTalent = card.category.contains("天赋")
                                val accentColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)

                                BioInfoCard(
                                    imageUrl = card.imageUrl,
                                    title = card.name,
                                    subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity).takeIf { !isTalent } ?: "")
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    accentColor = accentColor,
                                    icon = Icons.Outlined.PhoneAndroid,
                                    onClick = { selectedMobileCard = card }
                                )
                            }
                        }
                    }

                    BioCardTab.DECK -> {
                        if (filteredDecks.isEmpty()) {
                            item { EmptyBioCardState("没有匹配的卡组") }
                        } else {
                            itemsIndexed(
                                filteredDecks,
                                key = { _, deck -> "deck:${checkNotNull(deckKeys[deck])}" }
                            ) { _, deck ->
                                BioInfoCard(
                                    imageUrl = null,
                                    title = deck.title,
                                    subtitle = listOfNotNull(deck.faction, deck.author.takeIf { it.isNotBlank() })
                                        .joinToString(" · "),
                                    accentColor = MaterialTheme.colorScheme.tertiary,
                                    icon = null,
                                    onClick = { selectedDeck = deck }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            selectedPcCard?.let { card ->
                PcCardDetailSheet(card = card, onDismiss = { selectedPcCard = null })
            }
            selectedMobileCard?.let { card ->
                MobileCardDetailSheet(card = card, onDismiss = { selectedMobileCard = null })
            }
            selectedDeck?.let { deck ->
                DeckDetailSheet(
                    deck = deck,
                    onDismiss = { selectedDeck = null },
                    onCopyShareId = {
                        copyText("分享码", deck.shareId)
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun BioTabBar(selected: BioCardTab, onSelect: (BioCardTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = Color.Transparent) {
        listOf(BioCardTab.PC to "PC 卡牌", BioCardTab.MOBILE to "移动端卡牌", BioCardTab.DECK to "卡组分享").forEach { (tab, label) ->
            Tab(selected = selected == tab, onClick = { onSelect(tab) }, text = { Text(label) })
        }
    }
}

@Composable
private fun BioCardSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = smoothCornerShape(26.dp)
            )
        }
        item {
            Card(
                shape = smoothCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(Modifier.width(88.dp).height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(Modifier.weight(1f).height(56.dp), shape = smoothCornerShape(16.dp))
                        ShimmerBox(Modifier.weight(1f).height(56.dp), shape = smoothCornerShape(16.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { index ->
                            ShimmerBox(
                                Modifier.width((72 - index * 4).dp).height(32.dp),
                                shape = smoothCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
        repeat(6) { index ->
            item(key = "bio-card-skeleton-$index") {
                BioInfoCardSkeleton()
            }
        }
    }
}

@Composable
private fun BioInfoCardSkeleton() {
    Card(
        shape = smoothCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShimmerBox(Modifier.size(84.dp), shape = smoothCornerShape(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShimmerBox(Modifier.fillMaxWidth(0.55f).height(18.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.85f).height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.65f).height(14.dp))
            }
        }
    }
}

@Composable
private fun BioInfoCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!imageUrl.isNullOrBlank() || icon != null) {
                Surface(
                    shape = smoothCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
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
                        } else if (icon != null) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckShareComposerCard(
    editor: BioDeckEditorState,
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
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showCardSelector by remember { mutableStateOf(false) }

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

    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
            ) {
                Icon(Icons.Outlined.Publish, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("卡组发布（Beta）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isLoggedIn) {
                        Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("未检测到 Wiki 登录状态", color = MaterialTheme.colorScheme.onErrorContainer)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = onOpenLogin, shape = smoothCornerShape(24.dp)) { Text("去登录") }
                                    OutlinedButton(onClick = onRefreshLoginState, shape = smoothCornerShape(24.dp)) { Text("刷新") }
                                }
                            }
                        }
                    }

                    if (isLoadingCardMap) {
                        Text("正在加载卡牌数据…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (!cardMapError.isNullOrBlank()) {
                        Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cardMapError, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = onReloadCardMap, shape = smoothCornerShape(24.dp)) { Text("重试") }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { deckName = it },
                        label = { Text("卡组名称") },
                        singleLine = true,
                        shape = smoothCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("分享作者") },
                        singleLine = true,
                        shape = smoothCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SimpleDropdownSelector(
                        label = "阵营",
                        options = factionOptions.ifEmpty { listOf("") },
                        selected = selectedFaction,
                        onSelected = { selectFaction(it) }
                    )

                    OutlinedTextField(
                        value = intro,
                        onValueChange = { intro = it },
                        label = { Text("卡组介绍") },
                        shape = smoothCornerShape(16.dp),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = shareCodeInput,
                            onValueChange = { shareCodeInput = it },
                            label = { Text("导入分享码") },
                            placeholder = { Text("粘贴后点击导入") },
                            singleLine = true,
                            shape = smoothCornerShape(16.dp),
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
                            shape = smoothCornerShape(24.dp)
                        ) { Text("导入") }
                    }

                    if (defaultCards.isNotEmpty()) {
                        Text(
                            text = "默认携带：${defaultCards.joinToString("、") { it.name }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("稀有度统计（含默认卡）", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            listOf("完美", "卓越", "精致").forEach { key ->
                                val now = rarityCounts[key] ?: 0
                                val need = threshold[key] ?: 0
                                val ok = now in need..MAX_PER_RARITY
                                Text(
                                    "$key：$now / $need（上限$MAX_PER_RARITY）${if (ok) " ✓" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Text("自定义卡牌（${mergedSelectedIds.size - defaultCards.size}张）", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { showCardSelector = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(16.dp)
                    ) {
                        Text("选择卡牌...")
                    }

                    if (statusText.isNotBlank()) {
                        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (unindexedCards.isNotEmpty()) {
                        Text(
                            "${unindexedCards.joinToString("、") { it.name }}：Wiki 尚未提供分享码索引，不能生成包含这些卡牌的分享码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = allowCardListOnly, onCheckedChange = { allowCardListOnly = it })
                            Text("仅发布完整卡牌列表，不生成分享码", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            runCatching { payload(cards, isLoggedIn, allowCardListOnly) }
                                .onSuccess(onSubmit).onFailure { statusText = it.message ?: "无法发布卡组" }
                        },
                        enabled = !isSubmitting && !isLoadingCardMap && cardMapError.isNullOrBlank() &&
                            (unindexedCards.isEmpty() || allowCardListOnly),
                        shape = smoothCornerShape(24.dp),
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
        BioDeckCardSelectorSheet(cards = userSelectableCards, selectedIds = selectableIds.toList(), images = cardImages,
            onDismiss = { showCardSelector = false }, onToggle = { card ->
                if (!isSubmitting) toggle(card, cards)?.let(onShowMessage)
            })
    }
    }
}

@Composable
private fun PcCardDetailSheet(card: PcCard, onDismiss: () -> Unit) {
    val isTalent = card.category.contains("天赋")
    val badge = if (isTalent) card.category else rarityLabel(card.rarity)
    val badgeColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)

    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = badge,
        badgeColor = badgeColor,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "刷新概率",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                listOf("第1阶段" to probability.stage1, "第2阶段" to probability.stage2),
                listOf("第3阶段" to probability.stage3, "第4阶段" to probability.stage4)
            ).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = smoothCornerShape(12.dp),
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
private fun MobileCardDetailSheet(card: MobileCard, onDismiss: () -> Unit) {
    val isTalent = card.category.contains("天赋")
    val badge = if (isTalent) card.category else rarityLabel(card.rarity)
    val badgeColor = if (isTalent) talentColor(card.faction) else rarityColor(card.rarity)

    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = badge,
        badgeColor = badgeColor,
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
    val scope = rememberCoroutineScope()

    CardDetailSheet(
        title = deck.title,
        subtitle = listOfNotNull(deck.faction, deck.author.takeIf { it.isNotBlank() }).joinToString(" · "),
        imageUrl = null,
        badge = "卡组",
        badgeColor = MaterialTheme.colorScheme.tertiary,
        onDismiss = onDismiss,
        heroIcon = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        shape = smoothCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(smoothCornerShape(16.dp))
                            .combinedClickable(
                                onClick = onCopyShareId,
                                onLongClick = { scope.launch { tooltipState.show() } }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("分享码", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                                Text("点击复制，长按查看完整内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
            if (deck.intro.isNotBlank()) DetailLine("简介", deck.intro)
            if (deck.cardNames.isNotEmpty()) ChipSection("卡牌", deck.cardNames)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(
    title: String,
    subtitle: String,
    imageUrl: String?,
    badge: String,
    badgeColor: Color,
    onDismiss: () -> Unit,
    heroIcon: ImageVector? = Icons.Outlined.AutoAwesome,
    content: @Composable ColumnScope.() -> Unit
) {
    val hasHero = !imageUrl.isNullOrBlank() || heroIcon != null

    WikiScrollableSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (hasHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
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

            Column(Modifier.padding(horizontal = 20.dp, vertical = if (hasHero) 0.dp else 8.dp)) {
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

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
        color = color.copy(alpha = 0.9f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    DetailField(label = label, value = value)
}

@Composable
private fun ChipSection(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Surface(
                    shape = smoothCornerShape(12.dp),
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
private fun EmptyBioCardState(text: String) {
    CatalogEmptyResults(message = text)
}

private fun rarityLabel(rarity: Int): String = when (rarity) {
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    else -> ""
}

@Composable
private fun talentColor(faction: String): Color = when {
    faction.contains("晶源体") -> Color(0xFFF06A6A)
    faction.contains("超弦体") -> Color(0xFF8B5CF6)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun rarityColor(rarity: Int): Color = bioRarityColor(rarity)
