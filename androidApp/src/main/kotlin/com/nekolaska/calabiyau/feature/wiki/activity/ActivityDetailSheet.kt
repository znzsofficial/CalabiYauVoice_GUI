package com.nekolaska.calabiyau.feature.wiki.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.LoadState
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.WikiSheetFrame
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.retainedWikiPageError
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityDetailApi
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import data.ErrorKind
import util.wikiPathEncode

/** Each visible modal can hand off to the Wiki OR dismiss, even if both callbacks arrive in one frame. */
internal class ActivitySheetVisit(private val ownsSelection: () -> Boolean) {
    private var finished = false
    fun isCurrent() = !finished && ownsSelection()
    fun dispose() { finished = true }

    fun leave(action: () -> Unit) {
        if (!isCurrent()) return
        finished = true
        action()
    }
}

@Composable
internal fun ActivityDetailSheet(
    title: String, activity: ActivityEntry, isActive: () -> Boolean,
    onDismiss: () -> Unit, onOpenWikiUrl: (String) -> Unit
) {
    val state = rememberLoadState(
        initial = emptyList<ActivityDetailBlock>(), cachedPrefetchDelayMs = 300L,
        cachedFetch = { ActivityDetailApi.fetch(title, cacheOnly = true) },
        fetch = { ActivityDetailApi.fetch(title, forceRefresh = it) }
    )
    ActivityReaderWindow(isActive) { listState, visit ->
        WikiSheetFrame(onDismiss = { visit.leave(onDismiss) }) {
            ActivityArticle(activity, state, listState, isCurrent = visit::isCurrent,
                onOpenWiki = { url -> visit.leave { onOpenWikiUrl(url) } })
        }
    }
}

@Composable
internal fun ActivityReaderWindow(
    isActive: () -> Boolean,
    content: @Composable (LazyListState, ActivitySheetVisit) -> Unit
) {
    // The selected article owns the scroll position; the temporary window only owns its callbacks.
    val listState = rememberLazyListState()
    if (!isActive()) return
    val latestActive by rememberUpdatedState(isActive)
    val visit = remember { ActivitySheetVisit { latestActive() } }
    DisposableEffect(visit) { onDispose { visit.dispose() } }
    content(listState, visit)
}

/** Indices in the LazyColumn include the optional introduction. Column labels stop at table boundaries. */
internal class ActivityArticleIndex(blocks: List<ActivityDetailBlock>, hasIntro: Boolean) {
    val headings = blocks.mapIndexedNotNull { index, block ->
        if (block is Heading) IndexedValue(index + if (hasIntro) 1 else 0, block.text) else null
    }
    // Anniversary compilations reuse section names such as 签到奖励 for different sub-events.
    val contentsLabels = run {
        val counts = headings.groupingBy { it.value }.eachCount()
        val seen = mutableMapOf<String, Int>()
        headings.map { (_, title) ->
            val total = counts.getValue(title)
            if (total == 1) title else {
                val occurrence = seen.getOrDefault(title, 0) + 1
                seen[title] = occurrence
                "$title（$occurrence/$total）"
            }
        }
    }
    val rowLabels = buildMap<Int, List<String>> {
        var header = emptyList<String>()
        blocks.forEachIndexed { index, block ->
            when {
                block !is TableRow -> header = emptyList()
                block.isHeader -> header = block.cells
                header.isNotEmpty() && header.size == block.cells.size -> put(index, header)
            }
        }
    }
}

// Append the DOM id as one encoded fragment; keep the original article path and query intact.
internal fun activitySectionUrl(articleUrl: String, anchor: String): String =
    articleUrl.substringBefore('#') + "#" + anchor.wikiPathEncode()

@Composable
internal fun ActivityArticle(
    activity: ActivityEntry, state: LoadState<List<ActivityDetailBlock>>, listState: LazyListState,
    isCurrent: () -> Boolean, onOpenWiki: (String) -> Unit
) {
    val dates = listOf(activity.startTime, activity.endTime).filter(String::isNotBlank).joinToString(" 至 ")
    val hasIntro = dates.isNotEmpty() || activity.description.isNotBlank()
    val index = remember(state.data, hasIntro) { ActivityArticleIndex(state.data, hasIntro) }
    val currentIndex by rememberUpdatedState(index)
    val parseError = state.error?.kind == ErrorKind.PARSE
    Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(activity.title, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (index.headings.size > 1) {
                var expanded by remember(index) { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { if (isCurrent()) expanded = true }) { Text("目录") }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        index.headings.forEachIndexed { i, (position, _) ->
                            DropdownMenuItem(text = { Text(index.contentsLabels[i]) }, onClick = {
                                expanded = false
                                if (isCurrent() && currentIndex === index) listState.requestScrollToItem(position)
                            })
                        }
                    }
                }
            }
            if (parseError) {
                FilledTonalButton(onClick = { onOpenWiki(activity.wikiUrl) }, enabled = activity.wikiUrl.isNotBlank()) { Text("Wiki 原文") }
            } else {
                OpenWikiActionButton(activity.wikiUrl, onOpenWiki, "查看 Wiki 原文")
            }
        }
        ApiResourceContent(state, retainedErrorMessage = ::retainedWikiPageError) { blocks ->
            val bodySize = MaterialTheme.typography.bodyMedium.fontSize
            val minCellWidth = with(LocalDensity.current) { bodySize.toDp() } * 10
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Account for article and cell padding before splitting the remaining width into columns.
                val tableWidth = maxWidth - 36.dp - 16.dp
                LazyColumn(state = listState, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp)) {
                    if (hasIntro) item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (activity.description.isNotBlank()) Text(activity.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    itemsIndexed(blocks, contentType = { _, block -> block.javaClass }) { position, block ->
                        when (block) {
                            is Heading -> Text(block.text, Modifier.padding(top = 10.dp, bottom = 3.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            is Paragraph -> Text(block.text, Modifier.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium)
                            is TableRow -> ActivityTableRow(block, index.rowLabels[position].orEmpty(),
                                stacked = tableWidth < minCellWidth * block.cells.size + 12.dp * (block.cells.size - 1))
                            is Image -> AsyncImage(model = block.url, contentDescription = block.label,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
                            is WikiSection -> TextButton(onClick = { onOpenWiki(activitySectionUrl(activity.wikiUrl, block.anchor)) }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("在 Wiki 查看${block.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTableRow(row: TableRow, labels: List<String>, stacked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val spoken = row.cells.mapIndexed { i, value ->
        labels.getOrNull(i)?.takeIf(String::isNotBlank)?.let { "$it：$value" } ?: value
    }.joinToString("；")
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {
        text = AnnotatedString(spoken)
        if (row.isHeader) heading()
    }) {
        val base = if (row.isHeader) Modifier.background(colors.surfaceContainerHigh, smoothCornerShape(12.dp)) else Modifier
        val inset = base.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        if (stacked && row.isHeader) {
            Text(row.cells.joinToString(" / "), inset, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        } else if (stacked) {
            Column(inset, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.cells.forEachIndexed { index, value ->
                    Text(value, style = MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) colors.onSurfaceVariant else colors.onSurface,
                        fontWeight = if (index == 0) FontWeight.Normal else FontWeight.SemiBold)
                }
            }
        } else {
            Row(inset, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.cells.forEachIndexed { column, value ->
                    // Two-column event tables pair a longer requirement with a count or reward.
                    val weight = if (row.cells.size == 2 && column == 0) 1.2f else 1f
                    Text(value, Modifier.weight(weight),
                    style = if (row.isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        if (!row.isHeader) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
    }
}
