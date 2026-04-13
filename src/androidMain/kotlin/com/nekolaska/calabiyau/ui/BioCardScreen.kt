package com.nekolaska.calabiyau.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.BioCardApi
import com.nekolaska.calabiyau.data.BioCardApi.MobileCard
import com.nekolaska.calabiyau.data.BioCardApi.PcCard
import com.nekolaska.calabiyau.data.BioCardApi.SharedDeck
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
        topBar = {
            TopAppBar(
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { state.reload(forceRefresh = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        val url = when (selectedTab) {
                            BioCardTab.PC -> data.pcWikiUrl
                            BioCardTab.MOBILE -> data.mobileWikiUrl
                            BioCardTab.DECK -> data.deckWikiUrl
                        }
                        if (url.isNotBlank()) onOpenWikiUrl(url)
                    }) {
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
                                    subtitle = listOf(deck.faction, deck.author.takeIf { it.isNotBlank() })
                                        .filterNotNull()
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
        DropdownSelector("阵营", factions, selectedFaction, onFactionChange)
        DropdownSelector("分类", categories, selectedCategory, onCategoryChange)
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
        DropdownSelector("阵营", factions, selectedFaction, onFactionChange)
        DropdownSelector("分类", categories, selectedCategory, onCategoryChange)
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
    Card(shape = smoothCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
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
                shape = smoothCornerShape(12.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
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
        subtitle = listOf(deck.faction, deck.author.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "),
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
    heroIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.AutoAwesome,
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
private fun CardHeader(
    imageUrl: String?,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.size(64.dp)
            )
        } else {
            Surface(shape = smoothCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    border = androidx.compose.foundation.BorderStroke(
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
    Card(shape = smoothCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
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
