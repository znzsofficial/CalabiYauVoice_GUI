package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ErrorState
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.TacticalCard
import com.nekolaska.calabiyau.core.ui.TacticalFramedIcon
import com.nekolaska.calabiyau.core.ui.TacticalStyle
import com.nekolaska.calabiyau.core.ui.WikiSheetFrame
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.FilterOption
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData
import com.nekolaska.calabiyau.feature.wiki.balance.model.PositionMeta
import com.nekolaska.calabiyau.feature.wiki.balance.BalanceSortOrder as SortOrder
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric as SortField

// ════════════════════════════════════════════════════════
//  平衡数据页面 —— 官网角色胜率/选取率/KD/伤害/评分
// ════════════════════════════════════════════════════════

private val BalanceFilterStateSaver = listSaver<BalanceFilterState, String>(
    save = { it.savedValues() },
    restore = { BalanceFilterState.restore(it) }
)

@Composable
fun BalanceDataScreen(
    onBack: () -> Unit
) {

    val requests = remember { BalanceScreenState() }
    val settings = requests.settings
    val balanceResult = requests.result
    val isLoadingSettings = requests.isLoadingSettings
    val isLoadingData = requests.isLoadingData
    val settingsError = requests.settingsError
    val dataError = requests.dataError
    var settingsRetryTrigger by remember { mutableIntStateOf(0) }
    var forceSettingsRefresh by remember { mutableStateOf(false) }
    var dataRetryTrigger by remember { mutableIntStateOf(0) }

    var filterState by rememberSaveable(stateSaver = BalanceFilterStateSaver) {
        mutableStateOf(BalanceFilterState())
    }
    val selectedMode = settings?.modes?.find { it.code == filterState.applied.mode }
    val selectedMap = settings?.maps?.find { it.code == filterState.applied.map }
    val selectedSeason = settings?.seasons?.find { it.code == filterState.applied.season }
    val selectedSeason2 = settings?.seasons?.find { it.code == filterState.applied.compareSeason }
    val selectedRanks = settings?.ranks?.filter { it.code in filterState.applied.ranks }.orEmpty()
    val showAttackers = filterState.showAttackers
    val sortField = filterState.sortField
    val sortOrder = filterState.sortOrder

    // 角色元信息映射 (id → CharacterMeta)
    val characterMap = remember(settings) {
        settings?.characters?.associateBy { it.code.toIntOrNull() ?: 0 } ?: emptyMap()
    }
    val positionMap = remember(settings) {
        settings?.positions?.associateBy { it.code } ?: emptyMap()
    }

    // ── 加载设置 ──
    fun loadSettings(forceRefresh: Boolean = false) {
        forceSettingsRefresh = forceSettingsRefresh || forceRefresh
        settingsRetryTrigger++
    }

    LaunchedEffect(settingsRetryTrigger) {
        val forceRefresh = forceSettingsRefresh
        forceSettingsRefresh = false
        requests.loadSettings(forceRefresh)?.let { fresh ->
            filterState = filterState.reconcile(fresh)
            dataRetryTrigger++
        }
    }

    // Only an applied query or an explicit retry/refresh can trigger a POST, never draft/side/sort edits.
    val query = if (settings != null) filterState.applied.queryOrNull() else null
    LaunchedEffect(query, dataRetryTrigger) {
        requests.loadData(query)
    }

    // 排序后的列表
    val sortedList = remember(balanceResult, showAttackers, sortField, sortOrder) {
        val list = if (showAttackers) balanceResult?.attackers else balanceResult?.defenders
        list?.filter { it.winRate > 0 }?.let { filtered ->
            val comparator = compareBy<HeroBalanceData> {
                when (sortField) {
                    SortField.WIN_RATE -> it.winRate
                    SortField.SELECT_RATE -> it.selectRate
                    SortField.KD -> it.kd
                    SortField.DAMAGE -> it.damageAve
                    SortField.SCORE -> it.score
                }
            }
            if (sortOrder == SortOrder.DESC) filtered.sortedWith(comparator.reversed())
            else filtered.sortedWith(comparator)
        } ?: emptyList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("平衡数据", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    RefreshActionButton(
                        onClick = { loadSettings(forceRefresh = true) },
                        enabled = !isLoadingSettings && !isLoadingData
                    )
                }
            )
        }
    ) { padding ->
        when {
            isLoadingSettings && settings == null -> {
                BalanceDataPageSkeleton(Modifier.padding(padding))
            }
            settingsError != null && settings == null -> {
                ErrorState(
                    message = settingsError.message,
                    kind = settingsError.kind,
                    onRetry = { loadSettings(forceRefresh = true) },
                    modifier = Modifier.padding(padding)
                )
            }
            settings != null -> {
                PullToRefreshBox(
                    isRefreshing = isLoadingSettings || isLoadingData,
                    onRefresh = { loadSettings(forceRefresh = true) },
                    state = rememberPullToRefreshState(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // 模式 / 赛季 / 地图 / 段位收进弹层，避免短屏上把角色列表挤没。
                        FilterSummaryBar(
                            mode = selectedMode?.name,
                            season = selectedSeason?.name,
                            map = selectedMap?.name,
                            rankNames = selectedRanks.map { it.name },
                            compareSeason = selectedSeason2?.name,
                            onOpen = { filterState = filterState.beginEditing(settings) }
                        )
                        filterState.draft?.let { draft ->
                            BalanceFilterSheet(
                                settings = settings,
                                draft = draft,
                                onDraftChange = { filterState = filterState.changeDraft(it, settings) },
                                onReset = { filterState = filterState.resetDraft(settings) },
                                onApply = { filterState = filterState.applyDraft(settings) },
                                onDismiss = { filterState = filterState.cancelDraft() }
                            )
                        }

                        // ── 进攻/防守切换 + 排序 ──
                        SideAndSortBar(
                            showAttackers = showAttackers,
                            sortField = sortField,
                            sortOrder = sortOrder,
                            scoreAvailable = data.BalanceSeasons.supportsScore(selectedSeason?.code.orEmpty()),
                            onSideToggle = {
                                if (showAttackers != it) {
                                    filterState = filterState.copy(showAttackers = it)
                                }
                            },
                            onSortChange = { field ->
                                if (sortField == field) {
                                    filterState = filterState.copy(sortOrder = if (sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC)
                                } else {
                                    filterState = filterState.copy(sortField = field, sortOrder = SortOrder.DESC)
                                }
                            }
                        )

                        // ── 数据列表 ──
                        Box(Modifier.fillMaxSize()) {
                            if (isLoadingData) {
                                BalanceHeroListSkeleton(Modifier.fillMaxSize())
                            } else if (dataError != null && balanceResult == null) {
                                ErrorState(
                                    message = dataError.message,
                                    kind = dataError.kind,
                                    onRetry = { dataRetryTrigger++ },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (settingsError != null && balanceResult == null) {
                                ErrorState(
                                    message = settingsError.message,
                                    kind = settingsError.kind,
                                    onRetry = { loadSettings(forceRefresh = true) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (sortedList.isEmpty() && !isLoadingData) {
                                Text(
                                    "暂无数据，试试其他赛季或筛选条件。",
                                    Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val compareMap = if (showAttackers) {
                                    balanceResult?.compareData?.attackers
                                } else {
                                    balanceResult?.compareData?.defenders
                                }
                                HeroBalanceList(
                                    list = sortedList,
                                    listKey = "$showAttackers-${sortField.name}-${sortOrder.name}-${selectedSeason?.code.orEmpty()}-${selectedSeason2?.code.orEmpty()}",
                                    characterMap = characterMap,
                                    positionMap = positionMap,
                                    sortField = sortField,
                                    compareMap = compareMap,
                                    scoreAvailable = data.BalanceSeasons.supportsScore(selectedSeason?.code.orEmpty()),
                                    compareScoreAvailable = data.BalanceSeasons.supportsScore(selectedSeason2?.code.orEmpty()),
                                    compareSeasonName = selectedSeason2?.name,
                                    isLoading = isLoadingData
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  筛选摘要 + 底部弹层
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSummaryBar(
    mode: String?,
    season: String?,
    map: String?,
    rankNames: List<String>,
    compareSeason: String?,
    onOpen: () -> Unit
) {
    val summary = balanceFilterSummary(mode, season, map, rankNames, compareSeason)
    val hasCompare = summary.compareLabel != null
    TacticalCard(
        onClick = onOpen,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        highlighted = hasCompare,
        accentColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TacticalFramedIcon(icon = Icons.Outlined.Tune, size = TacticalStyle.compactIconFrameSize, highlighted = hasCompare)
                FlowRow(
                    Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "统计范围",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    summary.compareLabel?.let { label ->
                        Text(label, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(summary.headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceFilterSheet(
    settings: BalanceSettings,
    draft: BalanceFilterCodes,
    onDraftChange: (BalanceFilterCodes) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedMode = settings.modes.find { it.code == draft.mode }
    val selectedSeason = settings.seasons.find { it.code == draft.season }
    val selectedSeason2 = settings.seasons.find { it.code == draft.compareSeason }
    val colors = MaterialTheme.colorScheme
    WikiSheetFrame(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "筛选条件",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                FilterDropdown(
                    label = "模式",
                    options = settings.modes,
                    selected = selectedMode,
                    onSelect = { onDraftChange(draft.copy(mode = it.code)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterDropdown(
                    label = "赛季",
                    options = settings.seasons,
                    selected = selectedSeason,
                    onSelect = { onDraftChange(draft.copy(season = it.code)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterDropdown(
                    label = "对比赛季",
                    options = settings.seasons.filter { it.code != selectedSeason?.code },
                    selected = selectedSeason2,
                    onSelect = { onDraftChange(draft.copy(compareSeason = it.code)) },
                    onClearSelection = { onDraftChange(draft.copy(compareSeason = null)) },
                    clearLabel = "不对比",
                    placeholderText = "不对比",
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChipGroup(
                    label = "地图",
                    options = settings.maps,
                    isSelected = { draft.map == it.code },
                    onClick = { onDraftChange(draft.copy(map = if (draft.map == it.code) null else it.code)) },
                    allSelected = draft.map == null,
                    onSelectAll = { onDraftChange(draft.copy(map = null)) }
                )
                FilterChipGroup(
                    label = "段位",
                    hint = "可多选",
                    options = settings.ranks,
                    isSelected = { it.code in draft.ranks },
                    onClick = { rank ->
                        onDraftChange(
                            draft.copy(ranks = if (rank.code in draft.ranks) draft.ranks - rank.code else draft.ranks + rank.code)
                        )
                    },
                    allSelected = draft.ranks.isEmpty(),
                    onSelectAll = { onDraftChange(draft.copy(ranks = emptyList())) }
                )
            }
            HorizontalDivider(color = TacticalStyle.dividerColor())
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("重置筛选") }
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    FilledTonalButton(
                        onClick = onApply,
                        enabled = draft.mode != null && draft.season != null,
                        shape = TacticalStyle.tileShape
                    ) { Text("应用筛选") }
                }
            }
        }
    }
}

@Composable
private fun FilterChipGroup(
    label: String,
    options: List<FilterOption>,
    isSelected: (FilterOption) -> Boolean,
    onClick: (FilterOption) -> Unit,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    hint: String? = null
) {
    val colors = MaterialTheme.colorScheme
    TacticalCard(
        compact = true,
        containerColor = colors.surfaceContainer,
        border = TacticalStyle.tileBorder()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TacticalFilterOptionChip(selected = allSelected, label = "全部", onClick = onSelectAll)
                options.forEach { option ->
                    TacticalFilterOptionChip(
                        selected = isSelected(option),
                        label = option.name,
                        onClick = { onClick(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalFilterOptionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = TacticalStyle.badgeShape,
        border = TacticalStyle.cardBorder(highlighted = selected),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
            selectedLabelColor = colors.onPrimaryContainer,
            containerColor = colors.surfaceContainerLow,
            labelColor = colors.onSurface
        ),
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    )
}

// ────────────────────────────────────────────
//  下拉选择器
// ────────────────────────────────────────────

@Composable
private fun FilterDropdown(
    modifier: Modifier = Modifier,
    label: String,
    options: List<FilterOption>,
    selected: FilterOption?,
    onSelect: (FilterOption) -> Unit,
    onClearSelection: (() -> Unit)? = null,
    clearLabel: String? = null,
    placeholderText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholderText?.let { { Text(it, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (onClearSelection != null && clearLabel != null) {
                DropdownMenuItem(
                    text = { Text(clearLabel) },
                    onClick = {
                        onClearSelection()
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  进攻/防守切换 + 排序栏
// ────────────────────────────────────────────

@Composable
internal fun SideAndSortBar(
    showAttackers: Boolean,
    sortField: SortField,
    sortOrder: SortOrder,
    scoreAvailable: Boolean,
    onSideToggle: (Boolean) -> Unit,
    onSortChange: (SortField) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // 进攻/防守切换
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            listOf(false, true).forEach { attack ->
                val label = if (attack) "进攻方" else "防守方"
                val icon = if (attack) Icons.Outlined.FlashOn else Icons.Outlined.Shield
                val selected = showAttackers == attack
                customItem(
                    buttonGroupContent = {
                        FilledTonalToggleButton(
                            checked = selected, onCheckedChange = { if (!selected) onSideToggle(attack) },
                            shapes = if (attack) ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        ) { Text(label) }
                    },
                    menuContent = { menu ->
                        SelectableDropdownMenuItem(
                            selected = selected, text = { Text(label) }, shapes = MenuDefaults.itemShapes(),
                            onClick = { if (!selected) onSideToggle(attack); menu.dismiss() }
                        )
                    }
                )
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                OutlinedButton(
                    onClick = { sortMenuOpen = true }, shape = TacticalStyle.tileShape,
                    border = TacticalStyle.tileBorder(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("排序：${sortField.label}", modifier = Modifier.padding(end = 4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortField.entries.forEach { field ->
                        SelectableDropdownMenuItem(
                            selected = field == sortField,
                            enabled = field != SortField.SCORE || scoreAvailable,
                            text = { Text(field.label) },
                            shapes = MenuDefaults.itemShapes(),
                            onClick = {
                                sortMenuOpen = false
                                if (field != sortField) onSortChange(field)
                            }
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = { onSortChange(sortField) }, shape = TacticalStyle.tileShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(if (sortOrder == SortOrder.DESC) "降序 ↓" else "升序 ↑")
            }
        }
    }
}

// ────────────────────────────────────────────
//  角色数据列表
// ────────────────────────────────────────────

@Composable
private fun HeroBalanceList(
    list: List<HeroBalanceData>,
    listKey: String,
    characterMap: Map<Int, CharacterMeta>,
    positionMap: Map<String, PositionMeta>,
    sortField: SortField,
    compareMap: Map<Int, HeroBalanceData>?,
    scoreAvailable: Boolean,
    compareScoreAvailable: Boolean,
    compareSeasonName: String?,
    isLoading: Boolean
) {
    key(listKey) {
        val listState = rememberLazyListState()

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (compareSeasonName != null) {
                    item(key = "comparison-legend") {
                        Text(
                            "对比 $compareSeasonName · pp 表示百分点",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!scoreAvailable || (compareSeasonName != null && !compareScoreAvailable)) {
                    item(key = "score-availability") {
                        Text(
                            "表现分从 S8 赛季开始提供。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(list, key = { _, item -> item.id }) { index, hero ->
                    val meta = characterMap[hero.id]
                    val position = meta?.let { positionMap[it.positionCode] }
                    val compareHero = compareMap?.get(hero.id)

                    BalanceHeroCard(
                        rank = index + 1,
                        hero = hero,
                        compareHero = compareHero,
                        meta = meta,
                        positionName = position?.name ?: "",
                        highlightField = sortField,
                        hasCompare = compareSeasonName != null,
                        scoreAvailable = scoreAvailable,
                        compareScoreAvailable = compareScoreAvailable
                    )
                }
                item(key = "bottom-nav-spacer") {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }

            // 保留筛选刷新时的加载反馈。
            if (isLoading) {
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun BalanceDataPageSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // 对齐 FilterSummaryBar
        TacticalCard(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            accentColor = MaterialTheme.colorScheme.primary
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.size(TacticalStyle.compactIconFrameSize), shape = TacticalStyle.iconFrameShape)
                    ShimmerBox(Modifier.width(64.dp).height(14.dp))
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(Modifier.size(20.dp), shape = TacticalStyle.pillShape)
                }
                ShimmerBox(Modifier.fillMaxWidth(0.72f).height(16.dp))
            }
        }

        // 对齐 SideAndSortBar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerBox(Modifier.width(84.dp).height(32.dp), shape = TacticalStyle.tileShape)
            ShimmerBox(Modifier.width(84.dp).height(32.dp), shape = TacticalStyle.tileShape)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShimmerBox(Modifier.width(126.dp).height(40.dp), shape = TacticalStyle.tileShape)
            ShimmerBox(Modifier.width(72.dp).height(40.dp), shape = TacticalStyle.tileShape)
        }

        BalanceHeroListSkeleton(Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun BalanceHeroListSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(5) { index ->
            BalanceHeroCardSkeleton(topRank = index < 3)
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun BalanceHeroCardSkeleton(topRank: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    TacticalCard(
        compact = true,
        highlighted = topRank,
        accentColor = if (topRank) colors.primary else colors.outlineVariant.copy(alpha = 0.65f)
    ) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
            val compact = maxWidth < (280 * LocalDensity.current.fontScale).dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShimmerBox(Modifier.size(48.dp), shape = TacticalStyle.iconFrameShape)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShimmerBox(Modifier.width(88.dp).height(16.dp))
                            ShimmerBox(Modifier.width(68.dp).height(14.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShimmerBox(Modifier.width(36.dp).height(12.dp))
                        ShimmerBox(Modifier.width(72.dp).height(24.dp))
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShimmerBox(Modifier.size(48.dp), shape = TacticalStyle.iconFrameShape)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShimmerBox(Modifier.width(96.dp).height(16.dp))
                            ShimmerBox(Modifier.width(72.dp).height(14.dp))
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShimmerBox(Modifier.width(36.dp).height(12.dp))
                            ShimmerBox(Modifier.width(68.dp).height(24.dp))
                        }
                    }
                }

                HorizontalDivider(color = TacticalStyle.dividerColor())

                val columns = if (compact) 2 else 4
                repeat(if (compact) 2 else 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(columns) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = TacticalStyle.tileShape,
                                color = colors.surfaceContainer,
                                border = TacticalStyle.tileBorder()
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ShimmerBox(Modifier.width(42.dp).height(11.dp))
                                    ShimmerBox(Modifier.width(50.dp).height(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
