package com.nekolaska.calabiyau.feature.wiki.imprint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.HorizontalFilterChips
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.PreviewImage
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiIconBox
import com.nekolaska.calabiyau.core.ui.WikiListSkeleton
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.imprint.api.ImprintApi
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintItem
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintPage
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintSection

private const val ALL_CHARACTERS = "全部角色"
private const val ALL_LEVELS = "全部等级"

@Composable
fun ImprintScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = ImprintPage(
            title = "印迹",
            notice = "",
            wikiUrl = "",
            sections = emptyList()
        )
    ) { force ->
        ImprintApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedCharacter by remember { mutableStateOf(ALL_CHARACTERS) }
    var selectedLevel by remember { mutableStateOf(ALL_LEVELS) }
    var previewImage by remember { mutableStateOf<PreviewImage?>(null) }

    val page = state.data
    val characters = remember(page.sections) {
        listOf(ALL_CHARACTERS) + page.sections.map { it.character }
    }
    val levels = remember(page.sections) {
        listOf(ALL_LEVELS) + page.sections
            .asSequence()
            .flatMap { it.imprints }
            .mapNotNull { it.level }
            .distinct()
            .sorted()
            .map { "等级$it" }
            .toList()
    }
    val filteredSections = remember(page.sections, keyword, selectedCharacter, selectedLevel) {
        page.sections.mapNotNull { section ->
            if (selectedCharacter != ALL_CHARACTERS && section.character != selectedCharacter) return@mapNotNull null
            val imprints = section.imprints.filter { item ->
                val levelMatches = selectedLevel == ALL_LEVELS || item.level?.let { "等级$it" } == selectedLevel
                val keywordMatches = keyword.isBlank() || item.name.contains(keyword, true) ||
                    item.quote.contains(keyword, true) || item.obtainMethod.contains(keyword, true) ||
                    section.character.contains(keyword, true)
                levelMatches && keywordMatches
            }
            if (imprints.isEmpty()) null else section.copy(imprints = imprints)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("印迹", fontWeight = FontWeight.Bold) },
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
            enablePullToRefresh = false,
            loading = { mod -> WikiListSkeleton(modifier = mod, chipRows = 2) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ImprintOverview(page) }
                item {
                    SearchBar(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSearch = {},
                        onClear = { keyword = "" },
                        isSearching = state.isLoading,
                        placeholder = "搜索印迹 / 台词 / 获得方式"
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = characters,
                        selected = selectedCharacter,
                        label = { it },
                        onSelected = { selectedCharacter = it }
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = levels,
                        selected = selectedLevel,
                        label = { it },
                        onSelected = { selectedLevel = it }
                    )
                }
                items(filteredSections, key = { it.character }) { section ->
                    ImprintSectionCard(
                        section = section,
                        onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                    )
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
private fun ImprintOverview(page: ImprintPage) {
    val imprintCount = page.sections.sumOf { it.imprints.size }
    Card(
        shape = smoothCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (page.notice.isNotBlank()) {
                Text(page.notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("角色", page.sections.size.toString(), Modifier.weight(1f))
                StatPill("印迹", imprintCount.toString(), Modifier.weight(1f))
                StatPill("等级", page.sections.flatMap { it.imprints }.mapNotNull { it.level }.distinct().size.toString(), Modifier.weight(1f))
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
private fun ImprintSectionCard(section: ImprintSection, onPreviewImage: (String, String) -> Unit) {
    Card(shape = smoothCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(section.character, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${section.imprints.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            section.imprints.forEach { item ->
                ImprintItemCard(item, onPreviewImage)
            }
        }
    }
}

@Composable
private fun ImprintItemCard(item: ImprintItem, onPreviewImage: (String, String) -> Unit) {
    Surface(
        shape = smoothCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            WikiIconBox(
                imageUrl = item.imageUrl,
                fallbackIcon = Icons.Outlined.MilitaryTech,
                size = 58.dp,
                modifier = item.imageUrl?.let { Modifier.clickable { onPreviewImage(it, item.name) } } ?: Modifier
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    item.level?.let { LevelPill(it) }
                }
                if (item.quote.isNotBlank()) {
                    Text("【${item.quote}】", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.obtainMethod.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "获得方式",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(item.obtainMethod, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}


@Composable
private fun LevelPill(level: Int) {
    Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Lv.$level",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
