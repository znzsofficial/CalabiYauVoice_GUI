package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.balance.api.BalanceDataApi
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceResult
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.FilterOption
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData
import com.nekolaska.calabiyau.feature.wiki.balance.model.PositionMeta
import data.ApiResult
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric as SortField

// ════════════════════════════════════════════════════════
//  平衡数据页面 —— 官网角色胜率/选取率/KD/伤害/评分
// ════════════════════════════════════════════════════════

/** 排序方向 */
private enum class SortOrder { ASC, DESC }



@Composable
fun BalanceDataScreen(
    onBack: () -> Unit
) {

    // ViewModel-like state
    var settings by remember { mutableStateOf<BalanceSettings?>(null) }
    var balanceResult by remember { mutableStateOf<BalanceResult?>(null) }
    var isLoadingSettings by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<ApiResult.Error?>(null) }
    var dataError by remember { mutableStateOf<ApiResult.Error?>(null) }
    var settingsRetryTrigger by remember { mutableIntStateOf(0) }
    var forceSettingsRefresh by remember { mutableStateOf(false) }
    var dataRetryTrigger by remember { mutableIntStateOf(0) }

    // 筛选状态
    var selectedMode by remember { mutableStateOf<FilterOption?>(null) }
    var selectedMap by remember { mutableStateOf<FilterOption?>(null) }
    var selectedSeason by remember { mutableStateOf<FilterOption?>(null) }
    var selectedSeason2 by remember { mutableStateOf<FilterOption?>(null) }
    var selectedRanks by remember { mutableStateOf<List<FilterOption>>(emptyList()) }

    // 显示状态：进攻方 / 防守方
    var showAttackers by remember { mutableStateOf(false) } // false = 防守方(side2), true = 进攻方(side1)
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // 排序
    var sortField by remember { mutableStateOf(SortField.WIN_RATE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESC) }

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
        isLoadingSettings = true
        settingsError = null
        when (val result = BalanceDataApi.fetchSettings(forceRefresh)) {
            is ApiResult.Success -> {
                val s = result.value
                settings = s
                // 默认选中：排位爆破、全选地图、全选段位、最新赛季
                selectedMode = s.modes.find { it.name == "排位爆破" } ?: s.modes.firstOrNull()
                selectedMap = null // null 表示全选
                selectedSeason = s.seasons.firstOrNull()
                selectedSeason2 = null
                selectedRanks = emptyList() // 空表示全选
                dataRetryTrigger++
            }
            is ApiResult.Error -> settingsError = result
        }
        isLoadingSettings = false
    }

    // 设置变化时自动刷新数据
    LaunchedEffect(selectedMode, selectedMap, selectedSeason, selectedSeason2, selectedRanks, dataRetryTrigger) {
        val mode = selectedMode
        val season = selectedSeason
        if (settings == null || mode == null || season == null) {
            isLoadingData = false
            balanceResult = null
            return@LaunchedEffect
        }

        isLoadingData = true
        dataError = null
        if (sortField == SortField.SCORE && !data.BalanceSeasons.supportsScore(season.code)) {
            sortField = SortField.WIN_RATE
            sortOrder = SortOrder.DESC
        }
        val rankCodes = if (selectedRanks.isEmpty()) listOf("-255") else selectedRanks.map { it.code }
        when (val result = BalanceDataApi.fetchBalanceData(
            modeCode = mode.code,
            mapCode = selectedMap?.code ?: "-255",
            rankCodes = rankCodes,
            season1Code = season.code,
            season2Code = selectedSeason2?.code ?: "0"
        )) {
            is ApiResult.Success -> {
                balanceResult = result.value
                settingsError = null
            }
            is ApiResult.Error -> {
                balanceResult = null
                dataError = result
            }
        }
        isLoadingData = false
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
                LoadingState(Modifier.padding(padding), "正在加载平衡数据…")
            }
            settingsError != null && settings == null -> {
                ErrorState(
                    message = settingsError!!.message,
                    kind = settingsError!!.kind,
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
                            onOpen = { showFilters = true }
                        )
                        if (showFilters) {
                            BalanceFilterSheet(
                                settings = settings!!,
                                selectedMode = selectedMode,
                                selectedMap = selectedMap,
                                selectedSeason = selectedSeason,
                                selectedSeason2 = selectedSeason2,
                                selectedRanks = selectedRanks,
                                onModeChange = { selectedMode = it },
                                onMapChange = { selectedMap = it },
                                onSeasonChange = {
                                    selectedSeason = it
                                    if (selectedSeason2?.code == it.code) selectedSeason2 = null
                                },
                                onSeason2Change = { selectedSeason2 = it },
                                onRanksChange = { selectedRanks = it },
                                onDismiss = { showFilters = false }
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
                                    showAttackers = it
                                }
                            },
                            onSortChange = { field ->
                                if (sortField == field) {
                                    sortOrder = if (sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC
                                } else {
                                    sortField = field
                                    sortOrder = SortOrder.DESC
                                }
                            }
                        )

                        // ── 数据列表 ──
                        Box(Modifier.fillMaxSize()) {
                            if (isLoadingData) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            } else if (dataError != null && balanceResult == null) {
                                ErrorState(
                                    message = dataError!!.message,
                                    kind = dataError!!.kind,
                                    onRetry = { dataRetryTrigger++ },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (settingsError != null && balanceResult == null) {
                                ErrorState(
                                    message = settingsError!!.message,
                                    kind = settingsError!!.kind,
                                    onRetry = { loadSettings(forceRefresh = true) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (sortedList.isEmpty() && !isLoadingData) {
                                Text(
                                    "当前赛季、筛选条件及攻防方暂无有效数据，请切换赛季或筛选条件。",
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
private fun FilterSummaryBar(
    mode: String?,
    season: String?,
    map: String?,
    rankNames: List<String>,
    compareSeason: String?,
    onOpen: () -> Unit
) {
    val summary = balanceFilterSummary(mode, season, map, rankNames, compareSeason)
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = smoothCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("筛选", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    summary.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                summary.compareLabel?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceFilterSheet(
    settings: BalanceSettings,
    selectedMode: FilterOption?,
    selectedMap: FilterOption?,
    selectedSeason: FilterOption?,
    selectedSeason2: FilterOption?,
    selectedRanks: List<FilterOption>,
    onModeChange: (FilterOption) -> Unit,
    onMapChange: (FilterOption?) -> Unit,
    onSeasonChange: (FilterOption) -> Unit,
    onSeason2Change: (FilterOption?) -> Unit,
    onRanksChange: (List<FilterOption>) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        val maxSheetHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp() * 0.92f
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("筛选条件", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "修改后自动查询",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "模式",
                        options = settings.modes,
                        selected = selectedMode,
                        onSelect = onModeChange,
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "赛季",
                        options = settings.seasons,
                        selected = selectedSeason,
                        onSelect = onSeasonChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                FilterDropdown(
                    label = "对比赛季",
                    options = settings.seasons.filter { it.code != selectedSeason?.code },
                    selected = selectedSeason2,
                    onSelect = onSeason2Change,
                    onClearSelection = { onSeason2Change(null) },
                    clearLabel = "不对比",
                    placeholderText = "不对比",
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedSeason2 != null) {
                    Text(
                        "正在对比：${selectedSeason?.name.orEmpty()} → ${selectedSeason2.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChipGroup(
                    label = "地图",
                    options = settings.maps,
                    isSelected = { selectedMap == it },
                    onClick = { onMapChange(if (selectedMap == it) null else it) },
                    allSelected = selectedMap == null,
                    onSelectAll = { onMapChange(null) }
                )
                FilterChipGroup(
                    label = "段位",
                    hint = "可多选",
                    options = settings.ranks,
                    isSelected = { selectedRanks.contains(it) },
                    onClick = { rank ->
                        onRanksChange(
                            if (selectedRanks.contains(rank)) selectedRanks - rank else selectedRanks + rank
                        )
                    },
                    allSelected = selectedRanks.isEmpty(),
                    onSelectAll = { onRanksChange(emptyList()) }
                )
            }
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = smoothCornerShape(16.dp)
            ) {
                Text("完成")
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = allSelected,
                onClick = onSelectAll,
                shape = smoothCornerShape(12.dp),
                label = { Text("全选", maxLines = 1) }
            )
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(option.name, maxLines = 1) }
                )
            }
        }
    }
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
            label = { Text(label) },
            placeholder = placeholderText?.let { { Text(it) } },
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
private fun SideAndSortBar(
    showAttackers: Boolean,
    sortField: SortField,
    sortOrder: SortOrder,
    scoreAvailable: Boolean,
    onSideToggle: (Boolean) -> Unit,
    onSortChange: (SortField) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // 进攻/防守切换
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showAttackers,
                onClick = { onSideToggle(false) },
                shape = smoothCornerShape(12.dp),
                label = { Text("防守方") },
                leadingIcon = if (!showAttackers) {
                    { Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = showAttackers,
                onClick = { onSideToggle(true) },
                shape = smoothCornerShape(12.dp),
                label = { Text("进攻方") },
                leadingIcon = if (showAttackers) {
                    { Icon(Icons.Outlined.FlashOn, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        // 卡片的指标自带标签，不再保留与窄表格列对齐的表头。
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("排序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SortField.entries.forEach { field ->
                FilterChip(
                    selected = sortField == field,
                    enabled = field != SortField.SCORE || scoreAvailable,
                    onClick = { onSortChange(field) },
                    label = { Text(field.label) },
                    trailingIcon = if (sortField == field) {
                        { Icon(if (sortOrder == SortOrder.DESC) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp, contentDescription = if (sortOrder == SortOrder.DESC) "降序" else "升序", modifier = Modifier.size(18.dp)) }
                    } else null
                )
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (compareSeasonName != null) {
                    item(key = "comparison-legend") {
                        Text(
                            "当前值在上 · 原值来自 $compareSeasonName\npp 表示百分点；数值变化不代表角色强弱。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!scoreAvailable || (compareSeasonName != null && !compareScoreAvailable)) {
                    item(key = "score-availability") {
                        Text(
                            "S8 以前无表现分，以 — 标示。",
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
