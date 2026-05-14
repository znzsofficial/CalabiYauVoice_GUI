package com.nekolaska.calabiyau.feature.wiki.playerlevel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiIconBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.playerlevel.api.PlayerLevelApi
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PLAYER_LEVEL_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelEntry
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelPage
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelReward
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelRewardItem

@Composable
fun PlayerLevelScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = PlayerLevelPage(
            title = "玩家等级",
            wikiUrl = PLAYER_LEVEL_PAGE_URL,
            intro = "",
            levels = emptyList(),
            rewards = emptyList(),
            note = null
        )
    ) { force ->
        PlayerLevelApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedSegment by remember { mutableStateOf<PlayerLevelSegment?>(null) }
    var previewImage by remember { mutableStateOf<PlayerLevelPreviewImage?>(null) }
    val page = state.data
    val filteredLevels = remember(page.levels, keyword) {
        page.levels.filter { level ->
            keyword.isBlank() || level.level.toString().contains(keyword) ||
                level.requiredExp?.toString()?.contains(keyword) == true
        }
    }
    val levelSegments = remember(filteredLevels) {
        filteredLevels.toPlayerLevelSegments()
    }
    val filteredRewards = remember(page.rewards, keyword) {
        page.rewards.filter { reward ->
            keyword.isBlank() || reward.level.toString().contains(keyword) ||
                reward.items.any { it.name.contains(keyword, true) } ||
                reward.weapons.any { it.name.contains(keyword, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("玩家等级", fontWeight = FontWeight.Bold) },
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
            loading = { mod -> LoadingState(mod, "正在加载玩家等级数据…") }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { PlayerLevelOverview(page) }
                item {
                    SearchBar(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSearch = {},
                        onClear = { keyword = "" },
                        isSearching = state.isLoading,
                        placeholder = "搜索等级 / 经验 / 奖励 / 武器"
                    )
                }
                if (filteredRewards.isNotEmpty()) {
                    item { SectionHeader("等级奖励", filteredRewards.size, Icons.Outlined.Reviews) }
                    items(filteredRewards, key = { "reward-${it.level}" }) { reward ->
                        RewardCard(
                            reward = reward,
                            onPreviewImage = { url, title -> previewImage = PlayerLevelPreviewImage(url, title) }
                        )
                    }
                }
                page.note?.takeIf { it.isNotBlank() }?.let { note -> item { NoteCard(note) } }
                if (filteredLevels.isNotEmpty()) {
                    item { SectionHeader("等级框与经验", levelSegments.size, Icons.Outlined.Grade) }
                    items(levelSegments, key = { "level-segment-${it.startLevel}" }) { segment ->
                        LevelSegmentCard(
                            segment = segment,
                            onClick = { selectedSegment = segment },
                            onPreviewImage = { url, title -> previewImage = PlayerLevelPreviewImage(url, title) }
                        )
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    selectedSegment?.let { segment ->
        LevelExpDialog(segment = segment, onDismiss = { selectedSegment = null })
    }
    previewImage?.let { image ->
        ImagePreviewDialog(
            model = image.url,
            contentDescription = image.title,
            onDismiss = { previewImage = null },
            onSave = {}
        )
    }
}

@Composable
private fun PlayerLevelOverview(page: PlayerLevelPage) {
    Card(
        shape = smoothCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (page.intro.isNotBlank()) {
                Text(page.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("$count 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RewardCard(
    reward: PlayerLevelReward,
    onPreviewImage: (String, String) -> Unit
) {
    Card(shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${reward.level}级", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${reward.items.size + reward.weapons.size} 项奖励", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reward.items.forEach { item -> RewardItemPill(item, onPreviewImage) }
                reward.weapons.forEach { weapon -> TextPill("解锁 ${weapon.name}") }
            }
        }
    }
}

@Composable
private fun RewardItemPill(item: PlayerLevelRewardItem, onPreviewImage: (String, String) -> Unit) {
    Surface(
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WikiIconBox(
                imageUrl = item.iconUrl,
                fallbackIcon = Icons.Outlined.MilitaryTech,
                size = 34.dp,
                modifier = item.iconUrl?.let { Modifier.clickable { onPreviewImage(it, item.name) } } ?: Modifier
            )
            Column {
                Text(item.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.count?.let { Text("x$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun LevelSegmentCard(
    segment: PlayerLevelSegment,
    onClick: () -> Unit,
    onPreviewImage: (String, String) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = smoothCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WikiIconBox(
                imageUrl = segment.frameImageUrl,
                fallbackIcon = Icons.Outlined.Grade,
                size = 46.dp,
                modifier = segment.frameImageUrl?.let { Modifier.clickable { onPreviewImage(it, "等级 ${segment.startLevel}-${segment.endLevel}") } } ?: Modifier
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("等级 ${segment.startLevel}-${segment.endLevel}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("点击查看每级升级所需经验", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LevelExpDialog(segment: PlayerLevelSegment, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        icon = { Icon(Icons.Outlined.Grade, contentDescription = null) },
        title = { Text("等级 ${segment.startLevel}-${segment.endLevel}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(segment.entries, key = { "dialog-level-${it.level}" }) { entry ->
                    Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Lv.${entry.level}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                            Text("升级所需经验", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                text = entry.requiredExp?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    )
}

private data class PlayerLevelSegment(
    val startLevel: Int,
    val endLevel: Int,
    val frameImageUrl: String?,
    val entries: List<PlayerLevelEntry>
)

private data class PlayerLevelPreviewImage(
    val url: String,
    val title: String
)

private fun List<PlayerLevelEntry>.toPlayerLevelSegments(): List<PlayerLevelSegment> {
    val sorted = distinctBy { it.level }.sortedBy { it.level }
    if (sorted.isEmpty()) return emptyList()
    val segments = mutableListOf<PlayerLevelSegment>()
    var current = mutableListOf<PlayerLevelEntry>()
    var currentFrameUrl: String? = null
    sorted.forEach { entry ->
        val startsNewSegment = current.isNotEmpty() && entry.frameImageUrl != currentFrameUrl
        if (startsNewSegment) {
            segments += current.toPlayerLevelSegment(currentFrameUrl)
            current = mutableListOf()
        }
        currentFrameUrl = entry.frameImageUrl
        current += entry
    }
    if (current.isNotEmpty()) segments += current.toPlayerLevelSegment(currentFrameUrl)
    return segments
}

private fun List<PlayerLevelEntry>.toPlayerLevelSegment(frameImageUrl: String?): PlayerLevelSegment {
    return PlayerLevelSegment(
        startLevel = first().level,
        endLevel = last().level,
        frameImageUrl = frameImageUrl,
        entries = this
    )
}

@Composable
private fun TextPill(text: String) {
    Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun NoteCard(note: String) {
    Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(note, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}
