package com.nekolaska.calabiyau.feature.wiki.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.rememberSectionSelection
import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail

/** A single-mode destination is a reading page rather than a one-row collapsible directory. */
@Composable
internal fun ModeReader(mode: GameModeDetail, onMap: (String) -> Unit, onOpenLink: (String) -> Unit) {
    val titles = remember(mode.chapters) { listOf("模式介绍") + mode.chapters.map { it.title } }
    // A legacy offline prefetch contains only the overview; it must not reset a restored chapter.
    val selection = rememberSectionSelection(mode.name, if (mode.chapters.isEmpty()) emptyList() else titles)
    val chapter = mode.chapters.firstOrNull { it.title == selection.selectedKey }
    val scrollStates = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (mode.chapters.isNotEmpty()) {
            var showChapters by remember { mutableStateOf(false) }
            val current = chapter?.title ?: titles.first()
            Box(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
                OutlinedButton(onClick = { showChapters = true }, shape = AppShapes.compactCard) {
                    Text("章节：$current")
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            if (showChapters) WikiSelectionSheet("选择章节", onDismiss = { showChapters = false }) {
                items(titles, key = { it }) { title ->
                    SelectionOptionRow(label = title, selected = title == current, onClick = {
                        selection.select(title); showChapters = false
                    })
                }
            }
        }
        scrollStates.SaveableStateProvider(chapter?.title ?: titles.first()) {
            if (chapter == null) ModeOverview(mode, onMap)
            else FieldGuideChapterPage(chapter, onOpenLink)
        }
    }
}

@Composable
private fun ModeOverview(mode: GameModeDetail, onMap: (String) -> Unit) {
    val paragraphs = remember(mode.settings) { mode.settings.split("\n\n").filter(String::isNotBlank) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize(), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            item("summary") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    Text(mode.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    if (mode.summary.isNotBlank()) SelectionContainer { Text(mode.summary, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (mode.maps.isNotEmpty()) item("maps") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                    mode.maps.forEach { name ->
                        FilledTonalButton(onClick = { onMap(name) }, shape = AppShapes.compactCard, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Map, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.width(AppSpacing.medium)); Text(name)
                        }
                    }
                }
            }
            if (mode.winCondition.isNotBlank()) item("win") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    Text("获胜条件", Modifier.semantics { heading() }, style = AppTextStyles.sectionTitle)
                    SelectionContainer { Text(mode.winCondition, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (paragraphs.isNotEmpty()) item("settings-title") {
                HorizontalDivider()
                Text("模式设定", Modifier.padding(top = AppSpacing.large).semantics { heading() }, style = AppTextStyles.sectionTitle)
            }
            items(paragraphs.size, key = { "paragraph:$it" }) { index ->
                SelectionContainer { Text(paragraphs[index], style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
internal fun ModeReaderSkeleton(modifier: Modifier) {
    Box(modifier.fillMaxSize().clearAndSetSemantics {
        contentDescription = "正在加载模式介绍"; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            SkeletonTextLine(widthFraction = .34f, height = 28.dp)
            repeat(4) { SkeletonTextLine(widthFraction = if (it % 2 == 0) .78f else .66f, height = 18.dp) }
            Spacer(Modifier.height(AppSpacing.xSmall))
            SkeletonTextLine(widthFraction = .32f, height = 44.dp)
            HorizontalDivider()
            SkeletonTextLine(widthFraction = .3f, height = 20.dp)
            repeat(3) { SkeletonTextLine(widthFraction = if (it == 2) .64f else .9f, height = 16.dp) }
        }
    }
}
