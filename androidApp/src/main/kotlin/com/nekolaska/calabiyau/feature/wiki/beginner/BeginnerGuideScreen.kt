package com.nekolaska.calabiyau.feature.wiki.beginner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.beginner.api.BeginnerGuideApi
import com.nekolaska.calabiyau.feature.wiki.beginner.model.*
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeginnerGuideScreen(onBack: () -> Unit, onOpenWikiUrl: (String) -> Unit) {
    val state = rememberLoadState(
        initial = BeginnerGuidePage(),
        cachedFetch = { BeginnerGuideApi.fetch(cacheOnly = true) },
        fetch = { force -> BeginnerGuideApi.fetch(forceRefresh = force, allowMemoryCache = false) }
    )
    val selection = rememberSectionSelection(BEGINNER_GUIDE_PAGE, state.data.sections.map { it.id })
    val scrollStates = rememberSaveableStateHolder()
    val uriHandler = LocalUriHandler.current
    val showSnack = rememberSnackbarLauncher()
    val openLink: (String) -> Unit = { url ->
        if (runCatching { URI(url).host == "wiki.biligame.com" }.getOrDefault(false)) onOpenWikiUrl(url)
        else runCatching { uriHandler.openUri(url) }.onFailure { showSnack("无法打开链接") }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(BEGINNER_GUIDE_PAGE, fontWeight = FontWeight.Bold) },
            navigationIcon = { BackNavButton(onClick = onBack) },
            actions = {
                RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                OpenWikiActionButton(wikiUrl = BEGINNER_GUIDE_URL, onOpenWikiUrl = onOpenWikiUrl, contentDescription = "查看 Wiki 原文")
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val current = state.data.sections.firstOrNull { it.id == selection.selectedKey } ?: state.data.sections.firstOrNull()
            if (current != null) HorizontalFilterChips(items = state.data.sections, selected = current,
                label = { it.title }, onSelected = { selection.select(it.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            ApiResourceContent(
                state = state, modifier = Modifier.weight(1f), isDataEmpty = BeginnerGuidePage::isEmpty,
                retainedErrorMessage = ::retainedWikiPageError,
                loading = { BeginnerGuideSkeleton(it) }
            ) {
                if (current != null) scrollStates.SaveableStateProvider(current.id) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item("source") {
                            Text(if (current.title == "常见问题") "卡拉彼丘 Wiki 作者问答" else "来自卡拉彼丘 Wiki", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(current.blocks, key = { it.id }) { block -> GuideBlockContent(block, openLink) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideBlockContent(block: GuideBlock, onOpenLink: (String) -> Unit) {
    when (block) {
        is GuideBlock.Heading -> Text(block.title, Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        is GuideBlock.Term -> WikiRichText(block.content, onOpenLink, Modifier.padding(top = 8.dp), heading = true)
        is GuideBlock.Paragraph -> {
            val content: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(start = ((block.depth - 1).coerceIn(0, 4) * 16).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.marker?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    WikiRichText(block.content, onOpenLink, Modifier.weight(1f))
                }
            }
            if (block.notice) Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.padding(16.dp)) { content() }
            } else content()
        }
        is GuideBlock.Record -> Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WikiRichText(block.title, onOpenLink, heading = true)
                block.fields.forEach { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WikiRichText(field.value, onOpenLink)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeginnerGuideSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().semantics { contentDescription = "正在加载新人入坑指南" },
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).height(32.dp), AppShapes.capsule) }
            }
        }
        item { SkeletonTextLine(widthFraction = 0.7f, height = 16.dp) }
        items(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonTextLine(widthFraction = 0.45f, height = 22.dp)
                repeat(3) { line -> SkeletonTextLine(widthFraction = if (line == 2) 0.7f else 1f, height = 20.dp) }
            }
        }
    }
}
