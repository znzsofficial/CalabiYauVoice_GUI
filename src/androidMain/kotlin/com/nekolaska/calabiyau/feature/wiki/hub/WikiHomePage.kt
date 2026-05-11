package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nekolaska.calabiyau.LocalWallpaperSeedColor
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.liquidGlass
import com.nekolaska.calabiyau.core.ui.liquidGlassLight
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

// ────────────────────────────────────────────
//  Wiki 主页
// ────────────────────────────────────────────

/** 是否有壁纸背景（用于非液态玻璃模式下的半透明卡片） */
internal val LocalHasWallpaper = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiHomePage(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    listState: LazyListState,
    onNavigateTo: (WikiHubPage) -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    factions: List<CharacterListApi.FactionData>,
    isLoadingCharacters: Boolean,
    gameModes: List<GameModeData>,
    isLoadingMaps: Boolean,
    selectedHomeFaction: Int,
    onHomeFactionChanged: (Int) -> Unit,
    selectedHomeMapMode: Int,
    onHomeMapModeChanged: (Int) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value

    // ── 壁纸背景（液态玻璃和普通模式均可显示） ──
    var wallpaperUrl by remember { mutableStateOf(AppPrefs.wallpaperUrl) }
    LaunchedEffect(Unit) {
        val currentCachedUrl = AppPrefs.wallpaperUrl
        if (!currentCachedUrl.isNullOrBlank() && wallpaperUrl != currentCachedUrl) {
            wallpaperUrl = currentCachedUrl
        }
        val needRefresh = when {
            currentCachedUrl.isNullOrBlank() -> true              // 无缓存，必须获取
            AppPrefs.wallpaperAutoRefresh
                && !WallpaperApi.hasRefreshedThisSession -> true  // 启动后首次自动刷新
            else -> false                                        // 已刷新过或仅手动模式
        }
        if (needRefresh) {
            val loadedUrl = withContext(Dispatchers.IO) {
                WallpaperApi.ensureWallpaperUrl(forceRefresh = !currentCachedUrl.isNullOrBlank())
            }
            if (!loadedUrl.isNullOrBlank()) {
                wallpaperUrl = loadedUrl
            }
        }
    }
    val hasWallpaper = wallpaperUrl != null

    // ── 壁纸刷新后重新提取主题色 ──
    //
    // 复用 Coil 的 ImageLoader：走内存/磁盘缓存，命中时几乎瞬时返回，
    // 且共享 WikiEngine.client（UA 轮换 + 403/429/503 重试）。
    // 相比原先手动 OkHttp 下载 + 两次 BitmapFactory 解码，省一次网络请求和一次 decode。
    val wallpaperSeedColor = LocalWallpaperSeedColor.current
    val context = LocalContext.current
    LaunchedEffect(wallpaperUrl) {
        val url = wallpaperUrl ?: return@LaunchedEffect
        // URL 未变且已缓存过取色结果：直接使用缓存色
        val cachedUrl = AppPrefs.wallpaperSeedColorUrl
        val cachedColor = AppPrefs.wallpaperSeedColorCache
        if (url == cachedUrl && cachedColor != 0) {
            wallpaperSeedColor.intValue = cachedColor
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)  // Palette 需读像素，必须软件位图
                .size(Size(128, 128))  // 降采样：128px 提取主色已足够，省显存
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            if (result !is SuccessResult) return@withContext
            val bitmap = runCatching { result.image.toBitmap() }.getOrNull()
                ?: return@withContext
            val palette = Palette.from(bitmap).generate()
            val dominant = palette.getVibrantColor(palette.getMutedColor(0))
            if (dominant != 0) {
                wallpaperSeedColor.intValue = dominant
                AppPrefs.wallpaperSeedColorCache = dominant
                AppPrefs.wallpaperSeedColorUrl = url
            }
        }
    }

    // ── Backdrop：始终 remember，避免分支切换时重建 ──
    val layerBackdrop = rememberLayerBackdrop()
    val empty = remember { emptyBackdrop() }
    val backdrop: Backdrop = if (liquidGlassEnabled) layerBackdrop else empty

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primaryContainer

    // ── 壁纸渐变叠加（remember 避免每帧重建 Brush） ──
    val wallpaperOverlayBrush = remember(liquidGlassEnabled, surfaceColor, primaryColor) {
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
    }

    // ── TopBar 滚动折叠比例（0 = 完全展开，1 = 完全折叠） ──
    val collapsedFraction by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction }
    }

    // ── 根 Box：把壁纸层放在 Scaffold 外面，确保一定覆盖整个窗口 ──
    //
    // 如果把壁纸放在 Scaffold 的 content lambda 里，渐变会受 Scaffold 内部布局影响，
    // 可能不会延伸到 TopAppBar 背后的区域，导致顶栏看起来比下方列表亮一截。
    // 在 Scaffold 外层的 Box 里渲染壁纸则不存在这个问题——Scaffold 整体透明，
    // 所有内容都叠在壁纸之上。
    Box(Modifier.fillMaxSize()) {
        // ── 壁纸背景层（根层，一定覆盖整屏） ──
        if (hasWallpaper) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (liquidGlassEnabled) Modifier.layerBackdrop(layerBackdrop)
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = wallpaperUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(wallpaperOverlayBrush)
                )
            }
        } else {
            // 无壁纸时用主题背景填底，保持与原行为一致
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
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
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    modifier = if (hasWallpaper) {
                        // 展开时顶栏完全透明（壁纸渐变已有平台期，顶栏与下方内容区 tint 一致）；
                        // 滚动折叠时逐渐叠加 surface 遮罩，收起后变成正常的 surface 顶栏。
                        Modifier.drawBehind {
                            drawRect(surfaceColor.copy(alpha = collapsedFraction * 0.78f))
                        }
                    } else Modifier
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            CompositionLocalProvider(LocalHasWallpaper provides hasWallpaper) {
            LazyColumn(
                state = listState,
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
                    selectedFaction = selectedHomeFaction,
                    onSelectedFactionChanged = onHomeFactionChanged,
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
                    selectedMode = selectedHomeMapMode,
                    onSelectedModeChanged = onHomeMapModeChanged,
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

            // ── 平衡数据 ──
            item(key = "balance_data", contentType = "action_card") {
                ActionCard(
                    title = "平衡数据",
                    subtitle = "官网角色胜率/选取率/KD等数据",
                    icon = Icons.Outlined.BarChart,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.BALANCE_DATA) },
                    backdrop = backdrop
                )
            }

            // ── 道具图鉴 ──
            item(key = "items", contentType = "action_card") {
                ActionCard(
                    title = "道具图鉴",
                    subtitle = "浏览功能道具、货币与礼盒礼包",
                    icon = Icons.Outlined.Inventory2,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.ITEMS) },
                    backdrop = backdrop
                )
            }

            // ── 时装筛选 ──
            item(key = "costumes", contentType = "action_card") {
                ActionCard(
                    title = "时装筛选",
                    subtitle = "浏览全部角色时装与外观",
                    icon = Icons.Outlined.Checkroom,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.COSTUMES) },
                    backdrop = backdrop
                )
            }

            // ── 武器外观 ──
            item(key = "weapon_skins", contentType = "action_card") {
                ActionCard(
                    title = "武器外观",
                    subtitle = "浏览全部武器外观与皮肤",
                    icon = Icons.Outlined.Palette,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.WEAPON_SKINS) },
                    backdrop = backdrop
                )
            }

            // ── 投票 ──
            item(key = "voting", contentType = "action_card") {
                ActionCard(
                    title = "投票",
                    subtitle = "查看并参与当前 Wiki 投票",
                    icon = Icons.Outlined.HowToVote,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.VOTING) },
                    backdrop = backdrop
                )
            }

            // ── 游戏延伸 ──
            item(key = "others", contentType = "content_block") {
                ContentBlockCard(
                    title = "游戏延伸",
                    icon = Icons.Outlined.MoreHoriz,
                    items = listOf(
                        "剧情故事" to "剧情故事",
                        "游戏历史" to "游戏历史",
                        "BGM" to "BGM",
                        "壁纸" to "壁纸",
                        "表情包" to "表情包",
                        "四格漫画" to "官方四格漫画",
                        "联动" to "联动",
                        "喵言喵语" to "喵言喵语",
                        "梗百科" to "梗百科",
                        "游戏Tips" to "游戏Tips"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "剧情故事" to { onNavigateTo(WikiHubPage.STORY) },
                        "游戏历史" to { onNavigateTo(WikiHubPage.GAME_HISTORY) },
                        "壁纸" to { onNavigateTo(WikiHubPage.WALLPAPERS) },
                        "表情包" to { onNavigateTo(WikiHubPage.STICKERS) },
                        "四格漫画" to { onNavigateTo(WikiHubPage.COMICS) },
                        "喵言喵语" to { onNavigateTo(WikiHubPage.MEOW_LANGUAGE) },
                        "游戏Tips" to { onNavigateTo(WikiHubPage.GAME_TIPS) }
                    ),
                    backdrop = backdrop
                )
            }

            // ── 移动端内容 ──
            item(key = "mobile_content", contentType = "content_block") {
                ContentBlockCard(
                    title = "移动端内容",
                    icon = Icons.Outlined.PhoneAndroid,
                    items = listOf(
                        "头像框" to "头像框",
                        "载具外观" to "极限推进模式载具外观",
                        "超弦体天赋" to "超弦体天赋",
                        "超弦推进卡牌" to "战斗模式/超弦推进"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "头像框" to { onNavigateTo(WikiHubPage.AVATAR_FRAMES) },
                        "载具外观" to { onNavigateTo(WikiHubPage.VEHICLE_SKINS) },
                        "超弦体天赋" to { onNavigateTo(WikiHubPage.STRINGER_TALENTS) },
                        "超弦推进卡牌" to { onNavigateTo(WikiHubPage.STRINGER_PUSH_CARDS) }
                    ),
                    backdrop = backdrop
                )
            }

            // ── 玩家装饰 ──
            item(key = "decorations", contentType = "content_block") {
                ContentBlockCard(
                    title = "玩家装饰",
                    icon = Icons.Outlined.Palette,
                    items = listOf(
                        "基板" to "基板",
                        "封装" to "封装",
                        "勋章" to "勋章",
                        "喷漆" to "喷漆",
                        "聊天气泡" to "聊天气泡",
                        "头套" to "头套",
                        "超弦体动作" to "超弦体动作",
                        "房间外观" to "房间外观"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "基板" to { onNavigateTo(WikiHubPage.BASEPLATES) },
                        "封装" to { onNavigateTo(WikiHubPage.ENCASINGS) },
                        "勋章" to { onNavigateTo(WikiHubPage.MEDALS) },
                        "喷漆" to { onNavigateTo(WikiHubPage.SPRAYS) },
                        "聊天气泡" to { onNavigateTo(WikiHubPage.CHAT_BUBBLES) },
                        "头套" to { onNavigateTo(WikiHubPage.HEADGEAR) },
                        "超弦体动作" to { onNavigateTo(WikiHubPage.STRINGER_ACTIONS) },
                        "房间外观" to { onNavigateTo(WikiHubPage.ROOM_APPEARANCES) }
                    ),
                    backdrop = backdrop
                )
            }

            // ── 卡牌 ──
            item(key = "bio_cards", contentType = "action_card") {
                ActionCard(
                    title = "卡牌",
                    subtitle = "整合 PC、生化卡牌与卡组分享",
                    icon = Icons.Outlined.Style,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.BIO_CARDS) },
                    backdrop = backdrop
                )
            }

            // ── 活动页 ──
            item(key = "activities", contentType = "action_card") {
                ActionCard(
                    title = "活动",
                    subtitle = "浏览当前与历史活动时间和内容简介",
                    icon = Icons.Outlined.Event,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.ACTIVITIES) },
                    backdrop = backdrop
                )
            }

            item(key = "announcements", contentType = "action_card") {
                ActionCard(
                    title = "公告资讯",
                    subtitle = "查看最新游戏公告和更新信息",
                    icon = Icons.Outlined.Campaign,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.ANNOUNCEMENTS) },
                    backdrop = backdrop
                )
            }

            // ── 其他玩法 ──
            item(key = "gameplay", contentType = "content_block") {
                ContentBlockCard(
                    title = "其他玩法",
                    icon = Icons.Outlined.Extension,
                    items = listOf(
                        "弦化" to "弦化",
                        "弦能增幅网络" to "弦能增幅网络",
                        "特别行动" to "特别行动",
                        "赫尔墨斯" to "赫尔墨斯",
                        "超弦体定位" to "超弦体定位",
                        "誓约" to "誓约",
                        "印迹" to "印迹",
                        "赛事系统" to "赛事系统"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = emptyMap(),
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
            } // CompositionLocalProvider
        } // Scaffold content
    } // 根 Box
}

// ────────────────────────────────────────────
//  快捷入口网格（2 行 3 列圆角图标按钮）
// ────────────────────────────────────────────

/** 快捷入口数据（提取到顶层避免每次重组分配） */
internal data class QuickEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val targetPage: WikiHubPage?,       // null 表示使用 url
    val url: String? = null
)

internal val allQuickEntries = listOf(
    QuickEntry("characters", "角色", Icons.Outlined.People, WikiHubPage.CHARACTERS),
    QuickEntry("weapons", "武器", Icons.Outlined.GpsFixed, WikiHubPage.WEAPONS),
    QuickEntry("maps", "地图", Icons.Outlined.Map, WikiHubPage.MAPS),
    QuickEntry("items", "道具", Icons.Outlined.Inventory2, WikiHubPage.ITEMS),
    QuickEntry("voting", "投票", Icons.Outlined.HowToVote, WikiHubPage.VOTING),
    QuickEntry("costumes", "时装", Icons.Outlined.Checkroom, WikiHubPage.COSTUMES),
    QuickEntry("bio_cards", "卡牌", Icons.Outlined.Style, WikiHubPage.BIO_CARDS),
    QuickEntry("activities", "活动", Icons.Outlined.Event, WikiHubPage.ACTIVITIES),
    QuickEntry("announcements", "公告", Icons.Outlined.Campaign, WikiHubPage.ANNOUNCEMENTS),
    QuickEntry("balance_data", "平衡", Icons.Outlined.BarChart, WikiHubPage.BALANCE_DATA),
    QuickEntry("game_modes", "玩法", Icons.Outlined.Extension, WikiHubPage.GAME_MODES),
    QuickEntry("wallpapers", "壁纸", Icons.Outlined.Wallpaper, WikiHubPage.WALLPAPERS),
    QuickEntry("navigation", "导航", Icons.Outlined.AccountTree, WikiHubPage.NAVIGATION),
)
internal val defaultQuickEntryIds = listOf(
    "characters", "weapons", "maps", "voting", "costumes", "bio_cards"
)
internal val quickEntryById = allQuickEntries.associateBy(QuickEntry::id)

// ── 预分配的渐变画笔（避免在 LazyRow item 中反复创建） ──
private val characterGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
)
private val mapGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
)

@Composable
private fun QuickAccessGrid(
    onOpenWikiUrl: (String) -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val configuredIds = remember { AppPrefs.homeQuickEntryIds }
    val quickEntries = remember(configuredIds) {
        configuredIds.resolveQuickEntries()
    }
    val quickEntryRows = remember(quickEntries) { quickEntries.chunked(3) }

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

private fun List<String>.resolveQuickEntries(): List<QuickEntry> {
    val sourceIds = ifEmpty { defaultQuickEntryIds }
    val selectedEntries = sourceIds.mapNotNull(quickEntryById::get)
    val selectedIds = selectedEntries.mapTo(mutableSetOf()) { it.id }
    val fallbackEntries = allQuickEntries.filterNot { it.id in selectedIds }
    return (selectedEntries + fallbackEntries).take(6)
}

// ────────────────────────────────────────────
//  角色预览区（横向滚动立绘卡片）
// ────────────────────────────────────────────

@Composable
private fun CharacterPreviewSection(
    factions: List<CharacterListApi.FactionData>,
    isLoading: Boolean,
    selectedFaction: Int,
    onSelectedFactionChanged: (Int) -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    onViewAll: () -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val charCardShape = smoothCornerShape(24.dp)
    val btnShape = smoothCornerShape(20.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(
        shape = charCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { charCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.cardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
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
                            onClick = { onSelectedFactionChanged(index) },
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
                val currentFaction = factions.getOrNull(selectedFaction.coerceIn(0, factions.lastIndex))
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
    val cardShape = smoothCornerShape(16.dp)
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.width(90.dp),
        colors = cardColors
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
                    .background(characterGradient),
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
    gameModes: List<GameModeData>,
    isLoading: Boolean,
    selectedMode: Int,
    onSelectedModeChanged: (Int) -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    onViewAll: () -> Unit = {},
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val mapCardShape = smoothCornerShape(24.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(
        shape = mapCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { mapCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.cardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
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
                            onClick = { onSelectedModeChanged(index) },
                            shape = smoothCornerShape(12.dp),
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
                val currentMode = gameModes.getOrNull(selectedMode.coerceIn(0, gameModes.lastIndex))
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
    map: MapInfo,
    onClick: () -> Unit
) {
    val cardShape = smoothCornerShape(16.dp)
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.width(220.dp),
        colors = cardColors
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
                    .background(mapGradient),
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
    Card(
        shape = blockCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { blockCardShape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.cardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )
            hasWallpaper -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = onSurface
            )
            else -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
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

            // 预计算 URL，避免每次重组都调用 URLEncoder
            val resolvedUrls = remember(items) {
                items.map { (display, page) ->
                    display to if (page.startsWith("http")) page
                    else "$WIKI_BASE${URLEncoder.encode(page, "UTF-8").replace("+", "%20")}"
                }
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
                    resolvedUrls.forEach { (display, url) ->
                        val nativeAction = nativePages[display]
                        FilledTonalButton(
                            onClick = nativeAction ?: { onOpenWikiUrl(url) },
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
//  地图一览全屏页（从主页"查看全部"或快捷入口进入）
// ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapListFullScreen(
    onBack: () -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    gameModes: List<GameModeData>,
    isLoading: Boolean,
    initialTab: Int = 0,
    onTabChanged: ((Int) -> Unit)? = null
) {
    var selectedMode by remember { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地图一览", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
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
                                onClick = {
                                    selectedMode = index
                                    onTabChanged?.invoke(index)
                                },
                                shape = smoothCornerShape(12.dp),
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
    map: MapInfo,
    onClick: () -> Unit
) {
    val cardShape = smoothCornerShape(16.dp)
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors
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
                    .background(mapGradient),
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
