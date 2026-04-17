package com.nekolaska.calabiyau.ui.wiki

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.data.CharacterListApi
import com.nekolaska.calabiyau.data.MapListApi
import com.nekolaska.calabiyau.ui.shared.rememberLoadState

// ════════════════════════════════════════════════════════
//  Wiki 主页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

/** 子页面枚举 (保留用于兼容上层或原有内部组件) */
enum class WikiHubPage { HOME, CHARACTERS, CHAR_DETAIL, WEAPONS, WEAPON_DETAIL, MAPS, MAP_DETAIL, COSTUMES, WEAPON_SKINS, ACTIVITIES, ANNOUNCEMENTS, GAME_MODES, BALANCE_DATA, VOTING, BIO_CARDS,
    BIO_MOBILE_CARDS, // 兼容保留：当前 WikiHomePage 未提供独立入口（通过 BioCardScreen 内部 Tab 可切换）
    NAVIGATION, WALLPAPERS, STICKERS, COMICS, BASEPLATES, ENCASINGS, MEDALS, SPRAYS, CHAT_BUBBLES, HEADGEAR, STRINGER_ACTIONS, AVATAR_FRAMES }

/** 子页面路由（替代上帝变量状态的路由密封接口） */
sealed interface WikiRoute {
    data object Home : WikiRoute
    data object Characters : WikiRoute
    data class CharDetail(val name: String, val portrait: String?) : WikiRoute
    data object Weapons : WikiRoute
    data class WeaponDetail(val name: String) : WikiRoute
    data object Maps : WikiRoute
    data class MapDetail(val name: String, val imageUrl: String?) : WikiRoute
    data class Costumes(val character: String?) : WikiRoute
    data class WeaponSkins(val weapon: String?) : WikiRoute
    data object Activities : WikiRoute
    data object Announcements : WikiRoute
    data object GameModes : WikiRoute
    data object BalanceData : WikiRoute
    data object Voting : WikiRoute
    data object BioCards : WikiRoute
    data object BioMobileCards : WikiRoute
    data object Navigation : WikiRoute
    data object Wallpapers : WikiRoute
    data object Stickers : WikiRoute
    data object Comics : WikiRoute
    data object Baseplates : WikiRoute
    data object Encasings : WikiRoute
    data object Medals : WikiRoute
    data object Sprays : WikiRoute
    data object ChatBubbles : WikiRoute
    data object Headgear : WikiRoute
    data object StringerActions : WikiRoute
    data object AvatarFrames : WikiRoute
}

private fun WikiHubPage.toRoute(): WikiRoute = when (this) {
    WikiHubPage.HOME -> WikiRoute.Home
    WikiHubPage.CHARACTERS -> WikiRoute.Characters
    WikiHubPage.WEAPONS -> WikiRoute.Weapons
    WikiHubPage.MAPS -> WikiRoute.Maps
    WikiHubPage.ACTIVITIES -> WikiRoute.Activities
    WikiHubPage.ANNOUNCEMENTS -> WikiRoute.Announcements
    WikiHubPage.GAME_MODES -> WikiRoute.GameModes
    WikiHubPage.VOTING -> WikiRoute.Voting
    WikiHubPage.BIO_CARDS -> WikiRoute.BioCards
    WikiHubPage.BIO_MOBILE_CARDS -> WikiRoute.BioMobileCards
    WikiHubPage.NAVIGATION -> WikiRoute.Navigation
    WikiHubPage.WALLPAPERS -> WikiRoute.Wallpapers
    WikiHubPage.STICKERS -> WikiRoute.Stickers
    WikiHubPage.COMICS -> WikiRoute.Comics
    WikiHubPage.BALANCE_DATA -> WikiRoute.BalanceData
    WikiHubPage.BASEPLATES -> WikiRoute.Baseplates
    WikiHubPage.ENCASINGS -> WikiRoute.Encasings
    WikiHubPage.MEDALS -> WikiRoute.Medals
    WikiHubPage.SPRAYS -> WikiRoute.Sprays
    WikiHubPage.CHAT_BUBBLES -> WikiRoute.ChatBubbles
    WikiHubPage.HEADGEAR -> WikiRoute.Headgear
    WikiHubPage.STRINGER_ACTIONS -> WikiRoute.StringerActions
    WikiHubPage.AVATAR_FRAMES -> WikiRoute.AvatarFrames
    // 以下带参数页面由于是从 WikiHomePage 跳转而来，按理说不会直接触发（通常走具名参数跳转），给出默认保底
    WikiHubPage.CHAR_DETAIL -> WikiRoute.CharDetail("", null)
    WikiHubPage.WEAPON_DETAIL -> WikiRoute.WeaponDetail("")
    WikiHubPage.MAP_DETAIL -> WikiRoute.MapDetail("", null)
    WikiHubPage.COSTUMES -> WikiRoute.Costumes(null)
    WikiHubPage.WEAPON_SKINS -> WikiRoute.WeaponSkins(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    isOverlaid: Boolean = false,
    initialPage: WikiHubPage = WikiHubPage.HOME
) {
    var backStack by remember { mutableStateOf(listOf(initialPage.toRoute())) }
    val currentRoute = backStack.lastOrNull() ?: WikiRoute.Home

    val homeListState = rememberLazyListState()

    // 内部分页 Tab 状态继续保留，因为它们不随压栈出栈而丢失（或者让它们由各个页面自行接管）
    var characterListTab by rememberSaveable { mutableIntStateOf(0) }
    var weaponListTab by rememberSaveable { mutableIntStateOf(0) }
    var mapListTab by rememberSaveable { mutableIntStateOf(0) }

    // ── 数据缓存（提升到此层级，子页面切换不丢失） ──
    val characterState =
        rememberLoadState(emptyList<CharacterListApi.FactionData>()) { force ->
            CharacterListApi.fetchAllFactions(force)
        }
    val mapState =
        rememberLoadState(emptyList<MapListApi.GameModeData>()) { force ->
            MapListApi.fetchAllModes(force)
        }
    val factions = characterState.data
    val isLoadingCharacters = characterState.isLoading
    val gameModes = mapState.data
    val isLoadingMaps = mapState.isLoading

    // ── 导航方向追踪（用于过渡动画） ──
    var isNavigatingBack by remember { mutableStateOf(false) }

    fun popBackStack() {
        if (backStack.size > 1) {
            isNavigatingBack = true
            backStack = backStack.dropLast(1)
        }
    }

    fun navigateTo(route: WikiRoute) {
        isNavigatingBack = false
        backStack = backStack + route
    }

    BackHandler(enabled = backStack.size > 1 && !isOverlaid) {
        popBackStack()
    }

    // 采用 MD3 约定的 Shared Axis (X轴) 过渡：轻量位移配合快速淡入淡出，比大范围拉扯更具现代感
    val animDuration = 400
    AnimatedContent(
        targetState = currentRoute,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (isNavigatingBack) {
                // 返回：从左侧轻微滑入 + 淡入，旧页面向右侧轻微滑出 + 快速淡出
                (slideInHorizontally(tween(animDuration)) { -it / 8 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { it / 8 } + fadeOut(tween(animDuration / 2)))
            } else {
                // 前进：从右侧轻微滑入 + 淡入，旧页面向左侧轻微滑出 + 快速淡出
                (slideInHorizontally(tween(animDuration)) { it / 8 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { -it / 8 } + fadeOut(tween(animDuration / 2)))
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
                onNavigateTo = { navigateTo(it.toRoute()) },
                onOpenCharacterDetail = { name, portrait ->
                    navigateTo(WikiRoute.CharDetail(name, portrait))
                },
                onOpenMapDetail = { name, imageUrl ->
                    navigateTo(WikiRoute.MapDetail(name, imageUrl))
                },
                factions = factions,
                isLoadingCharacters = isLoadingCharacters,
                gameModes = gameModes,
                isLoadingMaps = isLoadingMaps
            )
        }
        is WikiRoute.Characters -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.character.CharacterListScreen(
                onBack = { popBackStack() },
                onOpenCharacterDetail = { name, portrait ->
                    navigateTo(WikiRoute.CharDetail(name, portrait))
                },
                initialTab = characterListTab,
                onTabChanged = { characterListTab = it }
            )
        }
        is WikiRoute.CharDetail -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.character.CharacterDetailScreen(
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
            _root_ide_package_.com.nekolaska.calabiyau.ui.weapon.WeaponListScreen(
                onBack = { popBackStack() },
                onOpenWeaponDetail = { name ->
                    navigateTo(WikiRoute.WeaponDetail(name))
                },
                initialTab = weaponListTab,
                onTabChanged = { weaponListTab = it }
            )
        }
        is WikiRoute.WeaponDetail -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.weapon.WeaponDetailScreen(
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
                    navigateTo(WikiRoute.MapDetail(name, imageUrl))
                },
                gameModes = gameModes,
                isLoading = isLoadingMaps,
                initialTab = mapListTab,
                onTabChanged = { mapListTab = it }
            )
        }
        is WikiRoute.MapDetail -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.MapDetailScreen(
                mapName = route.name,
                mapImageUrl = route.imageUrl,
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        is WikiRoute.Costumes -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.character.CostumeFilterScreen(
                initialCharacter = route.character,
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.WeaponSkins -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.weapon.WeaponSkinFilterScreen(
                initialWeapon = route.weapon,
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Activities -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.ActivityScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        is WikiRoute.Announcements -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.AnnouncementScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        is WikiRoute.GameModes -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.GameModeScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenMapDetail = { name, imageUrl ->
                    navigateTo(WikiRoute.MapDetail(name, imageUrl))
                }
            )
        }
        is WikiRoute.Voting -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.VotingScreen(onBack = { popBackStack() })
        }
        is WikiRoute.BioCards -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.BioCardScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl,
                initialTab = 0
            )
        }
        is WikiRoute.BioMobileCards -> {
            // 兼容保留路由：当前无首页直达入口，仅用于未来深链或外部跳转扩展
            _root_ide_package_.com.nekolaska.calabiyau.ui.BioCardScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl,
                initialTab = 1
            )
        }
        is WikiRoute.Navigation -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.navigation.NavigationMenuScreen(
                onBack = { popBackStack() },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        is WikiRoute.Wallpapers -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.GalleryScreen(
                title = "壁纸",
                pageName = "壁纸",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Stickers -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.GalleryScreen(
                title = "表情包",
                pageName = "表情包",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Comics -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.GalleryScreen(
                title = "四格漫画",
                pageName = "官方四格漫画",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.BalanceData -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.BalanceDataScreen(
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Baseplates -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.BaseplateScreen(
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Encasings -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "封装",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.ChatBubbles -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "聊天气泡",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Headgear -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "头套",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.StringerActions -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "超弦体动作",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Medals -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "勋章",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.Sprays -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "喷漆",
                onBack = { popBackStack() }
            )
        }
        is WikiRoute.AvatarFrames -> {
            _root_ide_package_.com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                title = "头像框",
                onBack = { popBackStack() }
            )
        }
    }
    } // AnimatedContent
}
