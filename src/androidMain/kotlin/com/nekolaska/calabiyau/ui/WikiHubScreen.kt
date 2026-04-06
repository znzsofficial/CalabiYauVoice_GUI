package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    isOverlaid: Boolean = false
) {
    var currentPage by remember { mutableStateOf(WikiHubPage.HOME) }
    val homeListState = rememberLazyListState()
    var selectedCharacterName by remember { mutableStateOf("") }
    var selectedCharacterPortrait by remember { mutableStateOf<String?>(null) }
    var selectedWeaponName by remember { mutableStateOf("") }
    var selectedCostumeCharacter by remember { mutableStateOf<String?>(null) }
    var selectedWeaponSkinWeapon by remember { mutableStateOf<String?>(null) }
    var costumesFrom by remember { mutableStateOf(WikiHubPage.HOME) }
    var weaponSkinsFrom by remember { mutableStateOf(WikiHubPage.HOME) }
    var characterListTab by remember { mutableIntStateOf(0) }
    var weaponListTab by remember { mutableIntStateOf(0) }
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
    var mapDetailFrom by remember { mutableStateOf(WikiHubPage.HOME) }

    BackHandler(enabled = currentPage != WikiHubPage.HOME && !isOverlaid) {
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

    when (currentPage) {
        WikiHubPage.HOME -> {
            WikiHomePage(
                onOpenDrawer = onOpenDrawer,
                onOpenWikiUrl = onOpenWikiUrl,
                listState = homeListState,
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
            CharacterListScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenCharacterDetail = { name, portrait ->
                    selectedCharacterName = name
                    selectedCharacterPortrait = portrait
                    currentPage = WikiHubPage.CHAR_DETAIL
                },
                initialTab = characterListTab,
                onTabChanged = { characterListTab = it }
            )
        }
        WikiHubPage.CHAR_DETAIL -> {
            CharacterDetailScreen(
                characterName = selectedCharacterName,
                portraitUrl = selectedCharacterPortrait,
                onBack = { currentPage = WikiHubPage.CHARACTERS },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenCostumes = { charName ->
                    selectedCostumeCharacter = charName
                    costumesFrom = WikiHubPage.CHAR_DETAIL
                    currentPage = WikiHubPage.COSTUMES
                },
                onOpenWeaponSkins = { weaponName ->
                    selectedWeaponSkinWeapon = weaponName
                    weaponSkinsFrom = WikiHubPage.CHAR_DETAIL
                    currentPage = WikiHubPage.WEAPON_SKINS
                }
            )
        }
        WikiHubPage.WEAPONS -> {
            WeaponListScreen(
                onBack = { currentPage = WikiHubPage.HOME },
                onOpenWeaponDetail = { name ->
                    selectedWeaponName = name
                    currentPage = WikiHubPage.WEAPON_DETAIL
                },
                initialTab = weaponListTab,
                onTabChanged = { weaponListTab = it }
            )
        }
        WikiHubPage.WEAPON_DETAIL -> {
            WeaponDetailScreen(
                weaponName = selectedWeaponName,
                onBack = { currentPage = WikiHubPage.WEAPONS },
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenWeaponSkins = { weaponName ->
                    selectedWeaponSkinWeapon = weaponName
                    weaponSkinsFrom = WikiHubPage.WEAPON_DETAIL
                    currentPage = WikiHubPage.WEAPON_SKINS
                }
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
                initialCharacter = selectedCostumeCharacter,
                onBack = {
                    selectedCostumeCharacter = null
                    currentPage = costumesFrom
                    costumesFrom = WikiHubPage.HOME
                }
            )
        }
        WikiHubPage.WEAPON_SKINS -> {
            WeaponSkinFilterScreen(
                initialWeapon = selectedWeaponSkinWeapon,
                onBack = {
                    selectedWeaponSkinWeapon = null
                    currentPage = weaponSkinsFrom
                    weaponSkinsFrom = WikiHubPage.HOME
                }
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
        WikiHubPage.BALANCE_DATA -> {
            BalanceDataScreen(
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.BASEPLATES -> {
            BaseplateScreen(
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.ENCASINGS -> {
            PlayerDecorationScreen(
                title = "封装",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.CHAT_BUBBLES -> {
            PlayerDecorationScreen(
                title = "聊天气泡",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.HEADGEAR -> {
            PlayerDecorationScreen(
                title = "头套",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.STRINGER_ACTIONS -> {
            PlayerDecorationScreen(
                title = "超弦体动作",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.MEDALS -> {
            PlayerDecorationScreen(
                title = "勋章",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.SPRAYS -> {
            PlayerDecorationScreen(
                title = "喷漆",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
        WikiHubPage.AVATAR_FRAMES -> {
            PlayerDecorationScreen(
                title = "头像框",
                onBack = { currentPage = WikiHubPage.HOME }
            )
        }
    }
}

