package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.data.CharacterListApi
import com.nekolaska.calabiyau.data.MapListApi
import kotlinx.coroutines.launch
import data.ApiResult

// ════════════════════════════════════════════════════════
//  Wiki 主页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

/** 子页面枚举 */
enum class WikiHubPage { HOME, CHARACTERS, CHAR_DETAIL, WEAPONS, WEAPON_DETAIL, MAPS, MAP_DETAIL, COSTUMES, WEAPON_SKINS, ANNOUNCEMENTS, GAME_MODES, BALANCE_DATA, VOTING, NAVIGATION, WALLPAPERS, STICKERS, COMICS, BASEPLATES, ENCASINGS, MEDALS, SPRAYS, CHAT_BUBBLES, HEADGEAR, STRINGER_ACTIONS, AVATAR_FRAMES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    isOverlaid: Boolean = false,
    initialPage: WikiHubPage = WikiHubPage.HOME
) {
    var currentPage by rememberSaveable { mutableStateOf(initialPage) }
    val homeListState = rememberLazyListState()
    var selectedCharacterName by rememberSaveable { mutableStateOf("") }
    var selectedCharacterPortrait by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWeaponName by rememberSaveable { mutableStateOf("") }
    var selectedCostumeCharacter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWeaponSkinWeapon by rememberSaveable { mutableStateOf<String?>(null) }
    var costumesFrom by rememberSaveable { mutableStateOf(WikiHubPage.HOME) }
    var weaponSkinsFrom by rememberSaveable { mutableStateOf(WikiHubPage.HOME) }
    var characterListTab by rememberSaveable { mutableIntStateOf(0) }
    var weaponListTab by rememberSaveable { mutableIntStateOf(0) }
    var mapListTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMapName by rememberSaveable { mutableStateOf("") }
    var selectedMapImage by rememberSaveable { mutableStateOf<String?>(null) }
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
                    is ApiResult.Success -> factions = result.value
                    is ApiResult.Error -> { /* 静默失败 */ }
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
                    is ApiResult.Success -> gameModes = result.value
                    is ApiResult.Error -> { /* 静默失败 */ }
                }
                isLoadingMaps = false
            }
        } else {
            isLoadingMaps = false
        }
    }

    // 子页面按返回键回到上一级
    // 记录地图详情的来源页面（首页或战斗模式）
    var mapDetailFrom by rememberSaveable { mutableStateOf(WikiHubPage.HOME) }

    // ── 导航方向追踪（用于过渡动画） ──
    var isNavigatingBack by remember { mutableStateOf(false) }  // 动画方向无需持久化

    BackHandler(enabled = currentPage != WikiHubPage.HOME && !isOverlaid) {
        isNavigatingBack = true
        currentPage = when (currentPage) {
            WikiHubPage.CHAR_DETAIL -> WikiHubPage.CHARACTERS
            WikiHubPage.WEAPON_DETAIL -> WikiHubPage.WEAPONS
            WikiHubPage.MAP_DETAIL -> mapDetailFrom
            WikiHubPage.COSTUMES -> costumesFrom
            WikiHubPage.WEAPON_SKINS -> weaponSkinsFrom
            WikiHubPage.MAPS -> WikiHubPage.HOME
            else -> WikiHubPage.HOME
        }
    }

    // 包装导航回调：前进时标记方向
    fun navigateForward(page: WikiHubPage) {
        isNavigatingBack = false
        currentPage = page
    }
    fun navigateBack(page: WikiHubPage) {
        isNavigatingBack = true
        currentPage = page
    }

    val animDuration = 300
    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (isNavigatingBack) {
                // 返回：从左滑入 + 淡入，旧页面向右滑出 + 淡出
                (slideInHorizontally(tween(animDuration)) { -it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { it / 4 } + fadeOut(tween(animDuration / 2)))
            } else {
                // 前进：从右滑入 + 淡入，旧页面向左滑出 + 淡出
                (slideInHorizontally(tween(animDuration)) { it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { -it / 4 } + fadeOut(tween(animDuration / 2)))
            }
        },
        label = "WikiHubPageTransition"
    ) { page ->
    when (page) {
        WikiHubPage.HOME -> {
            WikiHomePage(
                onOpenDrawer = onOpenDrawer,
                onOpenWikiUrl = onOpenWikiUrl,
                listState = homeListState,
                onNavigateTo = { navigateForward(it) },
                onOpenCharacterDetail = { name, portrait ->
                    selectedCharacterName = name
                    selectedCharacterPortrait = portrait
                    navigateForward(WikiHubPage.CHAR_DETAIL)
                },
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.HOME
                    navigateForward(WikiHubPage.MAP_DETAIL)
                },
                factions = factions,
                isLoadingCharacters = isLoadingCharacters,
                gameModes = gameModes,
                isLoadingMaps = isLoadingMaps
            )
        }
        WikiHubPage.CHARACTERS -> {
            CharacterListScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenCharacterDetail = { name, portrait ->
                    selectedCharacterName = name
                    selectedCharacterPortrait = portrait
                    navigateForward(WikiHubPage.CHAR_DETAIL)
                },
                initialTab = characterListTab,
                onTabChanged = { characterListTab = it }
            )
        }
        WikiHubPage.CHAR_DETAIL -> {
            CharacterDetailScreen(
                characterName = selectedCharacterName,
                portraitUrl = selectedCharacterPortrait,
                onBack = { navigateBack(WikiHubPage.CHARACTERS) },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenCostumes = { charName ->
                    selectedCostumeCharacter = charName
                    costumesFrom = WikiHubPage.CHAR_DETAIL
                    navigateForward(WikiHubPage.COSTUMES)
                },
                onOpenWeaponSkins = { weaponName ->
                    selectedWeaponSkinWeapon = weaponName
                    weaponSkinsFrom = WikiHubPage.CHAR_DETAIL
                    navigateForward(WikiHubPage.WEAPON_SKINS)
                }
            )
        }
        WikiHubPage.WEAPONS -> {
            WeaponListScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenWeaponDetail = { name ->
                    selectedWeaponName = name
                    navigateForward(WikiHubPage.WEAPON_DETAIL)
                },
                initialTab = weaponListTab,
                onTabChanged = { weaponListTab = it }
            )
        }
        WikiHubPage.WEAPON_DETAIL -> {
            WeaponDetailScreen(
                weaponName = selectedWeaponName,
                onBack = { navigateBack(WikiHubPage.WEAPONS) },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenWeaponSkins = { weaponName ->
                    selectedWeaponSkinWeapon = weaponName
                    weaponSkinsFrom = WikiHubPage.WEAPON_DETAIL
                    navigateForward(WikiHubPage.WEAPON_SKINS)
                }
            )
        }
        WikiHubPage.MAPS -> {
            MapListFullScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.MAPS
                    navigateForward(WikiHubPage.MAP_DETAIL)
                },
                gameModes = gameModes,
                isLoading = isLoadingMaps,
                initialTab = mapListTab,
                onTabChanged = { mapListTab = it }
            )
        }
        WikiHubPage.MAP_DETAIL -> {
            MapDetailScreen(
                mapName = selectedMapName,
                mapImageUrl = selectedMapImage,
                onBack = { navigateBack(mapDetailFrom) },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.COSTUMES -> {
            CostumeFilterScreen(
                initialCharacter = selectedCostumeCharacter,
                onBack = {
                    selectedCostumeCharacter = null
                    navigateBack(costumesFrom)
                    costumesFrom = WikiHubPage.HOME
                }
            )
        }
        WikiHubPage.WEAPON_SKINS -> {
            WeaponSkinFilterScreen(
                initialWeapon = selectedWeaponSkinWeapon,
                onBack = {
                    selectedWeaponSkinWeapon = null
                    navigateBack(weaponSkinsFrom)
                    weaponSkinsFrom = WikiHubPage.HOME
                }
            )
        }
        WikiHubPage.ANNOUNCEMENTS -> {
            AnnouncementScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.GAME_MODES -> {
            GameModeScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenMapDetail = { name, imageUrl ->
                    selectedMapName = name
                    selectedMapImage = imageUrl
                    mapDetailFrom = WikiHubPage.GAME_MODES
                    navigateForward(WikiHubPage.MAP_DETAIL)
                }
            )
        }
        WikiHubPage.VOTING -> {
            VotingScreen(onBack = { navigateBack(WikiHubPage.HOME) })
        }
        WikiHubPage.NAVIGATION -> {
            NavigationMenuScreen(
                onBack = { navigateBack(WikiHubPage.HOME) },
                onOpenWikiUrl = onOpenWikiUrl
            )
        }
        WikiHubPage.WALLPAPERS -> {
            GalleryScreen(
                title = "壁纸",
                pageName = "壁纸",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.STICKERS -> {
            GalleryScreen(
                title = "表情包",
                pageName = "表情包",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.COMICS -> {
            GalleryScreen(
                title = "四格漫画",
                pageName = "官方四格漫画",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.BALANCE_DATA -> {
            BalanceDataScreen(
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.BASEPLATES -> {
            BaseplateScreen(
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.ENCASINGS -> {
            PlayerDecorationScreen(
                title = "封装",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.CHAT_BUBBLES -> {
            PlayerDecorationScreen(
                title = "聊天气泡",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.HEADGEAR -> {
            PlayerDecorationScreen(
                title = "头套",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.STRINGER_ACTIONS -> {
            PlayerDecorationScreen(
                title = "超弦体动作",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.MEDALS -> {
            PlayerDecorationScreen(
                title = "勋章",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.SPRAYS -> {
            PlayerDecorationScreen(
                title = "喷漆",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
        WikiHubPage.AVATAR_FRAMES -> {
            PlayerDecorationScreen(
                title = "头像框",
                onBack = { navigateBack(WikiHubPage.HOME) }
            )
        }
    }
    } // AnimatedContent
}

