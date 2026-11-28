package com.nekolaska.calabiyau.feature.wiki.stringer

import com.nekolaska.calabiyau.core.usage.CardVisit

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.saveable.rememberSaveable
import com.nekolaska.calabiyau.core.ui.WikiScrollableSheet
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.catalogKeys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.Dp
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
import com.nekolaska.calabiyau.core.ui.TacticalBadge
import com.nekolaska.calabiyau.core.ui.TacticalCard
import com.nekolaska.calabiyau.core.ui.TacticalSectionHeader
import com.nekolaska.calabiyau.core.ui.TacticalStyle
import com.nekolaska.calabiyau.core.ui.tacticalTabular
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.character.components.CharacterSelector
import com.nekolaska.calabiyau.feature.character.components.rememberCharacterSelectorOptions
import com.nekolaska.calabiyau.feature.wiki.stringer.api.StringerPushCardApi
import com.nekolaska.calabiyau.feature.wiki.stringer.model.CardPage
import com.nekolaska.calabiyau.feature.wiki.stringer.model.ModeCard
import java.text.Collator
import java.util.Locale

private enum class PushCardSortOption(val label: String) {
    RARITY_DESC("品质优先"),
    RARITY_ASC("品质升序"),
    NAME_ASC("名称 A-Z")
}

private val zhNameCollator: Collator = Collator.getInstance(Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringerPushCardScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val visit = remember { CardVisit() }
    val state = rememberLoadState(
        initial = CardPage(
            title = "超弦推进卡牌",
            summary = "",
            wikiUrl = "",
            cards = emptyList()
        ),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { StringerPushCardApi.fetch(cacheOnly = true) }
    ) { force ->
        visit.load(force) { StringerPushCardApi.fetch(forceRefresh = force, allowMemoryCache = false) }
    }

    var keyword by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部分类") }
    var role by rememberSaveable { mutableStateOf("全部角色") }
    var rarity by rememberSaveable { mutableIntStateOf(0) }
    var sortOption by rememberSaveable { mutableStateOf(PushCardSortOption.RARITY_DESC) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<ModeCard?>(null) }

    val page = state.data
    val cardKeys = remember(page.cards) {
        val cards = page.cards.distinct()
        cards.zip(catalogKeys(cards) { "${it.category}:${it.name}:${it.rarity}" }).toMap()
    }
    val categories = remember(page.cards) {
        listOf("全部分类") + page.cards.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val roles = remember(page.cards) {
        page.cards.flatMap { it.roles }.filter { it.isNotBlank() }.distinct()
    }
    val roleOptions = rememberCharacterSelectorOptions(roles)
    val filteredCards = remember(page.cards, keyword, category, role, rarity, sortOption) {
        val filtered = page.cards.distinct().filter {
            (keyword.isBlank() || it.name.contains(keyword, true) || it.effect.contains(keyword, true) || it.roles.any { role -> role.contains(keyword, true) }) &&
                (category == "全部分类" || it.category == category) &&
                (role == "全部角色" || it.roles.contains(role)) &&
                (rarity == 0 || it.rarity == rarity)
        }
        when (sortOption) {
            PushCardSortOption.RARITY_DESC -> filtered.sortedWith(compareByDescending<ModeCard> { it.rarity }.thenBy { it.name })
            PushCardSortOption.RARITY_ASC -> filtered.sortedWith(compareBy<ModeCard> { it.rarity }.thenBy { it.name })
            PushCardSortOption.NAME_ASC -> filtered.sortedWith { left, right ->
                zhNameCollator.compare(left.name, right.name)
            }
        }
    }

    val conditions = listOfNotNull(
        category.takeUnless { it == "全部分类" }, role.takeUnless { it == "全部角色" },
        rarityLabel(rarity).takeIf { rarity != 0 }, sortOption.label.takeUnless { sortOption == PushCardSortOption.RARITY_DESC }
    )
    fun resetFilters() {
        category = "全部分类"
        role = "全部角色"
        rarity = 0
        sortOption = PushCardSortOption.RARITY_DESC
    }
    val minimumCardWidth = 168.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超弦推进卡牌", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = page.wikiUrl, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            isDataEmpty = { it.cards.isEmpty() },
            enablePullToRefresh = false,
            loading = { mod -> StringerPushCardSkeleton(minimumCardWidth, mod) }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minimumCardWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page.summary.isNotBlank()) {
                    item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                        TacticalCard(compact = true) {
                            Text(
                                page.summary,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchBar(
                            modifier = Modifier.weight(1f), keyword = keyword,
                            onKeywordChange = { keyword = it }, onClear = { keyword = "" },
                            isSearching = state.isLoading, placeholder = "搜索卡牌、效果或角色"
                        )
                        FilledTonalIconButton(
                            onClick = { showFilters = true }, modifier = Modifier.size(48.dp), shape = TacticalStyle.iconFrameShape
                        ) {
                            BadgedBox(badge = { if (conditions.isNotEmpty()) Badge() }) {
                                Icon(Icons.Outlined.FilterAlt, contentDescription = "筛选与排序")
                            }
                        }
                    }
                }

                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    PushCardFilterSummary(filteredCards.size, conditions, ::resetFilters)
                }

                if (filteredCards.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        CatalogEmptyResults(message = "没有匹配的卡牌", onClear = {
                            keyword = ""
                            resetFilters()
                        })
                    }
                } else {
                    gridItems(filteredCards, key = { checkNotNull(cardKeys[it]) }, contentType = { "push-card" }) { card ->
                        ModeCardGridItem(card = card, onClick = { selectedCard = card })
                    }
                }

                item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showFilters) WikiScrollableSheet(onDismiss = { showFilters = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TacticalSectionHeader("筛选与排序")
            SimpleDropdownSelector(label = "分类", options = categories, selected = category, onSelected = { category = it })
            CharacterSelector(
                label = "适用角色", options = roleOptions,
                selectedName = role.takeUnless { it == "全部角色" },
                onSelectedNameChange = { role = it ?: "全部角色" }, allLabel = "全部角色"
            )
            RarityChips(selectedRarity = rarity, onRarityChange = { rarity = it })
            EnumDropdownSelector("排序", PushCardSortOption.entries, sortOption) { sortOption = it }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = ::resetFilters, enabled = conditions.isNotEmpty()) { Text("重置筛选") }
                TextButton(onClick = { showFilters = false }) { Text("完成") }
            }
        }
    }

    selectedCard?.let { card ->
        WikiScrollableSheet(onDismiss = { selectedCard = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(card.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PushCardRarityBadge(card.rarity)
                    if (card.category.isNotBlank()) TacticalBadge(card.category)
                }
                if (card.effect.isNotBlank()) TacticalCard(compact = true) {
                    Text(card.effect, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge.tacticalTabular())
                }
                if (card.roles.isNotEmpty()) {
                    HorizontalDivider(color = TacticalStyle.dividerColor())
                    Text("适用角色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(card.roles.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun PushCardFilterSummary(count: Int, conditions: List<String>, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TacticalBadge("$count 张卡牌", tabular = true)
            conditions.forEach { TacticalBadge(it) }
        }
        if (conditions.isNotEmpty()) TextButton(onClick = onReset) { Text("重置") }
    }
}

@Composable
private fun StringerPushCardSkeleton(minimumCardWidth: Dp, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minimumCardWidth),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TacticalCard(compact = true) {
                ShimmerBox(Modifier.padding(12.dp).fillMaxWidth(0.8f).height(16.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.weight(1f).height(52.dp), shape = smoothCornerShape(26.dp))
                ShimmerBox(Modifier.size(48.dp), shape = TacticalStyle.iconFrameShape)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShimmerBox(Modifier.width(76.dp).height(24.dp), shape = TacticalStyle.badgeShape)
        }
        items(8) { StringerPushCardSkeletonItem() }
    }
}

@Composable
private fun StringerPushCardSkeletonItem() {
    TacticalCard(compact = true, accentColor = MaterialTheme.colorScheme.outlineVariant) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.size(56.dp), shape = TacticalStyle.iconFrameShape)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.7f).height(18.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.9f).height(14.dp))
                }
            }
            ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.85f).height(14.dp))
            ShimmerBox(Modifier.width(96.dp).height(24.dp), shape = smoothCornerShape(8.dp))
        }
    }
}

@Composable
internal fun ModeCardGridItem(
    card: ModeCard,
    onClick: () -> Unit
) {
    val accent = rarityColor(card.rarity)
    TacticalCard(
        onClick = onClick,
        compact = true,
        accentColor = accent
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = TacticalStyle.iconFrameShape,
                    color = accent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!card.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = card.imageUrl,
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = rarityColor(card.rarity), modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        card.name, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    PushCardRarityBadge(card.rarity)
                    if (card.category.isNotBlank()) {
                        Text(card.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                card.effect,
                style = MaterialTheme.typography.bodyMedium.tacticalTabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = TextUnit(1.4f, TextUnitType.Em)
            )
            if (card.roles.isNotEmpty()) Surface(
                shape = TacticalStyle.badgeShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = TacticalStyle.tileBorder()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        card.roles.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PushCardRarityBadge(rarity: Int) {
    val color = rarityColor(rarity)
    TacticalBadge(
        text = rarityLabel(rarity), dotColor = color,
        containerColor = color.copy(alpha = 0.14f), borderColor = color.copy(alpha = 0.32f)
    )
}

@Composable
private fun EnumDropdownSelector(
    label: String,
    options: List<PushCardSortOption>,
    selected: PushCardSortOption,
    onSelected: (PushCardSortOption) -> Unit
) {
    SimpleDropdownSelector(
        label = label,
        options = options.map { it.label },
        selected = selected.label,
        onSelected = { value ->
            options.firstOrNull { it.label == value }?.let(onSelected)
        }
    )
}

@Composable
private fun RarityChips(selectedRarity: Int, onRarityChange: (Int) -> Unit) {
    QualityFilterChips(
        selectedLevel = selectedRarity.takeIf { it != 0 },
        levels = listOf(2 to "精致", 3 to "卓越", 4 to "完美"),
        onSelectedLevelChange = { onRarityChange(it ?: 0) },
        colorForLevel = { level -> rarityColor(level) }
    )
}

private fun rarityLabel(rarity: Int): String = when (rarity) {
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    else -> "未知"
}

private fun rarityColor(rarity: Int): Color = when (rarity) {
    2 -> Color(0xFF42A5F5)
    3 -> Color(0xFFAB47BC)
    4 -> Color(0xFFF59E0B)
    else -> Color(0xFF9E9E9E)
}
