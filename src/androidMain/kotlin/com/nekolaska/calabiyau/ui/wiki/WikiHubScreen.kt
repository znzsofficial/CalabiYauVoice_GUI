package com.nekolaska.calabiyau.ui.wiki

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.data.CharacterListApi
import com.nekolaska.calabiyau.data.MapListApi
import com.nekolaska.calabiyau.ui.shared.rememberLoadState

// ════════════════════════════════════════════════════════
//  Wiki 主页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

/** 子页面枚举 (保留用于兼容上层或原有内部组件) */
enum class WikiHubPage {
    HOME, CHARACTERS, WEAPONS, MAPS, COSTUMES, WEAPON_SKINS, ACTIVITIES, ANNOUNCEMENTS, GAME_MODES, BALANCE_DATA, VOTING, BIO_CARDS,
    BIO_MOBILE_CARDS, // 兼容保留：当前 WikiHomePage 未提供独立入口（通过 BioCardScreen 内部 Tab 可切换）
    NAVIGATION, WALLPAPERS, STICKERS, COMICS, BASEPLATES, ENCASINGS, MEDALS, SPRAYS, CHAT_BUBBLES, HEADGEAR, STRINGER_ACTIONS, AVATAR_FRAMES, ROOM_APPEARANCES, VEHICLE_SKINS
}

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
    data object RoomAppearances : WikiRoute
    data object VehicleSkins : WikiRoute
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
    WikiHubPage.ROOM_APPEARANCES -> WikiRoute.RoomAppearances
    WikiHubPage.VEHICLE_SKINS -> WikiRoute.VehicleSkins
    // 以下带参数页面由于是从 WikiHomePage 跳转而来，按理说不会直接触发（通常走具名参数跳转），给出默认保底
    WikiHubPage.COSTUMES -> WikiRoute.Costumes(null)
    WikiHubPage.WEAPON_SKINS -> WikiRoute.WeaponSkins(null)
}

private const val ROUTE_SEPARATOR = "|"

private fun encodeRoutePart(value: String?): String = Uri.encode(value.orEmpty())

private fun decodeRoutePart(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    return Uri.decode(value)
}

private fun WikiRoute.encode(): String = when (this) {
    WikiRoute.Home -> "home"
    WikiRoute.Characters -> "characters"
    is WikiRoute.CharDetail -> "charDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}$ROUTE_SEPARATOR${
        encodeRoutePart(
            portrait
        )
    }"

    WikiRoute.Weapons -> "weapons"
    is WikiRoute.WeaponDetail -> "weaponDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}"
    WikiRoute.Maps -> "maps"
    is WikiRoute.MapDetail -> "mapDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}$ROUTE_SEPARATOR${
        encodeRoutePart(
            imageUrl
        )
    }"

    is WikiRoute.Costumes -> "costumes$ROUTE_SEPARATOR${encodeRoutePart(character)}"
    is WikiRoute.WeaponSkins -> "weaponSkins$ROUTE_SEPARATOR${encodeRoutePart(weapon)}"
    WikiRoute.Activities -> "activities"
    WikiRoute.Announcements -> "announcements"
    WikiRoute.GameModes -> "gameModes"
    WikiRoute.BalanceData -> "balanceData"
    WikiRoute.Voting -> "voting"
    WikiRoute.BioCards -> "bioCards"
    WikiRoute.BioMobileCards -> "bioMobileCards"
    WikiRoute.Navigation -> "navigation"
    WikiRoute.Wallpapers -> "wallpapers"
    WikiRoute.Stickers -> "stickers"
    WikiRoute.Comics -> "comics"
    WikiRoute.Baseplates -> "baseplates"
    WikiRoute.Encasings -> "encasings"
    WikiRoute.Medals -> "medals"
    WikiRoute.Sprays -> "sprays"
    WikiRoute.ChatBubbles -> "chatBubbles"
    WikiRoute.Headgear -> "headgear"
    WikiRoute.StringerActions -> "stringerActions"
    WikiRoute.AvatarFrames -> "avatarFrames"
    WikiRoute.RoomAppearances -> "roomAppearances"
    WikiRoute.VehicleSkins -> "vehicleSkins"
}

private fun decodeRoute(encoded: String): WikiRoute? {
    val parts = encoded.split(ROUTE_SEPARATOR)
    return when (parts.firstOrNull()) {
        "home" -> WikiRoute.Home
        "characters" -> WikiRoute.Characters
        "charDetail" -> WikiRoute.CharDetail(
            name = decodeRoutePart(parts.getOrNull(1)) ?: return null,
            portrait = decodeRoutePart(parts.getOrNull(2))
        )

        "weapons" -> WikiRoute.Weapons
        "weaponDetail" -> WikiRoute.WeaponDetail(
            name = decodeRoutePart(parts.getOrNull(1)) ?: return null
        )

        "maps" -> WikiRoute.Maps
        "mapDetail" -> WikiRoute.MapDetail(
            name = decodeRoutePart(parts.getOrNull(1)) ?: return null,
            imageUrl = decodeRoutePart(parts.getOrNull(2))
        )

        "costumes" -> WikiRoute.Costumes(decodeRoutePart(parts.getOrNull(1)))
        "weaponSkins" -> WikiRoute.WeaponSkins(decodeRoutePart(parts.getOrNull(1)))
        "activities" -> WikiRoute.Activities
        "announcements" -> WikiRoute.Announcements
        "gameModes" -> WikiRoute.GameModes
        "balanceData" -> WikiRoute.BalanceData
        "voting" -> WikiRoute.Voting
        "bioCards" -> WikiRoute.BioCards
        "bioMobileCards" -> WikiRoute.BioMobileCards
        "navigation" -> WikiRoute.Navigation
        "wallpapers" -> WikiRoute.Wallpapers
        "stickers" -> WikiRoute.Stickers
        "comics" -> WikiRoute.Comics
        "baseplates" -> WikiRoute.Baseplates
        "encasings" -> WikiRoute.Encasings
        "medals" -> WikiRoute.Medals
        "sprays" -> WikiRoute.Sprays
        "chatBubbles" -> WikiRoute.ChatBubbles
        "headgear" -> WikiRoute.Headgear
        "stringerActions" -> WikiRoute.StringerActions
        "avatarFrames" -> WikiRoute.AvatarFrames
        "roomAppearances" -> WikiRoute.RoomAppearances
        "vehicleSkins" -> WikiRoute.VehicleSkins
        else -> null
    }
}

private val wikiRouteStackSaver = listSaver<List<WikiRoute>, String>(
    save = { stack -> stack.map(WikiRoute::encode) },
    restore = { encodedStack ->
        encodedStack.mapNotNull(::decodeRoute).ifEmpty { listOf(WikiRoute.Home) }
    }
)

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
        if (backStack.lastOrNull() == route) return
        isNavigatingBack = false
        backStack = backStack + route
    }

    BackHandler(enabled = backStack.size > 1 && !isOverlaid) {
        popBackStack()
    }

    // 采用 MD3 约定的 Shared Axis (X轴) 过渡：轻量位移配合快速淡入淡出，比大范围拉扯更具现代感
    AnimatedContent(
        targetState = currentRoute,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val duration = 400
            if (isNavigatingBack) {
                // 返回：从左滑入(强调减速) + 淡入，旧页向右滑出(加速) + 淡出
                (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { -it / 8 } + 
                    fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                    .togetherWith(
                        slideOutHorizontally(tween(duration / 2, easing = FastOutLinearInEasing)) { it / 8 } + 
                        fadeOut(tween(duration / 2, easing = FastOutLinearInEasing))
                    )
            } else {
                // 前进：从右滑入(强调减速) + 淡入，旧页向左滑出(加速) + 淡出
                (slideInHorizontally(tween(duration, easing = LinearOutSlowInEasing)) { it / 8 } + 
                    fadeIn(tween(duration, easing = LinearOutSlowInEasing)))
                    .togetherWith(
                        slideOutHorizontally(tween(duration / 2, easing = FastOutLinearInEasing)) { -it / 8 } + 
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
                com.nekolaska.calabiyau.ui.character.CharacterListScreen(
                    onBack = { popBackStack() },
                    onOpenCharacterDetail = { name, portrait ->
                        navigateTo(WikiRoute.CharDetail(name, portrait))
                    },
                    initialTab = characterListTab,
                    onTabChanged = { characterListTab = it }
                )
            }

            is WikiRoute.CharDetail -> {
                com.nekolaska.calabiyau.ui.character.CharacterDetailScreen(
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
                com.nekolaska.calabiyau.ui.weapon.WeaponListScreen(
                    onBack = { popBackStack() },
                    onOpenWeaponDetail = { name ->
                        navigateTo(WikiRoute.WeaponDetail(name))
                    },
                    initialTab = weaponListTab,
                    onTabChanged = { weaponListTab = it }
                )
            }

            is WikiRoute.WeaponDetail -> {
                com.nekolaska.calabiyau.ui.weapon.WeaponDetailScreen(
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
                com.nekolaska.calabiyau.ui.MapDetailScreen(
                    mapName = route.name,
                    mapImageUrl = route.imageUrl,
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Costumes -> {
                com.nekolaska.calabiyau.ui.character.CostumeFilterScreen(
                    initialCharacter = route.character,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.WeaponSkins -> {
                com.nekolaska.calabiyau.ui.weapon.WeaponSkinFilterScreen(
                    initialWeapon = route.weapon,
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Activities -> {
                com.nekolaska.calabiyau.ui.ActivityScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Announcements -> {
                com.nekolaska.calabiyau.ui.AnnouncementScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.GameModes -> {
                com.nekolaska.calabiyau.ui.GameModeScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenMapDetail = { name, imageUrl ->
                        navigateTo(WikiRoute.MapDetail(name, imageUrl))
                    }
                )
            }

            is WikiRoute.Voting -> {
                com.nekolaska.calabiyau.ui.VotingScreen(onBack = { popBackStack() })
            }

            is WikiRoute.BioCards -> {
                com.nekolaska.calabiyau.ui.BioCardScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    initialTab = 0
                )
            }

            is WikiRoute.BioMobileCards -> {
                // 兼容保留路由：当前无首页直达入口，仅用于未来深链或外部跳转扩展
                com.nekolaska.calabiyau.ui.BioCardScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl,
                    initialTab = 1
                )
            }

            is WikiRoute.Navigation -> {
                com.nekolaska.calabiyau.ui.navigation.NavigationMenuScreen(
                    onBack = { popBackStack() },
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }

            is WikiRoute.Wallpapers -> {
                com.nekolaska.calabiyau.ui.GalleryScreen(
                    title = "壁纸",
                    pageName = "壁纸",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Stickers -> {
                com.nekolaska.calabiyau.ui.GalleryScreen(
                    title = "表情包",
                    pageName = "表情包",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Comics -> {
                com.nekolaska.calabiyau.ui.GalleryScreen(
                    title = "四格漫画",
                    pageName = "官方四格漫画",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.BalanceData -> {
                com.nekolaska.calabiyau.ui.BalanceDataScreen(
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Baseplates -> {
                com.nekolaska.calabiyau.ui.BaseplateScreen(
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Encasings -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "封装",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.ChatBubbles -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "聊天气泡",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Headgear -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "头套",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.StringerActions -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "超弦体动作",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Medals -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "勋章",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.Sprays -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "喷漆",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.AvatarFrames -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "头像框",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.RoomAppearances -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "房间外观",
                    onBack = { popBackStack() }
                )
            }

            is WikiRoute.VehicleSkins -> {
                com.nekolaska.calabiyau.ui.PlayerDecorationScreen(
                    title = "极限推进模式载具外观",
                    onBack = { popBackStack() }
                )
            }
        }
    } // AnimatedContent
}
