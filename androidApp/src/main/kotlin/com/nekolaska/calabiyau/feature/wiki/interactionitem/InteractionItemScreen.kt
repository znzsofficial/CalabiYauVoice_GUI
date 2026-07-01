package com.nekolaska.calabiyau.feature.wiki.interactionitem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.HorizontalFilterChips
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiListSkeleton
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.interactionitem.api.InteractionItemApi
import com.nekolaska.calabiyau.feature.wiki.interactionitem.model.INTERACTION_ITEM_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.interactionitem.model.InteractionItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality

private const val FILTER_ALL = "全部"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionItemScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = emptyList<InteractionItemInfo>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { InteractionItemApi.fetch(cacheOnly = true) },
        fetch = { force -> InteractionItemApi.fetch(forceRefresh = force) }
    )
    var keyword by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf(FILTER_ALL) }

    val items = state.data
    val qualityFilters = remember(items) {
        listOf(FILTER_ALL) + items.map { it.qualityName.ifBlank { "其他" } }.distinct()
    }
    val filteredItems = remember(items, keyword, selectedQuality) {
        items.filter { item ->
            val qualityName = item.qualityName.ifBlank { "其他" }
            val matchesQuality = selectedQuality == FILTER_ALL || qualityName == selectedQuality
            val matchesKeyword = keyword.isBlank() ||
                item.name.contains(keyword, ignoreCase = true) ||
                item.description.contains(keyword, ignoreCase = true) ||
                item.obtainMethod.contains(keyword, ignoreCase = true) ||
                qualityName.contains(keyword, ignoreCase = true)
            matchesQuality && matchesKeyword
        }.sortedWith(
            compareByDescending<InteractionItemInfo> { it.quality?.level ?: 0 }
                .thenBy { it.name }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("互动道具", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = INTERACTION_ITEM_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> WikiListSkeleton(mod) }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchBar(
                            keyword = keyword,
                            onKeywordChange = { keyword = it },
                            onSearch = {},
                            onClear = { keyword = "" },
                            isSearching = state.isLoading,
                            placeholder = "搜索互动道具 / 简介 / 获得方式"
                        )
                        HorizontalFilterChips(
                            items = qualityFilters,
                            selected = selectedQuality,
                            label = { it },
                            onSelected = { selectedQuality = it }
                        )
                    }
                }
                items(filteredItems, key = { it.name }) { item ->
                    InteractionItemCard(item)
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun InteractionItemCard(item: InteractionItemInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            item.quality?.qualityColor()?.copy(alpha = 0.4f)
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(smoothCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(18.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                item.quality?.let { quality ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = smoothCapsuleShape(),
                        color = quality.qualityColor().copy(alpha = 0.85f)
                    ) {
                        Text(
                            quality.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    item.description.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(shape = smoothCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "获得方式",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            item.obtainMethod.ifBlank { "未知" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Quality?.qualityColor() = when (this) {
    Quality.LEGENDARY -> Color(0xFFEF4444)
    Quality.PERFECT -> Color(0xFFF59E0B)
    Quality.SUPERIOR -> Color(0xFFA855F7)
    Quality.EXQUISITE -> Color(0xFF3B82F6)
    null -> MaterialTheme.colorScheme.outline
}
