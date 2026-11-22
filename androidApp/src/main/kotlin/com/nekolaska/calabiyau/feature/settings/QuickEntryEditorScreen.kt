package com.nekolaska.calabiyau.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryById
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryColumns
import kotlinx.coroutines.launch

/** Hosts must disable their pop handler here and deactivate outgoing editor instances. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun QuickEntryEditorScreen(isActive: () -> Boolean, onBack: () -> Unit) {
    val active = isActive()
    val original = rememberSaveable(saver = QuickEntryDraft.Saver) {
        QuickEntryDraft.fromHome(AppPrefs.homeQuickEntryIds, AppPrefs.homeQuickEntryLayout)
    }
    var draft by rememberSaveable(stateSaver = QuickEntryDraft.Saver) { mutableStateOf(original) }
    var slot by rememberSaveable { mutableIntStateOf(-1) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmOverwrite by rememberSaveable { mutableStateOf(false) }
    fun save(overwrite: Boolean) {
        if (!isActive()) return
        val latest = QuickEntryDraft.fromHome(AppPrefs.homeQuickEntryIds, AppPrefs.homeQuickEntryLayout)
        val resolved = draft.resolveSave(original, latest, overwrite)
        if (resolved == null) { confirmOverwrite = true; return }
        AppPrefs.saveHomeQuickEntries(resolved.ids, resolved.layout)
        confirmOverwrite = false
        onBack()
    }
    val requestBack: () -> Unit = {
        if (isActive()) {
            if (draft != original) confirmDiscard = true else onBack()
        }
    }
    BackHandler(enabled = active, onBack = requestBack)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val showPreview = {
        focus.clearFocus()
        scope.launch { listState.animateScrollToItem(0) }
    }
    val candidates = remember(query) { quickEntryCandidates(query) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("首页快捷入口", style = AppTextStyles.topBarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackNavButton(onClick = requestBack, enabled = active) },
                actions = {
                    TextButton(enabled = active, onClick = { save(false) }) { Text("保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = AppSpacing.screen, end = AppSpacing.screen, bottom = AppSpacing.xxxLarge)
            ) {
                item(key = "preview") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("首页预览", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = draft != QuickEntryDraft.default, onClick = { draft = QuickEntryDraft.default }) {
                            Text("恢复默认")
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(AppPrefs.HOME_QUICK_LAYOUT_GRID to "网格", AppPrefs.HOME_QUICK_LAYOUT_BUTTONS to "六按钮")
                            .forEachIndexed { index, (layout, label) ->
                                SegmentedButton(
                                    selected = draft.layout == layout,
                                    onClick = { draft = draft.copy(layout = layout) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                                ) { Text(label) }
                            }
                    }
                    Spacer(Modifier.height(AppSpacing.large))
                    QuickEntryPreview(draft, slot, onSelect = { slot = it })
                    Text(
                        "点选位置，再从列表选择入口。",
                        Modifier.padding(top = AppSpacing.medium, bottom = AppSpacing.small),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Keep the target visible and changeable even deep in the candidate list.
                stickyHeader(key = "slot") {
                    SlotControls(draft.ids, slot, active, onSelect = { slot = it }, onMove = { to ->
                        draft = draft.move(slot, to)
                        slot = to
                        showPreview()
                    })
                }
                item(key = "search") {
                    SearchBar(
                        keyword = query, onKeywordChange = { query = it }, onClear = { query = "" },
                        isSearching = false, placeholder = "搜索名称或别名"
                    )
                }
                if (candidates.isEmpty()) item(key = "empty") {
                    Text("没有匹配的入口", Modifier.padding(vertical = AppSpacing.sectionGap),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                candidates.forEach { (group, entries) ->
                    item(key = "category:$group", contentType = "heading") {
                        Text(group, Modifier.padding(top = AppSpacing.xxLarge, bottom = AppSpacing.xSmall).semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(entries, key = { it.entry.id }, contentType = { "candidate" }) { candidate ->
                        CandidateRow(candidate, usedSlot = draft.ids.indexOf(candidate.entry.id), slot = slot) {
                            draft = draft.choose(slot, candidate.entry.id)
                            showPreview()
                        }
                    }
                }
            }
        }
    }
    if (active && confirmDiscard) {
        SettingsDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃更改？") },
            text = { Text("首页快捷入口有尚未保存的更改。") },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
            confirmButton = {
                TextButton(onClick = { if (isActive()) { confirmDiscard = false; onBack() } }) { Text("放弃更改") }
            }
        )
    }
    if (active && confirmOverwrite) SettingsDialog(
        onDismissRequest = { confirmOverwrite = false }, title = { Text("替换已保存的快捷入口？") },
        text = { Text("快捷入口已在另一处更改。要用当前编辑的入口和顺序替换吗？") },
        dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("继续编辑") } },
        confirmButton = { TextButton(onClick = { save(true) }) { Text("替换并保存") } }
    )
}

@Composable
private fun QuickEntryPreview(draft: QuickEntryDraft, selected: Int, onSelect: (Int) -> Unit) {
    val grid = draft.layout == AppPrefs.HOME_QUICK_LAYOUT_GRID
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // Match WikiHomePage's content width (after side margins) and large-font reflow.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = quickEntryColumns(maxWidth, LocalDensity.current.fontScale)
        val rows = draft.ids.chunked(columns)
        val divider = MaterialTheme.colorScheme.outlineVariant
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup().then(
                if (grid) Modifier.background(surface, AppShapes.card).padding(6.dp) else Modifier
            ),
            verticalArrangement = Arrangement.spacedBy(if (grid) 0.dp else 10.dp)
        ) {
            rows.forEachIndexed { rowIndex, ids ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (grid) 0.dp else 10.dp)) {
                    ids.forEachIndexed { column, id ->
                        val position = rowIndex * columns + column
                        val entry = quickEntryById.getValue(id)
                        val isSelected = position == selected
                        Column(
                            Modifier.weight(1f).clip(AppShapes.compactCard)
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else surface)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(position) })
                                .semantics { contentDescription = "位置 ${position + 1}，${entry.label}" }
                                .heightIn(min = if (grid) 56.dp else 60.dp)
                                .padding(horizontal = AppSpacing.xSmall, vertical = AppSpacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(entry.icon, null, Modifier.size(22.dp), tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(AppSpacing.xSmall))
                            Text(entry.label, style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center)
                        }
                        if (grid && column < ids.lastIndex) VerticalDivider(Modifier.height(44.dp), color = divider)
                    }
                }
                if (grid && rowIndex < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = divider)
            }
        }
    }
}

@Composable
private fun SlotControls(ids: List<String>, slot: Int, active: Boolean, onSelect: (Int) -> Unit, onMove: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            var expanded by remember(active) { mutableStateOf(false) }
            TextButton(enabled = active, onClick = { expanded = true }) {
                Text(ids.getOrNull(slot)?.let { "位置 ${slot + 1}：${quickEntryById.getValue(it).label}" } ?: "选择位置",
                    Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            SlotMenu(ids, expanded, slot, onDismiss = { expanded = false }, onChoose = {
                expanded = false
                onSelect(it)
            })
        }
        Box {
            var expanded by remember(active) { mutableStateOf(false) }
            TextButton(enabled = active && slot >= 0, onClick = { expanded = true }) { Text("移动到…") }
            SlotMenu(ids, expanded, slot, onDismiss = { expanded = false }, onChoose = {
                expanded = false
                onMove(it)
            })
        }
    }
}

@Composable
private fun SlotMenu(ids: List<String>, expanded: Boolean, slot: Int, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ids.forEachIndexed { index, id ->
            DropdownMenuItem(
                text = { Text("位置 ${index + 1}：${quickEntryById.getValue(id).label}") },
                enabled = index != slot,
                onClick = { onChoose(index) }
            )
        }
    }
}

@Composable
private fun CandidateRow(candidate: QuickEntryCandidate, usedSlot: Int, slot: Int, onChoose: () -> Unit) {
    val status = when {
        usedSlot < 0 -> null
        slot < 0 -> "位置 ${usedSlot + 1}"
        usedSlot == slot -> "当前位置 ${slot + 1}"
        else -> "位置 ${usedSlot + 1}，与位置 ${slot + 1} 交换"
    }
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.compactCard)
            .clickable(enabled = slot >= 0 && usedSlot != slot,
                role = Role.Button, onClickLabel = if (usedSlot >= 0) "交换入口" else "替换入口", onClick = onChoose)
            .heightIn(min = 52.dp).padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
    ) {
        Icon(candidate.entry.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(candidate.title, style = MaterialTheme.typography.bodyLarge)
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                color = if (usedSlot == slot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
