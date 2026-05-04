package com.nekolaska.calabiyau.feature.wiki.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.history.api.GameHistoryApi
import com.nekolaska.calabiyau.feature.wiki.history.model.GAME_HISTORY_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistoryEntry
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistorySection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameHistoryScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberLoadState(emptyList<GameHistorySection>()) { force ->
        GameHistoryApi.fetchGameHistory(forceRefresh = force)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("游戏历史", fontWeight = FontWeight.Bold) },
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
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { onOpenWikiUrl(GAME_HISTORY_PAGE_URL) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器中打开")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> LoadingState("正在加载游戏历史…", mod) }
        ) { sections ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sections, key = { it.title }) { section ->
                    GameHistorySectionCard(
                        section = section,
                        onOpenWikiUrl = onOpenWikiUrl
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun GameHistorySectionCard(
    section: GameHistorySection,
    onOpenWikiUrl: (String) -> Unit
) {
    val sectionShape = smoothCornerShape(24.dp)
    Card(
        shape = sectionShape,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (section.title.contains("编年史") || section.title.contains("体验服") || section.title.contains("移动端")) {
                        Icons.Outlined.Timeline
                    } else {
                        Icons.Outlined.History
                    },
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    section.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (section.entries.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))

                val cardEntries = section.entries.filter { it.imageUrl != null }
                val linkEntries = section.entries.filter { it.imageUrl == null }

                if (cardEntries.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        cardEntries.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { entry ->
                                    GameHistoryImageCard(
                                        entry = entry,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenWikiUrl(entry.url) }
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (linkEntries.isNotEmpty()) {
                    if (cardEntries.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        linkEntries.forEach { entry ->
                            FilledTonalButton(
                                onClick = { onOpenWikiUrl(entry.url) },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = smoothCornerShape(12.dp)
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.labelLarge,
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
private fun GameHistoryImageCard(
    entry: GameHistoryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(12.dp),
        modifier = modifier.aspectRatio(1.4f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            if (entry.imageUrl != null) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}