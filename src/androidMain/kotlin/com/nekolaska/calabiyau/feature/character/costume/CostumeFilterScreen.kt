package com.nekolaska.calabiyau.feature.character.costume

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.CostumeInfo
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi.Quality
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.character.components.CharacterSelector
import com.nekolaska.calabiyau.feature.character.components.rememberCharacterSelectorOptions

// ════════════════════════════════════════════════════════
//  角色时装筛选页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private const val CRYSTAL_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_3.png"
private const val CRYSTAL_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%B7%B4%E5%B8%83%E6%B4%9B%E6%99%B6%E6%A0%B8.png"
private const val BASE_ICON_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E9%81%93%E5%85%B7%E5%9B%BE%E6%A0%87_6.png"
private const val BASE_ICON_FALLBACK_URL = "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%9B%BE%E6%A0%87-%E5%B0%8F%E5%9F%BA%E5%BC%A6.png"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostumeFilterScreen(
    initialCharacter: String? = null,
    onBack: () -> Unit
) {
    val state = rememberLoadState(
        initial = emptyList<CostumeInfo>(),
        cachedFetch = { CostumeFilterApi.fetchAllCostumes(cacheOnly = true) },
        fetch = { force -> CostumeFilterApi.fetchAllCostumes(force) }
    )
    val allCostumes = state.data

    // 筛选状态
    var selectedCharacter by remember { mutableStateOf(initialCharacter) }
    var selectedQuality by remember { mutableStateOf<Quality?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 筛选后的列表
    val filteredCostumes = remember(allCostumes, selectedCharacter, selectedQuality, searchQuery) {
        allCostumes.filter { costume ->
            (selectedCharacter == null || costume.character == selectedCharacter) &&
                    (selectedQuality == null || costume.quality == selectedQuality) &&
                    (searchQuery.isBlank() || costume.name.contains(searchQuery, ignoreCase = true)
                            || costume.character.contains(searchQuery, ignoreCase = true))
        }
    }

    // 角色列表（去重）
    val characters = remember(allCostumes) {
        allCostumes.map { it.character }.distinct().sorted()
    }
    val characterOptions = rememberCharacterSelectorOptions(characters)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色时装", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (allCostumes.isNotEmpty()) {
                        Text(
                            text = filteredCostumes.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> CostumeFilterSkeleton(mod) }
        ) {
            var selectedCostume by remember { mutableStateOf<CostumeInfo?>(null) }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SearchBar(
                            keyword = searchQuery,
                            onKeywordChange = { searchQuery = it },
                            onSearch = {},
                            onClear = { searchQuery = "" },
                            isSearching = false,
                            placeholder = "搜索时装名称或角色…",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // ── 筛选栏 ──
                        CostumeFilterBar(
                            characterOptions = characterOptions,
                            selectedCharacter = selectedCharacter,
                            onCharacterSelected = { selectedCharacter = it },
                            selectedQuality = selectedQuality,
                            onQualitySelected = { selectedQuality = it }
                        )
                    }
                }

                // ── 时装网格 ──
                if (filteredCostumes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的时装", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredCostumes, key = { it.name + it.character }) { costume ->
                        CostumeCard(
                            costume = costume,
                            onClick = { selectedCostume = costume }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            // ── 时装详情底部弹窗 ──
            if (selectedCostume != null) {
                CostumeDetailSheet(
                    costume = selectedCostume!!,
                    onDismiss = { selectedCostume = null }
                )
            }
        }
    }
}

@Composable
private fun CostumeFilterSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(52.dp),
            shape = smoothCornerShape(28.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(72.dp)
                    .height(14.dp),
                shape = smoothCornerShape(6.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = smoothCornerShape(20.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .width(72.dp)
                    .height(14.dp),
                shape = smoothCornerShape(6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(if (it == 0) 72.dp else 60.dp)
                            .height(32.dp),
                        shape = smoothCapsuleShape()
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false
        ) {
            items(12) {
                Card(
                    shape = smoothCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = smoothCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(
                            Modifier.fillMaxWidth(0.75f).height(12.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(
                            Modifier.fillMaxWidth(0.5f).height(10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  筛选栏
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumeFilterBar(
    characterOptions: List<com.nekolaska.calabiyau.feature.character.components.CharacterSelectorOption>,
    selectedCharacter: String?,
    onCharacterSelected: (String?) -> Unit,
    selectedQuality: Quality?,
    onQualitySelected: (Quality?) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "按角色筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CharacterSelector(
            options = characterOptions,
            selectedName = selectedCharacter,
            onSelectedNameChange = onCharacterSelected,
            label = "角色",
            allLabel = "全部角色"
        )

        // 品质筛选（跳过“初始”品质）
        Text(
            text = "按品质筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedQuality == null,
                onClick = { onQualitySelected(null) },
                shape = smoothCornerShape(12.dp),
                label = { Text("全部品质", maxLines = 1) }
            )
            Quality.entries.filter { it != Quality.INITIAL }.forEach { quality ->
                FilterChip(
                    selected = selectedQuality == quality,
                    onClick = {
                        onQualitySelected(if (selectedQuality == quality) null else quality)
                    },
                    shape = smoothCornerShape(12.dp),
                    label = { Text(quality.displayName, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = qualityColor(quality).copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}


// ────────────────────────────────────────────
//  时装卡片
// ────────────────────────────────────────────

@Composable
private fun CostumeCard(costume: CostumeInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = costume.quality?.let { qualityColor(it).copy(alpha = 0.4f) }
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 时装图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(smoothCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (costume.thumbnailUrl != null) {
                    AsyncImage(
                        model = costume.fullImageUrl ?: costume.thumbnailUrl,
                        contentDescription = costume.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Checkroom, null,
                                Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // 品质角标（跳过“初始”品质）
                if (costume.quality != null && costume.quality != Quality.INITIAL) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = smoothCapsuleShape(),
                        color = qualityColor(costume.quality).copy(alpha = 0.85f)
                    ) {
                        Text(
                            costume.quality.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 时装名
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    costume.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  品质颜色
// ────────────────────────────────────────────

@Composable
private fun qualityColor(quality: Quality): Color {
    return when (quality) {
        Quality.INITIAL -> Color(0xFF94A3B8)    // 灰蓝
        Quality.EXQUISITE -> Color(0xFF3B82F6)  // 蓝
        Quality.SUPERIOR -> Color(0xFFA855F7)   // 紫
        Quality.PERFECT -> Color(0xFFF59E0B)    // 金
        Quality.LEGENDARY -> Color(0xFFEF4444)  // 红
        Quality.SECRET -> Color(0xFFFF6B2C)     // 橙
    }
}

// ────────────────────────────────────────────
//  时装详情底部弹窗
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumeDetailSheet(
    costume: CostumeInfo,
    onDismiss: () -> Unit
) {
    val qColor = costume.quality?.let { qualityColor(it) } ?: MaterialTheme.colorScheme.outline
    val displayModes = buildList {
        if (!costume.fullImageUrl.isNullOrBlank() || !costume.thumbnailUrl.isNullOrBlank()) add("立绘")
        if (!costume.screenshotUrl.isNullOrBlank()) add("游戏截图")
    }
    var selectedMode by remember(costume.name, costume.character) {
        mutableStateOf(if (displayModes.contains("立绘")) "立绘" else displayModes.firstOrNull().orEmpty())
    }
    val displayedImage = when (selectedMode) {
        "游戏截图" -> costume.screenshotUrl ?: costume.fullImageUrl ?: costume.thumbnailUrl
        else -> costume.fullImageUrl ?: costume.thumbnailUrl ?: costume.screenshotUrl
    }
    val imageContentScale = if (selectedMode == "游戏截图") ContentScale.Fit else ContentScale.Crop
    val imageBackgroundColor = if (selectedMode == "游戏截图") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        Color.Transparent
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── 头部：图片 + 渐变 + 名称 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(imageBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (displayedImage != null) {
                    AsyncImage(
                        model = displayedImage,
                        contentDescription = costume.name,
                        contentScale = imageContentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 底部渐变
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
                // 品质角标
                if (costume.quality != null && costume.quality != Quality.INITIAL) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = smoothCapsuleShape(),
                        color = qColor.copy(alpha = 0.9f)
                    ) {
                        Text(
                            costume.quality.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── 名称 & 角色 ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    costume.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    costume.character,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (displayModes.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayModes.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode) },
                                shape = smoothCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (costume.description.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = smoothCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    val scrollState = rememberScrollState()
                    Text(
                        text = costume.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isDescriptionExpanded) 240.dp else Dp.Unspecified)
                            .then(if (isDescriptionExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isDescriptionExpanded = !isDescriptionExpanded }
                            .animateContentSize()
                            .padding(20.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // ── 信息卡片 ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp)) {
                    // 获取方式
                    if (costume.sources.isNotEmpty()) {
                        DetailRow(
                            icon = Icons.Outlined.ShoppingBag,
                            label = "获取方式",
                            value = costume.sources.joinToString("、")
                        )
                    }

                    // 巴布洛晶核价格
                    if (costume.crystalCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            imageUrl = CRYSTAL_ICON_URL,
                            fallbackImageUrl = CRYSTAL_ICON_FALLBACK_URL,
                            label = "巴布洛晶核",
                            value = costume.crystalCost
                        )
                    }

                    // 基弦价格
                    if (costume.baseCost.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            imageUrl = BASE_ICON_URL,
                            fallbackImageUrl = BASE_ICON_FALLBACK_URL,
                            label = "基弦",
                            value = costume.baseCost
                        )
                    }

                    // 品质
                    if (costume.quality != null && costume.quality != Quality.INITIAL) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            icon = Icons.Outlined.Star,
                            label = "品质",
                            value = costume.quality.displayName,
                            valueColor = qColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector? = null,
    imageUrl: String? = null,
    fallbackImageUrl: String? = null,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (imageUrl != null) {
                    var useFallback by remember(imageUrl, fallbackImageUrl) { mutableStateOf(false) }
                    AsyncImage(
                        model = if (useFallback) fallbackImageUrl ?: imageUrl else imageUrl,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        onError = {
                            if (!useFallback && !fallbackImageUrl.isNullOrBlank()) {
                                useFallback = true
                            }
                        },
                        modifier = Modifier.size(22.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
