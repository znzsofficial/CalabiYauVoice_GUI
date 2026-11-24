package com.nekolaska.calabiyau.feature.wiki.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.WikiRichText
import com.nekolaska.calabiyau.core.ui.WikiSheetFrame
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock.Kind
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideChapter
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideSection
import com.nekolaska.calabiyau.feature.wiki.game.model.sectionAt
import com.nekolaska.calabiyau.feature.wiki.game.model.sections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun FieldGuideChapterPage(chapter: FieldGuideChapter, onOpenLink: (String) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember(chapter) { chapter.sections() }
    // A short last section (or a whole short chapter) may fit without its title reaching the top.
    var endTarget by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(state, endTarget) {
        if (endTarget >= 0) {
            snapshotFlow { state.layoutInfo.totalItemsCount > 0 && state.canScrollForward }.first { it }
            endTarget = -1
        }
    }
    val current by remember(sections, state) {
        derivedStateOf {
            val top = state.firstVisibleItemIndex
            val block = if (state.canScrollForward) top else endReadingBlock(
                visible = top..(state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: top),
                atStart = !state.canScrollBackward, selected = endTarget
            )
            sections.sectionAt(block)
        }
    }
    var showContents by remember(chapter.title) { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(current?.label ?: chapter.title, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sections.isNotEmpty()) TextButton(onClick = { showContents = true }) {
                Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("章内目录")
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) { fieldGuideContent(chapter, onOpenLink) }
    }
    if (showContents) FieldGuideContents(sections, current, onDismiss = { showContents = false }) { section ->
        showContents = false
        scope.launch {
            val target = section.blocks.first
            state.scrollToItem(target)
            endTarget = if (state.canScrollForward) -1 else target
        }
    }
}

/** At the scroll limit several section titles can share one physical scroll position. */
internal fun endReadingBlock(visible: IntRange, atStart: Boolean, selected: Int): Int = when {
    selected in visible -> selected
    atStart -> visible.first
    else -> visible.last
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FieldGuideContents(
    sections: List<FieldGuideSection>, current: FieldGuideSection?,
    onDismiss: () -> Unit, onSelect: (FieldGuideSection) -> Unit
) {
    val activeIndex = sections.indexOf(current).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (activeIndex - 1).coerceAtLeast(0))
    WikiSheetFrame(onDismiss) {
        Text("章内目录", Modifier.padding(horizontal = AppSpacing.screen).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), state = listState,
            contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            sections.forEachIndexed { index, section ->
                val isParent = (sections.getOrNull(index + 1)?.depth ?: -1) > section.depth
                val row: @Composable () -> Unit = {
                    SelectionOptionRow(
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(start = (section.depth * 14).dp,
                                top = if (section.depth == 0 && index > 0) AppSpacing.medium else 0.dp)
                            .then(if (isParent) Modifier.semantics { heading() } else Modifier),
                        label = section.label, selected = section == current, onClick = { onSelect(section) }
                    )
                }
                if (section.depth == 0) stickyHeader(key = section.blocks.first) { row() }
                else item(key = section.blocks.first) { row() }
            }
        }
    }
}

internal fun LazyListScope.fieldGuideContent(chapter: FieldGuideChapter, openLink: (String) -> Unit) {
    items(chapter.blocks.size, contentType = { chapter.blocks[it].kind }) { index ->
        val block = chapter.blocks[index]
        when (block.kind) {
            Kind.HEADING -> WikiRichText(block.content, openLink, Modifier.padding(top = 4.dp).semantics { heading() }, heading = true)
            Kind.TEXT -> WikiRichText(block.content, openLink)
            Kind.RECORD -> Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                WikiRichText(block.content, openLink, Modifier.fillMaxWidth().padding(14.dp))
            }
        }
    }
}
