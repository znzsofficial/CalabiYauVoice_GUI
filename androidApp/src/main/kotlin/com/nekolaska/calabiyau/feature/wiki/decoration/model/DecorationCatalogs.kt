package com.nekolaska.calabiyau.feature.wiki.decoration.model

internal enum class DecorationArtwork { FILE_FIELD, CHAT, HEADGEAR, ACTION, ROOM, AVATAR, VEHICLE, FIGURE, NAMECARD }

internal sealed interface DecorationCatalogSource {
    data object Html : DecorationCatalogSource

    /** Verified renderer rules and complete dependency pair, captured 2026-11-14. */
    data class Lua(
        val modulePage: String,
        val waysPage: String,
        val artwork: DecorationArtwork,
        val minimumItems: Int,
        val platform: String = "PC",
        val sortById: Boolean = true
    ) : DecorationCatalogSource
}

internal data class DecorationCatalogPolicy(val pageName: String, val source: DecorationCatalogSource)

internal object DecorationCatalogs {
    val policies = listOf(
        // These pages have filters/hand-authored sections/platform mixes; Lua enumeration is NOT their directory.
        DecorationCatalogPolicy("基板", DecorationCatalogSource.Html),
        DecorationCatalogPolicy("喷漆", DecorationCatalogSource.Html),
        DecorationCatalogPolicy("勋章", DecorationCatalogSource.Html),
        // Historical, currently absent upstream; retain generic HTML behavior, not an invented module.
        DecorationCatalogPolicy("登场特效", DecorationCatalogSource.Html),
        DecorationCatalogPolicy("封装", DecorationCatalogSource.Lua("模块:玩家装饰/FrameData", "模块:获得方式/Frame", DecorationArtwork.FILE_FIELD, 52)),
        DecorationCatalogPolicy("聊天气泡", DecorationCatalogSource.Lua("模块:玩家装饰/ChatBubblesData", "模块:获得方式/ChatBubbles", DecorationArtwork.CHAT, 37)),
        DecorationCatalogPolicy("头套", DecorationCatalogSource.Lua("模块:玩家装饰/MascotHeadData", "模块:获得方式/MascotHead", DecorationArtwork.HEADGEAR, 9)),
        DecorationCatalogPolicy("超弦体动作", DecorationCatalogSource.Lua("模块:玩家装饰/RoleActionData", "模块:获得方式/RoleAction", DecorationArtwork.ACTION, 16)),
        DecorationCatalogPolicy("房间外观", DecorationCatalogSource.Lua("模块:玩家装饰/ChangeBgData", "模块:获得方式/ChangeBg", DecorationArtwork.ROOM, 7)),
        DecorationCatalogPolicy("头像框", DecorationCatalogSource.Lua("模块:玩家装饰移动端/HeadFrameData", "模块:获得方式/HeadFrameMobile", DecorationArtwork.AVATAR, 71, "移动端", false)),
        DecorationCatalogPolicy("极限推进模式载具外观", DecorationCatalogSource.Lua("模块:玩家装饰移动端/VehicleSkinData", "模块:获得方式/VehicleSkinMobile", DecorationArtwork.VEHICLE, 15, "移动端", false)),
        DecorationCatalogPolicy("休息室手办", DecorationCatalogSource.Lua("模块:玩家装饰/GarageKitsData", "模块:获得方式/GarageKits", DecorationArtwork.FIGURE, 14)),
        DecorationCatalogPolicy("个性名片", DecorationCatalogSource.Lua("模块:玩家装饰/ProfileCardData", "模块:获得方式/ProfileCard", DecorationArtwork.NAMECARD, 5))
    )
    private val byPage = policies.associateBy { it.pageName }
    fun forPage(pageName: String) = byPage[pageName] ?: DecorationCatalogPolicy(pageName, DecorationCatalogSource.Html)
}
