package com.nekolaska.calabiyau.feature.wiki.bio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.bio.BioCardApi.MobileCard
import com.nekolaska.calabiyau.feature.wiki.bio.BioCardApi.PcCard
import com.nekolaska.calabiyau.feature.wiki.bio.BioCardApi.SharedDeck
import com.nekolaska.calabiyau.feature.wiki.hub.hasWikiLoginCookie
import data.ApiResult
import kotlinx.coroutines.launch

private enum class BioCardTab { PC, MOBILE, DECK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioCardScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state = rememberLoadState(
        initial = BioCardApi.CardPageData(
            pcCards = emptyList(),
            mobileCards = emptyList(),
            decks = emptyList(),
            pcWikiUrl = "",
            mobileWikiUrl = "",
            deckWikiUrl = "",
            refreshProbabilityWikiUrl = ""
        )
    ) { force ->
        BioCardApi.fetchAll(force)
    }
    val showSnack = rememberSnackbarLauncher()
    var isWikiLoggedIn by remember { mutableStateOf(hasWikiLoginCookie()) }
    var isSubmittingDeck by remember { mutableStateOf(false) }
    val deckShareCardState = rememberLoadState(
        initial = emptyMap<String, List<BioDeckShareApi.DeckCardOption>>()
    ) { force ->
        BioDeckShareApi.fetchDeckCardMap(force)
    }
    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                1 -> BioCardTab.MOBILE
                2 -> BioCardTab.DECK
                else -> BioCardTab.PC
            }
        )
    }
    var keyword by remember { mutableStateOf("") }
    var pcFaction by remember { mutableStateOf("全部阵营") }
    var pcCategory by remember { mutableStateOf("全部分类") }
    var pcRarity by remember { mutableIntStateOf(0) }
    var mobileFaction by remember { mutableStateOf("全部阵营") }
    var mobileCategory by remember { mutableStateOf("全部分类") }
    var mobileRarity by remember { mutableIntStateOf(0) }
    var deckFaction by remember { mutableStateOf("全部卡组") }
    var selectedPcCard by remember { mutableStateOf<PcCard?>(null) }
    var selectedMobileCard by remember { mutableStateOf<MobileCard?>(null) }
    var selectedDeck by remember { mutableStateOf<SharedDeck?>(null) }

    val data = state.data
    val pcFactions = remember(data.pcCards) { listOf("全部阵营") + data.pcCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val pcCategories = remember(data.pcCards) { listOf("全部分类") + data.pcCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val mobileFactions = remember(data.mobileCards) { listOf("全部阵营") + data.mobileCards.map { it.faction }.filter { it.isNotBlank() }.distinct() }
    val mobileCategories = remember(data.mobileCards) { listOf("全部分类") + data.mobileCards.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val deckFactions = remember(data.decks) { listOf("全部卡组") + data.decks.map { it.faction }.filter { it.isNotBlank() }.distinct() }

    val filteredPc = remember(data.pcCards, keyword, pcFaction, pcCategory, pcRarity) {
        data.pcCards.filter {
            (keyword.isBlank() || it.name.contains(keyword, true) || it.roles.any { role -> role.contains(keyword, true) }) &&
                (pcFaction == "全部阵营" || it.faction == pcFaction) &&
                (pcCategory == "全部分类" || it.category == pcCategory) &&
                (pcRarity == 0 || it.rarity == pcRarity)
        }
    }
    val filteredMobile = remember(data.mobileCards, keyword, mobileFaction, mobileCategory, mobileRarity) {
        data.mobileCards.filter {
            (keyword.isBlank() || it.name.contains(keyword, true) || it.effect.contains(keyword, true)) &&
                (mobileFaction == "全部阵营" || it.faction == mobileFaction) &&
                (mobileCategory == "全部分类" || it.category == mobileCategory) &&
                (mobileRarity == 0 || it.rarity == mobileRarity)
        }
    }
    val filteredDecks = remember(data.decks, keyword, deckFaction) {
        data.decks.filter {
            (keyword.isBlank() || it.title.contains(keyword, true) || it.author.contains(keyword, true) || it.cardNames.any { card -> card.contains(keyword, true) }) &&
                (deckFaction == "全部卡组" || it.faction == deckFaction)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                    FilledTonalIconButton(
                        onClick = { state.reload(forceRefresh = true) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val url = when (selectedTab) {
                                BioCardTab.PC -> data.pcWikiUrl
                                BioCardTab.MOBILE -> data.mobileWikiUrl
                                BioCardTab.DECK -> data.deckWikiUrl
                            }
                            if (url.isNotBlank()) onOpenWikiUrl(url)
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "打开 Wiki")
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState("正在加载卡牌数据…", mod) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        listOf(
                            BioCardTab.PC to "PC 卡牌",
                            BioCardTab.MOBILE to "移动端卡牌",
                            BioCardTab.DECK to "卡组分享"
                        ).forEach { (tab, label) ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(label) },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            BioCardTab.PC -> Icons.Outlined.ViewInAr
                                            BioCardTab.MOBILE -> Icons.Outlined.PhoneAndroid
                                            BioCardTab.DECK -> Icons.Outlined.Tab
                                        },
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
                item {
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
                item {
                    when (selectedTab) {
                        BioCardTab.PC -> PcFilterBar(
                            factions = pcFactions,
                            categories = pcCategories,
                            selectedFaction = pcFaction,
                            selectedCategory = pcCategory,
                            selectedRarity = pcRarity,
                            onFactionChange = { pcFaction = it },
                            onCategoryChange = { pcCategory = it },
                            onRarityChange = { pcRarity = it }
                        )

                        BioCardTab.MOBILE -> MobileFilterBar(
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
                    item {
                        DeckShareComposerCard(
                            isLoggedIn = isWikiLoggedIn,
                            isSubmitting = isSubmittingDeck,
                            cardMap = deckShareCardState.data,
                            pcCards = data.pcCards,
                            mobileCards = data.mobileCards,
                            isLoadingCardMap = deckShareCardState.isLoading,
                            cardMapError = deckShareCardState.error?.message,
                            onReloadCardMap = { deckShareCardState.reload(forceRefresh = true) },
                            onOpenLogin = {
                                onOpenWikiUrl("https://wiki.biligame.com/wiki/Special:UserLogin?returnto=%E6%99%B6%E6%BA%90%E6%84%9F%E6%9F%93%E5%8D%A1%E7%BB%84%E5%88%86%E4%BA%AB")
                            },
                            onRefreshLoginState = { isWikiLoggedIn = hasWikiLoginCookie() },
                            onSubmit = { payload ->
                                scope.launch {
                                    isSubmittingDeck = true
                                    when (val result = BioDeckShareApi.submitDeck(payload, deckShareCardState.data)) {
                                        is ApiResult.Success -> {
                                            showSnack(
                                                if (result.value.isPending) {
                                                    "发布成功，已进入审核队列"
                                                } else {
                                                    "发布成功"
                                                }
                                            )
                                            state.reload(forceRefresh = true)
                                        }

                                        is ApiResult.Error -> {
                                            showSnack(result.message)
                                        }
                                    }
                                    isWikiLoggedIn = hasWikiLoginCookie()
                                    isSubmittingDeck = false
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
                                key = { index, card -> "pc-${card.name}-${card.faction}-${card.category}-$index" }
                            ) { _, card ->
                                BioInfoCard(
                                    imageUrl = card.imageUrl,
                                    title = card.name,
                                    subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    accentColor = rarityColor(card.rarity),
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
                                key = { index, card -> "mobile-${card.name}-${card.faction}-${card.category}-$index" }
                            ) { _, card ->
                                BioInfoCard(
                                    imageUrl = card.imageUrl,
                                    title = card.name,
                                    subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    accentColor = rarityColor(card.rarity),
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
                                key = { index, deck -> "deck-${deck.faction}-${deck.title}-${deck.shareId}-$index" }
                            ) { _, deck ->
                                BioInfoCard(
                                    imageUrl = null,
                                    title = deck.title,
                                    subtitle = listOfNotNull(deck.faction, deck.author.takeIf { it.isNotBlank() })
                                        .joinToString(" · "),
                                    accentColor = MaterialTheme.colorScheme.tertiary,
                                    icon = Icons.Outlined.BrowseGallery,
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
                        copyText(context, deck.shareId)
                        showSnack("已复制分享码")
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PcFilterBar(
    factions: List<String>,
    categories: List<String>,
    selectedFaction: String,
    selectedCategory: String,
    selectedRarity: Int,
    onFactionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRarityChange: (Int) -> Unit
) {
    FilterCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { DropdownSelector("阵营", factions, selectedFaction, onFactionChange) }
            Box(Modifier.weight(1f)) { DropdownSelector("分类", categories, selectedCategory, onCategoryChange) }
        }
        RarityChips(selectedRarity, onRarityChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileFilterBar(
    factions: List<String>,
    categories: List<String>,
    selectedFaction: String,
    selectedCategory: String,
    selectedRarity: Int,
    onFactionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRarityChange: (Int) -> Unit
) {
    FilterCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { DropdownSelector("阵营", factions, selectedFaction, onFactionChange) }
            Box(Modifier.weight(1f)) { DropdownSelector("分类", categories, selectedCategory, onCategoryChange) }
        }
        RarityChips(selectedRarity, onRarityChange)
    }
}

@Composable
private fun DeckFilterBar(
    factions: List<String>,
    selectedFaction: String,
    onFactionChange: (String) -> Unit
) {
    FilterCard {
        DropdownSelector("卡组", factions, selectedFaction, onFactionChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCard(content: @Composable () -> Unit) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember(options, selected) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                ),
            shape = smoothCornerShape(16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RarityChips(selectedRarity: Int, onRarityChange: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0 to "全部品质", 2 to "精致", 3 to "卓越", 4 to "完美").forEach { (value, label) ->
            FilterChip(
                selected = selectedRarity == value,
                onClick = { onRarityChange(value) },
                label = { Text(label) },
                leadingIcon = if (selectedRarity == value) {
                    { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                shape = smoothCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun BioInfoCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
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
            Surface(
                shape = smoothCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                modifier = Modifier.size(72.dp)
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
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
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

private const val MAX_PER_RARITY = 20
private val DECK_RARITY_THRESHOLDS = mapOf(
    "超弦体" to mapOf("完美" to 10, "卓越" to 15, "精致" to 15),
    "晶源体" to mapOf("完美" to 8, "卓越" to 11, "精致" to 11)
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DeckShareComposerCard(
    isLoggedIn: Boolean,
    isSubmitting: Boolean,
    cardMap: Map<String, List<BioDeckShareApi.DeckCardOption>>,
    pcCards: List<PcCard>,
    mobileCards: List<MobileCard>,
    isLoadingCardMap: Boolean,
    cardMapError: String?,
    onReloadCardMap: () -> Unit,
    onOpenLogin: () -> Unit,
    onRefreshLoginState: () -> Unit,
    onSubmit: (BioDeckShareApi.SubmitDeckPayload) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showCardSelector by remember { mutableStateOf(false) }

    var deckName by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var shareCodeInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }

    val factionOptions = remember(cardMap) {
        listOf("超弦体", "晶源体")
            .filter { cardMap[it]?.isNotEmpty() == true }
            .ifEmpty { cardMap.keys.sorted() }
    }
    var selectedFaction by remember(factionOptions) { mutableStateOf(factionOptions.firstOrNull().orEmpty()) }

    val selectableIds = remember { mutableStateListOf<String>() }
    val cards = remember(cardMap, selectedFaction) { cardMap[selectedFaction].orEmpty() }
    val defaultCards = remember(cards) { cards.filter { it.isDefault } }
    val userSelectableCards = remember(cards) { cards.filter { !it.isDefault } }
    val cardById = remember(cards) { cards.filter { it.cardId.isNotBlank() }.associateBy { it.cardId } }
    val indexToCardId = remember(cards) { cards.filter { it.cardId.isNotBlank() && it.index >= 0 }.associate { it.index to it.cardId } }

    val pcCardImages = remember(pcCards) { pcCards.associate { it.name to it.imageUrl } }
    val mobileCardImages = remember(mobileCards) { mobileCards.associate { it.name to it.imageUrl } }

    LaunchedEffect(selectedFaction) {
        selectableIds.clear()
        statusText = ""
    }

    val mergedSelectedIds = (selectableIds + defaultCards.map { it.cardId }).filter { it.isNotBlank() }.distinct()

    val rarityCounts = remember(mergedSelectedIds, cardById) {
        mutableMapOf("完美" to 0, "卓越" to 0, "精致" to 0).apply {
            mergedSelectedIds.forEach { id ->
                when (normalizeDeckQuality(cardById[id]?.quality.orEmpty())) {
                    "完美" -> this["完美"] = (this["完美"] ?: 0) + 1
                    "卓越" -> this["卓越"] = (this["卓越"] ?: 0) + 1
                    "精致" -> this["精致"] = (this["精致"] ?: 0) + 1
                }
            }
        }
    }

    val threshold = remember(selectedFaction) { DECK_RARITY_THRESHOLDS[selectedFaction].orEmpty() }
    val canSubmitByRarity = remember(rarityCounts, threshold) {
        listOf("完美", "卓越", "精致").all { key ->
            val count = rarityCounts[key] ?: 0
            count >= (threshold[key] ?: 0) && count <= MAX_PER_RARITY
        }
    }

    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
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

                    DropdownSelector(
                        label = "阵营",
                        options = factionOptions.ifEmpty { listOf("") },
                        selected = selectedFaction,
                        onSelected = { selectedFaction = it }
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
                                if (selectedFaction.isBlank()) {
                                    statusText = "请先选择阵营"
                                    return@FilledTonalButton
                                }
                                runCatching {
                                    val parsedName = BioDeckShareApi.extractDeckNameFromShareInput(shareCodeInput)
                                    val (ids, _) = BioDeckShareApi.decodeShareCode(
                                        rawInput = shareCodeInput,
                                        expectedFaction = selectedFaction,
                                        indexToCardId = indexToCardId
                                    )
                                    selectableIds.clear()
                                    selectableIds.addAll(ids.filter { id -> cardById[id]?.isDefault == false }.distinct())
                                    if (deckName.isBlank() && !parsedName.isNullOrBlank()) deckName = parsedName
                                    statusText = "已导入 ${selectableIds.size} 张卡牌"
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

                    FilledTonalButton(
                        onClick = {
                            if (!isLoggedIn) {
                                statusText = "请先登录 Wiki"
                                return@FilledTonalButton
                            }
                            if (deckName.isBlank() || selectedFaction.isBlank()) {
                                statusText = "请填写卡组名称并选择阵营"
                                return@FilledTonalButton
                            }
                            if (!canSubmitByRarity) {
                                statusText = "稀有度条件不满足，无法发布"
                                return@FilledTonalButton
                            }
                            if (mergedSelectedIds.isEmpty()) {
                                statusText = "至少需要选择 1 张卡牌"
                                return@FilledTonalButton
                            }
                            onSubmit(
                                BioDeckShareApi.SubmitDeckPayload(
                                    deckName = deckName.trim(),
                                    author = author.trim(),
                                    intro = intro.trim(),
                                    faction = selectedFaction,
                                    selectedCardIds = mergedSelectedIds
                                )
                            )
                        },
                        enabled = !isSubmitting && !isLoadingCardMap && cardMapError.isNullOrBlank(),
                        shape = smoothCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("发布中…")
                        } else {
                            Text("发布卡组")
                        }
                    }
                }
            }
        }
    }

    if (showCardSelector) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCardSelector = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "选择卡牌",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (userSelectableCards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("当前阵营无可选卡牌", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(userSelectableCards, key = { it.cardId }) { card ->
                            val selected = card.cardId in selectableIds
                            val q = normalizeDeckQuality(card.quality)
                            val imgUrl = pcCardImages[card.name] ?: mobileCardImages[card.name]

                            val rarityColor = when (q) {
                                "完美" -> Color(0xFFD4AF37) // Gold-ish
                                "卓越" -> Color(0xFF9C27B0) // Purple
                                "精致" -> Color(0xFF2196F3) // Blue
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Card(
                                onClick = {
                                    if (selected) {
                                        selectableIds.remove(card.cardId)
                                    } else {
                                        val nextCount = (rarityCounts[q] ?: 0) + 1
                                        if (q in listOf("完美", "卓越", "精致") && nextCount > MAX_PER_RARITY) {
                                            statusText = "$q 已达到上限（$MAX_PER_RARITY）"
                                            showCardSelector = false
                                        } else {
                                            selectableIds.add(card.cardId)
                                        }
                                    }
                                },
                                shape = smoothCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = card.name,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(smoothCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        if (q.isNotBlank()) {
                                            Text(q, style = MaterialTheme.typography.bodySmall, color = rarityColor)
                                        }
                                    }
                                    if (selected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeDeckQuality(raw: String): String = when {
    raw.contains("完") -> "完美"
    raw.contains("卓") -> "卓越"
    raw.contains("精") -> "精致"
    else -> raw
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PcCardDetailSheet(card: PcCard, onDismiss: () -> Unit) {
    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = rarityLabel(card.rarity),
        badgeColor = rarityColor(card.rarity),
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
private fun ProbabilitySection(probability: BioCardApi.CardRefreshProbability) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "刷新概率",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        listOf(
            "第1阶段" to probability.stage1,
            "第2阶段" to probability.stage2,
            "第3阶段" to probability.stage3,
            "第4阶段" to probability.stage4
        ).forEach { (label, value) ->
            Surface(
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileCardDetailSheet(card: MobileCard, onDismiss: () -> Unit) {
    CardDetailSheet(
        title = card.name,
        subtitle = listOf(card.faction, card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = card.imageUrl,
        badge = rarityLabel(card.rarity),
        badgeColor = rarityColor(card.rarity),
        onDismiss = onDismiss
    ) {
        DetailLine("最大等级", card.maxLevel)
        DetailLine("效果", card.effect)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        heroIcon = Icons.Outlined.BrowseGallery
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
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
    heroIcon: ImageVector = Icons.Outlined.AutoAwesome,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
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
                } else {
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
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = smoothCapsuleShape(),
                        color = badgeColor.copy(alpha = 0.9f)
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun rarityLabel(rarity: Int): String = when (rarity) {
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    else -> if (rarity > 0) "品质$rarity" else ""
}

@Composable
private fun rarityColor(rarity: Int): Color = when (rarity) {
    4 -> Color(0xFFF59E0B)
    3 -> Color(0xFFAB47BC)
    2 -> Color(0xFF42A5F5)
    else -> MaterialTheme.colorScheme.primary
}

private fun copyText(context: Context, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("分享码", value))
}
