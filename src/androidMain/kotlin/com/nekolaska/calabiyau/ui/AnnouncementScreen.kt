package com.nekolaska.calabiyau.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.AnnouncementApi
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════
//  公告资讯页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var announcements by remember { mutableStateOf<List<AnnouncementApi.Announcement>>(emptyList()) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = AnnouncementApi.fetchAnnouncements(forceRefresh = forceRefresh)) {
                is AnnouncementApi.ApiResult.Success -> announcements = result.value
                is AnnouncementApi.ApiResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

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
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载公告…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            errorMessage != null && announcements.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(errorMessage!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { loadData(forceRefresh = true) }) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(announcements, key = { it.title }) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            onOpenWikiUrl = onOpenWikiUrl,
                            onOpenExternalUrl = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) { }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
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
    ElevatedCard(
        onClick = { onOpenWikiUrl(announcement.wikiUrl) },
        shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
