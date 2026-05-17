package com.nekolaska.calabiyau.feature.wiki.collaboration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.collaboration.api.CollaborationApi
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.*

private const val FILTER_ALL = "全部"
private const val FILTER_TIMELINE = "时间轴"
private const val FILTER_DETAIL = "联动详情"

@Composable
fun CollaborationScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = CollaborationPage(
            timelineYears = emptyList(),
            events = emptyList()
        )
    ) { force ->
        CollaborationApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    var previewImage by remember { mutableStateOf<String?>(null) }

    val page = state.data
    val filteredTimeline = remember(page.timelineYears, keyword, selectedFilter) {
        if (selectedFilter == FILTER_DETAIL) emptyList() else page.timelineYears.mapNotNull { year ->
            val items = year.items.filter { item ->
                keyword.isBlank() || year.year.contains(keyword, true) || item.date.contains(keyword, true) || item.title.contains(keyword, true)
            }
            if (items.isEmpty()) null else year.copy(items = items)
        }
    }
    val filteredEvents = remember(page.events, keyword, selectedFilter) {
        if (selectedFilter == FILTER_TIMELINE) emptyList() else page.events.filter { event ->
            keyword.isBlank() || event.title.contains(keyword, true) ||
                event.sectionTitle.orEmpty().contains(keyword, true) ||
                event.date.orEmpty().contains(keyword, true) ||
                event.theme.orEmpty().contains(keyword, true) ||
                event.content.contains(keyword, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联动", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = COLLABORATION_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState(mod, "正在加载联动数据…") }
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
                        placeholder = "搜索联动 / 时间 / 主题 / 内容"
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = listOf(FILTER_ALL, FILTER_TIMELINE, FILTER_DETAIL),
                        selected = selectedFilter,
                        label = { it },
                        onSelected = { selectedFilter = it }
                    )
                }
                if (filteredTimeline.isNotEmpty()) {
                    item { SectionTitle("时间轴", filteredTimeline.sumOf { it.items.size }, Icons.Outlined.Schedule) }
                    items(filteredTimeline, key = { it.year }) { year -> TimelineYearCard(year) }
                }
                if (filteredEvents.isNotEmpty()) {
                    item { SectionTitle("联动详情", filteredEvents.size, Icons.Outlined.Handshake) }
                    itemsIndexed(filteredEvents, key = { index, event -> "${event.title}-${event.sectionTitle}-$index" }) { _, event ->
                        CollaborationEventCard(event = event, onPreviewImage = { previewImage = it })
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    previewImage?.let { imageUrl ->
        ImagePreviewDialog(
            model = imageUrl,
            contentDescription = "联动图片",
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = smoothCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("$count 项", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineYearCard(year: CollaborationTimelineYear) {
    Card(shape = smoothCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(year.year, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("${year.items.size} 项", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            year.items.forEachIndexed { index, item ->
                TimelineItemRow(item)
                if (index != year.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun TimelineItemRow(item: CollaborationTimelineItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                item.date.ifBlank { "时间待补充" },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CollaborationEventCard(event: CollaborationEvent, onPreviewImage: (String) -> Unit) {
    Card(shape = smoothCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            event.sectionTitle?.takeIf { it.isNotBlank() }?.let { section ->
                Surface(shape = smoothCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        section,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                event.date?.takeIf { it.isNotBlank() }?.let { InfoPill(Icons.Outlined.Schedule, it) }
                event.theme?.takeIf { it.isNotBlank() }?.let { InfoPill(Icons.Outlined.Title, it) }
            }
            if (event.content.isNotBlank()) {
                Surface(
                    shape = smoothCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Text(
                        event.content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
            event.publishInfo?.takeIf { it.isNotBlank() }?.let { publishInfo ->
                Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        publishInfo,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            event.imageUrls.forEach { imageUrl ->
                EventImage(imageUrl = imageUrl, onClick = { onPreviewImage(imageUrl) })
            }
        }
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventImage(imageUrl: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    Text("查看图片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}
