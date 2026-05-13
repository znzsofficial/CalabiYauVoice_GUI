package com.nekolaska.calabiyau.feature.wiki.bgm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.nekolaska.calabiyau.core.media.AudioPlayButton
import com.nekolaska.calabiyau.core.media.AudioPlayerManager
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.HorizontalFilterChips
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.bgm.api.BgmApi
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BGM_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmAlbum
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmPage
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmTrack

private const val FILTER_ALL = "全部"
private const val GROUP_CATEGORY = "分类"
private const val GROUP_CHARACTER = "角色"
private const val GROUP_ALBUM = "专辑"

@Composable
fun BgmScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = BgmPage(tracks = emptyList(), albums = emptyList(), lyricSections = emptyList())
    ) { force ->
        BgmApi.fetch(force)
    }
    var keyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FILTER_ALL) }
    var groupMode by remember { mutableStateOf(GROUP_CATEGORY) }

    DisposableEffect(Unit) {
        onDispose { AudioPlayerManager.stop() }
    }

    val page = state.data
    val categories = remember(page.tracks) {
        listOf(FILTER_ALL) + page.tracks.map { it.category }.distinct()
    }
    val filteredTracks = remember(page.tracks, keyword, selectedCategory) {
        page.tracks.filter { track ->
            (selectedCategory == FILTER_ALL || track.category == selectedCategory) &&
                (keyword.isBlank() || track.title.contains(keyword, true) ||
                    track.category.contains(keyword, true) ||
                    track.group.orEmpty().contains(keyword, true) ||
                    track.section.orEmpty().contains(keyword, true) ||
                    track.album.orEmpty().contains(keyword, true) ||
                    track.character.orEmpty().contains(keyword, true) ||
                    track.scene.orEmpty().contains(keyword, true))
        }
    }
    val groupedTracks = remember(filteredTracks, groupMode) {
        filteredTracks.groupBy { track ->
            when (groupMode) {
                GROUP_CHARACTER -> track.character ?: "非角色音乐"
                GROUP_ALBUM -> track.album ?: "未归属专辑"
                else -> track.category
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BGM", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = BGM_PAGE_URL, onOpenWikiUrl = onOpenWikiUrl)
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            enablePullToRefresh = false,
            loading = { mod -> LoadingState("正在加载 BGM…", mod) }
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
                        placeholder = "搜索曲名 / 分类 / 场景"
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = categories,
                        selected = selectedCategory,
                        label = { it },
                        onSelected = { selectedCategory = it }
                    )
                }
                item {
                    HorizontalFilterChips(
                        items = listOf(GROUP_CATEGORY, GROUP_CHARACTER, GROUP_ALBUM),
                        selected = groupMode,
                        label = { "按$it" },
                        onSelected = { groupMode = it }
                    )
                }
                item { TrackCountHeader(filteredTracks.size, page.albums.size) }
                groupedTracks.forEach { (groupName, tracks) ->
                    item(key = "group_$groupMode$groupName") {
                        GroupHeader(groupName, tracks.size)
                    }
                    items(tracks, key = { it.audioUrl }) { track ->
                        BgmTrackCard(track)
                    }
                }
                if (page.albums.isNotEmpty()) {
                    item { SectionHeader("专辑表格", page.albums.size, Icons.Outlined.LibraryMusic) }
                    items(page.albums, key = { it.title }) { album ->
                        BgmAlbumCard(album)
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun TrackCountHeader(trackCount: Int, albumCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Album, contentDescription = null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
        Text("音乐库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("$trackCount 首 · $albumCount 专辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun GroupHeader(title: String, count: Int) {
    Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            Text("$count 首", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun BgmTrackCard(track: BgmTrack) {
    Card(shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoverImage(track.coverUrl, track.title)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TrackMetaLine(track)
                    if (!track.album.isNullOrBlank() || !track.duration.isNullOrBlank() || !track.scene.isNullOrBlank()) {
                        Text(
                            listOf(track.album, track.duration, track.scene).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AudioPlayButton(source = track.audioUrl, size = 48)
            }
        }
    }
}

@Composable
private fun CoverImage(coverUrl: String?, title: String) {
    Card(shape = smoothCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrackMetaLine(track: BgmTrack) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            listOf(track.category, track.character ?: track.group, track.section).filterNot { it.isNullOrBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BgmAlbumCard(album: BgmAlbum) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = smoothCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(album.coverUrl, album.title)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(album.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${album.tracks.size} 首曲目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    album.tracks.forEachIndexed { index, track ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Text("${index + 1}.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                track.scene?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            track.duration?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
