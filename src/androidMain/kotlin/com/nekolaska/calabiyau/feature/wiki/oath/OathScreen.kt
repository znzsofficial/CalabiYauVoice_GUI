package com.nekolaska.calabiyau.feature.wiki.oath

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.oath.api.OathApi
import com.nekolaska.calabiyau.feature.wiki.oath.model.*

private enum class OathTab(val label: String, val icon: ImageVector) {
    OVERVIEW("概览", Icons.Outlined.FavoriteBorder),
    LEVELS("等级", Icons.Outlined.TableChart),
    GIFTS("礼物", Icons.Outlined.CardGiftcard),
    BONDS("羁绊", Icons.Outlined.Stars)
}

private const val ALL_GIFT_SOURCES = "全部礼物"
private const val BIRTHDAY_GIFT_SOURCE = "生日专属礼物"

@Composable
fun OathScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = OathPage(
            title = "誓约",
            summary = "",
            wikiUrl = "",
            levels = emptyList(),
            birthdayGifts = emptyList(),
            favorGifts = emptyList(),
            bondSections = emptyList()
        )
    ) { force ->
        OathApi.fetch(force)
    }
    var selectedTab by remember { mutableStateOf(OathTab.OVERVIEW) }
    var keyword by remember { mutableStateOf("") }
    var selectedCharacter by remember { mutableStateOf("全部角色") }
    var selectedGiftSource by remember { mutableStateOf(ALL_GIFT_SOURCES) }
    var previewImage by remember { mutableStateOf<PreviewImage?>(null) }

    val page = state.data
    val characters = remember(page) {
        listOf("全部角色") + (page.birthdayGifts.map { it.character } +
            page.favorGifts.flatMap { it.favorByCharacter.keys } +
            page.bondSections.map { it.character })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val giftSources = remember(page.favorGifts) {
        listOf(ALL_GIFT_SOURCES, BIRTHDAY_GIFT_SOURCE) + page.favorGifts.map { it.source }.distinct()
    }
    val filteredBirthdayGifts = remember(page.birthdayGifts, keyword, selectedCharacter, selectedGiftSource) {
        page.birthdayGifts.filter { gift ->
            (selectedGiftSource == ALL_GIFT_SOURCES || selectedGiftSource == BIRTHDAY_GIFT_SOURCE) &&
                (selectedCharacter == "全部角色" || gift.character == selectedCharacter) &&
                (keyword.isBlank() || gift.name.contains(keyword, true) || gift.character.contains(keyword, true) || gift.description.contains(keyword, true))
        }
    }
    val filteredFavorGifts = remember(page.favorGifts, keyword, selectedCharacter, selectedGiftSource) {
        page.favorGifts.filter { gift ->
            (selectedGiftSource == ALL_GIFT_SOURCES || gift.source == selectedGiftSource) &&
                (selectedCharacter == "全部角色" || gift.favorByCharacter.containsKey(selectedCharacter)) &&
                (keyword.isBlank() || gift.name.contains(keyword, true) || gift.description.contains(keyword, true) || gift.favorByCharacter.keys.any { it.contains(keyword, true) })
        }
    }
    val filteredBondSections = remember(page.bondSections, keyword, selectedCharacter) {
        page.bondSections.mapNotNull { section ->
            if (selectedCharacter != "全部角色" && section.character != selectedCharacter) return@mapNotNull null
            val items = section.items.filter { item ->
                keyword.isBlank() || item.name.contains(keyword, true) || item.description.contains(keyword, true) || section.character.contains(keyword, true)
            }
            if (items.isEmpty()) null else section.copy(items = items)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("誓约", fontWeight = FontWeight.Bold) },
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
            isDataEmpty = { it.levels.isEmpty() && it.birthdayGifts.isEmpty() && it.favorGifts.isEmpty() && it.bondSections.isEmpty() },
            enablePullToRefresh = false,
            loading = { mod -> WikiListSkeleton(modifier = mod, chipRows = 2) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        OathTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.label) },
                                icon = { Icon(tab.icon, contentDescription = null) }
                            )
                        }
                    }
                }
                if (selectedTab != OathTab.OVERVIEW && selectedTab != OathTab.LEVELS) {
                    item {
                        SearchBar(
                            keyword = keyword,
                            onKeywordChange = { keyword = it },
                            onSearch = {},
                            onClear = { keyword = "" },
                            isSearching = state.isLoading,
                            placeholder = when (selectedTab) {
                                OathTab.GIFTS -> "搜索礼物 / 简介 / 角色"
                                OathTab.BONDS -> "搜索羁绊之物 / 简介"
                                else -> "搜索"
                            }
                        )
                    }
                    item {
                        CharacterChips(
                            characters = characters,
                            selectedCharacter = selectedCharacter,
                            onSelectedCharacterChange = { selectedCharacter = it }
                        )
                    }
                }
                when (selectedTab) {
                    OathTab.OVERVIEW -> item { OathOverview(page = page) }
                    OathTab.LEVELS -> items(page.levels, key = { it.level }) { level -> OathLevelCard(level) }
                    OathTab.GIFTS -> {
                        item {
                            GiftSourceChips(
                                sources = giftSources,
                                selectedSource = selectedGiftSource,
                                onSelectedSourceChange = { selectedGiftSource = it }
                            )
                        }
                        if (filteredBirthdayGifts.isNotEmpty()) {
                            item { SectionTitle("角色生日专属礼物", filteredBirthdayGifts.size) }
                            items(filteredBirthdayGifts, key = { "birthday-${it.character}-${it.name}" }) { gift ->
                                BirthdayGiftCard(
                                    gift = gift,
                                    onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                                )
                            }
                        }
                        if (filteredFavorGifts.isNotEmpty()) {
                            item { SectionTitle("礼物好感表", filteredFavorGifts.size) }
                            items(filteredFavorGifts, key = { "favor-${it.source}-${it.name}" }) { gift ->
                                FavorGiftCard(
                                    gift = gift,
                                    selectedCharacter = selectedCharacter,
                                    onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                                )
                            }
                        }
                    }
                    OathTab.BONDS -> items(filteredBondSections, key = { it.character }) { section ->
                        BondSectionCard(
                            section = section,
                            onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                        )
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            model = image.url,
            contentDescription = image.title,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
private fun OathOverview(page: OathPage) {
    Card(
        shape = smoothCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (page.summary.isNotBlank()) {
                Text(page.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("等级", page.levels.size.toString(), Modifier.weight(1f))
                StatPill("礼物", (page.birthdayGifts.size + page.favorGifts.size).toString(), Modifier.weight(1f))
                StatPill("羁绊", page.bondSections.sumOf { it.items.size }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OathLevelCard(level: OathLevel) {
    Card(shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Lv.${level.level}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(level.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            DetailInfoRow("升级所需好感度", level.requiredFavor, labelWidth = 116.dp)
            DetailInfoRow("累计所需好感度", level.totalFavor, labelWidth = 116.dp)
        }
    }
}

@Composable
private fun BirthdayGiftCard(gift: OathBirthdayGift, onPreviewImage: (String, String) -> Unit) {
    InfoCard(title = gift.name, subtitle = gift.character, icon = Icons.Outlined.CardGiftcard, imageUrl = gift.imageUrl, onPreviewImage = onPreviewImage) {
        Text(gift.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DetailInfoRow("效果", gift.effect, labelWidth = 116.dp)
    }
}

@Composable
private fun FavorGiftCard(
    gift: OathFavorGift,
    selectedCharacter: String,
    onPreviewImage: (String, String) -> Unit
) {
    val maxFavor = remember(gift.favorByCharacter) {
        gift.favorByCharacter.values.mapNotNull { it.toIntOrNull() }.maxOrNull()
    }
    InfoCard(
        title = gift.name,
        subtitle = listOf(gift.source, gift.rarity).filter { it.isNotBlank() }.joinToString(" · "),
        icon = Icons.Outlined.CardGiftcard,
        imageUrl = gift.imageUrl,
        onPreviewImage = onPreviewImage
    ) {
        Text(gift.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gift.favorByCharacter.forEach { (character, favor) ->
                if (selectedCharacter == "全部角色" || selectedCharacter == character) {
                    AssistPill(
                        text = "$character +$favor",
                        highlighted = favor.toIntOrNull()?.let { it == maxFavor } == true
                    )
                }
            }
        }
    }
}

@Composable
private fun BondSectionCard(section: OathBondSection, onPreviewImage: (String, String) -> Unit) {
    Card(shape = smoothCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(section.character, section.items.size)
            section.items.forEachIndexed { index, item ->
                BondItemView(item, onPreviewImage)
                if (index != section.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun BondItemView(item: OathBondItem, onPreviewImage: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WikiIconBox(
            imageUrl = item.imageUrl,
            fallbackIcon = Icons.Outlined.Stars,
            size = 52.dp,
            modifier = item.imageUrl?.let { Modifier.clickable { onPreviewImage(it, item.name) } } ?: Modifier
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    imageUrl: String?,
    onPreviewImage: (String, String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WikiIconBox(
                    imageUrl = imageUrl,
                    fallbackIcon = icon,
                    size = 56.dp,
                    modifier = imageUrl?.let { Modifier.clickable { onPreviewImage(it, title) } } ?: Modifier
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}


@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("$count 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CharacterChips(characters: List<String>, selectedCharacter: String, onSelectedCharacterChange: (String) -> Unit) {
    HorizontalFilterChips(
        items = characters,
        selected = selectedCharacter,
        label = { it },
        onSelected = onSelectedCharacterChange
    )
}

@Composable
private fun GiftSourceChips(sources: List<String>, selectedSource: String, onSelectedSourceChange: (String) -> Unit) {
    HorizontalFilterChips(
        items = sources,
        selected = selectedSource,
        label = { it },
        onSelected = onSelectedSourceChange
    )
}

@Composable
private fun AssistPill(text: String, highlighted: Boolean = false) {
    val containerColor = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (highlighted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Surface(
        shape = smoothCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        )
    }
}
