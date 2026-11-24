package com.nekolaska.calabiyau.feature.wiki.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityApi
import com.nekolaska.calabiyau.feature.wiki.activity.model.ACTIVITY_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry

// Keep the small article header even if the activity rotates off the index while it is open.
internal val selectedActivitySaver = listSaver<ActivityEntry?, String>(
    save = { entry ->
        entry?.detailPageTitle?.let { page ->
            listOf(page, entry.title, entry.startTime, entry.endTime, entry.description, entry.wikiUrl)
        }.orEmpty()
    },
    restore = { fields ->
        if (fields.size != 6) null else ActivityEntry(fields[1], fields[2], fields[3], fields[4],
            wikiUrl = fields[5], detailPageTitle = fields[0])
    }
)

@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    isActive: () -> Boolean = { true }
) {
    val state = rememberLoadState(
        initial = emptyList<ActivityEntry>(),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { ActivityApi.fetch(cacheOnly = true) },
        fetch = { force -> ActivityApi.fetch(forceRefresh = force, allowMemoryCache = false) }
    )
    var selected by rememberSaveable(stateSaver = selectedActivitySaver) { mutableStateOf<ActivityEntry?>(null) }

    fun canUseList() = isActive() && selected == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("活动", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = { if (canUseList()) onBack() })
                },
                actions = {
                    OpenWikiActionButton(
                        wikiUrl = ACTIVITY_PAGE_URL,
                        onOpenWikiUrl = { if (canUseList()) onOpenWikiUrl(it) },
                        contentDescription = "查看活动列表原文"
                    )
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> ActivitySkeleton(mod) }
        ) { activities ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activities, key = { it.title + it.startTime + it.endTime }) { activity ->
                    ActivityCard(activity = activity, onOpen = {
                        if (canUseList()) {
                            if (activity.detailPageTitle != null) selected = activity else onOpenWikiUrl(activity.wikiUrl)
                        }
                    })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    selected?.let { entry ->
        val title = requireNotNull(entry.detailPageTitle)
        key(title) {
            ActivityDetailSheet(
                title = title,
                activity = state.data.firstOrNull { it.detailPageTitle == title } ?: entry,
                isActive = { isActive() && selected === entry },
                onDismiss = { selected = null },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
    }
}

@Composable
private fun ActivityCard(
    activity: ActivityEntry,
    onOpen: () -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            if (activity.imageUrl != null) {
                AsyncImage(
                    model = activity.imageUrl,
                    contentDescription = activity.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Celebration,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        activity.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                TimeInfoRow(
                    icon = Icons.Outlined.EventAvailable,
                    label = "开始时间",
                    value = activity.startTime
                )
                Spacer(Modifier.height(8.dp))
                TimeInfoRow(
                    icon = Icons.Outlined.AccessTime,
                    label = "结束时间",
                    value = activity.endTime
                )

                if (activity.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        activity.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onOpen,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Icon(if (activity.detailPageTitle != null) Icons.AutoMirrored.Outlined.Article else Icons.Outlined.OpenInBrowser,
                        contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(when {
                        activity.detailPageTitle != null -> "查看活动详情"
                        activity.wikiUrl == ACTIVITY_PAGE_URL -> "查看活动列表原文"
                        else -> "查看活动页"
                    })
                }
            }
        }
    }
}

@Composable
private fun TimeInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(8.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActivitySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Card(
                shape = smoothCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = smoothCornerShape(24.dp)
                    )
                    Column(Modifier.padding(20.dp)) {
                        SkeletonTextLine(widthFraction = 0.7f)
                        Spacer(Modifier.height(12.dp))
                        SkeletonTextLine(widthFraction = 0.45f)
                        Spacer(Modifier.height(8.dp))
                        SkeletonTextLine(widthFraction = 0.5f)
                        Spacer(Modifier.height(12.dp))
                        SkeletonTextLine()
                        Spacer(Modifier.height(6.dp))
                        SkeletonTextLine(widthFraction = 0.8f)
                    }
                }
            }
        }
    }
}
