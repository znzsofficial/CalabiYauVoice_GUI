package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
import kotlin.math.abs
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════
//  平衡数据页面 —— 官网角色胜率/选取率/KD/伤害/评分
// ════════════════════════════════════════════════════════

/** 排序字段 */
private enum class SortField(val label: String) {
    WIN_RATE("胜率"),
    SELECT_RATE("选取率"),
    KD("KD"),
    DAMAGE("场均伤害"),
    SCORE("场均得分")
}

/** 排序方向 */
private enum class SortOrder { ASC, DESC }



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDataScreen(
    onBack: () -> Unit
) {

    // ViewModel-like state
    var settings by remember { mutableStateOf<BalanceSettings?>(null) }
    var balanceResult by remember { mutableStateOf<BalanceResult?>(null) }
    var isLoadingSettings by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var errorResult by remember { mutableStateOf<ApiResult.Error?>(null) }

    // 筛选状态
    var selectedMode by remember { mutableStateOf<FilterOption?>(null) }
    var selectedMap by remember { mutableStateOf<FilterOption?>(null) }
    var selectedSeason by remember { mutableStateOf<FilterOption?>(null) }
    var selectedSeason2 by remember { mutableStateOf<FilterOption?>(null) }
    var selectedRanks by remember { mutableStateOf<List<FilterOption>>(emptyList()) }

    // 显示状态：进攻方 / 防守方
    var showAttackers by remember { mutableStateOf(false) } // false = 防守方(side2), true = 进攻方(side1)

    // 排序
    var sortField by remember { mutableStateOf(SortField.WIN_RATE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESC) }

    val scope = rememberCoroutineScope()

    // 角色元信息映射 (id → CharacterMeta)
    val characterMap = remember(settings) {
        settings?.characters?.associateBy { it.code.toIntOrNull() ?: 0 } ?: emptyMap()
    }
    val positionMap = remember(settings) {
        settings?.positions?.associateBy { it.code } ?: emptyMap()
    }

    // ── 加载设置 ──
    fun loadSettings(forceRefresh: Boolean = false) {
        scope.launch {
            isLoadingSettings = true
            errorResult = null
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
                }
                is ApiResult.Error -> errorResult = result
            }
            isLoadingSettings = false
        }
    }

    // ── 加载平衡数据 ──
    fun loadBalanceData() {
        val mode = selectedMode ?: return
        val season = selectedSeason ?: return
        scope.launch {
            isLoadingData = true
            val rankCodes = if (selectedRanks.isEmpty()) listOf("-255")
            else selectedRanks.map { it.code }
            val mapCode = selectedMap?.code ?: "-255"

            when (val result = BalanceDataApi.fetchBalanceData(
                modeCode = mode.code,
                mapCode = mapCode,
                rankCodes = rankCodes,
                season1Code = season.code,
                season2Code = selectedSeason2?.code ?: "0"
            )) {
                is ApiResult.Success -> {
                    balanceResult = result.value
                    errorResult = null
                }
                is ApiResult.Error -> errorResult = result
            }
            isLoadingData = false
        }
    }

    // 初始加载
    LaunchedEffect(Unit) { loadSettings() }

    // 设置变化时自动刷新数据
    LaunchedEffect(selectedMode, selectedMap, selectedSeason, selectedSeason2, selectedRanks) {
        if (settings != null && selectedMode != null && selectedSeason != null) {
            loadBalanceData()
        }
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
            errorResult != null && settings == null -> {
                ErrorState(
                    message = errorResult!!.message,
                    kind = errorResult!!.kind,
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
                        // ── 筛选栏 ──
                        FilterBar(
                            settings = settings!!,
                            selectedMode = selectedMode,
                            selectedMap = selectedMap,
                            selectedSeason = selectedSeason,
                            selectedSeason2 = selectedSeason2,
                            selectedRanks = selectedRanks,
                            onModeChange = { selectedMode = it },
                            onMapChange = { selectedMap = it },
                            onSeasonChange = { selectedSeason = it },
                            onSeason2Change = { selectedSeason2 = it },
                            onRanksChange = { selectedRanks = it }
                        )

                        // ── 进攻/防守切换 + 排序 ──
                        SideAndSortBar(
                            showAttackers = showAttackers,
                            sortField = sortField,
                            sortOrder = sortOrder,
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
                            if (isLoadingData && balanceResult == null) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            } else if (sortedList.isEmpty() && !isLoadingData) {
                                Text(
                                    "暂无数据",
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
                                    listKey = "$showAttackers-${sortField.name}-${sortOrder.name}-${selectedSeason2?.code.orEmpty()}",
                                    characterMap = characterMap,
                                    positionMap = positionMap,
                                    sortField = sortField,
                                    compareMap = compareMap,
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
//  筛选栏
// ────────────────────────────────────────────

@Composable
private fun FilterBar(
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
    onRanksChange: (List<FilterOption>) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // 第一行：模式 + 赛季
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        Spacer(Modifier.height(4.dp))

        FilterDropdown(
            label = "对比赛季",
            options = settings.seasons,
            selected = selectedSeason2,
            onSelect = onSeason2Change,
            onClearSelection = { onSeason2Change(null) },
            clearLabel = "不对比",
            placeholderText = "不对比",
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedSeason2 != null) {
            Text(
                text = "正在对比：${selectedSeason?.name.orEmpty()} → ${selectedSeason2.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // 第二行：地图 + 段位（横向滚动 Chip）
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "地图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedMap == null,
                    onClick = { onMapChange(null) },
                    shape = smoothCornerShape(12.dp),
                    label = { Text("全选", maxLines = 1) }
                )
                settings.maps.forEach { map ->
                    FilterChip(
                        selected = selectedMap == map,
                        onClick = { onMapChange(map) },
                        shape = smoothCornerShape(12.dp),
                        label = { Text(map.name, maxLines = 1) }
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "段位",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedRanks.isEmpty(),
                    onClick = { onRanksChange(emptyList()) },
                    shape = smoothCornerShape(12.dp),
                    label = { Text("全选", maxLines = 1) }
                )
                settings.ranks.forEach { rank ->
                    FilterChip(
                        selected = selectedRanks.contains(rank),
                        onClick = {
                            onRanksChange(
                                if (selectedRanks.contains(rank)) selectedRanks - rank
                                else selectedRanks + rank
                            )
                        },
                        shape = smoothCornerShape(12.dp),
                        label = { Text(rank.name, maxLines = 1) }
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

// ────────────────────────────────────────────
//  下拉选择器
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<FilterOption>,
    selected: FilterOption?,
    onSelect: (FilterOption) -> Unit,
    onClearSelection: (() -> Unit)? = null,
    clearLabel: String? = null,
    placeholderText: String? = null,
    modifier: Modifier = Modifier
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

        // 排序表头
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 角色列（只显示头像）
            Spacer(Modifier.width(52.dp))

            // 5 列数据
            SortField.entries.forEach { field ->
                SortableHeader(
                    label = field.label,
                    isActive = sortField == field,
                    order = if (sortField == field) sortOrder else null,
                    onClick = { onSortChange(field) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SortableHeader(
    label: String,
    isActive: Boolean,
    order: SortOrder?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
            .height(28.dp),
        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (isActive && order != null) {
            Icon(
                if (order == SortOrder.DESC) Icons.Default.ArrowDropDown
                else Icons.Default.ArrowDropUp,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
    isLoading: Boolean
) {
    key(listKey) {
        val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(list, key = { _, item -> item.id }) { index, hero ->
                val meta = characterMap[hero.id]
                val position = meta?.let { positionMap[it.positionCode] }
                val compareHero = compareMap?.get(hero.id)

                HeroRow(
                    rank = index + 1,
                    hero = hero,
                    compareHero = compareHero,
                    meta = meta,
                    positionName = position?.name ?: "",
                    highlightField = sortField
                )

                if (index < list.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 加载中遮罩
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

// ────────────────────────────────────────────
//  单行角色数据
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroRow(
    rank: Int,
    hero: HeroBalanceData,
    compareHero: HeroBalanceData?,
    meta: CharacterMeta?,
    positionName: String,
    highlightField: SortField
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.width(4.dp))

        // 角色头像（点击浮动显示角色名 + 定位）
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    val name = meta?.name ?: hero.heroName
                    Text(if (positionName.isNotEmpty()) "$name · $positionName" else name)
                }
            },
            state = tooltipState
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { scope.launch { tooltipState.show() } },
                contentAlignment = Alignment.Center
            ) {
                if (meta != null) {
                    AsyncImage(
                        model = meta.imageUrl,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            hero.heroName.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(2.dp))

        // 5 列数据
        DataCell(
            field = SortField.WIN_RATE,
            current = hero.winRate,
            compare = compareHero?.winRate,
            isHighlight = highlightField == SortField.WIN_RATE,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            field = SortField.SELECT_RATE,
            current = hero.selectRate,
            compare = compareHero?.selectRate,
            isHighlight = highlightField == SortField.SELECT_RATE,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            field = SortField.KD,
            current = hero.kd,
            compare = compareHero?.kd,
            isHighlight = highlightField == SortField.KD,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            field = SortField.DAMAGE,
            current = hero.damageAve,
            compare = compareHero?.damageAve,
            isHighlight = highlightField == SortField.DAMAGE,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            field = SortField.SCORE,
            current = hero.score,
            compare = compareHero?.score,
            isHighlight = highlightField == SortField.SCORE,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DataCell(
    modifier: Modifier = Modifier,
    field: SortField,
    current: Double,
    compare: Double?,
    isHighlight: Boolean,
) {
    val currentText = field.formatValue(current)
    val compareText = compare?.let { field.formatValue(it) }
    val changeColor = compare?.let { if (current >= it) Color(0xFF4CAF50) else Color(0xFFF44336) }
    val hasBigChange = compare?.let { field.isBigChange(current, it) } == true
    val mainColor = when {
        changeColor != null && hasBigChange -> changeColor
        isHighlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val compareColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
                color = mainColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (compare != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = if (current >= compare) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = changeColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (compareText != null) {
            Text(
                text = compareText,
                style = MaterialTheme.typography.labelSmall,
                color = compareColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun SortField.formatValue(value: Double): String = when (this) {
    SortField.WIN_RATE, SortField.SELECT_RATE -> "%.1f%%".format(value)
    SortField.KD -> "%.2f".format(value)
    SortField.DAMAGE, SortField.SCORE -> "%.0f".format(value)
}

private fun SortField.compareThresholdPercent(): Double = when (this) {
    SortField.WIN_RATE -> 15.0
    SortField.SELECT_RATE -> 25.0
    SortField.KD -> 20.0
    SortField.DAMAGE -> 20.0
    SortField.SCORE -> 18.0
}

private fun SortField.isBigChange(current: Double, compare: Double): Boolean {
    if (compare == 0.0) return current != 0.0
    val pctDiff = abs((current - compare) / compare) * 100
    return pctDiff >= compareThresholdPercent()
}
