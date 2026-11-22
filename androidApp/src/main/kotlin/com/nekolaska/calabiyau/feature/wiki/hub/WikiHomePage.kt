package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.wiki.prefetchWikiPortraits
import com.nekolaska.calabiyau.core.wiki.portraitPrefetchIndices
import com.nekolaska.calabiyau.core.wiki.wikiPortraitRequest
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ErrorState
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.LocalWallpaperSeedColor
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.WallpaperSeedColor
import com.nekolaska.calabiyau.core.ui.liquidGlass
import com.nekolaska.calabiyau.core.ui.liquidGlassLight
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import data.ErrorKind
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import util.wikiPathEncode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// ────────────────────────────────────────────
//  Wiki 主页
// ────────────────────────────────────────────

/** 是否有壁纸背景（用于非液态玻璃模式下的半透明卡片） */
internal val LocalHasWallpaper = staticCompositionLocalOf { false }

@Composable
internal fun WikiHomePage(
    onOpenDrawer: () -> Unit,
    showDrawerButton: Boolean = true,
    onOpenWikiUrl: (String) -> Unit,
    listState: LazyListState,
    characterRowState: LazyListState,
    mapRowState: LazyListState,
    topAppBarState: TopAppBarState,
    wallpaperUrl: String?,
    resetKey: Int,
    onNavigateTo: (WikiRoute) -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    factions: List<CharacterListApi.FactionData>,
    isLoadingCharacters: Boolean,
    gameModes: List<GameModeData>,
    isLoadingMaps: Boolean,
    selectedHomeFaction: Int,
    onHomeFactionChanged: (Int) -> Unit,
    selectedHomeMapMode: Int,
    onHomeMapModeChanged: (Int) -> Unit,
    backdrop: Backdrop,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isPageActive: Boolean = true
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(state = topAppBarState)
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    var searchQuery by rememberSaveable(resetKey) { mutableStateOf("") }
    var showMaps by rememberSaveable(resetKey) { mutableStateOf(false) }
    val factionFilterScroll = rememberScrollState()
    val modeFilterScroll = rememberScrollState()

    val wallpaperSeedColor = LocalWallpaperSeedColor.current
    val context = LocalContext.current
    val contentBounds = remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(wallpaperUrl) {
        val color = WallpaperSeedColor.resolve(context, wallpaperUrl)
        if (color != 0) wallpaperSeedColor.intValue = color
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    val topBarScrimAlpha by remember(scrollBehavior, liquidGlassEnabled) {
        derivedStateOf {
            wikiTopBarScrimAlpha(
                progress = scrollBehavior.state.overlappedFraction,
                liquidGlassEnabled = liquidGlassEnabled,
                regularMaxAlpha = 0.78f,
                startThreshold = 0.08f
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("卡拉彼丘 Wiki", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    TextButton(onClick = { onNavigateTo(WikiRoute.Navigation) }) { Text("完整导航") }
                },
                navigationIcon = {
                    if (showDrawerButton) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Outlined.Menu, contentDescription = "菜单")
                        }
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
                    Modifier.wikiTopBarScrim(surfaceColor, topBarScrimAlpha)
                } else Modifier
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // LocalHasWallpaper 已由 Hub 层 provide，这里只关 overscroll
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 960.dp)
                        .fillMaxSize()
                        .onGloballyPositioned { contentBounds.value = it.boundsInWindow() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "hub_search", contentType = "search") {
                        HubSearchPanel(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onNavigateTo = onNavigateTo,
                            factions = factions,
                            gameModes = gameModes,
                             isIndexLoading = isLoadingCharacters || isLoadingMaps,
                             isPageActive = isPageActive,
                            backdrop = backdrop
                        )
                    }

                    // ── 快捷入口 ──
                    item(key = "quick_access", contentType = "grid") {
                        Column {
                            HomeSectionHeader("我的快捷入口", "编辑", "编辑我的快捷入口") { onNavigateTo(WikiRoute.QuickEntryEditor) }
                            QuickAccessGrid(
                                onNavigateTo = onNavigateTo,
                                backdrop = backdrop
                            )
                        }
                    }

                    item(key = "data_links", contentType = "direct_grid") {
                        HomeLinkGrid(dataLinks, onNavigateTo, backdrop)
                    }

                    item(key = "extraction_hub", contentType = "action_card") {
                        DirectEntryTile(
                            title = "弦溯战线",
                            subtitle = "物资、装备与掉落",
                            icon = Icons.Outlined.Explore,
                            onClick = { onNavigateTo(WikiRoute.ExtractionHub) },
                            backdrop = backdrop
                        )
                    }

                    item(key = HOME_PREVIEW_KEY, contentType = "preview_section") {
                        Column {
                            HomeSectionHeader("资料预览", if (showMaps) "全部地图" else "全部角色", onAction = {
                                onNavigateTo(if (showMaps) WikiRoute.Maps else WikiRoute.Characters)
                            })
                            HomePreviewCard(showMaps, { showMaps = it }, backdrop) {
                                if (showMaps) {
                                    MapPreviewSection(
                                        gameModes, isLoadingMaps, selectedHomeMapMode, onHomeMapModeChanged,
                                        mapRowState, modeFilterScroll, onOpenMapDetail,
                                        sharedTransitionScope, animatedVisibilityScope
                                    )
                                } else {
                                    CharacterPreviewSection(
                                        factions, isLoadingCharacters, selectedHomeFaction, onHomeFactionChanged,
                                        characterRowState, factionFilterScroll, onOpenCharacterDetail,
                                         listState, contentBounds, isPageActive, sharedTransitionScope, animatedVisibilityScope
                                    )
                                }
                            }
                        }
                    }

                    item(key = "hub_grid", contentType = "hub_grid") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeSectionTitle("更多资料", "玩法、图鉴与游戏内容分类")
                            HomeLinkGrid(categoryLinks, onNavigateTo, backdrop, categoryCards = true)
                        }
                    }

                    item(key = "direct_entries_grid", contentType = "direct_grid") {
                        HomeLinkGrid(directLinks, onNavigateTo, backdrop)
                    }

                    // 底部留白（适配手势导航安全区）
                    item { Spacer(Modifier.navigationBarsPadding().height(AppSpacing.xxLarge)) }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  聚合入口卡（通向 WikiAggregateScreens 的二级菜单页）
//  用浅色图标底 + 主色描边 + 竖排瓦片，与直连内容的行卡区分
// ────────────────────────────────────────────

@Composable
private fun HomeSectionTitle(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!description.isNullOrBlank()) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, action: String, actionDescription: String = action, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { HomeSectionTitle(title) }
        TextButton(onClick = onAction) {
            Text(action, Modifier.clearAndSetSemantics { contentDescription = actionDescription })
        }
    }
}

private data class HomeLink(val title: String, val subtitle: String, val icon: ImageVector, val route: WikiRoute)

private val dataLinks = listOf(
    HomeLink("平衡数据", "官网胜率、选取率与 KD", Icons.Outlined.BarChart, WikiRoute.BalanceData),
    HomeLink("武器一览", "武器分类与数据", Icons.Outlined.GpsFixed, WikiRoute.Weapons)
)
private val categoryLinks = listOf(
    HomeLink("玩法与养成", "角色培养、玩法系统与移动端内容", Icons.Outlined.Extension, WikiRoute.GameplayHub),
    HomeLink("外观与图鉴", "道具图鉴、时装筛选与武器外观", Icons.Outlined.Inventory2, WikiRoute.CatalogHub),
    HomeLink("玩家装饰", "基板、封装、勋章、喷漆等外观装饰", Icons.Outlined.Palette, WikiRoute.DecorationHub),
    HomeLink("游戏延伸", "实用指南、剧情、历史与影音资料", Icons.Outlined.MoreHoriz, WikiRoute.ExtensionHub)
)
private val directLinks = listOf(
    HomeLink("生化卡牌", "图鉴与卡组", Icons.Outlined.Style, WikiRoute.BioCards),
    HomeLink("活动", "当前与历史", Icons.Outlined.Event, WikiRoute.Activities),
    HomeLink("公告资讯", "公告与更新", Icons.Outlined.Campaign, WikiRoute.Announcements)
)

@Composable
private fun HomeLinkGrid(
    links: List<HomeLink>,
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop,
    categoryCards: Boolean = false
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = if (maxWidth >= 308.dp * fontScale) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { link ->
                        val tile = Modifier.weight(1f).fillMaxHeight()
                        val open = { onNavigateTo(link.route) }
                        if (categoryCards) {
                            HubCard(link.title, link.subtitle, link.icon, open, tile, backdrop)
                        } else {
                            DirectEntryTile(link.title, link.subtitle, link.icon, open, tile, backdrop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val hubCardShape = AppShapes.card
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        shape = hubCardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                liquidGlass -> Color.Transparent
                hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { hubCardShape },
                surfaceAlpha = 0.25f
            )
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────
//  快捷入口网格（2 行 3 列圆角图标按钮）
// ────────────────────────────────────────────

/** 快捷入口数据（提取到顶层避免每次重组分配） */
internal data class QuickEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val targetRoute: WikiRoute?
)

internal val allQuickEntries = listOf(
    QuickEntry("characters", "角色", Icons.Outlined.People, WikiRoute.Characters),
    QuickEntry("weapons", "武器", Icons.Outlined.GpsFixed, WikiRoute.Weapons),
    QuickEntry("maps", "地图", Icons.Outlined.Map, WikiRoute.Maps),
    QuickEntry("items", "PC 图鉴", Icons.Outlined.Inventory2, WikiRoute.Items),
    QuickEntry("mobile_items", "移动端图鉴", Icons.Outlined.Inventory2, WikiRoute.MobileItems),
    QuickEntry("voting", "投票", Icons.Outlined.HowToVote, WikiRoute.Voting),
    QuickEntry("costumes", "时装", Icons.Outlined.Checkroom, WikiRoute.Costumes()),
    QuickEntry("weapon_skins", "枪皮", Icons.Outlined.Palette, WikiRoute.WeaponSkins()),
    QuickEntry("bio_cards", "卡牌", Icons.Outlined.Style, WikiRoute.BioCards),
    QuickEntry("activities", "活动", Icons.Outlined.Event, WikiRoute.Activities),
    QuickEntry("announcements", "公告", Icons.Outlined.Campaign, WikiRoute.Announcements),
    QuickEntry("oath", "誓约", Icons.Outlined.FavoriteBorder, WikiRoute.Oath),
    QuickEntry("imprints", "印迹", Icons.Outlined.MilitaryTech, WikiRoute.Imprints),
    QuickEntry("memes", "梗百科", Icons.Outlined.AutoAwesome, WikiRoute.Memes),
    QuickEntry("collaborations", "联动", Icons.Outlined.Handshake, WikiRoute.Collaborations),
    QuickEntry("bgm", "BGM", Icons.Outlined.MusicNote, WikiRoute.Bgm),
    QuickEntry("balance_data", "平衡", Icons.Outlined.BarChart, WikiRoute.BalanceData),
    QuickEntry("game_modes", "玩法", Icons.Outlined.Extension, WikiRoute.GameplayHub),
    QuickEntry("extraction_hub", "弦溯战线", Icons.Outlined.Explore, WikiRoute.ExtractionHub),
    QuickEntry("decorations", "装饰", Icons.Outlined.Palette, WikiRoute.DecorationHub),
    QuickEntry("extension_hub", "延伸", Icons.Outlined.MoreHoriz, WikiRoute.ExtensionHub),
    QuickEntry("wallpapers", "壁纸", Icons.Outlined.Wallpaper, WikiRoute.Wallpapers),
    QuickEntry("navigation", "导航", Icons.Outlined.AccountTree, WikiRoute.Navigation),
)
internal val defaultQuickEntryIds = listOf(
    "characters", "weapons", "maps", "voting", "costumes", "bio_cards"
)
internal val quickEntryById = allQuickEntries.associateBy(QuickEntry::id)

private val mapGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
)

@Composable
private fun DirectEntryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val tileShape = AppShapes.card
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        shape = tileShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                liquidGlass -> Color.Transparent
                hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = contentColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { tileShape },
                surfaceAlpha = 0.25f
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val configuredIds by AppPrefs.homeQuickEntryIdsState.collectAsState()
    val quickEntries = remember(configuredIds) {
        configuredIds.resolveQuickEntries()
    }
    val layout by AppPrefs.homeQuickEntryLayoutState.collectAsState()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = quickEntryColumns(maxWidth, LocalDensity.current.fontScale)
        if (layout == AppPrefs.HOME_QUICK_LAYOUT_BUTTONS) {
            QuickAccessButtonGrid(
                quickEntries = quickEntries,
                columns = columns,
                onNavigateTo = onNavigateTo,
                backdrop = backdrop
            )
        } else {
            QuickAccessGridCard(
                quickEntries = quickEntries,
                columns = columns,
                onNavigateTo = onNavigateTo,
                backdrop = backdrop
            )
        }
    }
}

internal fun quickEntryColumns(maxWidth: Dp, fontScale: Float): Int {
    val cellWidth = 66.dp * fontScale.coerceAtLeast(1f) + 12.dp
    return when {
        maxWidth >= maxOf(600.dp, cellWidth * 6) -> 6
        maxWidth >= cellWidth * 3 -> 3
        maxWidth >= cellWidth * 2 -> 2
        else -> 1
    }
}

@Composable
private fun QuickAccessGridCard(
    quickEntries: List<QuickEntry>,
    columns: Int,
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val quickEntryRows = remember(quickEntries, columns) { quickEntries.chunked(columns) }

    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val gridShape = AppShapes.card
    val cellShape = smoothCornerShape(18.dp)
    val surfaceColor = when {
        liquidGlass -> Color.Transparent
        hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val iconTint = MaterialTheme.colorScheme.primary
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (liquidGlass) 0.22f else 0.42f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { gridShape },
                blurRadius = 2.dp,
                lensHeight = 12.dp,
                lensAmount = 20.dp,
                surfaceAlpha = if (liquidGlass) 0.22f else 0.35f
            ),
        shape = gridShape,
        color = surfaceColor,
        contentColor = contentColor
    ) {
        Column(Modifier.padding(6.dp)) {
            quickEntryRows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    repeat(columns) { columnIndex ->
                        val entry = row.getOrNull(columnIndex)
                        if (entry != null) {
                            QuickAccessCell(
                                entry = entry,
                                iconTint = iconTint,
                                cellShape = cellShape,
                                onClick = { entry.targetRoute?.let(onNavigateTo) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            )
                        }
                        if (columnIndex < columns - 1) {
                            VerticalDivider(
                                modifier = Modifier.height(44.dp),
                                color = dividerColor
                            )
                        }
                    }
                }
                if (rowIndex < quickEntryRows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = dividerColor
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessButtonGrid(
    quickEntries: List<QuickEntry>,
    columns: Int,
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val quickEntryRows = remember(quickEntries, columns) { quickEntries.chunked(columns) }
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val entryShape = smoothCornerShape(20.dp)
    val surfaceColor = when {
        liquidGlass -> Color.Transparent
        hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
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
                        onClick = { entry.targetRoute?.let(onNavigateTo) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp)
                            .semantics { role = Role.Button }
                            .liquidGlassLight(
                                backdrop = backdrop,
                                shape = { entryShape }
                            ),
                        shape = entryShape,
                        color = surfaceColor,
                        contentColor = contentColor
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
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
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickAccessCell(
    entry: QuickEntry,
    iconTint: Color,
    cellShape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .padding(3.dp)
            .clip(cellShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
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
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

internal fun List<String>.resolveQuickEntries(): List<QuickEntry> {
    val selected = distinct().mapNotNull(quickEntryById::get)
        .ifEmpty { defaultQuickEntryIds.map(quickEntryById::getValue) }
    // Old partial configurations filled spare slots in catalog order. The new topic is opt-in.
    return (selected + allQuickEntries.filterNot { it.targetRoute == WikiRoute.ExtractionHub })
        .distinctBy(QuickEntry::id).take(6)
}

// ────────────────────────────────────────────
//  角色预览区（横向滚动立绘卡片）
// ────────────────────────────────────────────

private const val HOME_PREVIEW_KEY = "home_preview"

@Composable
private fun HomePreviewCard(
    showMaps: Boolean,
    onShowMapsChanged: (Boolean) -> Unit,
    backdrop: Backdrop,
    content: @Composable () -> Unit
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current

    val shape = AppShapes.card
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { shape },
                surfaceAlpha = 0.3f
            ),
        colors = when {
            liquidGlass -> CardDefaults.cardColors(
                containerColor = Color.Transparent, contentColor = onSurface
            )

            hasWallpaper -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
                contentColor = onSurface
            )

            else -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                repeat(2) { index ->
                    val maps = index == 1
                    SegmentedButton(
                        selected = showMaps == maps,
                        onClick = { onShowMapsChanged(maps) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(if (maps) "地图" else "角色") }
                }
            }
            content()
        }
    }
}

@Composable
private fun CharacterPreviewSection(
    factions: List<CharacterListApi.FactionData>,
    isLoading: Boolean,
    selectedFaction: Int,
    onSelectedFactionChanged: (Int) -> Unit,
    rowState: LazyListState,
    filterScroll: ScrollState,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    listState: LazyListState,
    contentBounds: State<Rect>,
    prefetchEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    if (factions.isEmpty()) {
        if (isLoading) {
            ShimmerBox(Modifier.fillMaxWidth().height(40.dp), shape = AppShapes.capsule)
            LazyRow(userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(4) { ShimmerBox(Modifier.width(90.dp).aspectRatio(5f / 12f), shape = AppShapes.compactCard) }
            }
        } else PreviewMessage("加载失败")
        return
    }

    val currentIndex = selectedFaction.coerceIn(0, factions.lastIndex)
    Row(Modifier.fillMaxWidth().horizontalScroll(filterScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        factions.forEachIndexed { index, faction ->
            FilterChip(selected = currentIndex == index, onClick = { onSelectedFactionChanged(index) },
                label = { Text(faction.faction, style = MaterialTheme.typography.labelMedium) })
        }
    }
    val faction = factions[currentIndex]
    if (faction.characters.isEmpty()) {
        PreviewMessage(if (faction.loadError != null) "该阵营加载失败，可进入角色一览重试" else "暂无角色数据")
        return
    }
    val factionColor = when (faction.faction) {
        "欧泊" -> Color(0xFF4B77DC)
        "剪刀手" -> Color(0xFFBC3B47)
        "乌尔比诺" -> Color(0xFFDEB639)
        "晶源体" -> Color(0xFF8B5CF6)
        else -> null
    }
    val portraitBounds = remember(faction.faction) { mutableStateOf(Rect.Zero) }
    LazyRow(
        state = rowState,
        modifier = Modifier.onGloballyPositioned { portraitBounds.value = it.boundsInWindow() },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(faction.characters, key = { it.name }) { character ->
            CharacterPortraitCard(
                character, factionColor, { onOpenCharacterDetail(character.name, character.portraitUrl ?: character.imageUrl) },
                sharedTransitionScope, animatedVisibilityScope
            )
        }
    }
    if (prefetchEnabled) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val routeTransition = animatedVisibilityScope?.transition
        val characters = faction.characters
        LaunchedEffect(characters, listState, rowState, portraitBounds, lifecycle, routeTransition, sharedTransitionScope, context) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow {
                    if (listState.isScrollInProgress || rowState.isScrollInProgress ||
                        listState.layoutInfo.visibleItemsInfo.none { it.key == HOME_PREVIEW_KEY } ||
                        !isSettledPortraitViewport(
                            viewport = contentBounds.value,
                            portraits = portraitBounds.value,
                            current = routeTransition?.currentState,
                            target = routeTransition?.targetState,
                            animating = routeTransition?.isRunning == true || sharedTransitionScope?.isTransitionActive == true
                        )
                    ) return@snapshotFlow emptyList()

                    val layout = rowState.layoutInfo
                    val visible = layout.visibleItemsInfo.filter { item ->
                        characters.getOrNull(item.index)?.name == item.key &&
                            item.offset + item.size > layout.viewportStartOffset && item.offset < layout.viewportEndOffset
                    }.map { it.index }
                    portraitPrefetchIndices(characters.size, visible).map { index ->
                        characters[index].let { it.portraitUrl ?: it.imageUrl }
                    }
                }.collectLatest { urls ->
                    if (urls.isNotEmpty()) {
                        delay(160)
                        SingletonImageLoader.get(context).prefetchWikiPortraits(context, urls)
                    }
                }
            }
        }
    }
}

// AnimatedContent retains outgoing and predictive-back pages with valid old layout bounds.
internal fun isSettledPortraitViewport(
    viewport: Rect, portraits: Rect, current: EnterExitState?, target: EnterExitState?, animating: Boolean
): Boolean {
    val stableRoute = (current == null && target == null) ||
        (current == EnterExitState.Visible && target == EnterExitState.Visible)
    return stableRoute && !animating && !viewport.isEmpty && !portraits.isEmpty && viewport.overlaps(portraits)
}

@Composable
private fun PreviewMessage(message: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 角色立绘卡片，底部色带随名字换行增高。 */
@Composable
private fun CharacterPortraitCard(
    character: CharacterListApi.CharacterInfo,
    factionBaseColor: Color? = null,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val portraitRequest = remember(context, character.portraitUrl, character.imageUrl) {
        wikiPortraitRequest(context, character.portraitUrl ?: character.imageUrl, crossfade = false)
    }
    val cardShape = AppShapes.compactCard
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
    val captionColor = lerp(Color.Black, factionBaseColor ?: Color.Black, 0.18f).copy(alpha = 0.88f)

    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.width(90.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        )
    ) {
        Box {
            // 立绘图片：使用原始比例 (280x680 ≈ 5:12)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 12f)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "home-char-image-${character.name}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(350) }
                                )
                            }
                        } else Modifier
                    )
            ) {
                AsyncImage(
                    model = portraitRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.85f),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = character.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(captionColor)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
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
    rowState: LazyListState,
    filterScroll: ScrollState,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    if (gameModes.isEmpty()) {
        if (isLoading) {
            LazyRow(userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(4) { ShimmerBox(Modifier.width(68.dp).height(32.dp), shape = AppShapes.chip) }
            }
            LazyRow(userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(2) { ShimmerBox(Modifier.width(220.dp).aspectRatio(16f / 9f), shape = AppShapes.compactCard) }
            }
        } else PreviewMessage("加载失败")
        return
    }

    val currentIndex = selectedMode.coerceIn(0, gameModes.lastIndex)
    Row(Modifier.fillMaxWidth().horizontalScroll(filterScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        gameModes.forEachIndexed { index, mode ->
            FilterChip(selected = currentIndex == index, onClick = { onSelectedModeChanged(index) },
                label = { Text(mode.displayName, style = MaterialTheme.typography.labelMedium) })
        }
    }
    val mode = gameModes[currentIndex]
    if (mode.maps.isEmpty()) {
        PreviewMessage(if (mode.loadError != null) "该模式加载失败，可进入地图一览重试" else "暂无地图数据")
        return
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(mode.maps, key = { it.name }) { map ->
            MapCard(map, { onOpenMapDetail(map.name, map.imageUrl) }, sharedTransitionScope, animatedVisibilityScope)
        }
    }
}

/** 首页地图卡片，16:9 图片与可换行的名称。 */
@Composable
private fun MapCard(
    map: MapInfo,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val cardShape = AppShapes.compactCard
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.width(220.dp)
            .then(
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "home-map-image-${map.name}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(350) }
                        )
                    }
                } else Modifier
            ),
        colors = cardColors
    ) {
        var imageFailed by remember(map.imageUrl) { mutableStateOf(false) }
        Box {
            if (!map.imageUrl.isNullOrBlank() && !imageFailed) {
                AsyncImage(
                    model = map.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = map.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ────────────────────────────────────────────
//  通用内容块卡片（标题 + FlowRow 标签）
// ────────────────────────────────────────────

private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

@Composable
internal fun ContentBlockCard(
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

    val blockCardShape = AppShapes.card
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
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
                    else "$WIKI_BASE${page.wikiPathEncode()}"
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(220)) + expandVertically(tween(250, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing))
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
internal fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backdrop: Backdrop = emptyBackdrop()
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val actionCardShape = AppShapes.card
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        shape = actionCardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                liquidGlass -> Color.Transparent
                hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = contentColor
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
                shape = AppShapes.capsule,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────
//  地图一览全屏页（从主页"查看全部"或快捷入口进入）
// ────────────────────────────────────────────

@Composable
internal fun MapListFullScreen(
    onBack: () -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    gameModes: List<GameModeData>,
    isLoading: Boolean,
    error: String? = null,
    offlineAge: Long? = null,
    onRetry: () -> Unit = {},
    selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val selectedMode = selectedTab.coerceIn(0, gameModes.lastIndex.coerceAtLeast(0))
    val hasWallpaper = LocalHasWallpaper.current
    val translucentSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

    Scaffold(
        containerColor = if (hasWallpaper) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("地图一览", fontWeight = FontWeight.Bold) },
                actions = { com.nekolaska.calabiyau.core.ui.RefreshActionButton(onClick = onRetry, enabled = !isLoading) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                colors = if (hasWallpaper) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = translucentSurface,
                        scrolledContainerColor = translucentSurface
                    )
                } else TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { innerPadding ->
        when {
            isLoading && gameModes.isEmpty() -> {
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

            error != null && gameModes.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(message = error, onRetry = onRetry, kind = ErrorKind.NETWORK)
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
                    offlineAge?.let { com.nekolaska.calabiyau.core.ui.OfflineBanner(ageMs = it) }
                    error?.let { com.nekolaska.calabiyau.core.ui.WikiListGroupError(it, isLoading, onRetry) }
                    Surface(color = if (hasWallpaper) translucentSurface else MaterialTheme.colorScheme.surface) {
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
                                     onClick = { onTabChanged(index) },
                                    shape = smoothCornerShape(12.dp),
                                    label = { Text(mode.displayName, maxLines = 1) }
                                )
                            }
                        }
                    }

                    // 地图网格
                    val currentMode = gameModes.getOrNull(selectedMode.coerceIn(0, gameModes.lastIndex.coerceAtLeast(0)))
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
                                    onClick = { onOpenMapDetail(map.name, map.imageUrl) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    } else if (currentMode?.loadError != null) {
                        com.nekolaska.calabiyau.core.ui.WikiListGroupError(currentMode.loadError, isLoading, onRetry)
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
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val cardShape = AppShapes.compactCard
    val hasWallpaper = LocalHasWallpaper.current
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (hasWallpaper) 0.86f else 1f)
    )
    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
            .then(
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "list-map-image-${map.name}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(350) }
                        )
                    }
                } else Modifier
            ),
        colors = cardColors
    ) {
        var imageFailed by remember(map.imageUrl) { mutableStateOf(false) }
        Box {
            if (!map.imageUrl.isNullOrBlank() && !imageFailed) {
                AsyncImage(
                    model = map.imageUrl,
                    contentDescription = map.name,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
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
