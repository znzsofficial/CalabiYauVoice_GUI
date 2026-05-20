package com.nekolaska.calabiyau.feature.wiki.announcement

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.announcement.api.AnnouncementApi
import com.nekolaska.calabiyau.feature.wiki.announcement.model.Announcement

// ════════════════════════════════════════════════════════
//  公告资讯页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state = rememberLoadState(emptyList<Announcement>()) { force ->
        AnnouncementApi.fetchAnnouncements(forceRefresh = force)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("公告资讯", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> AnnouncementSkeleton(mod) }
        ) { announcements ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(announcements, key = { it.title }) { announcement ->
                    AnnouncementCard(
                        announcement = announcement,
                        onOpenWikiUrl = onOpenWikiUrl,
                        onOpenExternalUrl = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onOpenWikiUrl: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    Card(
        onClick = { onOpenWikiUrl(announcement.wikiUrl) },
        shape = smoothCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Outlined.Campaign,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
                Text(
                    announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // 日期
            if (announcement.date.isNotBlank()) {
                Surface(
                    shape = smoothCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        announcement.date,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 外部链接按钮
            if (announcement.biliUrl.isNotBlank() || announcement.officialUrl.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (announcement.biliUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { onOpenExternalUrl(announcement.biliUrl) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            shape = smoothCornerShape(14.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("B站", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (announcement.officialUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { onOpenExternalUrl(announcement.officialUrl) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            shape = smoothCornerShape(14.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("官网", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(5) {
            Card(
                shape = smoothCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(Modifier.size(32.dp), shape = smoothCornerShape(12.dp))
                        ShimmerBox(Modifier.weight(1f).height(16.dp))
                    }
                    ShimmerBox(Modifier.width(80.dp).height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(Modifier.width(70.dp).height(36.dp), shape = smoothCornerShape(14.dp))
                        ShimmerBox(Modifier.width(70.dp).height(36.dp), shape = smoothCornerShape(14.dp))
                    }
                }
            }
        }
    }
}
