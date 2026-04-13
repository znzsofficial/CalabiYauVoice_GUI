package com.nekolaska.calabiyau.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.data.AnnouncementApi

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
    val state = rememberLoadState(emptyList<AnnouncementApi.Announcement>()) { force ->
        AnnouncementApi.fetchAnnouncements(forceRefresh = force)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("公告资讯", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
                            } catch (_: Exception) { }
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
    announcement: AnnouncementApi.Announcement,
    onOpenWikiUrl: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    Card(
        onClick = { onOpenWikiUrl(announcement.wikiUrl) },
        shape = smoothCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题
            Text(
                announcement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 日期
            if (announcement.date.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    announcement.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 外部链接按钮
            if (announcement.biliUrl.isNotBlank() || announcement.officialUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (announcement.biliUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { onOpenExternalUrl(announcement.biliUrl) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = smoothCornerShape(10.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Outlined.PlayCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("B站", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (announcement.officialUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { onOpenExternalUrl(announcement.officialUrl) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = smoothCornerShape(10.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Outlined.Language, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("官网", style = MaterialTheme.typography.labelSmall)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) {
            Card(
                shape = smoothCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.85f).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.width(80.dp).height(10.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(Modifier.width(60.dp).height(32.dp), shape = smoothCapsuleShape())
                        ShimmerBox(Modifier.width(60.dp).height(32.dp), shape = smoothCapsuleShape())
                    }
                }
            }
        }
    }
}
