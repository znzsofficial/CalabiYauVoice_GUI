package com.nekolaska.calabiyau.feature.wiki.hub

/** 子页面路由（替代上帝变量状态的路由密封接口） */
sealed interface WikiRoute {
    data object Home : WikiRoute
    data object Characters : WikiRoute
    data class CharDetail(val name: String, val portrait: String?, val source: String = "list") : WikiRoute
    data object Weapons : WikiRoute
    data class WeaponDetail(val name: String, val imageUrl: String? = null) : WikiRoute
    data object Maps : WikiRoute
    data class MapDetail(val name: String, val imageUrl: String?) : WikiRoute
    data object Items : WikiRoute
    data class Costumes(val character: String? = null) : WikiRoute
    data class WeaponSkins(val weapon: String? = null) : WikiRoute
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
