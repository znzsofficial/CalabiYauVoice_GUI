package com.nekolaska.calabiyau.feature.wiki.hub

import android.net.Uri
import androidx.compose.runtime.saveable.listSaver

private const val ROUTE_SEPARATOR = "|"

private fun encodeRoutePart(value: String?): String = Uri.encode(value.orEmpty())

private fun decodeRoutePart(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    return Uri.decode(value)
}

internal fun WikiRoute.encode(): String = when (this) {
    WikiRoute.Home -> "home"
    WikiRoute.Characters -> "characters"
    is WikiRoute.CharDetail -> "charDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}$ROUTE_SEPARATOR${encodeRoutePart(portrait)}"
    WikiRoute.Weapons -> "weapons"
    is WikiRoute.WeaponDetail -> "weaponDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}"
    WikiRoute.Maps -> "maps"
    is WikiRoute.MapDetail -> "mapDetail$ROUTE_SEPARATOR${encodeRoutePart(name)}$ROUTE_SEPARATOR${encodeRoutePart(imageUrl)}"
    WikiRoute.Items -> "items"
    is WikiRoute.Costumes -> "costumes$ROUTE_SEPARATOR${encodeRoutePart(character)}"
    is WikiRoute.WeaponSkins -> "weaponSkins$ROUTE_SEPARATOR${encodeRoutePart(weapon)}"
    WikiRoute.Activities -> "activities"
    WikiRoute.Announcements -> "announcements"
    WikiRoute.GameModes -> "gameModes"
    WikiRoute.BalanceData -> "balanceData"
    WikiRoute.Voting -> "voting"
    WikiRoute.BioCards -> "bioCards"
    WikiRoute.BioMobileCards -> "bioMobileCards"
    WikiRoute.Story -> "story"
    WikiRoute.GameHistory -> "gameHistory"
    WikiRoute.Memes -> "memes"
    WikiRoute.Collaborations -> "collaborations"
    WikiRoute.Bgm -> "bgm"
    WikiRoute.Navigation -> "navigation"
    WikiRoute.Wallpapers -> "wallpapers"
    WikiRoute.Stickers -> "stickers"
    WikiRoute.Comics -> "comics"
    WikiRoute.MeowLanguage -> "meowLanguage"
    WikiRoute.GameTips -> "gameTips"
    WikiRoute.Baseplates -> "baseplates"
    WikiRoute.Encasings -> "encasings"
    WikiRoute.Medals -> "medals"
    WikiRoute.Sprays -> "sprays"
    WikiRoute.ChatBubbles -> "chatBubbles"
    WikiRoute.Headgear -> "headgear"
    WikiRoute.StringerActions -> "stringerActions"
    WikiRoute.StringerTalents -> "stringerTalents"
    WikiRoute.StringerPushCards -> "stringerPushCards"
    WikiRoute.AvatarFrames -> "avatarFrames"
    WikiRoute.RoomAppearances -> "roomAppearances"
    WikiRoute.VehicleSkins -> "vehicleSkins"
    WikiRoute.Oath -> "oath"
    WikiRoute.Imprints -> "imprints"
    WikiRoute.Achievements -> "achievements"
    WikiRoute.PlayerLevels -> "playerLevels"
    WikiRoute.GameplayHub -> "gameplayHub"
    WikiRoute.DecorationHub -> "decorationHub"
    WikiRoute.CatalogHub -> "catalogHub"
    WikiRoute.ExtensionHub -> "extensionHub"
}

internal fun decodeRoute(encoded: String): WikiRoute? {
    val parts = encoded.split(ROUTE_SEPARATOR)
    return when (parts.firstOrNull()) {
        "home" -> WikiRoute.Home
        "characters" -> WikiRoute.Characters
        "charDetail" -> WikiRoute.CharDetail(
            name = decodeRoutePart(parts.getOrNull(1)) ?: return null,
            portrait = decodeRoutePart(parts.getOrNull(2))
        )
        "weapons" -> WikiRoute.Weapons
        "weaponDetail" -> WikiRoute.WeaponDetail(name = decodeRoutePart(parts.getOrNull(1)) ?: return null)
        "maps" -> WikiRoute.Maps
        "mapDetail" -> WikiRoute.MapDetail(
            name = decodeRoutePart(parts.getOrNull(1)) ?: return null,
            imageUrl = decodeRoutePart(parts.getOrNull(2))
        )
        "items" -> WikiRoute.Items
        "costumes" -> WikiRoute.Costumes(decodeRoutePart(parts.getOrNull(1)))
        "weaponSkins" -> WikiRoute.WeaponSkins(decodeRoutePart(parts.getOrNull(1)))
        "activities" -> WikiRoute.Activities
        "announcements" -> WikiRoute.Announcements
        "gameModes" -> WikiRoute.GameModes
        "balanceData" -> WikiRoute.BalanceData
        "voting" -> WikiRoute.Voting
        "bioCards" -> WikiRoute.BioCards
        "bioMobileCards" -> WikiRoute.BioMobileCards
        "story" -> WikiRoute.Story
        "gameHistory" -> WikiRoute.GameHistory
        "memes" -> WikiRoute.Memes
        "collaborations" -> WikiRoute.Collaborations
        "bgm" -> WikiRoute.Bgm
        "navigation" -> WikiRoute.Navigation
        "wallpapers" -> WikiRoute.Wallpapers
        "stickers" -> WikiRoute.Stickers
        "comics" -> WikiRoute.Comics
        "meowLanguage" -> WikiRoute.MeowLanguage
        "gameTips" -> WikiRoute.GameTips
        "baseplates" -> WikiRoute.Baseplates
        "encasings" -> WikiRoute.Encasings
        "medals" -> WikiRoute.Medals
        "sprays" -> WikiRoute.Sprays
        "chatBubbles" -> WikiRoute.ChatBubbles
        "headgear" -> WikiRoute.Headgear
        "stringerActions" -> WikiRoute.StringerActions
        "stringerTalents" -> WikiRoute.StringerTalents
        "stringerPushCards" -> WikiRoute.StringerPushCards
        "avatarFrames" -> WikiRoute.AvatarFrames
        "roomAppearances" -> WikiRoute.RoomAppearances
        "vehicleSkins" -> WikiRoute.VehicleSkins
        "oath" -> WikiRoute.Oath
        "imprints" -> WikiRoute.Imprints
        "achievements" -> WikiRoute.Achievements
        "playerLevels" -> WikiRoute.PlayerLevels
        "gameplayHub" -> WikiRoute.GameplayHub
        "decorationHub" -> WikiRoute.DecorationHub
        "catalogHub" -> WikiRoute.CatalogHub
        "extensionHub" -> WikiRoute.ExtensionHub
        else -> null
    }
}

internal val wikiRouteStackSaver = listSaver<List<WikiRoute>, String>(
    save = { stack -> stack.map(WikiRoute::encode) },
    restore = { encodedStack ->
        encodedStack.mapNotNull(::decodeRoute).ifEmpty { listOf(WikiRoute.Home) }
    }
)
