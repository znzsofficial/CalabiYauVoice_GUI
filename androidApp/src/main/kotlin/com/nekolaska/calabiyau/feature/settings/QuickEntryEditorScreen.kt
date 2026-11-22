package com.nekolaska.calabiyau.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
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
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("首页快捷入口", style = AppTextStyles.topBarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackNavButton(onClick = requestBack, enabled = active) },
                actions = {
                    TextButton(enabled = active, onClick = { save(false) }) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = AppSpacing.screen,
                    end = AppSpacing.screen,
                    top = AppSpacing.small,
                    bottom = AppSpacing.xxxLarge
                )
            ) {
                item(key = "preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SettingsCardShape,
                        color = colors.surfaceContainerLow,
                        border = settingsCardBorder(highlighted = slot >= 0)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SettingsAccentBarHeight)
                                    .background(colors.primary)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "首页预览",
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { heading() },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = colors.primary
                                    )
                                    TextButton(
                                        enabled = draft != QuickEntryDraft.default,
                                        onClick = { draft = QuickEntryDraft.default }
                                    ) {
                                        Text("恢复默认")
                                    }
                                }

                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    listOf(
                                        AppPrefs.HOME_QUICK_LAYOUT_GRID to "网格",
                                        AppPrefs.HOME_QUICK_LAYOUT_BUTTONS to "六按钮"
                                    ).forEachIndexed { index, (layout, label) ->
                                        SegmentedButton(
                                            selected = draft.layout == layout,
                                            onClick = { draft = draft.copy(layout = layout) },
                                            shape = SegmentedButtonDefaults.itemShape(index, 2)
                                        ) { Text(label) }
                                    }
                                }

                                QuickEntryPreview(draft, slot, onSelect = { slot = it })

                                Text(
                                    text = "点选位置，再从列表选择入口。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Keep the target visible and changeable even deep in the candidate list.
                stickyHeader(key = "slot") {
                    SlotControls(
                        ids = draft.ids,
                        slot = slot,
                        active = active,
                        onSelect = { slot = it },
                        onMove = { to ->
                            draft = draft.move(slot, to)
                            slot = to
                            showPreview()
                        }
                    )
                }

                item(key = "search") {
                    Box(Modifier.padding(bottom = AppSpacing.small)) {
                        SearchBar(
                            keyword = query,
                            onKeywordChange = { query = it },
                            onClear = { query = "" },
                            isSearching = false,
                            placeholder = "搜索名称或别名"
                        )
                    }
                }

                if (candidates.isEmpty()) item(key = "empty") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.medium),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainerLow,
                        border = settingsCardBorder()
                    ) {
                        Text(
                            text = "没有匹配的入口",
                            modifier = Modifier.padding(AppSpacing.screen),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                candidates.forEach { (group, entries) ->
                    item(key = "category:$group", contentType = "heading") {
                        Text(
                            text = group,
                            modifier = Modifier
                                .padding(top = AppSpacing.xLarge, bottom = AppSpacing.medium)
                                .semantics { heading() },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = colors.primary
                        )
                    }
                    items(entries, key = { it.entry.id }, contentType = { "candidate" }) { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            usedSlot = draft.ids.indexOf(candidate.entry.id),
                            slot = slot
                        ) {
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

    if (active && confirmOverwrite) {
        SettingsDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("替换已保存的快捷入口？") },
            text = { Text("快捷入口已在另一处更改。要用当前编辑的入口和顺序替换吗？") },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("继续编辑") } },
            confirmButton = { TextButton(onClick = { save(true) }) { Text("替换并保存") } }
        )
    }
}

@Composable
private fun QuickEntryPreview(draft: QuickEntryDraft, selected: Int, onSelect: (Int) -> Unit) {
    val grid = draft.layout == AppPrefs.HOME_QUICK_LAYOUT_GRID
    val colors = MaterialTheme.colorScheme
    val gridContainerShape = smoothCornerShape(12.dp)
    val tileShape = smoothCornerShape(12.dp)
    val gridCellShape = SettingsIconFrameShape
    val dividerColor = settingsDividerColor()

    // Match WikiHomePage's content width (after side margins) and large-font reflow.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = quickEntryColumns(maxWidth, LocalDensity.current.fontScale)
        val rows = draft.ids.chunked(columns)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .then(
                    if (grid) {
                        Modifier
                            .clip(gridContainerShape)
                            .background(colors.surfaceContainer)
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), gridContainerShape)
                            .padding(6.dp)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(if (grid) 0.dp else 8.dp)
        ) {
            rows.forEachIndexed { rowIndex, ids ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (grid) 0.dp else 8.dp)
                ) {
                    ids.forEachIndexed { column, id ->
                        val position = rowIndex * columns + column
                        val entry = quickEntryById.getValue(id)
                        val isSelected = position == selected
                        val cellShape = if (grid) gridCellShape else tileShape
                        val cellModifier = Modifier
                            .weight(1f)
                            .clip(cellShape)
                            .background(if (isSelected) colors.primaryContainer else colors.surfaceContainer)
                            .then(
                                when {
                                    isSelected -> Modifier.border(1.5.dp, colors.primary, cellShape)
                                    !grid -> Modifier.border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), cellShape)
                                    else -> Modifier
                                }
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(position) }
                            )
                            .semantics { contentDescription = "位置 ${position + 1}，${entry.label}" }
                            .heightIn(min = if (grid) 64.dp else 66.dp)

                        Box(cellModifier) {
                            Text(
                                text = "${position + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = if (isSelected) {
                                    colors.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    colors.onSurfaceVariant.copy(alpha = 0.65f)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 7.dp, top = 4.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .padding(horizontal = AppSpacing.xSmall, vertical = AppSpacing.medium),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) colors.onPrimaryContainer else colors.primary
                                )
                                Spacer(Modifier.height(AppSpacing.xSmall))
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        if (grid && column < ids.lastIndex) {
                            VerticalDivider(
                                modifier = Modifier.height(44.dp),
                                thickness = 1.dp,
                                color = dividerColor
                            )
                        }
                    }
                }
                if (grid && rowIndex < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 1.dp,
                        color = dividerColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotControls(
    ids: List<String>,
    slot: Int,
    active: Boolean,
    onSelect: (Int) -> Unit,
    onMove: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(vertical = AppSpacing.medium)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = smoothCornerShape(12.dp),
            color = colors.surfaceContainerLow,
            border = settingsCardBorder(highlighted = slot >= 0)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xSmall, vertical = AppSpacing.tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    var expanded by remember(active) { mutableStateOf(false) }
                    TextButton(enabled = active, onClick = { expanded = true }) {
                        Text(
                            text = ids.getOrNull(slot)?.let { "位置 ${slot + 1}：${quickEntryById.getValue(it).label}" } ?: "选择位置",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    SlotMenu(
                        ids = ids,
                        expanded = expanded,
                        slot = slot,
                        onDismiss = { expanded = false },
                        onChoose = {
                            expanded = false
                            onSelect(it)
                        }
                    )
                }
                Box {
                    var expanded by remember(active) { mutableStateOf(false) }
                    TextButton(enabled = active && slot >= 0, onClick = { expanded = true }) {
                        Text("移动到…")
                    }
                    SlotMenu(
                        ids = ids,
                        expanded = expanded,
                        slot = slot,
                        onDismiss = { expanded = false },
                        onChoose = {
                            expanded = false
                            onMove(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotMenu(
    ids: List<String>,
    expanded: Boolean,
    slot: Int,
    onDismiss: () -> Unit,
    onChoose: (Int) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ids.forEachIndexed { index, id ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = "位置 ${index + 1}：${quickEntryById.getValue(id).label}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                },
                enabled = index != slot,
                onClick = { onChoose(index) }
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: QuickEntryCandidate,
    usedSlot: Int,
    slot: Int,
    onChoose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isCurrentSlot = usedSlot >= 0 && usedSlot == slot
    val status = when {
        usedSlot < 0 -> null
        slot < 0 -> "位置 ${usedSlot + 1}"
        usedSlot == slot -> "当前位置 ${slot + 1}"
        else -> "位置 ${usedSlot + 1}，与位置 ${slot + 1} 交换"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xSmall),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainerLow,
        border = settingsCardBorder(highlighted = isCurrentSlot)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = slot >= 0 && usedSlot != slot,
                    role = Role.Button,
                    onClickLabel = if (usedSlot >= 0) "交换入口" else "替换入口",
                    onClick = onChoose
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = AppSpacing.large, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsFramedIcon(
                icon = candidate.entry.icon,
                containerColor = if (isCurrentSlot) colors.primaryContainer else colors.surfaceContainerHigh,
                borderColor = if (isCurrentSlot) {
                    colors.primary.copy(alpha = 0.35f)
                } else {
                    colors.outlineVariant.copy(alpha = 0.35f)
                },
                iconTint = if (isCurrentSlot) colors.onPrimaryContainer else colors.primary
            )
            Spacer(Modifier.width(SettingsRowIconGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
            ) {
                Text(
                    text = candidate.title,
                    style = AppTextStyles.settingsItemTitle,
                    color = colors.onSurface
                )
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = if (isCurrentSlot) colors.primary else colors.onSurfaceVariant
                    )
                }
            }
            if (usedSlot >= 0) {
                Spacer(Modifier.width(AppSpacing.medium))
                SettingsValueBadge(
                    text = "位置 ${usedSlot + 1}",
                    highlighted = isCurrentSlot,
                    tabular = true
                )
            }
        }
    }
}
