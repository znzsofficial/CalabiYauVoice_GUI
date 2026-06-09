package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterScreen
import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailScreen
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListScreen
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailScreen
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
import com.nekolaska.calabiyau.feature.wiki.history.GameHistoryScreen
import com.nekolaska.calabiyau.feature.wiki.imprint.ImprintScreen
import com.nekolaska.calabiyau.feature.wiki.item.ItemCatalogScreen
import com.nekolaska.calabiyau.feature.wiki.map.MapDetailScreen
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.meme.MemeScreen
import com.nekolaska.calabiyau.feature.wiki.meow.MeowLanguageScreen
import com.nekolaska.calabiyau.feature.wiki.navigation.NavigationMenuScreen
import com.nekolaska.calabiyau.feature.wiki.oath.OathScreen
import com.nekolaska.calabiyau.feature.wiki.playerlevel.PlayerLevelScreen
import com.nekolaska.calabiyau.feature.wiki.story.StoryScreen
import com.nekolaska.calabiyau.feature.wiki.stringer.StringerPushCardScreen
import com.nekolaska.calabiyau.feature.wiki.stringer.StringerTalentScreen
import com.nekolaska.calabiyau.feature.wiki.tips.GameTipsScreen
import com.nekolaska.calabiyau.feature.wiki.voting.VotingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var url: String? = AppPrefs.wallpaperUrl
        private set
    private var refreshJob: Deferred<String?>? = null
    private var autoRefreshAttempted = false
    private var displayedImageCacheKey: String? = null

    @Synchronized
    fun updateUrl(newUrl: String?) {
        if (!newUrl.isNullOrBlank()) {
            url = newUrl
        }
    }

    @Synchronized
    fun currentUrl(): String? = url

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

    @Synchronized
    fun shouldAutoRefresh(currentCachedUrl: String?): Boolean {
        return when {
            currentCachedUrl.isNullOrBlank() -> true
            !AppPrefs.wallpaperAutoRefresh -> false
            WallpaperApi.hasRefreshedThisSession -> false
            autoRefreshAttempted -> false
            else -> true
        }
    }

    @Synchronized
    fun ensureRefresh(forceRefresh: Boolean): Deferred<String?> {
        refreshJob?.takeIf { it.isActive }?.let { return it }
        if (forceRefresh) {
            autoRefreshAttempted = true
        }
        return scope.async {
            WallpaperApi.ensureWallpaperUrl(forceRefresh = forceRefresh)
        }.also { job ->
            refreshJob = job
            job.invokeOnCompletion { clearRefreshIfCurrent(job) }
        }
    }

    @Synchronized
    fun clearRefreshIfCurrent(job: Deferred<String?>) {
        if (refreshJob === job) {
            refreshJob = null
        }
    }
}

@Composable
private fun WikiHubWallpaperBackground(
    wallpaperUrl: String?,
    liquidGlassEnabled: Boolean,
    backdrop: LayerBackdrop,
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
                if (liquidGlassEnabled) Modifier.layerBackdrop(backdrop)
                else Modifier
            )
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    isOverlaid: Boolean = false,
    initialPage: WikiHubPage = WikiHubPage.HOME,
    resetKey: Int = 0
) {
    var backStack by rememberSaveable(resetKey, stateSaver = wikiRouteStackSaver) {
        mutableStateOf(listOf(initialPage.toRoute()))
    }
    val currentRoute = backStack.lastOrNull() ?: WikiRoute.Home

    val homeListState = rememberSaveable(resetKey, saver = lazyListStateSaver) { LazyListState() }
    val homeTopAppBarState = rememberTopAppBarState()

    // 内部分页 Tab 状态继续保留，因为它们不随压栈出栈而丢失（或者让它们由各个页面自行接管）
    var homeFactionTab by rememberSaveable { mutableIntStateOf(0) }
    var homeMapModeTab by rememberSaveable { mutableIntStateOf(0) }
    var characterListTab by rememberSaveable { mutableIntStateOf(0) }
    var weaponListTab by rememberSaveable { mutableIntStateOf(0) }
    var mapListTab by rememberSaveable { mutableIntStateOf(0) }

    // ── 数据缓存（提升到此层级，子页面切换不丢失） ──
    val characterState =
        rememberLoadState(emptyList<CharacterListApi.FactionData>()) { force ->
            CharacterListApi.fetchAllFactions(force)
        }
    val mapState =
        rememberLoadState(emptyList<GameModeData>()) { force ->
            MapListApi.fetchAllModes(force)
        }
    val factions = characterState.data
    val isLoadingCharacters = characterState.isLoading
    val gameModes = mapState.data
    val isLoadingMaps = mapState.isLoading

    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value
    var wallpaperUrl by remember { mutableStateOf(WikiHubWallpaperState.currentUrl() ?: AppPrefs.wallpaperUrl) }
    val hubBackdrop = rememberLayerBackdrop()
    LaunchedEffect(Unit) {
        val currentCachedUrl = AppPrefs.wallpaperUrl
        if (!currentCachedUrl.isNullOrBlank() && wallpaperUrl != currentCachedUrl) {
            wallpaperUrl = currentCachedUrl
            WikiHubWallpaperState.updateUrl(currentCachedUrl)
        }
        val needRefresh = WikiHubWallpaperState.shouldAutoRefresh(currentCachedUrl)
        if (needRefresh) {
            val refreshJob = WikiHubWallpaperState.ensureRefresh(forceRefresh = !currentCachedUrl.isNullOrBlank())
            val loadedUrl = refreshJob.await()
            if (!loadedUrl.isNullOrBlank()) {
                wallpaperUrl = loadedUrl
                WikiHubWallpaperState.updateUrl(loadedUrl)
            }
        }
    }
    val hasWallpaper = !wallpaperUrl.isNullOrBlank()

    // ── 导航方向追踪（用于过渡动画） ──
    var isNavigatingBack by remember { mutableStateOf(false) }

    fun popBackStack() {
        if (backStack.size > 1) {
            isNavigatingBack = true
            backStack = backStack.dropLast(1)
        }
    }

    fun navigateTo(route: WikiRoute) {
        if (backStack.lastOrNull() == route) return
        isNavigatingBack = false
        backStack = backStack + route
    }

    fun navigateToMapDetail(name: String, imageUrl: String?) {
        val resolvedImageUrl = imageUrl ?: gameModes
            .asSequence()
            .flatMap { it.maps.asSequence() }
            .firstOrNull { it.name == name }
            ?.imageUrl
        navigateTo(WikiRoute.MapDetail(name, resolvedImageUrl))
    }

    BackHandler(enabled = backStack.size > 1 && !isOverlaid) {
        popBackStack()
    }

    // 采用 MD3 约定的 Shared Axis (X轴) 过渡：轻量位移配合快速淡入淡出，比大范围拉扯更具现代感
    Box(Modifier.fillMaxSize()) {
        WikiHubWallpaperBackground(
            wallpaperUrl = wallpaperUrl,
            liquidGlassEnabled = liquidGlassEnabled,
            backdrop = hubBackdrop
        )

        androidx.compose.runtime.CompositionLocalProvider(LocalHasWallpaper provides hasWallpaper) {
            AnimatedContent(
                targetState = currentRoute,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val duration = 400
                    if (isNavigatingBack) {
                        // 返回：从左滑入(强调减速) + 淡入，旧页向右滑出(加速) + 淡出
                        (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { -it / 8 } +
                                fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    tween(
                                        duration / 2,
                                        easing = FastOutLinearInEasing
                                    )
                                ) { it / 8 } +
                                        fadeOut(tween(duration / 2, easing = FastOutLinearInEasing))
                            )
                    } else {
                        // 前进：从右滑入(强调减速) + 淡入，旧页向左滑出(加速) + 淡出
                        (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { it / 8 } +
                                fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    tween(
                                        duration / 2,
                                        easing = FastOutLinearInEasing
                                    )
                                ) { -it / 8 } +
                                        fadeOut(tween(duration / 2, easing = FastOutLinearInEasing))
                            )
                    }
                },
                label = "WikiHubPageTransition"
            ) { route ->
            when (route) {
            is WikiRoute.Home -> {
                WikiHomePage(
                    onOpenDrawer = onOpenDrawer,
                    onOpenWikiUrl = onOpenWikiUrl,
                    listState = homeListState,
                    topAppBarState = homeTopAppBarState,
                    wallpaperUrl = wallpaperUrl,
                    onNavigateTo = { navigateTo(it.toRoute()) },
                    onNavigateRoute = { navigateTo(it) },
                    onOpenCharacterDetail = { name, portrait ->
                        navigateTo(WikiRoute.CharDetail(name, portrait))
                    },
                    onOpenMapDetail = { name, imageUrl ->
                        navigateToMapDetail(name, imageUrl)
                    },
                    factions = factions,
                    isLoadingCharacters = isLoadingCharacters,
                    gameModes = gameModes,
                    isLoadingMaps = isLoadingMaps,
                    selectedHomeFaction = homeFactionTab,
                    onHomeFactionChanged = { homeFactionTab = it },
                    selectedHomeMapMode = homeMapModeTab,
                    onHomeMapModeChanged = { homeMapModeTab = it },
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.Characters -> {
                CharacterListScreen(
                    onBack = { popBackStack() },
                    onOpenCharacterDetail = { name, portrait ->
                        navigateTo(WikiRoute.CharDetail(name, portrait))
                    },
                    initialTab = characterListTab,
                    onTabChanged = { characterListTab = it }
                )
            }

            is WikiRoute.CharDetail -> {
                CharacterDetailScreen(
                    characterName = route.name,
                    portraitUrl = route.portrait,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenCostumes = { charName ->
                        navigateTo(WikiRoute.Costumes(charName))
                    },
                    onOpenWeaponSkins = { weaponName ->
                        navigateTo(WikiRoute.WeaponSkins(weaponName))
                    },
                    onOpenWeaponDetail = { weaponName ->
                        navigateTo(WikiRoute.WeaponDetail(weaponName))
                    }
                )
            }

            is WikiRoute.Weapons -> {
                WeaponListScreen(
                    onBack = { popBackStack() },
                    onOpenWeaponDetail = { name ->
                        navigateTo(WikiRoute.WeaponDetail(name))
                    },
                    initialTab = weaponListTab,
                    onTabChanged = { weaponListTab = it }
                )
            }

            is WikiRoute.WeaponDetail -> {
                WeaponDetailScreen(
                    weaponName = route.name,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenWeaponSkins = { weaponName ->
                        navigateTo(WikiRoute.WeaponSkins(weaponName))
                    }
                )
            }

            is WikiRoute.Maps -> {
                MapListFullScreen(
                    onBack = { popBackStack() },
                    onOpenMapDetail = { name, imageUrl ->
                        navigateToMapDetail(name, imageUrl)
                    },
                    gameModes = gameModes,
                    isLoading = isLoadingMaps,
                    initialTab = mapListTab,
                    onTabChanged = { mapListTab = it }
                )
            }

            is WikiRoute.MapDetail -> {
                MapDetailScreen(
                    mapName = route.name,
                    mapImageUrl = route.imageUrl,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
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
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Announcements -> {
                AnnouncementScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.GameModes -> {
                GameModeScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenMapDetail = { name, imageUrl ->
                        navigateToMapDetail(name, imageUrl)
                    }
                )
            }

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
                // 兼容保留路由：当前无首页直达入口，仅用于未来深链或外部跳转扩展
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
                    onOpenWikiUrl = onOpenWikiUrl
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

            is WikiRoute.BalanceData -> {
                BalanceDataScreen(
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Baseplates -> {
                PlayerDecorationScreen(
                    title = "基板",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Encasings -> {
                PlayerDecorationScreen(
                    title = "封装",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.ChatBubbles -> {
                PlayerDecorationScreen(
                    title = "聊天气泡",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Headgear -> {
                PlayerDecorationScreen(
                    title = "头套",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.StringerActions -> {
                PlayerDecorationScreen(
                    title = "超弦体动作",
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
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Sprays -> {
                PlayerDecorationScreen(
                    title = "喷漆",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.AvatarFrames -> {
                PlayerDecorationScreen(
                    title = "头像框",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.RoomAppearances -> {
                PlayerDecorationScreen(
                    title = "房间外观",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.VehicleSkins -> {
                PlayerDecorationScreen(
                    title = "极限推进模式载具外观",
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

            is WikiRoute.GameplayHub -> {
                WikiGameplayHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it.toRoute()) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.DecorationHub -> {
                WikiDecorationHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it.toRoute()) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.CatalogHub -> {
                WikiCatalogHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it.toRoute()) },
                    backdrop = hubBackdrop
                )
            }

            is WikiRoute.ExtensionHub -> {
                WikiExtensionHubScreen(
                    onBack = { popBackStack() },
                    onNavigateTo = { navigateTo(it.toRoute()) },
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = hubBackdrop
                )
            }
        }
            }
        }
    }
}
