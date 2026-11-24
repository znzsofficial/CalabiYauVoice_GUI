package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.beginner.BeginnerGuideScreen
import com.nekolaska.calabiyau.feature.wiki.mechanics.MechanicsScreen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.SeekablePredictiveBackHandler
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberPredictiveBackTransition
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterScreen
import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailScreen
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListScreen
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailScreen
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListScreen
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterScreen
import com.nekolaska.calabiyau.feature.wiki.achievement.AchievementScreen
import com.nekolaska.calabiyau.feature.wiki.activity.ActivityScreen
import com.nekolaska.calabiyau.feature.wiki.announcement.AnnouncementScreen
import com.nekolaska.calabiyau.feature.wiki.balance.BalanceDataScreen
import com.nekolaska.calabiyau.feature.wiki.bgm.BgmScreen
import com.nekolaska.calabiyau.feature.wiki.bio.BioCardScreen
import com.nekolaska.calabiyau.feature.wiki.collaboration.CollaborationScreen
import com.nekolaska.calabiyau.feature.wiki.decoration.PlayerDecorationScreen
import com.nekolaska.calabiyau.feature.wiki.gallery.GalleryScreen
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.wiki.game.GameModeScreen
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.extraction.ExtractionCatalogScreen
import com.nekolaska.calabiyau.feature.settings.QuickEntryEditorScreen
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.EquipmentKind
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.ExtractionEquipmentScreen
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.ExtractionWorkshopScreen
import com.nekolaska.calabiyau.feature.wiki.extraction.ExtractionDropsScreen
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropTask
import com.nekolaska.calabiyau.feature.wiki.history.GameHistoryScreen
import com.nekolaska.calabiyau.feature.wiki.imprint.ImprintScreen
import com.nekolaska.calabiyau.feature.wiki.interactionitem.InteractionItemScreen
import com.nekolaska.calabiyau.feature.wiki.item.ItemCatalogScreen
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemCatalogPlatform
import com.nekolaska.calabiyau.feature.wiki.map.MapDetailScreen
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.meme.MemeScreen
import com.nekolaska.calabiyau.feature.wiki.meow.MeowLanguageScreen
import com.nekolaska.calabiyau.feature.wiki.navigation.NavigationMenuScreen
import com.nekolaska.calabiyau.feature.wiki.oath.OathScreen
import com.nekolaska.calabiyau.feature.wiki.playerlevel.PlayerLevelScreen
import com.nekolaska.calabiyau.feature.wiki.story.StoryScreen
import com.nekolaska.calabiyau.feature.wiki.submission.SubmissionScreen
import com.nekolaska.calabiyau.feature.wiki.stringer.StringerPushCardScreen
import com.nekolaska.calabiyau.feature.wiki.stringer.StringerTalentScreen
import com.nekolaska.calabiyau.feature.wiki.tips.GameTipsScreen
import com.nekolaska.calabiyau.feature.wiki.gamesettings.PcGameSettingsScreen
import com.nekolaska.calabiyau.feature.wiki.voting.VotingScreen

// ════════════════════════════════════════════════════════
//  Wiki 主页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private val lazyListStateSaver = listSaver<LazyListState, Int>(
    save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
    restore = { values ->
        LazyListState(
            firstVisibleItemIndex = values.getOrNull(0) ?: 0,
            firstVisibleItemScrollOffset = values.getOrNull(1) ?: 0
        )
    }
)

private object WikiHubWallpaperState {
    private var displayedImageCacheKey: String? = null

    @Synchronized
    fun imageCacheKey(imageUrl: String): String = "wiki-hub-wallpaper:${imageUrl.hashCode()}"

    @Synchronized
    fun placeholderCacheKey(nextImageCacheKey: String): String? {
        return displayedImageCacheKey?.takeIf { it != nextImageCacheKey }
    }

    @Synchronized
    fun markImageDisplayed(imageCacheKey: String) {
        displayedImageCacheKey = imageCacheKey
    }

}

/**
 * Hub 列表数据快照已移至独立文件 [HubListDataSnapshot]（internal，搜索索引共用）。
 */

@Composable
private fun WikiHubWallpaperBackground(
    wallpaperUrl: String?,
    liquidGlassEnabled: Boolean,
    backdrop: LayerBackdrop?,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val wallpaperRequest = remember(context, wallpaperUrl) {
        wallpaperUrl?.takeIf { it.isNotBlank() }?.let { url ->
            val cacheKey = WikiHubWallpaperState.imageCacheKey(url)
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .apply {
                    WikiHubWallpaperState.placeholderCacheKey(cacheKey)?.let { placeholderMemoryCacheKey(it) }
                }
                .build()
        }
    }
    val wallpaperOverlayBrush = remember(liquidGlassEnabled, surfaceColor) {
        if (liquidGlassEnabled) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to surfaceColor.copy(alpha = 0.18f),
                    0.42f to surfaceColor.copy(alpha = 0.18f),
                    0.72f to surfaceColor.copy(alpha = 0.6f),
                    1f to surfaceColor.copy(alpha = 0.86f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    surfaceColor.copy(alpha = 0.15f),
                    surfaceColor.copy(alpha = 0.5f),
                    surfaceColor.copy(alpha = 0.8f)
                )
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop)
                else Modifier
            )
            // Record an opaque theme base even while Coil is loading, or if the wallpaper fails.
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (wallpaperRequest != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = wallpaperRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        wallpaperUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            WikiHubWallpaperState.markImageDisplayed(WikiHubWallpaperState.imageCacheKey(url))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(wallpaperOverlayBrush)
                )
            }
        }
    }
}

@Composable
fun WikiHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    showDrawerButton: Boolean = true,
    isOverlaid: Boolean = false,
    initialPage: WikiRoute = WikiRoute.Home,
    resetKey: Int = 0
) {
    var backStack by rememberSaveable(resetKey, stateSaver = wikiRouteStackSaver) {
        mutableStateOf(listOf(initialPage))
    }
    val currentRoute = backStack.lastOrNull() ?: WikiRoute.Home

    val homeListState = rememberSaveable(resetKey, saver = lazyListStateSaver) { LazyListState() }
    val homeCharacterRowState = rememberSaveable(resetKey, saver = lazyListStateSaver) { LazyListState() }
    val homeMapRowState = rememberSaveable(resetKey, saver = lazyListStateSaver) { LazyListState() }
    val homeTopAppBarState = rememberSaveable(resetKey, saver = TopAppBarState.Saver) {
        TopAppBarState(initialHeightOffsetLimit = -Float.MAX_VALUE, initialHeightOffset = 0f, initialContentOffset = 0f)
    }

    // Tab/滚动状态跟随 resetKey 重置：backStack 清空后残留的越界 tab 会命中空阵营/空模式，
    // 下游消费点无法安全 coerce（数据量是动态的）
    var homeFactionTab by rememberSaveable(resetKey) { mutableIntStateOf(0) }
    var homeMapModeTab by rememberSaveable(resetKey) { mutableIntStateOf(0) }
    var characterListTab by rememberSaveable(resetKey) { mutableIntStateOf(0) }
    val characterGridState = rememberSaveable(resetKey, saver = LazyGridState.Saver) { LazyGridState() }
    var weaponListTab by rememberSaveable(resetKey) { mutableIntStateOf(0) }
    val weaponGridState = rememberSaveable(resetKey, saver = LazyGridState.Saver) { LazyGridState() }
    var mapListTab by rememberSaveable(resetKey) { mutableIntStateOf(0) }

    // ── 数据缓存（提升到此层级，子页面切换不丢失） ──
    // 快照单例：主级页面切换（留言板等）会销毁整个 Hub 分支，重建时从这里同步恢复
    // 已加载数据——首帧直接渲染内容，不再转圈
    val restoredFactions = HubListDataSnapshot.factions
    val restoredModes = HubListDataSnapshot.gameModes
    val restoredWeapons = HubListDataSnapshot.weaponCategories
    val snapshotGeneration = HubListDataSnapshot.generation
    // 角色列表 state 提升到 Hub：从角色详情返回时数据仍非空，
    // 列表直接渲染真实卡片，共享元素动画不被骨架屏抢占
    val shouldLoadCharacterList = backStack.any {
        it is WikiRoute.Characters || it is WikiRoute.Home || it is WikiRoute.WeaponDetail
    }
    val characterState = rememberLoadState(
        initial = restoredFactions ?: emptyList<CharacterListApi.FactionData>(),
        key = snapshotGeneration,
        enabled = shouldLoadCharacterList,
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { CharacterListApi.fetchAllFactions(cacheOnly = true) },
        fetch = { force -> HubListDataSnapshot.capture("factions",
            { CharacterListApi.fetchAllFactions(forceRefresh = force, allowMemoryCache = false) },
            { it.isNotEmpty() && it.all { group -> group.loadError == null } },
            { HubListDataSnapshot.factions = it }) },
        initiallyLoaded = restoredFactions != null,
        reloadOnEnter = restoredFactions == null
    )
    val mapState = rememberLoadState(
        initial = restoredModes ?: emptyList<GameModeData>(),
        key = snapshotGeneration,
        enabled = backStack.any { it is WikiRoute.Home || it is WikiRoute.Maps || it is WikiRoute.ModeMaps || it is WikiRoute.GameModes || it is WikiRoute.Navigation },
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { MapListApi.fetchAllModes(cacheOnly = true) },
        fetch = { force -> HubListDataSnapshot.capture("maps",
            { MapListApi.fetchAllModes(forceRefresh = force, allowMemoryCache = false) },
            { it.isNotEmpty() && it.all { group -> group.loadError == null } },
            { HubListDataSnapshot.gameModes = it }) },
        initiallyLoaded = restoredModes != null,
        reloadOnEnter = restoredModes == null
    )
    val shouldLoadWeaponList = backStack.any { it is WikiRoute.Weapons }
    val weaponState = rememberLoadState(
        initial = restoredWeapons ?: emptyList<WeaponListApi.WeaponCategoryData>(),
        key = snapshotGeneration,
        enabled = shouldLoadWeaponList,
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { WeaponListApi.fetchAllCategories(cacheOnly = true) },
        fetch = { force -> HubListDataSnapshot.capture("weapons",
            { WeaponListApi.fetchAllCategories(forceRefresh = force, allowMemoryCache = false) },
            { it.isNotEmpty() && it.all { group -> group.loadError == null } },
            { HubListDataSnapshot.weaponCategories = it }) },
        initiallyLoaded = restoredWeapons != null,
        reloadOnEnter = restoredWeapons == null
    )
    val factions = characterState.data
    val isLoadingCharacters = characterState.isLoading
    val gameModes = mapState.data
    val isLoadingMaps = mapState.isLoading

    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effectiveGlass = remember(liquidGlassEnabled) { mutableStateOf(liquidGlassEnabled) }
    val wallpaperUrl by AppPrefs.wallpaperUrlState.collectAsState()
    LaunchedEffect(Unit) {
        WallpaperApi.refresh(rotate = AppPrefs.wallpaperAutoRefresh)
    }
    val hasWallpaper = !wallpaperUrl.isNullOrBlank()

    val navigationTarget = HubTransitionTarget(currentRoute, backStack.lastIndex)
    val transitionState = rememberPredictiveBackTransition(navigationTarget, resetKey)
    // Include the incoming page before its first draw, including an uncommitted back preview.
    // Count retained readers rather than assuming an interrupted AnimatedContent holds only two pages.
    val retainedGlassReaders = remember { mutableIntStateOf(0) }
    val needsBackdrop = liquidGlassEnabled && (currentRoute.readsHubBackdrop ||
        transitionState.state.targetState.route.readsHubBackdrop || retainedGlassReaders.intValue != 0)
    val glassLayer = if (needsBackdrop && hasWallpaper) rememberLayerBackdrop() else null
    val hubBackdrop = when {
        glassLayer != null -> glassLayer
        needsBackdrop -> {
            val background by rememberUpdatedState(MaterialTheme.colorScheme.background)
            // Fill the sample including its blur padding, without recording a flat window-sized layer.
            rememberCanvasBackdrop { drawContext.canvas.nativeCanvas.drawColor(background.toArgb()) }
        }
        else -> remember { emptyBackdrop() }
    }
    val routeStates = key(resetKey) { rememberSaveableStateHolder() }
    val retainedRoutes by rememberUpdatedState(backStack)
    val overlayVisible by rememberUpdatedState(isOverlaid)
    var editorVisit by rememberSaveable(resetKey) { mutableIntStateOf(0) }

    fun popRoute() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    fun pushRoute(route: WikiRoute) {
        if (backStack.lastOrNull() == route) return
        if (route == WikiRoute.QuickEntryEditor) editorVisit++
        backStack = backStack + route
    }

    val previousRoute = backStack.getOrNull(backStack.lastIndex - 1)
    val canPredictivePop = !isOverlaid &&
        currentRoute != WikiRoute.QuickEntryEditor &&
        previousRoute != null &&
        !currentRoute.isDetailRoute &&
        !previousRoute.isDetailRoute

    SeekablePredictiveBackHandler(
        enabled = canPredictivePop,
        currentState = navigationTarget,
        targetState = previousRoute?.let { HubTransitionTarget(it, backStack.lastIndex - 1) } ?: navigationTarget,
        transitionState = transitionState,
        onCommit = {
            // A toolbar click can mutate the stack before the next composition invalidates the ticket.
            if (backStack.lastIndex == navigationTarget.depth && backStack.lastOrNull() == navigationTarget.route) popRoute()
        },
        resolveTarget = { backStack.getOrNull(backStack.lastIndex - 1)?.let { HubTransitionTarget(it, backStack.lastIndex - 1) } }
    )

    BackHandler(enabled = backStack.size > 1 && !isOverlaid && !canPredictivePop && currentRoute != WikiRoute.QuickEntryEditor) {
        popRoute()
    }

    fun openMapDetail(name: String, imageUrl: String?, source: String = "list") {
        val resolvedImageUrl = imageUrl?.takeIf(String::isNotBlank) ?: gameModes
            .asSequence()
            .flatMap { it.maps.asSequence() }
            .firstOrNull { it.name == name && it.imageUrl.isNotBlank() }
            ?.imageUrl
        pushRoute(WikiRoute.MapDetail(name, resolvedImageUrl, source))
    }

    // 采用 MD3 约定的 Shared Axis (X轴) 过渡：轻量位移配合快速淡入淡出，比大范围拉扯更具现代感
    Box(Modifier.fillMaxSize()) {
        WikiHubWallpaperBackground(
            wallpaperUrl = wallpaperUrl,
            liquidGlassEnabled = liquidGlassEnabled,
            backdrop = glassLayer
        )

        androidx.compose.runtime.CompositionLocalProvider(
            LocalHasWallpaper provides hasWallpaper,
            LocalLiquidGlassEnabled provides effectiveGlass
        ) {
            SharedTransitionLayout {
            val transition = rememberTransition(transitionState.state, label = "WikiHubPageTransition")
            transition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.route },
                transitionSpec = {
                    val isDetailTransition = targetState.route.isDetailRoute || initialState.route.isDetailRoute
                    val duration = if (isDetailTransition) 400 else 220
                    if (isDetailTransition) {
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration / 2))
                    } else if (targetState.isPopFrom(initialState)) {
                        (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { -it / 8 } +
                                fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(duration / 2, easing = FastOutLinearInEasing)) { it / 8 } +
                                        fadeOut(tween(duration / 2, easing = FastOutLinearInEasing))
                            )
                    } else {
                        (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { it / 8 } +
                                fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(duration / 2, easing = FastOutLinearInEasing)) { -it / 8 } +
                                        fadeOut(tween(duration / 2, easing = FastOutLinearInEasing))
                            )
                    }
                }
            ) { page ->
            val route = page.route
            if (route.readsHubBackdrop) {
                DisposableEffect(retainedGlassReaders) {
                    retainedGlassReaders.intValue++
                    onDispose { retainedGlassReaders.intValue-- }
                }
            }
            // AnimatedContent retains outgoing pages. Their callbacks must not mutate the new stack.
            fun isCurrentPage() = backStack.lastIndex == page.depth && backStack.lastOrNull() == route
            fun isPageInteractive() = isCurrentPage() && !overlayVisible
            fun openCurrentWiki(url: String) { if (isPageInteractive()) onOpenWikiUrl(url) }
            fun popBackStack() { if (isPageInteractive()) popRoute() }
            fun navigateTo(next: WikiRoute) { if (isPageInteractive()) pushRoute(next) }
            fun navigateToMapDetail(name: String, imageUrl: String?, source: String = "list") {
                if (isPageInteractive()) openMapDetail(name, imageUrl, source)
            }
            val routeKey = route.encode()
            routeStates.SaveableStateProvider(routeKey) {
            DisposableEffect(routeKey) {
                onDispose {
                    // A popped page must not leave filters/drafts behind indefinitely.
                    if (route !in retainedRoutes) routeStates.removeState(routeKey)
                }
            }
            when (route) {
            is WikiRoute.Home -> {
                WikiHomePage(
                    onOpenDrawer = onOpenDrawer,
                    showDrawerButton = showDrawerButton,
                    listState = homeListState,
                    characterRowState = homeCharacterRowState,
                    mapRowState = homeMapRowState,
                    topAppBarState = homeTopAppBarState,
                    resetKey = resetKey,
                    onNavigateTo = { navigateTo(it) },
                    onOpenCharacterDetail = { name, portrait ->
                        navigateTo(WikiRoute.CharDetail(name, portrait, source = "home"))
                    },
                    onOpenMapDetail = { name, imageUrl ->
                        navigateToMapDetail(name, imageUrl, source = "home")
                    },
                    factions = factions,
                    isLoadingCharacters = isLoadingCharacters,
                    gameModes = gameModes,
                    isLoadingMaps = isLoadingMaps,
                    selectedHomeFaction = homeFactionTab,
                    onHomeFactionChanged = { homeFactionTab = it },
                    selectedHomeMapMode = homeMapModeTab,
                    onHomeMapModeChanged = { homeMapModeTab = it },
                    isPageActive = isPageInteractive(),
                    backdrop = hubBackdrop,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.Characters -> {
                CharacterListScreen(
                    onBack = { popBackStack() },
                    onOpenCharacterDetail = { name, portrait ->
                        navigateTo(WikiRoute.CharDetail(name, portrait))
                    },
                    initialTab = characterListTab,
                    onTabChanged = { characterListTab = it },
                    gridState = characterGridState,
                    loadState = characterState,
                    isPageActive = isPageInteractive(),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.CharDetail -> {
                CharacterDetailScreen(
                    characterName = route.name,
                    portraitUrl = route.portrait?.takeIf(String::isNotBlank),
                    source = route.source,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = ::openCurrentWiki,
                    onOpenCostumes = { charName ->
                        navigateTo(WikiRoute.Costumes(charName))
                    },
                    onOpenWeaponSkins = { weaponName ->
                        navigateTo(WikiRoute.WeaponSkins(weaponName))
                    },
                    onOpenWeaponDetail = { weaponName ->
                        navigateTo(WikiRoute.WeaponDetail(weaponName))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.Weapons -> {
                WeaponListScreen(
                    onBack = { popBackStack() },
                    onOpenWeaponDetail = { name, imageUrl ->
                        navigateTo(WikiRoute.WeaponDetail(name, imageUrl))
                    },
                    initialTab = weaponListTab,
                    onTabChanged = { weaponListTab = it },
                    gridState = weaponGridState,
                    loadState = weaponState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.WeaponDetail -> {
                val characterByName = remember(factions) {
                    factions.asSequence()
                        .flatMap { it.characters.asSequence() }
                        .associateBy { it.name }
                }
                WeaponDetailScreen(
                    weaponName = route.name,
                    imageUrl = route.imageUrl,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenWeaponSkins = { weaponName ->
                        navigateTo(WikiRoute.WeaponSkins(weaponName))
                    },
                    canOpenCharacter = { userName ->
                        userName in characterByName || backStack.any { it is WikiRoute.CharDetail && it.name == userName }
                    },
                    onOpenCharacter = { userName ->
                        backStack = page.openWeaponUser(backStack, overlayVisible, userName) {
                            characterByName[userName]?.let { WikiRoute.CharDetail(it.name, it.portraitUrl ?: it.imageUrl) }
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            WikiRoute.Maps, is WikiRoute.ModeMaps -> {
                var template by rememberSaveable { mutableStateOf((route as? WikiRoute.ModeMaps)?.templateName) }
                MapListFullScreen(
                    onBack = { popBackStack() },
                    onOpenMapDetail = { name, imageUrl ->
                        navigateToMapDetail(name, imageUrl)
                    },
                    gameModes = gameModes,
                    isLoading = isLoadingMaps,
                    error = mapState.error,
                    isPageActive = isPageInteractive(),
                    offlineAge = mapState.cacheAgeMs.takeIf { mapState.isOffline && !mapState.isShowingCachedPrefetch },
                    onRetry = { mapState.reload(forceRefresh = true) },
                    selectedTab = if (route is WikiRoute.ModeMaps) gameModes.indexOfFirst { it.templateName == template } else mapListTab,
                    onTabChanged = { index ->
                        if (isPageInteractive()) {
                            if (route is WikiRoute.ModeMaps) template = gameModes[index].templateName else mapListTab = index
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.MapDetail -> {
                MapDetailScreen(
                    mapName = route.name,
                    mapImageUrl = route.imageUrl,
                    source = route.source,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = ::openCurrentWiki,
                    isActive = ::isPageInteractive,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }

            is WikiRoute.Costumes -> {
                CostumeFilterScreen(
                    initialCharacter = route.character,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Items -> {
                ItemCatalogScreen(
                    platform = ItemCatalogPlatform.PC,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.MobileItems -> {
                ItemCatalogScreen(
                    platform = ItemCatalogPlatform.MOBILE,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.WeaponSkins -> {
                WeaponSkinFilterScreen(
                    initialWeapon = route.weapon,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Activities -> {
                ActivityScreen(
                    onBack = ::popBackStack,
                    onOpenWikiUrl = ::openCurrentWiki,
                    isActive = ::isPageInteractive
                )
            }

            is WikiRoute.Announcements -> {
                AnnouncementScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            WikiRoute.GameModes, WikiRoute.ExtractionMode -> {
                GameModeScreen(
                    focusedMode = if (route == WikiRoute.ExtractionMode) GameModeApi.EXTRACTION_MODE else null,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = ::openCurrentWiki,
                    onOpenMapDetail = { name, imageUrl -> navigateToMapDetail(name, imageUrl) },
                    onOpenExtractionMode = { if (isPageInteractive()) navigateTo(WikiRoute.ExtractionMode) }
                )
            }

            WikiRoute.ExtractionHub -> ExtractionHubScreen(
                onBack = { popBackStack() },
                onNavigateTo = { if (isPageInteractive()) navigateTo(it) },
                onOpenWikiUrl = ::openCurrentWiki,
                backdrop = hubBackdrop
            )

            WikiRoute.ExtractionGuns, WikiRoute.ExtractionArmor -> ExtractionEquipmentScreen(
                kind = if (route == WikiRoute.ExtractionGuns) EquipmentKind.GUNS else EquipmentKind.ARMOR,
                onBack = { popBackStack() }, onOpenWikiUrl = ::openCurrentWiki, isActive = ::isPageInteractive
            )
            WikiRoute.ExtractionWorkshop -> ExtractionWorkshopScreen(
                onBack = { popBackStack() }, onOpenWikiUrl = ::openCurrentWiki, isActive = ::isPageInteractive
            )
            WikiRoute.QuickEntryEditor -> key(editorVisit) {
                val visit = editorVisit
                QuickEntryEditorScreen(isActive = { visit == editorVisit && isPageInteractive() }, onBack = ::popBackStack)
            }

            WikiRoute.ExtractionSources -> ExtractionDropsScreen(
                initialTask = DropTask.SOURCES, onBack = { popBackStack() }, onOpenWikiUrl = ::openCurrentWiki,
                isActive = ::isPageInteractive
            )

            is WikiRoute.ExtractionCatalog -> ExtractionCatalogScreen(
                kind = route.kind, onBack = { popBackStack() }, onOpenWikiUrl = ::openCurrentWiki,
                isActive = ::isPageInteractive
            )

            is WikiRoute.Voting -> {
                VotingScreen(onBack = { popBackStack() })
            }

            is WikiRoute.BioCards -> {
                BioCardScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    initialTab = 0
                )
            }

            is WikiRoute.BioMobileCards -> {
                // 首页搜索与玩法聚合页的移动端入口，直接打开对应平台标签。
                BioCardScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    initialTab = 1
                )
            }

            is WikiRoute.Story -> {
                StoryScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.GameHistory -> {
                GameHistoryScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Submissions -> {
                SubmissionScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Memes -> {
                MemeScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Collaborations -> {
                CollaborationScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Bgm -> {
                BgmScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Navigation -> {
                NavigationMenuScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = { url ->
                        if (isCurrentPage() && !isOverlaid) {
                            val target = nativeWikiRoute(url,
                                characters = factions.flatMap { it.characters }.map { it.name }.toSet(),
                                maps = gameModes.flatMap { it.maps }.map { it.name }.toSet(),
                                weapons = HubListDataSnapshot.weaponCategories.orEmpty().flatMap { it.weapons }.map { it.name }.toSet())
                            when (target) {
                                null -> onOpenWikiUrl(url)
                                is WikiRoute.MapDetail -> navigateToMapDetail(target.name, target.imageUrl)
                                else -> navigateTo(target)
                            }
                        }
                    }
                )
            }

            is WikiRoute.Wallpapers -> {
                GalleryScreen(
                    title = "壁纸",
                    pageName = "壁纸",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Stickers -> {
                GalleryScreen(
                    title = "表情包",
                    pageName = "表情包",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Comics -> {
                GalleryScreen(
                    title = "四格漫画",
                    pageName = "官方四格漫画",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.MeowLanguage -> {
                MeowLanguageScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.GameTips -> {
                GameTipsScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.PcGameSettings -> {
                PcGameSettingsScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.BeginnerGuide -> {
                BeginnerGuideScreen(onBack = { popBackStack() }, onOpenWikiUrl = onOpenWikiUrl)
            }

            is WikiRoute.Mechanics -> {
                MechanicsScreen(kind = route.kind, onBack = { popBackStack() }, onOpenWikiUrl = onOpenWikiUrl,
                    onOpenCharacter = { name, portrait -> navigateTo(WikiRoute.CharDetail(name, portrait)) })
            }

            is WikiRoute.BalanceData -> {
                BalanceDataScreen(
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Baseplates -> {
                PlayerDecorationScreen(
                    title = "基板",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.PersonalCards -> {
                PlayerDecorationScreen(
                    title = "个性名片",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Encasings -> {
                PlayerDecorationScreen(
                    title = "封装",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.ChatBubbles -> {
                PlayerDecorationScreen(
                    title = "聊天气泡",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Headgear -> {
                PlayerDecorationScreen(
                    title = "头套",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.StringerActions -> {
                PlayerDecorationScreen(
                    title = "超弦体动作",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.StringerTalents -> {
                StringerTalentScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.StringerPushCards -> {
                StringerPushCardScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Medals -> {
                PlayerDecorationScreen(
                    title = "勋章",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Sprays -> {
                PlayerDecorationScreen(
                    title = "喷漆",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.AvatarFrames -> {
                PlayerDecorationScreen(
                    title = "头像框",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.RoomAppearances -> {
                PlayerDecorationScreen(
                    title = "房间外观",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.LoungeFigures -> {
                PlayerDecorationScreen(
                    title = "休息室手办",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.VehicleSkins -> {
                PlayerDecorationScreen(
                    title = "极限推进模式载具外观",
                    isActive = { isCurrentPage() && !isOverlaid },
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Oath -> {
                OathScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Imprints -> {
                ImprintScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Achievements -> {
                AchievementScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.PlayerLevels -> {
                PlayerLevelScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.InteractionItems -> {
                InteractionItemScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.GameplayHub -> {
                WikiGameplayHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.DecorationHub -> {
                WikiDecorationHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.CatalogHub -> {
                WikiCatalogHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it) },
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.ExtensionHub -> {
                WikiExtensionHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }
        }
            } // SaveableStateProvider
            } // AnimatedContent
            } // SharedTransitionLayout
        }
    }
}
