package com.nekolaska.calabiyau.feature.wiki.achievement

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MilitaryTech
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
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiIconBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.achievement.api.AchievementApi
import com.nekolaska.calabiyau.feature.wiki.achievement.model.ACHIEVEMENT_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementItem
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementPage
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementSection

private const val ALL_CATEGORIES = "全部分类"
private const val ALL_LEVELS = "全部等级"

@Composable
fun AchievementScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = AchievementPage(title = "成就", wikiUrl = ACHIEVEMENT_PAGE_URL, sections = emptyList())
    ) { force ->
        AchievementApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORIES) }
    var selectedLevel by remember { mutableStateOf(ALL_LEVELS) }

    val page = state.data
    val categories = remember(page.sections) { listOf(ALL_CATEGORIES) + page.sections.map { it.category } }
    val levels = remember(page.sections) {
        listOf(ALL_LEVELS) + page.sections
            .flatMap { it.achievements }
            .mapNotNull { it.level }
            .distinct()
    }
    val filteredSections = remember(page.sections, keyword, selectedCategory, selectedLevel) {
        page.sections.mapNotNull { section ->
            if (selectedCategory != ALL_CATEGORIES && section.category != selectedCategory) return@mapNotNull null
            val achievements = section.achievements.filter { achievement ->
                val levelMatches = selectedLevel == ALL_LEVELS || achievement.level == selectedLevel
                val keywordMatches = keyword.isBlank() || achievement.name.contains(keyword, true) ||
                    achievement.flavorText.contains(keyword, true) || achievement.condition.contains(keyword, true) ||
                    achievement.category.contains(keyword, true) || achievement.level.orEmpty().contains(keyword, true)
                levelMatches && keywordMatches
            }
            if (achievements.isEmpty()) null else section.copy(achievements = achievements)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成就", fontWeight = FontWeight.Bold) },
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
            loading = { mod -> LoadingState(mod, "正在加载成就数据…") }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SearchBar(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSearch = {},
                        onClear = { keyword = "" },
                        isSearching = state.isLoading,
                        placeholder = "搜索成就 / 获得方式 / 台词"
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = categories,
                        selected = selectedCategory,
                        label = { it },
                        onSelected = { selectedCategory = it }
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
                items(filteredSections, key = { it.category }) { section ->
                    AchievementSectionCard(section)
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun AchievementSectionCard(section: AchievementSection) {
    Card(shape = smoothCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(section.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${section.achievements.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            section.achievements.forEach { item -> AchievementItemCard(item) }
        }
    }
}

@Composable
private fun AchievementItemCard(item: AchievementItem) {
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
            WikiIconBox(imageUrl = item.imageUrl, fallbackIcon = Icons.Outlined.MilitaryTech, size = 58.dp)
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
                if (item.flavorText.isNotBlank()) {
                    Text(item.flavorText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.condition.isNotBlank()) {
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
                        Text(item.condition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelPill(level: String) {
    Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            level,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
