package com.nekolaska.calabiyau.feature.wiki.hub

/** 子页面枚举 (保留用于兼容上层或原有内部组件) */
enum class WikiHubPage {
    HOME, CHARACTERS, WEAPONS, MAPS, ITEMS, COSTUMES, WEAPON_SKINS, ACTIVITIES, ANNOUNCEMENTS, GAME_MODES, BALANCE_DATA, VOTING, BIO_CARDS,
    BIO_MOBILE_CARDS, // 兼容保留：当前 WikiHomePage 未提供独立入口（通过 BioCardScreen 内部 Tab 可切换）
    STORY, GAME_HISTORY, MEMES, COLLABORATIONS, BGM,
    NAVIGATION, WALLPAPERS, STICKERS, COMICS, MEOW_LANGUAGE, GAME_TIPS, BASEPLATES, ENCASINGS, MEDALS, SPRAYS, CHAT_BUBBLES, HEADGEAR, STRINGER_ACTIONS, STRINGER_TALENTS, STRINGER_PUSH_CARDS, AVATAR_FRAMES, ROOM_APPEARANCES, VEHICLE_SKINS, OATH, IMPRINTS, ACHIEVEMENTS, PLAYER_LEVELS, GAMEPLAY_HUB, DECORATION_HUB, CATALOG_HUB, EXTENSION_HUB
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
    data object Items : WikiRoute
    data class Costumes(val character: String?) : WikiRoute
    data class WeaponSkins(val weapon: String?) : WikiRoute
    data object Activities : WikiRoute
    data object Announcements : WikiRoute
    data object GameModes : WikiRoute
    data object BalanceData : WikiRoute
    data object Voting : WikiRoute
    data object BioCards : WikiRoute
    data object BioMobileCards : WikiRoute
    data object Story : WikiRoute
    data object GameHistory : WikiRoute
    data object Memes : WikiRoute
    data object Collaborations : WikiRoute
    data object Bgm : WikiRoute
    data object Navigation : WikiRoute
    data object Wallpapers : WikiRoute
    data object Stickers : WikiRoute
    data object Comics : WikiRoute
    data object MeowLanguage : WikiRoute
    data object GameTips : WikiRoute
    data object Baseplates : WikiRoute
    data object Encasings : WikiRoute
    data object Medals : WikiRoute
    data object Sprays : WikiRoute
    data object ChatBubbles : WikiRoute
    data object Headgear : WikiRoute
    data object StringerActions : WikiRoute
    data object StringerTalents : WikiRoute
    data object StringerPushCards : WikiRoute
    data object AvatarFrames : WikiRoute
    data object RoomAppearances : WikiRoute
    data object VehicleSkins : WikiRoute
    data object Oath : WikiRoute
    data object Imprints : WikiRoute
    data object Achievements : WikiRoute
    data object PlayerLevels : WikiRoute
    data object GameplayHub : WikiRoute
    data object DecorationHub : WikiRoute
    data object CatalogHub : WikiRoute
    data object ExtensionHub : WikiRoute
}

internal fun WikiHubPage.toRoute(): WikiRoute = when (this) {
    WikiHubPage.HOME -> WikiRoute.Home
    WikiHubPage.CHARACTERS -> WikiRoute.Characters
    WikiHubPage.WEAPONS -> WikiRoute.Weapons
    WikiHubPage.MAPS -> WikiRoute.Maps
    WikiHubPage.ACTIVITIES -> WikiRoute.Activities
    WikiHubPage.ANNOUNCEMENTS -> WikiRoute.Announcements
    WikiHubPage.GAME_MODES -> WikiRoute.GameModes
    WikiHubPage.ITEMS -> WikiRoute.Items
    WikiHubPage.VOTING -> WikiRoute.Voting
    WikiHubPage.BIO_CARDS -> WikiRoute.BioCards
    WikiHubPage.BIO_MOBILE_CARDS -> WikiRoute.BioMobileCards
    WikiHubPage.STORY -> WikiRoute.Story
    WikiHubPage.GAME_HISTORY -> WikiRoute.GameHistory
    WikiHubPage.MEMES -> WikiRoute.Memes
    WikiHubPage.COLLABORATIONS -> WikiRoute.Collaborations
    WikiHubPage.BGM -> WikiRoute.Bgm
    WikiHubPage.NAVIGATION -> WikiRoute.Navigation
    WikiHubPage.WALLPAPERS -> WikiRoute.Wallpapers
    WikiHubPage.STICKERS -> WikiRoute.Stickers
    WikiHubPage.COMICS -> WikiRoute.Comics
    WikiHubPage.MEOW_LANGUAGE -> WikiRoute.MeowLanguage
    WikiHubPage.GAME_TIPS -> WikiRoute.GameTips
    WikiHubPage.BALANCE_DATA -> WikiRoute.BalanceData
    WikiHubPage.BASEPLATES -> WikiRoute.Baseplates
    WikiHubPage.ENCASINGS -> WikiRoute.Encasings
    WikiHubPage.MEDALS -> WikiRoute.Medals
    WikiHubPage.SPRAYS -> WikiRoute.Sprays
    WikiHubPage.CHAT_BUBBLES -> WikiRoute.ChatBubbles
    WikiHubPage.HEADGEAR -> WikiRoute.Headgear
    WikiHubPage.STRINGER_ACTIONS -> WikiRoute.StringerActions
    WikiHubPage.STRINGER_TALENTS -> WikiRoute.StringerTalents
    WikiHubPage.STRINGER_PUSH_CARDS -> WikiRoute.StringerPushCards
    WikiHubPage.AVATAR_FRAMES -> WikiRoute.AvatarFrames
    WikiHubPage.ROOM_APPEARANCES -> WikiRoute.RoomAppearances
    WikiHubPage.VEHICLE_SKINS -> WikiRoute.VehicleSkins
    WikiHubPage.OATH -> WikiRoute.Oath
    WikiHubPage.IMPRINTS -> WikiRoute.Imprints
    WikiHubPage.ACHIEVEMENTS -> WikiRoute.Achievements
    WikiHubPage.PLAYER_LEVELS -> WikiRoute.PlayerLevels
    WikiHubPage.GAMEPLAY_HUB -> WikiRoute.GameplayHub
    WikiHubPage.DECORATION_HUB -> WikiRoute.DecorationHub
    WikiHubPage.CATALOG_HUB -> WikiRoute.CatalogHub
    WikiHubPage.EXTENSION_HUB -> WikiRoute.ExtensionHub
    WikiHubPage.COSTUMES -> WikiRoute.Costumes(null)
    WikiHubPage.WEAPON_SKINS -> WikiRoute.WeaponSkins(null)
}
