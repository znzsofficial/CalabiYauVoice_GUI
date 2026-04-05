package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.LocalWallpaperSeedColor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.CharacterListApi
import com.nekolaska.calabiyau.data.MapListApi
import com.nekolaska.calabiyau.data.WallpaperApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ════════════════════════════════════════════════════════
//  Wiki 主页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

/** 是否有壁纸背景（用于非液态玻璃模式下的半透明卡片） */
private val LocalHasWallpaper = staticCompositionLocalOf { false }

/** 子页面枚举 */
enum class WikiHubPage { HOME, CHARACTERS, CHAR_DETAIL, WEAPONS, WEAPON_DETAIL, MAPS, MAP_DETAIL, COSTUMES, ANNOUNCEMENTS, GAME_MODES, VOTING, NAVIGATION, WALLPAPERS, STICKERS, COMICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    isOverlaid: Boolean = false
) {
    var currentPage by remember { mutableStateOf(WikiHubPage.HOME) }
    var selectedCharacterName by remember { mutableStateOf("") }
    var selectedCharacterPortrait by remember { mutableStateOf<String?>(null) }
    var selectedWeaponName by remember { mutableStateOf("") }
    var selectedMapName by remember { mutableStateOf("") }
    var selectedMapImage by remember { mutableStateOf<String?>(null) }
    // ── 数据缓存（提升到此层级，子页面切换不丢失） ──
    var factions by remember { mutableStateOf<List<CharacterListApi.FactionData>>(emptyList()) }
    var isLoadingCharacters by remember { mutableStateOf(true) }
    var gameModes by remember { mutableStateOf<List<MapListApi.GameModeData>>(emptyList()) }
    var isLoadingMaps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (factions.isEmpty()) {
            isLoadingCharacters = true
            launch {
                when (val result = CharacterListApi.fetchAllFactions()) {
                    is CharacterListApi.ApiResult.Success -> factions = result.value
                    is CharacterListApi.ApiResult.Error -> { /* 静默失败 */ }
                }
                isLoadingCharacters = false
            }
        } else {
            isLoadingCharacters = false
        }
        if (gameModes.isEmpty()) {
            isLoadingMaps = true
            launch {
                when (val result = MapListApi.fetchAllModes()) {
                    is MapListApi.ApiResult.Success -> gameModes = result.value
                    is MapListApi.ApiResult.Error -> { /* 静默失败 */ }
                }
                isLoadingMaps = false
            }
        } else {
            isLoadingMaps = false
        }
    }

    // 子页面按返回键回到上一级
    // 记录地图详情的来源页面（首页或战斗模式）
    var mapDetailFrom by remember { mutableStateOf(WikiHubPage.HOME) }

    BackHandler(enabled = currentPage != WikiHubPage.HOME && !isOverlaid) {
        currentPage = when (currentPage) {
            WikiHubPage.CHAR_DETAIL -> WikiHubPage.CHARACTERS
            WikiHubPage.WEAPON_DETAIL -> WikiHubPage.WEAPONS
            WikiHubPage.MAP_DETAIL -> mapDetailFrom
            WikiHubPage.MAPS -> WikiHubPage.HOME
            else -> WikiHubPage.HOME
        }
    }

    when (currentPage) {
        WikiHubPage.HOME -> {
            WikiHomePage(
                onOpenDrawer = onOpenDrawer,
                onOpenWikiUrl = onOpenWikiUrl,
                onNavigateTo = { currentPage = it },
                onOpenCharacterDetail = { name, portrait ->
                    selectedCharacterName = name
                    selectedCharacterPortrait = portrait
                    currentPage = WikiHubPage.CHAR_DETAIL
                },
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.HOME
                    currentPage = WikiHubPage.MAP_DETAIL
                },
                factions = factions,
                isLoadingCharacters = isLoadingCharacters,
                gameModes = gameModes,
                isLoadingMaps = isLoadingMaps
            )
        }
        WikiHubPage.CHARACTERS -> {
            CharacterListFullScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenCharacterDetail = { name, portrait ->
                    selectedCharacterName = name
                    selectedCharacterPortrait = portrait
                    currentPage = WikiHubPage.CHAR_DETAIL
                }
            )
        }
        WikiHubPage.CHAR_DETAIL -> {
            CharacterDetailScreen(
                characterName = selectedCharacterName,
                portraitUrl = selectedCharacterPortrait,
                onBack = { currentPage = WikiHubPage.CHARACTERS },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.WEAPONS -> {
            WeaponListScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenWeaponDetail = { name ->
                    selectedWeaponName = name
                    currentPage = WikiHubPage.WEAPON_DETAIL
                }
            )
        }
        WikiHubPage.WEAPON_DETAIL -> {
            WeaponDetailScreen(
                weaponName = selectedWeaponName,
                onBack = { currentPage = WikiHubPage.WEAPONS },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.MAPS -> {
            MapListFullScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.MAPS
                    currentPage = WikiHubPage.MAP_DETAIL
                },
                gameModes = gameModes,
                isLoading = isLoadingMaps
            )
        }
        WikiHubPage.MAP_DETAIL -> {
            MapDetailScreen(
                mapName = selectedMapName,
                mapImageUrl = selectedMapImage,
                onBack = { currentPage = mapDetailFrom },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.COSTUMES -> {
            CostumeFilterScreen(
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.ANNOUNCEMENTS -> {
            AnnouncementScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.GAME_MODES -> {
            GameModeScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.GAME_MODES
                    currentPage = WikiHubPage.MAP_DETAIL
                }
            )
        }
        WikiHubPage.VOTING -> {
            VotingScreen(onBack = { currentPage = WikiHubPage.HOME })
        }
        WikiHubPage.NAVIGATION -> {
            NavigationMenuScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.WALLPAPERS -> {
            GalleryScreen(
                title = "壁纸",
                pageName = "壁纸",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.STICKERS -> {
            GalleryScreen(
                title = "表情包",
                pageName = "表情包",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.COMICS -> {
            GalleryScreen(
                title = "四格漫画",
                pageName = "官方四格漫画",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
    }
}

// ────────────────────────────────────────────
//  Wiki 主页
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WikiHomePage(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    factions: List<CharacterListApi.FactionData>,
    isLoadingCharacters: Boolean,
    gameModes: List<MapListApi.GameModeData>,
    isLoadingMaps: Boolean
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value

    // ── 壁纸背景（液态玻璃和普通模式均可显示） ──
    var wallpaperUrl by remember { mutableStateOf(AppPrefs.wallpaperUrl) }
    LaunchedEffect(Unit) {
        val needRefresh = when {
            wallpaperUrl == null -> true                          // 无缓存，必须获取
            AppPrefs.wallpaperAutoRefresh
                && !WallpaperApi.hasRefreshedThisSession -> true  // 启动后首次自动刷新
            else -> false                                        // 已刷新过或仅手动模式
        }
        if (needRefresh) {
            withContext(Dispatchers.IO) {
                wallpaperUrl = WallpaperApi.fetchRandomWallpaperUrl(
                    forceRefresh = wallpaperUrl != null
                )
            }
        }
    }
    val hasWallpaper = wallpaperUrl != null

    // ── 壁纸刷新后重新提取主题色 ──
    val wallpaperSeedColor = LocalWallpaperSeedColor.current
    LaunchedEffect(wallpaperUrl) {
        val url = wallpaperUrl ?: return@LaunchedEffect
        // 壁纸 URL 变化时（刷新了壁纸），重新提取主题色
        withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                val bytes = com.nekolaska.calabiyau.data.WikiEngine.client
                    .newCall(request).execute().body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext
                val palette = Palette.from(bitmap).generate()
                val dominant = palette.getVibrantColor(palette.getMutedColor(0))
                if (dominant != 0) {
                    wallpaperSeedColor.intValue = dominant
                    com.nekolaska.calabiyau.data.AppPrefs.wallpaperSeedColorCache = dominant
                }
                bitmap.recycle()
            } catch (_: Exception) { }
        }
    }

    // ── Backdrop：始终 remember，避免分支切换时重建 ──
    val layerBackdrop = rememberLayerBackdrop()
    val empty = remember { emptyBackdrop() }
    val backdrop: Backdrop = if (liquidGlassEnabled) layerBackdrop else empty

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primaryContainer

    // ── TopBar 滚动折叠比例（0 = 完全展开，1 = 完全折叠） ──
    val collapsedFraction by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("卡拉彼丘 Wiki") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "菜单")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = if (hasWallpaper) {
                    // 有壁纸：始终透明，通过 drawBehind 实现渐变蒙层
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                modifier = if (hasWallpaper) {
                    // 折叠时叠加半透明背景，展开时完全透明
                    Modifier.drawBehind {
                        drawRect(surfaceColor.copy(alpha = collapsedFraction * 0.78f))
                    }
                } else Modifier
            )
        },
        containerColor = if (hasWallpaper) Color.Transparent
            else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        CompositionLocalProvider(LocalHasWallpaper provides hasWallpaper) {
        Box(Modifier.fillMaxSize()) {
            // ── 壁纸背景层 ──
            if (hasWallpaper) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (liquidGlassEnabled) Modifier.layerBackdrop(layerBackdrop)
                            else Modifier
                        )
                ) {
                    // 壁纸图片
                    AsyncImage(
                        model = wallpaperUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 主题色渐变叠加
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (liquidGlassEnabled) listOf(
                                        primaryColor.copy(alpha = 0.3f),
                                        surfaceColor.copy(alpha = 0.6f),
                                        surfaceColor.copy(alpha = 0.85f)
                                    ) else listOf(
                                        surfaceColor.copy(alpha = 0.15f),
                                        surfaceColor.copy(alpha = 0.5f),
                                        surfaceColor.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                }
            }

            // ── 内容层 ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // ── 快捷入口 ──
            item(key = "quick_access", contentType = "grid") {
                QuickAccessGrid(
                    onOpenWikiUrl = onOpenWikiUrl,
                    onNavigateTo = onNavigateTo,
                    backdrop = backdrop
                )
            }

            // ── 超弦体 & 晶源体 ──
            item(key = "characters", contentType = "preview_section") {
                CharacterPreviewSection(
                    factions = factions,
                    isLoading = isLoadingCharacters,
                    onOpenCharacterDetail = onOpenCharacterDetail,
                    onViewAll = { onNavigateTo(WikiHubPage.CHARACTERS) },
                    backdrop = backdrop
                )
            }

            // ── 武器 ──
            item(key = "weapons", contentType = "action_card") {
                ActionCard(
                    title = "武器一览",
                    subtitle = "查看全部武器数据",
                    icon = Icons.Outlined.GpsFixed,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.WEAPONS) },
                    backdrop = backdrop
                )
            }

            // ── 地图 ──
            item(key = "maps", contentType = "preview_section") {
                MapPreviewSection(
                    gameModes = gameModes,
                    isLoading = isLoadingMaps,
                    onOpenMapDetail = onOpenMapDetail,
                    onViewAll = { onNavigateTo(WikiHubPage.MAPS) },
                    backdrop = backdrop
                )
            }

            // ── 战斗模式 ──
            item(key = "game_modes", contentType = "action_card") {
                ActionCard(
                    title = "战斗模式",
                    subtitle = "查看所有战斗模式详情",
                    icon = Icons.Outlined.SportsEsports,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.GAME_MODES) },
                    backdrop = backdrop
                )
            }

            // ── 其他内容 ──
            item(key = "others", contentType = "content_block") {
                ContentBlockCard(
                    title = "游戏延申",
                    icon = Icons.Outlined.MoreHoriz,
                    items = listOf(
                        "剧情故事" to "剧情故事",
                        "游戏历史" to "游戏历史",
                        "BGM" to "BGM",
                        "壁纸" to "壁纸",
                        "表情包" to "表情包",
                        "四格漫画" to "官方四格漫画",
                        "喵言喵语" to "喵言喵语",
                        "梗百科" to "梗百科"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "壁纸" to { onNavigateTo(WikiHubPage.WALLPAPERS) },
                        "表情包" to { onNavigateTo(WikiHubPage.STICKERS) },
                        "四格漫画" to { onNavigateTo(WikiHubPage.COMICS) }
                    ),
                    backdrop = backdrop
                )
            }


            // ── 时装投票 ──
            item(key = "voting", contentType = "action_card") {
                ActionCard(
                    title = "时装投票",
                    subtitle = "为你喜欢的时装投票",
                    icon = Icons.Outlined.HowToVote,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.VOTING) },
                    backdrop = backdrop
                )
            }

            // ── 公告资讯 ──
            item(key = "announcements", contentType = "action_card") {
                ActionCard(
                    title = "公告资讯",
                    subtitle = "查看最新游戏公告和更新信息",
                    icon = Icons.Outlined.Campaign,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.ANNOUNCEMENTS) },
                    backdrop = backdrop
                )
            }

            // ── 玩法模式 ──
            item(key = "gameplay", contentType = "content_block") {
                ContentBlockCard(
                    title = "其他玩法",
                    icon = Icons.Outlined.Extension,
                    items = listOf(
                        "弦化" to "弦化",
                        "弦能增幅网络" to "弦能增幅网络",
                        "特别行动" to "特别行动",
                        "赫尔墨斯" to "赫尔墨斯",
                        "誓约" to "誓约",
                        "印迹" to "印迹",
                        "赛事系统" to "赛事系统"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = backdrop
                )
            }

            // ── Wiki 导航 ──
            item(key = "navigation", contentType = "action_card") {
                ActionCard(
                    title = "完整导航",
                    subtitle = "浏览 Wiki 全部分类目录",
                    icon = Icons.Outlined.AccountTree,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.NAVIGATION) },
                    backdrop = backdrop
                )
            }

            // 底部留白
            item { Spacer(Modifier.height(24.dp)) }
        }
        } // Box
        } // CompositionLocalProvider
    }
}

// ────────────────────────────────────────────
//  快捷入口网格（2 行 3 列圆角图标按钮）
// ────────────────────────────────────────────

/** 快捷入口数据（提取到顶层避免每次重组分配） */
private data class QuickEntry(
    val label: String,
    val icon: ImageVector,
    val targetPage: WikiHubPage?,       // null 表示使用 url
    val url: String? = null
)

private val quickEntries = listOf(
    QuickEntry("角色", Icons.Outlined.People, WikiHubPage.CHARACTERS),
    QuickEntry("武器", Icons.Outlined.GpsFixed, WikiHubPage.WEAPONS),
    QuickEntry("地图", Icons.Outlined.Map, WikiHubPage.MAPS),
    QuickEntry("投票", Icons.Outlined.HowToVote, WikiHubPage.VOTING),
    QuickEntry("时装", Icons.Outlined.Checkroom, WikiHubPage.COSTUMES),
    QuickEntry("导航", Icons.Outlined.AccountTree, WikiHubPage.NAVIGATION),
)
private val quickEntryRows = quickEntries.chunked(3)

@Composable
private fun QuickAccessGrid(
    onOpenWikiUrl: (String) -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val entryShape = smoothCornerShape(20.dp)
    val surfaceColor = when {
        liquidGlass -> Color.Transparent
        hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val iconTint = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quickEntryRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { entry ->
                    Surface(
                        onClick = {
                            if (entry.targetPage != null) onNavigateTo(entry.targetPage)
                            else entry.url?.let(onOpenWikiUrl)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .liquidGlassLight(
                                backdrop = backdrop,
                                shape = { entryShape }
                            ),
                        shape = entryShape,
                        color = surfaceColor,
                        contentColor = contentColor
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = iconTint
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                // 填充空位
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ────────────────────────────────────────────
//  角色预览区（横向滚动立绘卡片）
// ────────────────────────────────────────────

@Composable
private fun CharacterPreviewSection(
    factions: List<CharacterListApi.FactionData>,
    isLoading: Boolean,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    onViewAll: () -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    var selectedFaction by remember { mutableStateOf(0) }
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val charCardShape = smoothCornerShape(24.dp)
    val btnShape = smoothCornerShape(20.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    ElevatedCard(
        shape = charCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { charCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.elevatedCardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.elevatedCardColors()
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.People,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "超弦体 & 晶源体",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = btnShape
                ) {
                    Text("查看全部", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 阵营切换
            if (factions.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    factions.forEachIndexed { index, faction ->
                        SegmentedButton(
                            selected = selectedFaction == index,
                            onClick = { selectedFaction = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = factions.size
                            )
                        ) {
                            Text(
                                faction.faction,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 角色横向滚动
                val currentFaction = factions.getOrNull(selectedFaction)
                if (currentFaction != null) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(currentFaction.characters, key = { it.name }) { character ->
                            CharacterPortraitCard(
                                character = character,
                                onClick = { onOpenCharacterDetail(character.name, character.imageUrl) }
                            )
                        }
                    }
                }
            } else if (isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 角色立绘卡片 —— 完整显示立绘，底部渐变叠加角色名 */
@Composable
private fun CharacterPortraitCard(
    character: CharacterListApi.CharacterInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(16.dp),
        modifier = Modifier.width(90.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box {
            // 立绘图片：使用原始比例 (280x680 ≈ 5:12)
            AsyncImage(
                model = character.imageUrl,
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 12f)
            )

            // 底部渐变 + 角色名
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  地图预览区（按模式分类 + 横向滚动地图卡片）
// ────────────────────────────────────────────

@Composable
private fun MapPreviewSection(
    gameModes: List<MapListApi.GameModeData>,
    isLoading: Boolean,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    onViewAll: () -> Unit = {},
    backdrop: Backdrop = emptyBackdrop()
) {
    var selectedMode by remember { mutableStateOf(0) }
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val mapCardShape = smoothCornerShape(24.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    ElevatedCard(
        shape = mapCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { mapCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.elevatedCardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.elevatedCardColors()
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Map,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "地图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = smoothCornerShape(20.dp)
                ) {
                    Text("查看全部", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (gameModes.isNotEmpty()) {
                // 模式切换（横向滚动 FilterChip）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gameModes.forEachIndexed { index, mode ->
                        FilterChip(
                            selected = selectedMode == index,
                            onClick = { selectedMode = index },
                            label = {
                                Text(
                                    mode.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 地图横向滚动
                val currentMode = gameModes.getOrNull(selectedMode)
                if (currentMode != null && currentMode.maps.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(currentMode.maps, key = { it.name }) { map ->
                            MapCard(
                                map = map,
                                onClick = { onOpenMapDetail(map.name, map.imageUrl) }
                            )
                        }
                    }
                } else if (currentMode != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无地图数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 地图卡片 —— 16:9 图片 + 底部渐变叠加地图名 */
@Composable
private fun MapCard(
    map: MapListApi.MapInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(16.dp),
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box {
            // 地图图片 (600x338 ≈ 16:9)
            AsyncImage(
                model = map.imageUrl,
                contentDescription = map.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // 底部渐变 + 地图名
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = map.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  通用内容块卡片（标题 + FlowRow 标签）
// ────────────────────────────────────────────

private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBlockCard(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, String>>,  // displayName to pageName
    onOpenWikiUrl: (String) -> Unit,
    nativePages: Map<String, () -> Unit> = emptyMap(),  // displayName → 原生导航
    backdrop: Backdrop = emptyBackdrop()
) {
    var expanded by remember { mutableStateOf(true) }
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val blockCardShape = smoothCornerShape(24.dp)
    val clickableShape = smoothCornerShape(12.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    ElevatedCard(
        shape = blockCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { blockCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.elevatedCardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.elevatedCardColors()
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(clickableShape)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val btnShape = smoothCornerShape(14.dp)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { (display, page) ->
                        val nativeAction = nativePages[display]
                        val onClick: () -> Unit = if (nativeAction != null) {
                            nativeAction
                        } else {
                            val url = if (page.startsWith("http")) page
                            else "$WIKI_BASE${java.net.URLEncoder.encode(page, "UTF-8").replace("+", "%20")}"
                            { onOpenWikiUrl(url) }
                        }
                        FilledTonalButton(
                            onClick = onClick,
                            shape = btnShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                display,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  动作卡片（投票 / 导航入口）
// ────────────────────────────────────────────

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val actionCardShape = smoothCornerShape(24.dp)
    val capsuleShape = smoothCapsuleShape()
    Card(
        onClick = onClick,
        shape = actionCardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                liquidGlass -> Color.Transparent
                hasWallpaper -> containerColor.copy(alpha = 0.85f)
                else -> containerColor
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { actionCardShape },
                surfaceAlpha = 0.25f
            )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = capsuleShape,
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

// ────────────────────────────────────────────
//  角色列表全屏页（从主页"查看全部"进入）
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterListFullScreen(
    onBack: () -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超弦体 & 晶源体", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            CharacterListScreen(onOpenCharacterDetail = onOpenCharacterDetail)
        }
    }
}

// ────────────────────────────────────────────
//  地图一览全屏页（从主页"查看全部"或快捷入口进入）
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapListFullScreen(
    onBack: () -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    gameModes: List<MapListApi.GameModeData>,
    isLoading: Boolean
) {
    var selectedMode by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地图一览", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载地图数据…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            gameModes.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无地图数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Column(Modifier.padding(innerPadding)) {
                    // 模式切换
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gameModes.forEachIndexed { index, mode ->
                            FilterChip(
                                selected = selectedMode == index,
                                onClick = { selectedMode = index },
                                label = { Text(mode.displayName, maxLines = 1) }
                            )
                        }
                    }

                    // 地图网格
                    val currentMode = gameModes.getOrNull(selectedMode)
                    if (currentMode != null && currentMode.maps.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(currentMode.maps, key = { it.name }) { map ->
                                MapGridCard(
                                    map = map,
                                    onClick = { onOpenMapDetail(map.name, map.imageUrl) }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    } else {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("该模式暂无地图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** 地图网格卡片 —— 16:9 图片 + 底部渐变叠加地图名 */
@Composable
private fun MapGridCard(
    map: MapListApi.MapInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box {
            AsyncImage(
                model = map.imageUrl,
                contentDescription = map.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = map.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
