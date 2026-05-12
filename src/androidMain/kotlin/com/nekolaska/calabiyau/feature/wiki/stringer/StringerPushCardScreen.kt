package com.nekolaska.calabiyau.feature.wiki.stringer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.SearchBar
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
    val state = rememberLoadState(
        initial = CardPage(
            title = "超弦推进卡牌",
            summary = "",
            wikiUrl = "",
            cards = emptyList()
        )
    ) { force ->
        StringerPushCardApi.fetch(force)
    }

    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部分类") }
    var role by remember { mutableStateOf("全部角色") }
    var rarity by remember { mutableIntStateOf(0) }
    var sortOption by remember { mutableStateOf(PushCardSortOption.RARITY_DESC) }
    var selectedCard by remember { mutableStateOf<ModeCard?>(null) }

    val page = state.data
    val categories = remember(page.cards) {
        listOf("全部分类") + page.cards.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val roles = remember(page.cards) {
        page.cards.flatMap { it.roles }.filter { it.isNotBlank() }.distinct()
    }
    val roleOptions = rememberCharacterSelectorOptions(roles)
    val filteredCards = remember(page.cards, keyword, category, role, rarity, sortOption) {
        val filtered = page.cards.filter {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超弦推进卡牌 ${filteredCards.size}", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
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
                        onClick = { if (page.wikiUrl.isNotBlank()) onOpenWikiUrl(page.wikiUrl) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        enabled = page.wikiUrl.isNotBlank()
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "打开 Wiki")
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState("正在加载超弦推进卡牌…", mod) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page.summary.isNotBlank()) {
                    item {
                        Card(
                            shape = smoothCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Text(
                                page.summary,
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        placeholder = "搜索卡牌 / 效果 / 适用角色"
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.size(8.dp))
                                Text("筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.weight(1f)) {
                                    DropdownSelector(
                                        label = "分类",
                                        options = categories,
                                        selected = category,
                                        onSelected = { category = it }
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    EnumDropdownSelector(
                                        label = "排序",
                                        options = PushCardSortOption.entries,
                                        selected = sortOption,
                                        onSelected = { sortOption = it }
                                    )
                                }
                            }
                            CharacterSelector(
                                label = "适用角色",
                                options = roleOptions,
                                selectedName = role.takeUnless { it == "全部角色" },
                                onSelectedNameChange = { role = it ?: "全部角色" },
                                allLabel = "全部角色"
                            )
                            RarityChips(selectedRarity = rarity, onRarityChange = { rarity = it })
                        }
                    }
                }

                if (filteredCards.isEmpty()) {
                    item {
                        Text(
                            "没有匹配的卡牌",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cardMinWidth = 168.dp
                            val columns = (maxWidth / cardMinWidth).toInt().coerceAtLeast(1)
                            val cardWidth = (maxWidth - (12.dp * (columns - 1))) / columns

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = columns
                            ) {
                                filteredCards.forEach { card ->
                                    Box(modifier = Modifier.width(cardWidth)) {
                                        ModeCardGridItem(card = card, onClick = { selectedCard = card })
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedCard?.let { card ->
        ModalBottomSheet(onDismissRequest = { selectedCard = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(card.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOf(card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(card.effect, style = MaterialTheme.typography.bodyLarge)
                if (card.roles.isNotEmpty()) {
                    HorizontalDivider()
                    Text("适用角色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(card.roles.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ModeCardGridItem(
    card: ModeCard,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                    shape = smoothCornerShape(16.dp),
                    color = rarityColor(card.rarity).copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, rarityColor(card.rarity).copy(alpha = 0.4f)),
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
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(card.category, rarityLabel(card.rarity)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                card.effect,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = TextUnit(1.4f, TextUnitType.Em)
            )
            val hasRoles = card.roles.isNotEmpty()
            Surface(
                shape = smoothCornerShape(8.dp),
                color = if (hasRoles) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                contentColor = if (hasRoles) MaterialTheme.colorScheme.primary else Color.Transparent
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
private fun EnumDropdownSelector(
    label: String,
    options: List<PushCardSortOption>,
    selected: PushCardSortOption,
    onSelected: (PushCardSortOption) -> Unit
) {
    DropdownSelector(
        label = label,
        options = options.map { it.label },
        selected = selected.label,
        onSelected = { value ->
            options.firstOrNull { it.label == value }?.let(onSelected)
        }
    )
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = smoothCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                label = { Text(label, textAlign = TextAlign.Center) },
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
