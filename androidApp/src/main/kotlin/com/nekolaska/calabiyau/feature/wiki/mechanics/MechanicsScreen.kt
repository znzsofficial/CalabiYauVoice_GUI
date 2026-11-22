package com.nekolaska.calabiyau.feature.wiki.mechanics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.mechanics.api.MechanicsApi
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.*
import java.net.URI

@Composable
fun MechanicsScreen(kind: MechanicsKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit,
                    onOpenCharacter: (String, String?) -> Unit) {
    // Different route instances may share a composable position during transitions.
    key(kind) { MechanicsContent(kind, onBack, onOpenWikiUrl, onOpenCharacter) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MechanicsContent(kind: MechanicsKind, onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit,
                             onOpenCharacter: (String, String?) -> Unit) {
    val state = rememberLoadState(initial = MechanicsPage(),
        cachedFetch = { MechanicsApi.fetch(kind, cacheOnly = true) },
        fetch = { force -> MechanicsApi.fetch(kind, forceRefresh = force, allowMemoryCache = false) })
    val selection = rememberSectionSelection(kind.name, state.data.sections.map { it.id })
    val scrollStates = rememberSaveableStateHolder()
    val uriHandler = LocalUriHandler.current
    val showSnack = rememberSnackbarLauncher()
    val open: (String) -> Unit = { url ->
        if (runCatching { URI(url).host == "wiki.biligame.com" }.getOrDefault(false)) onOpenWikiUrl(url)
        else runCatching { uriHandler.openUri(url) }.onFailure { showSnack("无法打开链接") }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(kind.pageName, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) }, actions = {
                RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(wikiUrl = kind.url, onOpenWikiUrl = onOpenWikiUrl, contentDescription = "查看 Wiki 原文")
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val current = state.data.sections.firstOrNull { it.id == selection.selectedKey } ?: state.data.sections.firstOrNull()
            // Horizontal scrolling prevents seven section choices from consuming a short screen.
            if (current != null) HorizontalFilterChips(items = state.data.sections, selected = current,
                label = { it.title }, onSelected = { selection.select(it.id) })
            ApiResourceContent(state = state, modifier = Modifier.weight(1f), isDataEmpty = MechanicsPage::isEmpty,
                retainedErrorMessage = ::retainedWikiPageError,
                loading = { MechanicsSkeleton(it, kind) }) {
                if (current != null) scrollStates.SaveableStateProvider(current.id) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item("source") { Text("来自卡拉彼丘 Wiki", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(current.blocks, key = { it.id }) { block ->
                            when (block) {
                                is MechanicsBlock.Text -> Row(Modifier.fillMaxWidth().padding(start = ((block.depth - 1).coerceIn(0, 4) * 16).dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    block.marker?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                                    WikiRichText(block.content, open, Modifier.weight(1f))
                                }
                                is MechanicsBlock.Heading -> Text(block.title, Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                is MechanicsBlock.Character -> Surface(onClick = { onOpenCharacter(block.name, block.portrait) },
                                    shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        AsyncImage(model = block.portrait, contentDescription = null,
                                            modifier = Modifier.size(64.dp, 88.dp), contentScale = ContentScale.Fit)
                                        Text(block.label, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                is MechanicsBlock.Record -> Surface(shape = AppShapes.compactCard,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        WikiRichText(block.title, open, heading = true)
                                        block.fields.forEach { field -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            WikiRichText(field.value, open)
                                        } }
                                    }
                                }
                                is MechanicsBlock.Video -> FilledTonalButton(onClick = { open(block.url) },
                                    modifier = Modifier.semantics { contentDescription = "观看视频：${block.label}" }) {
                                    Icon(Icons.Outlined.PlayCircle, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("在哔哩哔哩观看")
                                }
                                is MechanicsBlock.Image -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Display the source thumbnail as a preview, never pretend it is the original or save it as one.
                                    AsyncImage(model = block.thumbnail, contentDescription = block.label,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp), contentScale = ContentScale.Fit)
                                    if (block.fileUrl != null) TextButton(onClick = { onOpenWikiUrl(block.fileUrl) },
                                        modifier = Modifier.semantics { contentDescription = "查看 Wiki 图片：${block.label}" }) { Text("在 Wiki 查看图片") }
                                    else Text(block.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MechanicsSkeleton(modifier: Modifier, kind: MechanicsKind) {
    LazyColumn(modifier.fillMaxSize().semantics { contentDescription = "正在加载${kind.pageName}" },
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).height(32.dp), AppShapes.capsule) }
        } }
        item { SkeletonTextLine(widthFraction = 0.5f, height = 16.dp) }
        items(3) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonTextLine(widthFraction = 0.5f, height = 22.dp)
            repeat(3) { line -> SkeletonTextLine(widthFraction = if (line == 2) 0.65f else 1f, height = 20.dp) }
        } }
    }
}
