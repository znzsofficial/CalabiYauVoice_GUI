package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.ErrorState
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.portraitPrefetchIndices
import com.nekolaska.calabiyau.core.wiki.wikiPortraitRequest
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import data.ApiResult
import util.wikiPathEncode

// ────────────────────────────────────────────
//  Wiki 主页
// ────────────────────────────────────────────

@Composable
internal fun WikiHomePage(
    onOpenDrawer: () -> Unit,
    showDrawerButton: Boolean = true,
    listState: LazyListState,
    characterRowState: LazyListState,
    mapRowState: LazyListState,
    topAppBarState: TopAppBarState,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = topAppBarState)
    val searchField = rememberTextFieldState()
    var showMaps by rememberSaveable(resetKey) { mutableStateOf(false) }

    val viewport = remember { LayoutBoundsHolder() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = { WikiHomeTopBar(scrollBehavior, showDrawerButton, onOpenDrawer) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // LocalHasWallpaper 已由 Hub 层 provide，这里只关 overscroll
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            HomeListViewport(innerPadding) { width ->
                val fontScale = LocalDensity.current.fontScale
                val linkColumns = homeLinkColumns(width, fontScale)
                val shortcutColumns = quickEntryColumns(width, fontScale)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().layoutBounds(viewport),
                    contentPadding = PaddingValues(horizontal = HomeGutter, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "hub_search", contentType = "search") {
                        HubSearchPanel(
                            textFieldState = searchField,
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
                        QuickAccessGrid(onNavigateTo, backdrop, shortcutColumns)
                    }

                    // ── 弦溯战线 Spotlight Hero ──
                    item(key = "extraction_hub", contentType = "spotlight") {
                        ExtractionSpotlightCard(onClick = { onNavigateTo(WikiRoute.ExtractionHub) }, backdrop = backdrop)
                    }

                    // ── 核心数据双列 Bento ──
                    item(key = "data_links", contentType = "direct_grid") {
                        HomeLinkGrid(dataLinks, onNavigateTo, backdrop, linkColumns)
                    }

                    // ── 资料预览画册 ──
                    item(key = HOME_PREVIEW_KEY, contentType = "preview_section") {
                        HomePreviewCard(
                            showMaps = showMaps,
                            onShowMapsChanged = { showMaps = it },
                            backdrop = backdrop,
                            onOpenAll = { onNavigateTo(if (showMaps) WikiRoute.Maps else WikiRoute.Characters) }
                        ) {
                            if (showMaps) {
                                MapPreviewSection(
                                    gameModes, isLoadingMaps, selectedHomeMapMode, onHomeMapModeChanged,
                                    mapRowState, isPageActive, onOpenMapDetail, sharedTransitionScope, animatedVisibilityScope
                                )
                            } else {
                                CharacterPreviewSection(
                                    factions, isLoadingCharacters, selectedHomeFaction, onHomeFactionChanged,
                                    characterRowState, onOpenCharacterDetail, listState, viewport, isPageActive,
                                    sharedTransitionScope, animatedVisibilityScope
                                )
                            }
                        }
                    }

                    // ── 更多资料 2×2 柔光 Bento ──
                    item(key = "hub_grid", contentType = "hub_grid") {
                        HomeCategoryDirectory(onNavigateTo, backdrop, linkColumns)
                    }

                    // ── 底部直达网格 ──
                    item(key = "direct_entries_grid", contentType = "direct_grid") {
                        HomeLinkGrid(directLinks, onNavigateTo, backdrop, linkColumns)
                    }

                    // 底部留白（适配手势导航安全区）
                    item { Spacer(Modifier.navigationBarsPadding().height(AppSpacing.xxLarge)) }
                }
            }
        }
    }
}

private val HomeGutter = 16.dp

/** Resolve breakpoints once for the list, outside the items which enter and leave during scroll. */
@Composable
private fun HomeListViewport(insets: PaddingValues, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val limit = 960.dp
        // Scaffold stores all four paddings together. A changing top-bar height must not recompose the list.
        val sideInsetsPx by remember(insets, density, direction) {
            derivedStateOf {
                with(density) { insets.calculateLeftPadding(direction).roundToPx() + insets.calculateRightPadding(direction).roundToPx() }
            }
        }
        val width = with(density) {
            (minOf(constraints.maxWidth - sideInsetsPx, limit.roundToPx()) - 2 * HomeGutter.roundToPx()).coerceAtLeast(0).toDp()
        }
        Box(Modifier.padding(insets).consumeWindowInsets(insets).widthIn(max = limit).fillMaxSize()) { content(width) }
    }
}

// ────────────────────────────────────────────
//  柔光画册基础原子与视觉层级
// ────────────────────────────────────────────

private enum class HubBadgeTone { Primary, Secondary, Tertiary }

@Composable
private fun HubBadgeTone.resolveColors(alpha: Float = 0.74f): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        HubBadgeTone.Primary -> scheme.primaryContainer.copy(alpha = alpha) to scheme.onPrimaryContainer
        HubBadgeTone.Secondary -> scheme.secondaryContainer.copy(alpha = alpha) to scheme.onSecondaryContainer
        HubBadgeTone.Tertiary -> scheme.tertiaryContainer.copy(alpha = alpha) to scheme.onTertiaryContainer
    }
}

@Composable
private fun HubIconBadge(
    icon: ImageVector,
    size: Dp = 38.dp,
    cornerRadius: Dp = 13.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        shape = smoothCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun HomeFloatingSectionTitle(title: String, modifier: Modifier = Modifier) {
    val hasWallpaper = LocalHasWallpaper.current
    val tone = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val pillModifier = if (hasWallpaper) {
        val backing = remember(tone, ink) { hubReadableTint(tone, ink, 0.72f) }
        modifier
            .background(backing, AppShapes.capsule)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    } else {
        modifier.padding(horizontal = 4.dp)
    }
    HomeSectionTitle(title, pillModifier)
}

@Composable
private fun QuickAccessEditPill(
    onClick: () -> Unit,
    onCard: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val containerColor = remember(colors.secondaryContainer, colors.onSecondaryContainer, onCard, hasWallpaper) {
        if (!onCard && hasWallpaper) {
            hubReadableTint(colors.secondaryContainer, colors.onSecondaryContainer, 0.84f)
        } else {
            colors.secondaryContainer.copy(alpha = 0.76f)
        }
    }
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(AppShapes.capsule)
            .background(containerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "编辑快捷入口" }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.onSecondaryContainer
        )
        Text(
            "编辑",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSecondaryContainer
        )
    }
}

// ────────────────────────────────────────────
//  旗舰「弦溯战线」Spotlight Hero 卡
// ────────────────────────────────────────────

@Composable
private fun ExtractionSpotlightCard(
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    val shape = AppShapes.card
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.44f,
        tone = colors.primaryContainer,
        ink = colors.onPrimaryContainer,
        supportingInk = colors.onPrimaryContainer,
        tintPlainGlass = true
    )
    val subtitleColor = if (hasWallpaper) {
        colors.onPrimaryContainer
    } else {
        colors.onPrimaryContainer.copy(alpha = 0.82f)
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = material.containerColor,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, shape, material)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.screen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            val badgeSurface = remember(colors.surface, colors.primary, hasWallpaper) {
                if (hasWallpaper) hubReadableTint(colors.surface, colors.primary, 0.88f)
                else colors.surface.copy(alpha = 0.88f)
            }
            HubIconBadge(
                icon = Icons.Outlined.Explore,
                size = 42.dp,
                cornerRadius = 14.dp,
                iconSize = 22.dp,
                containerColor = badgeSurface,
                contentColor = colors.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("弦溯战线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("物资、装备、工坊与掉落", style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = subtitleColor
            )
        }
    }
}

// ────────────────────────────────────────────
//  首页分区与目录
// ────────────────────────────────────────────

private data class HomeLink(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: WikiRoute,
    val tone: HubBadgeTone = HubBadgeTone.Primary
)

private val dataLinks = listOf(
    HomeLink("平衡数据", "官网胜率、选取率与 KD", Icons.Outlined.BarChart, WikiRoute.BalanceData, HubBadgeTone.Primary),
    HomeLink("武器一览", "武器分类与数据", Icons.Outlined.GpsFixed, WikiRoute.Weapons, HubBadgeTone.Secondary)
)
private val categoryLinks = listOf(
    HomeLink("玩法与养成", "战斗模式与誓约", Icons.Outlined.Extension, WikiRoute.GameplayHub, HubBadgeTone.Primary),
    HomeLink("外观与图鉴", "道具、时装与枪皮", Icons.Outlined.Inventory2, WikiRoute.CatalogHub, HubBadgeTone.Secondary),
    HomeLink("玩家装饰", "基板、勋章与喷漆", Icons.Outlined.Palette, WikiRoute.DecorationHub, HubBadgeTone.Tertiary),
    HomeLink("游戏延伸", "指南、剧情与影音", Icons.Outlined.MoreHoriz, WikiRoute.ExtensionHub, HubBadgeTone.Primary)
)
private val directLinks = listOf(
    HomeLink("生化卡牌", "图鉴与卡组", Icons.Outlined.Style, WikiRoute.BioCards, HubBadgeTone.Primary),
    HomeLink("活动", "当前与历史", Icons.Outlined.Event, WikiRoute.Activities, HubBadgeTone.Primary),
    HomeLink("公告资讯", "公告与更新", Icons.Outlined.Campaign, WikiRoute.Announcements, HubBadgeTone.Primary),
    HomeLink("完整导航", "Wiki 目录", Icons.Outlined.AccountTree, WikiRoute.Navigation, HubBadgeTone.Primary)
)

@Composable
private fun HomeLinkGrid(
    links: List<HomeLink>,
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop,
    columns: Int,
) {
    HomeLinkRows(links, columns) { link, tile ->
        val (badgeBg, badgeFg) = link.tone.resolveColors(alpha = 0.72f)
        DirectEntryTile(link.title, link.subtitle, link.icon, { onNavigateTo(link.route) }, tile, backdrop,
            compactHorizontal = columns == 1, badgeContainerColor = badgeBg, badgeContentColor = badgeFg)
    }
}

internal fun homeLinkColumns(contentWidth: Dp, fontScale: Float) = if (contentWidth >= 308.dp * fontScale.coerceAtLeast(1f)) 2 else 1

@Composable
internal fun HomeCategoryDirectory(onNavigateTo: (WikiRoute) -> Unit, backdrop: Backdrop, columns: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeFloatingSectionTitle("更多资料")
        HomeLinkRows(categoryLinks, columns) { link, tile ->
            HomeCategoryBentoCard(link, columns == 1, { onNavigateTo(link.route) }, backdrop, tile)
        }
    }
}

@Composable
private fun HomeLinkRows(links: List<HomeLink>, columns: Int, tile: @Composable (HomeLink, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (columns == 1) {
            links.forEach { tile(it, Modifier.fillMaxWidth()) }
        } else {
            remember(links, columns) { links.chunked(columns) }.forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryBentoCard(
    link: HomeLink,
    useHorizontalRow: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val shape = AppShapes.card
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(glassAlpha = 0.28f)
    val (badgeBg, badgeFg) = link.tone.resolveColors(alpha = 0.78f)
    Surface(
        onClick = onClick,
        shape = shape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, shape, material)
    ) {
        if (useHorizontalRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HubIconBadge(
                    icon = link.icon,
                    size = 40.dp,
                    cornerRadius = 14.dp,
                    iconSize = 21.dp,
                    containerColor = badgeBg,
                    contentColor = badgeFg
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        link.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        link.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.52f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HubIconBadge(
                        icon = link.icon,
                        size = 40.dp,
                        cornerRadius = 14.dp,
                        iconSize = 21.dp,
                        containerColor = badgeBg,
                        contentColor = badgeFg
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant.copy(alpha = 0.50f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        link.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        link.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeReadingSurface(backdrop: Backdrop, content: @Composable ColumnScope.() -> Unit) {
    val shape = AppShapes.card
    val material = rememberHubPanelMaterial(glassAlpha = 0.30f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, shape, material),
        shape = shape,
        color = material.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(content = content)
    }
}

// ────────────────────────────────────────────
//  快捷入口网格（2 行 3 列柔光图标入口）
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
    colorStops = arrayOf(
        0f to Color.Transparent,
        0.52f to Color.Black.copy(alpha = 0.34f),
        1f to Color.Black.copy(alpha = 0.74f)
    )
)
private val homeCaptionShadow = Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 3f)

@Composable
private fun DirectEntryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    compactHorizontal: Boolean = false,
    badgeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f),
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val tileShape = AppShapes.card
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(glassAlpha = 0.26f)
    Surface(
        onClick = onClick,
        shape = tileShape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, tileShape, material)
    ) {
        if (compactHorizontal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HubIconBadge(
                    icon = icon,
                    size = 38.dp,
                    cornerRadius = 13.dp,
                    iconSize = 20.dp,
                    containerColor = badgeContainerColor,
                    contentColor = badgeContentColor
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.48f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HubIconBadge(
                        icon = icon,
                        size = 38.dp,
                        cornerRadius = 13.dp,
                        iconSize = 20.dp,
                        containerColor = badgeContainerColor,
                        contentColor = badgeContentColor
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant.copy(alpha = 0.48f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop,
    columns: Int,
) {
    val configuredIds by AppPrefs.homeQuickEntryIdsState.collectAsState()
    val quickEntries = remember(configuredIds) {
        configuredIds.resolveQuickEntries()
    }
    val layout by AppPrefs.homeQuickEntryLayoutState.collectAsState()

    val edit = { onNavigateTo(WikiRoute.QuickEntryEditor) }
    if (layout == AppPrefs.HOME_QUICK_LAYOUT_BUTTONS) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                HomeFloatingSectionTitle("快捷入口")
                QuickAccessEditPill(onClick = edit, onCard = false)
            }
            QuickAccessButtonGrid(quickEntries, columns, onNavigateTo, backdrop)
        }
    } else {
        QuickAccessGridCard(quickEntries, columns, edit, onNavigateTo, backdrop)
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
    onEdit: () -> Unit,
    onNavigateTo: (WikiRoute) -> Unit,
    backdrop: Backdrop
) {
    val quickEntryRows = remember(quickEntries, columns) { quickEntries.chunked(columns) }

    val gridShape = AppShapes.card
    val cellShape = smoothCornerShape(16.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.26f,
        tone = MaterialTheme.colorScheme.surfaceContainerLow,
        supportingInk = contentColor
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, gridShape, material),
        shape = gridShape,
        color = material.containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HomeSectionTitle("快捷入口")
                QuickAccessEditPill(onClick = onEdit, onCard = true)
            }
            quickEntryRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    repeat(columns) { columnIndex ->
                        val entry = row.getOrNull(columnIndex)
                        if (entry != null) {
                            QuickAccessCell(
                                entry = entry,
                                cellShape = cellShape,
                                onClick = { entry.targetRoute?.let(onNavigateTo) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                            )
                        }
                    }
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
    backdrop: Backdrop
) {
    val quickEntryRows = remember(quickEntries, columns) { quickEntries.chunked(columns) }
    val entryShape = smoothCornerShape(20.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.26f,
        tone = MaterialTheme.colorScheme.surfaceContainerLow,
        supportingInk = contentColor
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quickEntryRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { entry ->
                    Surface(
                        onClick = { entry.targetRoute?.let(onNavigateTo) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 78.dp)
                            .semantics { role = Role.Button }
                            .hubPanelGlass(backdrop, entryShape, material),
                        shape = entryShape,
                        color = material.containerColor,
                        contentColor = contentColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            HubIconBadge(
                                icon = entry.icon,
                                size = 38.dp,
                                cornerRadius = 13.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
    cellShape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clip(cellShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HubIconBadge(
            icon = entry.icon,
            size = 38.dp,
            cornerRadius = 13.dp,
            iconSize = 20.dp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            entry.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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
//  角色 / 地图预览画册
// ────────────────────────────────────────────

private const val HOME_PREVIEW_KEY = "home_preview"

@Composable
internal fun HomePreviewCard(
    showMaps: Boolean,
    onShowMapsChanged: (Boolean) -> Unit,
    backdrop: Backdrop,
    onOpenAll: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val actionColor = remember(
        hasWallpaper,
        colors.surfaceContainerLow,
        colors.primary,
        colors.onSurfaceVariant,
        colors.onSurface
    ) {
        if (!hasWallpaper) {
            colors.primary
        } else {
            val primaryNeed = wallpaperInkProtection(colors.surfaceContainerLow, colors.primary)
            val supportingNeed = wallpaperInkProtection(colors.surfaceContainerLow, colors.onSurfaceVariant)
            if (primaryNeed <= supportingNeed) colors.primary else colors.onSurface
        }
    }
    HomeReadingSurface(backdrop) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewModeToggle(
                    showMaps = showMaps,
                    onShowMapsChanged = onShowMapsChanged,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .heightIn(min = 40.dp)
                        .clip(AppShapes.capsule)
                        .clickable(role = Role.Button, onClick = onOpenAll)
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        if (showMaps) "全部地图" else "全部角色",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = actionColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = actionColor
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun PreviewModeToggle(
    showMaps: Boolean,
    onShowMapsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppShapes.capsule,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PreviewTogglePill(
                label = "角色",
                selected = !showMaps,
                onClick = { onShowMapsChanged(false) }
            )
            PreviewTogglePill(
                label = "地图",
                selected = showMaps,
                onClick = { onShowMapsChanged(true) }
            )
        }
    }
}

@Composable
private fun PreviewTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "PreviewToggleBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "PreviewToggleFg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(AppShapes.capsule)
            .background(containerColor)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun factionAccentColor(faction: String): Color? = when (faction) {
    "欧泊" -> Color(0xFF4B77DC)
    "剪刀手" -> Color(0xFFBC3B47)
    "乌尔比诺" -> Color(0xFFDEB639)
    "晶源体" -> Color(0xFF8B5CF6)
    else -> null
}

@Composable
private fun CharacterPreviewSection(
    factions: List<CharacterListApi.FactionData>,
    isLoading: Boolean,
    selectedFaction: Int,
    onSelectedFactionChanged: (Int) -> Unit,
    rowState: LazyListState,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    listState: LazyListState,
    viewport: LayoutBoundsHolder,
    prefetchEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    if (factions.isEmpty()) {
        if (isLoading) ShimmerGroup {
            PreviewFiltersSkeleton()
            LazyRow(
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(4) {
                    ShimmerBox(
                        Modifier
                            .width(94.dp)
                            .aspectRatio(5f / 12f),
                        shape = AppShapes.compactCard
                    )
                }
            }
        } else PreviewMessage("角色资料未加载，可在「全部角色」中重试")
        return
    }

    val currentIndex = selectedFaction.coerceIn(0, factions.lastIndex)
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        factions.forEachIndexed { index, faction ->
            val selected = currentIndex == index
            val accent = factionAccentColor(faction.faction)
            val dotColor = accent ?: colors.primary
            val pillBg = if (selected) {
                accent?.copy(alpha = 0.16f) ?: colors.primaryContainer
            } else {
                colors.surfaceContainerHigh.copy(alpha = 0.72f)
            }
            val pillFg = when {
                !selected -> colors.onSurfaceVariant
                accent != null -> colors.onSurface
                else -> colors.onPrimaryContainer
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(AppShapes.capsule)
                    .background(pillBg)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelectedFactionChanged(index) }
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) dotColor else dotColor.copy(alpha = 0.55f))
                )
                Text(
                    faction.faction,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = pillFg
                )
            }
        }
    }
    val faction = factions[currentIndex]
    if (faction.characters.isEmpty()) {
        PreviewMessage(if (faction.loadError != null) "该阵营加载失败，可进入角色一览重试" else "暂无角色数据")
        return
    }
    val factionColor = factionAccentColor(faction.faction)
    var portraitsVisible by remember { mutableStateOf(false) }
    LazyRow(
        state = rowState,
        modifier = Modifier.onVisibilityChanged(viewportBounds = viewport, minFractionVisible = 0f) { portraitsVisible = it },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(faction.characters, key = { it.name }) { character ->
            CharacterPortraitCard(
                character,
                factionColor,
                { onOpenCharacterDetail(character.name, character.portraitUrl ?: character.imageUrl) },
                sharedTransitionScope,
                animatedVisibilityScope
            )
        }
    }
    HubPortraitPrefetch(prefetchEnabled, sharedTransitionScope, animatedVisibilityScope) {
        if (!canPrefetchHomePortraits(portraitsVisible, listState.isScrollInProgress, rowState.isScrollInProgress)) {
            return@HubPortraitPrefetch emptyList()
        }

        val characters = faction.characters
        val layout = rowState.layoutInfo
        val visible = layout.visibleItemsInfo.mapNotNull { item ->
            item.index.takeIf {
                characters.getOrNull(it)?.name == item.key &&
                    item.offset + item.size > layout.viewportStartOffset && item.offset < layout.viewportEndOffset
            }
        }
        portraitPrefetchIndices(characters.size, visible).map { index ->
            characters[index].let { it.portraitUrl ?: it.imageUrl }
        }
    }
}

internal fun canPrefetchHomePortraits(rowVisible: Boolean, pageScrolling: Boolean, rowScrolling: Boolean) =
    rowVisible && !pageScrolling && !rowScrolling

@Composable
private fun PreviewMessage(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 角色立绘卡片，阵营渐变随名字换行增高。 */
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
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
    val captionGradient = remember(factionBaseColor) {
        val base = factionBaseColor ?: Color.Black
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.45f to base.copy(alpha = 0.42f),
                1f to base.copy(alpha = 0.82f)
            )
        )
    }

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = cardBgColor,
        modifier = Modifier.width(94.dp)
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

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .background(captionGradient)
                    .padding(start = 5.dp, end = 5.dp, top = 14.dp, bottom = 7.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    character.name,
                    style = MaterialTheme.typography.labelSmall.copy(shadow = homeCaptionShadow),
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center
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
    rowState: LazyListState,
    isPageActive: Boolean,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    if (gameModes.isEmpty()) {
        if (isLoading) ShimmerGroup {
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = smoothCornerShape(24.dp)
            )
            LazyRow(
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(2) {
                    ShimmerBox(
                        Modifier
                            .width(226.dp)
                            .aspectRatio(16f / 9f),
                        shape = AppShapes.compactCard
                    )
                }
            }
        } else PreviewMessage("地图资料未加载，可在「全部地图」中重试")
        return
    }

    val currentIndex = selectedMode.coerceIn(0, gameModes.lastIndex)
    MapModePicker(gameModes, currentIndex, isPageActive, onSelectedModeChanged)
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

@Composable
internal fun MapModePicker(modes: List<GameModeData>, selected: Int, isActive: Boolean, onSelect: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val containerColor = remember(colors.surfaceContainerHigh, colors.onSurface, colors.onSurfaceVariant, hasWallpaper) {
        if (hasWallpaper) hubReadableTint(colors.surfaceContainerHigh, colors.onSurface, 0.84f, colors.onSurfaceVariant)
        else colors.surfaceContainerHigh
    }
    Surface(
        onClick = { if (isActive) open = true },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = smoothCornerShape(24.dp),
        color = containerColor,
        contentColor = colors.onSurface
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.primary
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                        append("地图模式 · ")
                    }
                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) {
                        append(modes[selected].displayName)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
    if (open && isActive) {
        var query by rememberSaveable { mutableStateOf("") }
        val matches = remember(modes, query) { matchingMapModes(modes, query) }
        WikiSelectionSheet(
            "地图模式",
            onDismiss = { open = false },
            query = query,
            onQueryChange = { query = it },
            searchPlaceholder = "搜索模式"
        ) {
            if (matches.isEmpty()) item { CatalogEmptyResults(message = "没有匹配的模式", onClear = { query = "" }) }
            items(matches, key = { it.value.templateName }) { (index, mode) ->
                SelectionOptionRow(label = mode.displayName, selected = index == selected, onClick = {
                    onSelect(index)
                    open = false
                })
            }
        }
    }
}

/** Keep the source index: choosing row 0 of a filtered list need not select mode 0. */
internal fun matchingMapModes(modes: List<GameModeData>, query: String): List<IndexedValue<GameModeData>> {
    val term = query.trim()
    return modes.withIndex().filter { (_, mode) -> mode.displayName.contains(term, true) || mode.templateName.contains(term, true) }
}

@Composable
private fun PreviewFiltersSkeleton() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        repeat(4) {
            ShimmerBox(
                Modifier
                    .width(72.dp)
                    .height(34.dp),
                shape = AppShapes.capsule
            )
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
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(226.dp)
    ) {
        Box {
            MapSharedArtwork(map, "home-map-image-${map.name}", sharedTransitionScope, animatedVisibilityScope)
            MapCaption(map.name, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MapCaption(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(mapGradient)
            .padding(start = AppSpacing.large, end = AppSpacing.large, top = AppSpacing.large, bottom = 9.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge.copy(shadow = homeCaptionShadow),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/** Match the image-only detail header. The caption belongs to the card, not the moving overlay. */
@Composable
private fun MapSharedArtwork(
    map: MapInfo,
    key: String,
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
    clipShape: Shape = AppShapes.compactCard
) {
    var failed by remember(map.imageUrl) { mutableStateOf(false) }
    val shared = if (sharedScope != null && visibilityScope != null) with(sharedScope) {
        Modifier.sharedElement(
            rememberSharedContentState(key),
            visibilityScope,
            boundsTransform = { _, _ -> tween(350) },
            clipInOverlayDuringTransition = remember(sharedScope, clipShape) { OverlayClip(clipShape) }
        )
    } else Modifier
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .then(shared)
    ) {
        if (!map.imageUrl.isNullOrBlank() && !failed) {
            AsyncImage(
                model = map.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
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
    }
}

// ────────────────────────────────────────────
//  通用内容块卡片（标题 + FlowRow 胶囊入口）
// ────────────────────────────────────────────

private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

@Composable
internal fun ContentBlockCard(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, String>>,  // displayName to pageName
    onOpenWikiUrl: (String) -> Unit,
    nativePages: Map<String, () -> Unit> = emptyMap(),  // displayName → 原生导航
    backdrop: Backdrop
) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val blockCardShape = AppShapes.card
    val clickableShape = smoothCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(0.30f)
    Surface(
        shape = blockCardShape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, blockCardShape, material)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(clickableShape)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "展开") {
                        expanded = !expanded
                    }
                    .semantics {
                        heading()
                        stateDescription = if (expanded) "已展开" else "已收起"
                    }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HubIconBadge(
                    icon = icon,
                    size = 40.dp,
                    cornerRadius = 14.dp,
                    iconSize = 21.dp,
                    containerColor = colors.primaryContainer.copy(alpha = 0.74f),
                    contentColor = colors.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${items.size} 项资料",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh.copy(alpha = 0.72f),
                    contentColor = colors.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
                            shape = AppShapes.capsule,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.secondaryContainer.copy(alpha = 0.84f),
                                contentColor = colors.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                display,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  动作卡片（聚合二级页直达入口）
// ────────────────────────────────────────────

@Composable
internal fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backdrop: Backdrop
) {
    val actionCardShape = AppShapes.card
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(0.26f)
    Surface(
        onClick = onClick,
        shape = actionCardShape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .hubPanelGlass(backdrop, actionCardShape, material)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HubIconBadge(
                icon = icon,
                size = 42.dp,
                cornerRadius = 14.dp,
                iconSize = 22.dp,
                containerColor = colors.primaryContainer.copy(alpha = 0.74f),
                contentColor = colors.onPrimaryContainer
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant.copy(alpha = 0.56f)
            )
        }
    }
}

// ────────────────────────────────────────────
//  地图一览全屏页（从主页"全部地图"或快捷入口进入）
// ────────────────────────────────────────────

@Composable
internal fun MapListFullScreen(
    onBack: () -> Unit,
    onOpenMapDetail: (name: String, imageUrl: String?) -> Unit,
    gameModes: List<GameModeData>,
    isLoading: Boolean,
    error: ApiResult.Error? = null,
    offlineAge: Long? = null,
    onRetry: () -> Unit = {},
    selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    isPageActive: Boolean = true,
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
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(message = error.message, onRetry = onRetry, kind = error.kind)
                }
            }

            gameModes.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无地图数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    offlineAge?.let { com.nekolaska.calabiyau.core.ui.OfflineBanner(ageMs = it) }
                    error?.let { com.nekolaska.calabiyau.core.ui.WikiListGroupError(it.message, isLoading, onRetry) }
                    Box(
                        Modifier
                            .widthIn(max = MapDirectoryMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium)
                    ) {
                        MapModePicker(gameModes, selectedMode, isPageActive, onTabChanged)
                    }

                    val currentMode = gameModes[selectedMode]
                    if (currentMode.maps.isNotEmpty()) {
                        MapDirectoryGrid(currentMode.maps, onOpenMapDetail, sharedTransitionScope, animatedVisibilityScope)
                    } else if (currentMode.loadError != null) {
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

private val MapDirectoryMaxWidth = 880.dp

@Composable
private fun MapDirectoryGrid(
    maps: List<MapInfo>,
    onOpen: (String, String?) -> Unit,
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
) {
    BoxWithConstraints(
        Modifier
            .widthIn(max = MapDirectoryMaxWidth)
            .fillMaxSize()
            .padding(horizontal = AppSpacing.screen)
    ) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val minTileWidth = 144.dp * fontScale
        val gap = AppSpacing.medium
        val useRows = fontScale >= 1.5f || maxWidth < minTileWidth * 2 + gap
        LazyVerticalGrid(
            columns = if (useRows) GridCells.Fixed(1) else GridCells.Adaptive(minSize = minTileWidth),
            contentPadding = PaddingValues(top = AppSpacing.xSmall, bottom = AppSpacing.xLarge),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(maps, key = { it.name }) { map ->
                MapDirectoryCard(map, useRows, { onOpen(map.name, map.imageUrl) }, sharedScope, visibilityScope)
            }
        }
    }
}

@Composable
private fun MapDirectoryCard(
    map: MapInfo,
    useRow: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val cardShape = if (useRow) smoothCornerShape(20.dp) else AppShapes.compactCard
    val thumbShape = smoothCornerShape(12.dp)
    val containerColor = remember(colors.surfaceContainerLow, colors.onSurface, hasWallpaper) {
        if (hasWallpaper) hubReadableTint(colors.surfaceContainerLow, colors.onSurface, 0.86f)
        else colors.surfaceContainerLow
    }
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = containerColor,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (useRow) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .width(76.dp)
                        .clip(thumbShape)
                ) {
                    MapSharedArtwork(
                        map,
                        "list-map-image-${map.name}",
                        sharedTransitionScope,
                        animatedVisibilityScope,
                        clipShape = thumbShape
                    )
                }
                Text(
                    map.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        } else {
            Box {
                MapSharedArtwork(
                    map,
                    "list-map-image-${map.name}",
                    sharedTransitionScope,
                    animatedVisibilityScope,
                    clipShape = cardShape
                )
                MapCaption(map.name, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
