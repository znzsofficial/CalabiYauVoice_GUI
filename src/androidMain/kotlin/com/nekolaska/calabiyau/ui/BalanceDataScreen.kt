package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.BalanceDataApi
import data.ApiResult
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
fun BalanceDataScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    // ── 状态 ──
    var isLoadingSettings by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf<BalanceDataApi.BalanceSettings?>(null) }
    var balanceResult by remember { mutableStateOf<BalanceDataApi.BalanceResult?>(null) }

    // 筛选状态
    var selectedMode by remember { mutableStateOf<BalanceDataApi.FilterOption?>(null) }
    var selectedMap by remember { mutableStateOf<BalanceDataApi.FilterOption?>(null) }
    var selectedSeason by remember { mutableStateOf<BalanceDataApi.FilterOption?>(null) }
    var selectedRanks by remember { mutableStateOf<List<BalanceDataApi.FilterOption>>(emptyList()) }

    // 显示状态：进攻方 / 防守方
    var showAttackers by remember { mutableStateOf(false) } // false = 防守方(side2), true = 进攻方(side1)

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
    fun loadSettings() {
        scope.launch {
            isLoadingSettings = true
            errorMessage = null
            when (val result = BalanceDataApi.fetchSettings()) {
                is ApiResult.Success -> {
                    val s = result.value
                    settings = s
                    // 默认选中：排位爆破、全选地图、全选段位、最新赛季
                    selectedMode = s.modes.find { it.name == "排位爆破" } ?: s.modes.firstOrNull()
                    selectedMap = null // null 表示全选
                    selectedSeason = s.seasons.firstOrNull()
                    selectedRanks = emptyList() // 空表示全选
                }
                is ApiResult.Error -> errorMessage = result.message
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
                season1Code = season.code
            )) {
                is ApiResult.Success -> {
                    balanceResult = result.value
                    errorMessage = null
                }
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoadingData = false
        }
    }

    // 初始加载
    LaunchedEffect(Unit) { loadSettings() }

    // 设置变化时自动刷新数据
    LaunchedEffect(selectedMode, selectedMap, selectedSeason, selectedRanks) {
        if (settings != null && selectedMode != null && selectedSeason != null) {
            loadBalanceData()
        }
    }

    // 排序后的列表
    val sortedList = remember(balanceResult, showAttackers, sortField, sortOrder) {
        val list = if (showAttackers) balanceResult?.attackers else balanceResult?.defenders
        list?.filter { it.winRate > 0 }?.let { filtered ->
            val comparator = compareBy<BalanceDataApi.HeroBalanceData> {
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
                title = { Text("平衡数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            loadSettings()
                        },
                        enabled = !isLoadingSettings && !isLoadingData
                    ) {
                        Icon(Icons.Outlined.Refresh, "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoadingSettings -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                errorMessage != null && settings == null -> {
                    ErrorState(
                        message = errorMessage!!,
                        onRetry = { loadSettings() }
                    )
                }
                settings != null -> {
                    Column(Modifier.fillMaxSize()) {
                        // ── 筛选栏 ──
                        FilterBar(
                            settings = settings!!,
                            selectedMode = selectedMode,
                            selectedMap = selectedMap,
                            selectedSeason = selectedSeason,
                            selectedRanks = selectedRanks,
                            onModeChange = { selectedMode = it },
                            onMapChange = { selectedMap = it },
                            onSeasonChange = { selectedSeason = it },
                            onRanksChange = { selectedRanks = it }
                        )

                        // ── 进攻/防守切换 + 排序 ──
                        SideAndSortBar(
                            showAttackers = showAttackers,
                            sortField = sortField,
                            sortOrder = sortOrder,
                            onSideToggle = { showAttackers = it },
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
                                HeroBalanceList(
                                    list = sortedList,
                                    characterMap = characterMap,
                                    positionMap = positionMap,
                                    sortField = sortField,
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
    settings: BalanceDataApi.BalanceSettings,
    selectedMode: BalanceDataApi.FilterOption?,
    selectedMap: BalanceDataApi.FilterOption?,
    selectedSeason: BalanceDataApi.FilterOption?,
    selectedRanks: List<BalanceDataApi.FilterOption>,
    onModeChange: (BalanceDataApi.FilterOption) -> Unit,
    onMapChange: (BalanceDataApi.FilterOption?) -> Unit,
    onSeasonChange: (BalanceDataApi.FilterOption) -> Unit,
    onRanksChange: (List<BalanceDataApi.FilterOption>) -> Unit
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

        Spacer(Modifier.height(6.dp))

        // 第二行：地图 + 段位（横向滚动 Chip）
        Text(
            "地图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedMap == null,
                onClick = { onMapChange(null) },
                label = { Text("全选", maxLines = 1) }
            )
            settings.maps.forEach { map ->
                FilterChip(
                    selected = selectedMap == map,
                    onClick = { onMapChange(map) },
                    label = { Text(map.name, maxLines = 1) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "段位",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedRanks.isEmpty(),
                onClick = { onRanksChange(emptyList()) },
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
                    label = { Text(rank.name, maxLines = 1) }
                )
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

// ────────────────────────────────────────────
//  下拉选择器
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<BalanceDataApi.FilterOption>,
    selected: BalanceDataApi.FilterOption?,
    onSelect: (BalanceDataApi.FilterOption) -> Unit,
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
                label = { Text("防守方") },
                leadingIcon = if (!showAttackers) {
                    { Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = showAttackers,
                onClick = { onSideToggle(true) },
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
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
                modifier = Modifier.size(16.dp),
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
    list: List<BalanceDataApi.HeroBalanceData>,
    characterMap: Map<Int, BalanceDataApi.CharacterMeta>,
    positionMap: Map<String, BalanceDataApi.PositionMeta>,
    sortField: SortField,
    isLoading: Boolean
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(list, key = { _, item -> item.id }) { index, hero ->
                val meta = characterMap[hero.id]
                val position = meta?.let { positionMap[it.positionCode] }

                HeroRow(
                    rank = index + 1,
                    hero = hero,
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

// ────────────────────────────────────────────
//  单行角色数据
// ────────────────────────────────────────────

/** 胜率颜色：高于 50% 偏绿，低于 50% 偏红 */
private fun winRateColor(rate: Double): Color = when {
    rate >= 52.0 -> Color(0xFF4CAF50)
    rate >= 50.0 -> Color(0xFF8BC34A)
    rate >= 48.0 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroRow(
    rank: Int,
    hero: BalanceDataApi.HeroBalanceData,
    meta: BalanceDataApi.CharacterMeta?,
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
            value = "%.1f%%".format(hero.winRate),
            isHighlight = highlightField == SortField.WIN_RATE,
            color = winRateColor(hero.winRate),
            modifier = Modifier.weight(1f)
        )
        DataCell(
            value = "%.1f%%".format(hero.selectRate),
            isHighlight = highlightField == SortField.SELECT_RATE,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            value = "%.2f".format(hero.kd),
            isHighlight = highlightField == SortField.KD,
            color = if (hero.kd >= 1.0) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.weight(1f)
        )
        DataCell(
            value = "%.0f".format(hero.damageAve),
            isHighlight = highlightField == SortField.DAMAGE,
            modifier = Modifier.weight(1f)
        )
        DataCell(
            value = "%.0f".format(hero.score),
            isHighlight = highlightField == SortField.SCORE,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DataCell(
    value: String,
    isHighlight: Boolean,
    color: Color? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
        color = color ?: if (isHighlight) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
    )
}
