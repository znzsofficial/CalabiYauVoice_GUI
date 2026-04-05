package ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import data.BalanceDataApi
import data.BalanceDataApi.ApiResult
import data.ImageLoader
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.surface.Card
import kotlinx.coroutines.launch
import ui.components.ComboBox
import ui.components.StyledWindow

// ════════════════════════════════════════════════════════
//  平衡数据窗口（Desktop / Fluent UI）
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalFluentApi::class)
@Composable
fun BalanceDataWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(
        width = 960.dp,
        height = 760.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "平衡数据",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        },
    ) { insetModifier ->
        BalanceDataContent(modifier = insetModifier)
    }
}

// ────────────────────────────────────────────
//  排序
// ────────────────────────────────────────────

private enum class SortField(val label: String) {
    WIN_RATE("胜率"),
    SELECT_RATE("选取率"),
    KD("KD"),
    DAMAGE("场均伤害"),
    SCORE("场均得分")
}

private enum class SortOrder { ASC, DESC }

// ────────────────────────────────────────────
//  主内容
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun BalanceDataContent(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    // ── 状态 ──
    var isLoadingSettings by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf<BalanceDataApi.BalanceSettings?>(null) }
    var balanceResult by remember { mutableStateOf<BalanceDataApi.BalanceResult?>(null) }

    // 筛选
    var selectedModeIndex by remember { mutableIntStateOf(-1) }
    var selectedMapIndex by remember { mutableIntStateOf(0) } // 0 = 全选
    var selectedSeasonIndex by remember { mutableIntStateOf(-1) }
    var selectedRankIndices by remember { mutableStateOf<Set<Int>>(emptySet()) } // 空 = 全选

    // 显示
    var showAttackers by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf(SortField.WIN_RATE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESC) }

    // 元信息映射
    val characterMap = remember(settings) {
        settings?.characters?.associateBy { it.code.toIntOrNull() ?: 0 } ?: emptyMap()
    }
    val positionMap = remember(settings) {
        settings?.positions?.associateBy { it.code } ?: emptyMap()
    }

    // 地图选项（前面加"全选"）
    val mapOptions = remember(settings) {
        listOf("全选") + (settings?.maps?.map { it.name } ?: emptyList())
    }

    // ── 加载设置 ──
    fun loadSettings() {
        scope.launch {
            isLoadingSettings = true
            errorMessage = null
            when (val result = BalanceDataApi.fetchSettings()) {
                is ApiResult.Success -> {
                    val s = result.data
                    settings = s
                    selectedModeIndex = s.modes.indexOfFirst { it.name == "排位爆破" }
                        .takeIf { it >= 0 } ?: 0
                    selectedSeasonIndex = 0
                    selectedMapIndex = 0
                    selectedRankIndices = emptySet()
                }
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoadingSettings = false
        }
    }

    // ── 加载数据 ──
    fun loadBalanceData() {
        val s = settings ?: return
        if (selectedModeIndex < 0 || selectedSeasonIndex < 0) return
        scope.launch {
            isLoadingData = true
            val mode = s.modes[selectedModeIndex]
            val season = s.seasons[selectedSeasonIndex]
            val mapCode = if (selectedMapIndex == 0) "-255"
            else s.maps.getOrNull(selectedMapIndex - 1)?.code ?: "-255"
            val rankCodes = if (selectedRankIndices.isEmpty()) listOf("-255")
            else selectedRankIndices.map { s.ranks[it].code }

            when (val result = BalanceDataApi.fetchBalanceData(
                modeCode = mode.code,
                mapCode = mapCode,
                rankCodes = rankCodes,
                season1Code = season.code
            )) {
                is ApiResult.Success -> {
                    balanceResult = result.data
                    errorMessage = null
                }
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoadingData = false
        }
    }

    LaunchedEffect(Unit) { loadSettings() }

    LaunchedEffect(selectedModeIndex, selectedMapIndex, selectedSeasonIndex, selectedRankIndices) {
        if (settings != null && selectedModeIndex >= 0 && selectedSeasonIndex >= 0) {
            loadBalanceData()
        }
    }

    // 排序
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

    Column(modifier.fillMaxSize()) {
        // ── 顶部工具栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Regular.DataBarVertical, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("平衡数据", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "数据来源：卡拉彼丘官网",
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { loadSettings() },
                disabled = isLoadingSettings || isLoadingData
            ) {
                Icon(Icons.Regular.ArrowSync, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新", fontSize = 12.sp)
            }
        }

        // ── 内容区 ──
        when {
            isLoadingSettings -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProgressRing()
                        Text("正在加载…", fontSize = 13.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                }
            }
            errorMessage != null && settings == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Regular.ErrorCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color(0xFFE57373)
                        )
                        Text(
                            errorMessage!!,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = FluentTheme.colors.text.text.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { loadSettings() }) { Text("重试") }
                    }
                }
            }
            settings != null -> {
                val s = settings!!

                // ── 筛选区（Card）──
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 第一行：模式 + 赛季 + 地图
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ComboBox(
                                header = "模式",
                                items = s.modes.map { it.name },
                                selected = selectedModeIndex.takeIf { it >= 0 },
                                onSelectionChange = { idx, _ -> selectedModeIndex = idx }
                            )
                            ComboBox(
                                header = "赛季",
                                items = s.seasons.map { it.name },
                                selected = selectedSeasonIndex.takeIf { it >= 0 },
                                onSelectionChange = { idx, _ -> selectedSeasonIndex = idx }
                            )
                            ComboBox(
                                header = "地图",
                                items = mapOptions,
                                selected = selectedMapIndex,
                                onSelectionChange = { idx, _ -> selectedMapIndex = idx }
                            )
                        }

                        // 第二行：段位多选
                        Column {
                            Text("段位", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 全选按钮
                                ToggleButton(
                                    checked = selectedRankIndices.isEmpty(),
                                    onCheckedChanged = { selectedRankIndices = emptySet() }
                                ) {
                                    Text("全选", fontSize = 11.sp)
                                }
                                s.ranks.forEachIndexed { idx, rank ->
                                    ToggleButton(
                                        checked = idx in selectedRankIndices,
                                        onCheckedChanged = {
                                            selectedRankIndices = if (idx in selectedRankIndices)
                                                selectedRankIndices - idx
                                            else selectedRankIndices + idx
                                        }
                                    ) {
                                        Text(rank.name, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 进攻/防守切换 ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToggleButton(
                        checked = !showAttackers,
                        onCheckedChanged = { showAttackers = false }
                    ) {
                        Icon(Icons.Regular.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("防守方", fontSize = 12.sp)
                    }
                    ToggleButton(
                        checked = showAttackers,
                        onCheckedChanged = { showAttackers = true }
                    ) {
                        Icon(Icons.Regular.Flash, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("进攻方", fontSize = 12.sp)
                    }

                    Spacer(Modifier.weight(1f))

                    if (isLoadingData) {
                        ProgressRing(Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("加载中…", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                }

                // ── 表格区（Card）──
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        // 表头
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "角色",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(140.dp)
                            )
                            SortField.entries.forEach { field ->
                                SortableHeaderCell(
                                    label = field.label,
                                    isActive = sortField == field,
                                    order = if (sortField == field) sortOrder else null,
                                    onClick = {
                                        if (sortField == field) {
                                            sortOrder = if (sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC
                                        } else {
                                            sortField = field
                                            sortOrder = SortOrder.DESC
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Divider(Modifier.padding(horizontal = 12.dp))

                        // 数据列表
                        if (sortedList.isEmpty() && !isLoadingData) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无数据", fontSize = 13.sp, color = FluentTheme.colors.text.text.secondary)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                itemsIndexed(sortedList, key = { _, item -> item.id }) { index, hero ->
                                    val meta = characterMap[hero.id]
                                    val position = meta?.let { positionMap[it.positionCode] }

                                    HeroRow(
                                        rank = index + 1,
                                        hero = hero,
                                        meta = meta,
                                        positionName = position?.name ?: "",
                                        highlightField = sortField
                                    )

                                    if (index < sortedList.lastIndex) {
                                        Divider(Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ────────────────────────────────────────────
//  排序表头单元格
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun SortableHeaderCell(
    label: String,
    isActive: Boolean,
    order: SortOrder?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) FluentTheme.colors.fillAccent.default
            else FluentTheme.colors.text.text.secondary
        )
        if (isActive && order != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                if (order == SortOrder.DESC) Icons.Regular.ChevronDown else Icons.Regular.ChevronUp,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = FluentTheme.colors.fillAccent.default
            )
        }
    }
}

// ────────────────────────────────────────────
//  角色数据行
// ────────────────────────────────────────────

private fun winRateColor(rate: Double): Color = when {
    rate >= 52.0 -> Color(0xFF4CAF50)
    rate >= 50.0 -> Color(0xFF8BC34A)
    rate >= 48.0 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun HeroRow(
    rank: Int,
    hero: BalanceDataApi.HeroBalanceData,
    meta: BalanceDataApi.CharacterMeta?,
    positionName: String,
    highlightField: SortField
) {
    // 角色头像
    var avatarBitmap by remember(meta?.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(meta?.imageUrl) {
        val url = meta?.imageUrl ?: return@LaunchedEffect
        avatarBitmap = ImageLoader.loadNetworkImage(BalanceDataApi.client, url)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名
        Text(
            "$rank",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> FluentTheme.colors.text.text.secondary
            },
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )

        // 头像
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(FluentTheme.colors.control.secondary),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap!!,
                    contentDescription = hero.heroName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Regular.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = FluentTheme.colors.text.text.secondary
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 名字 + 职业
        Column(Modifier.width(100.dp)) {
            Text(
                hero.heroName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (positionName.isNotEmpty()) {
                Text(
                    positionName,
                    fontSize = 10.sp,
                    color = FluentTheme.colors.text.text.secondary
                )
            }
        }

        // 数据列
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

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun DataCell(
    value: String,
    isHighlight: Boolean,
    color: Color? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        fontSize = 13.sp,
        fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
        color = color ?: if (isHighlight) FluentTheme.colors.fillAccent.default
        else FluentTheme.colors.text.text.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
    )
}

// ────────────────────────────────────────────
//  Divider（Fluent 风格分隔线）
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FluentTheme.colors.stroke.card.default.copy(alpha = 0.3f))
    )
}
