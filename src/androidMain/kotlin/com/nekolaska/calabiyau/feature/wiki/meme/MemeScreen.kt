package com.nekolaska.calabiyau.feature.wiki.meme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.HorizontalFilterChips
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.meme.api.MemeApi
import com.nekolaska.calabiyau.feature.wiki.meme.model.MEME_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemeEntry
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemeOfficialIssue
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemePage

private const val FILTER_ALL = "全部"
private const val FILTER_OFFICIAL = "官方编写"
private const val FILTER_EDITOR = "编辑编写"

@Composable
fun MemeScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = MemePage(
            officialIssues = emptyList(),
            editorEntries = emptyList()
        )
    ) { force ->
        MemeApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    var previewIssue by remember { mutableStateOf<MemeOfficialIssue?>(null) }

    val page = state.data
    val filteredIssues = remember(page.officialIssues, keyword, selectedFilter) {
        if (selectedFilter == FILTER_EDITOR) emptyList() else page.officialIssues.filter { issue ->
            keyword.isBlank() || issue.title.contains(keyword, true)
        }
    }
    val filteredEntries = remember(page.editorEntries, keyword, selectedFilter) {
        if (selectedFilter == FILTER_OFFICIAL) emptyList() else page.editorEntries.filter { entry ->
            keyword.isBlank() || entry.title.contains(keyword, true) || entry.description.contains(keyword, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("梗百科", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = MEME_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState(mod, "正在加载梗百科…") }
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
                        placeholder = "搜索梗 / 解释 / 官方期数"
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = listOf(FILTER_ALL, FILTER_OFFICIAL, FILTER_EDITOR),
                        selected = selectedFilter,
                        label = { it },
                        onSelected = { selectedFilter = it }
                    )
                }
                if (filteredIssues.isNotEmpty()) {
                    item { SectionHeader("官方编写", filteredIssues.size, Icons.Outlined.CollectionsBookmark) }
                    items(filteredIssues, key = { it.title }) { issue ->
                        OfficialIssueCard(issue = issue, onClick = { previewIssue = issue })
                    }
                }
                if (filteredEntries.isNotEmpty()) {
                    item { SectionHeader("编辑编写", filteredEntries.size, Icons.AutoMirrored.Outlined.MenuBook) }
                    items(filteredEntries, key = { it.title }) { entry ->
                        MemeEntryCard(entry)
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    previewIssue?.imageUrl?.let { imageUrl ->
        ImagePreviewDialog(
            model = imageUrl,
            contentDescription = previewIssue?.title.orEmpty(),
            onDismiss = { previewIssue = null },
            onSave = {}
        )
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
private fun OfficialIssueCard(issue: MemeOfficialIssue, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (issue.imageUrl != null) {
                AsyncImage(
                    model = issue.imageUrl,
                    contentDescription = issue.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            } else {
                Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                issue.title,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MemeEntryCard(entry: MemeEntry) {
    Card(
        shape = smoothCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Surface(
                shape = smoothCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    entry.description,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
