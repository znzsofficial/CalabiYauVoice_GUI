package com.nekolaska.calabiyau.feature.wiki.submission

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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.submission.api.SubmissionApi
import com.nekolaska.calabiyau.feature.wiki.submission.model.SUBMISSION_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.submission.model.SubmissionEntry
import com.nekolaska.calabiyau.feature.wiki.submission.model.SubmissionPage

private const val FILTER_ALL = "全部"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = SubmissionPage(entries = emptyList()),
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { SubmissionApi.fetch(cacheOnly = true) },
        fetch = { force -> SubmissionApi.fetch(forceRefresh = force, allowMemoryCache = false) }
    )
    var keyword by remember { mutableStateOf("") }
    var selectedTopic by remember { mutableStateOf(FILTER_ALL) }

    val page = state.data
    val topics = remember(page.entries) {
        listOf(FILTER_ALL) + page.entries.map { it.topic }.filter { it.isNotBlank() }.distinct().take(8)
    }
    val filteredEntries = remember(page.entries, keyword, selectedTopic) {
        page.entries.filter { entry ->
            val matchesTopic = selectedTopic == FILTER_ALL || entry.topic == selectedTopic
            val matchesKeyword = keyword.isBlank() ||
                entry.title.contains(keyword, ignoreCase = true) ||
                entry.author.contains(keyword, ignoreCase = true) ||
                entry.type.contains(keyword, ignoreCase = true) ||
                entry.topic.contains(keyword, ignoreCase = true)
            matchesTopic && matchesKeyword
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("投稿作品", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = SUBMISSION_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState(mod, "正在加载投稿作品…") }
        ) { data ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SubmissionIntroCard(
                        count = data.entries.size,
                        onSubmit = { onOpenWikiUrl(SUBMISSION_PAGE_URL) }
                    )
                }
                item {
                    SearchBar(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSearch = {},
                        onClear = { keyword = "" },
                        isSearching = state.isLoading,
                        placeholder = "搜索作品 / 作者 / 类型 / 题材"
                    )
                }
                if (topics.size > 1) {
                    item {
                        HorizontalFilterChips(
                            items = topics,
                            selected = selectedTopic,
                            label = { it },
                            onSelected = { selectedTopic = it }
                        )
                    }
                }
                items(filteredEntries, key = { it.title + it.date + it.author }) { entry ->
                    SubmissionEntryCard(
                        entry = entry,
                        onOpenWikiUrl = onOpenWikiUrl
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun SubmissionIntroCard(count: Int, onSubmit: () -> Unit) {
    Card(
        shape = smoothCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("引航者投稿作品", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("已收录 $count 个作品，投稿表单请在 Wiki 原页完成", style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalButton(onClick = onSubmit) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("前往投稿页面", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SubmissionEntryCard(entry: SubmissionEntry, onOpenWikiUrl: (String) -> Unit) {
    Card(
        onClick = { onOpenWikiUrl(entry.wikiUrl) },
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    shape = smoothCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(entry.type.typeIcon(), contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(entry.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(Icons.Outlined.Person, entry.author.ifBlank { "未知作者" })
                InfoPill(Icons.Outlined.Style, entry.topic.ifBlank { "未分类" })
                InfoPill(entry.type.typeIcon(), entry.type.ifBlank { "作品" })
            }
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    Surface(
        shape = smoothCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun String.typeIcon(): ImageVector = when {
    contains("视频") -> Icons.Outlined.Movie
    contains("文章") -> Icons.AutoMirrored.Outlined.Article
    else -> Icons.Outlined.Brush
}
