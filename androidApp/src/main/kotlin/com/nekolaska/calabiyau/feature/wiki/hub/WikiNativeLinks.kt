package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.MechanicsKind
import java.net.URI
import java.net.URLDecoder

/** Only navigation-directory links use this resolver. Explicit "Wiki original" actions stay web. */
internal fun nativeWikiRoute(url: String, characters: Set<String> = emptySet(), maps: Set<String> = emptySet(), weapons: Set<String> = emptySet()): WikiRoute? {
    val uri = runCatching { URI("https://wiki.biligame.com/klbq/").resolve(url) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.host != "wiki.biligame.com" || uri.userInfo != null ||
        uri.port !in setOf(-1, 80, 443) || !uri.rawFragment.isNullOrEmpty()) return null
    // Do not turn edit/history/old revisions, anchor destinations or arbitrary query actions into a different page.
    val title = if (uri.path == "/klbq/index.php") {
        val parts = uri.rawQuery.orEmpty().split('&')
        if (parts.size != 1 || !parts.single().startsWith("title=")) return null
        runCatching { URLDecoder.decode(parts.single().substringAfter('='), "UTF-8") }.getOrNull() ?: return null
    } else {
        if (uri.rawQuery != null || !uri.path.startsWith("/klbq/")) return null
        uri.path.removePrefix("/klbq/")
    }.replace('_', ' ')
    nativePages[title]?.let { return it }
    if (title in characters) return WikiRoute.CharDetail(title, null)
    if (title in maps) return WikiRoute.MapDetail(title, null)
    if (title in weapons) return WikiRoute.WeaponDetail(title)
    return null
}

private val nativePages: Map<String, WikiRoute> = mapOf(
    "首页" to WikiRoute.Home,
    "角色阵营" to WikiRoute.Characters,
    "武器" to WikiRoute.Weapons,
    "地图" to WikiRoute.Maps,
    "功能道具图鉴PC端" to WikiRoute.Items,
    "功能道具图鉴移动端" to WikiRoute.MobileItems,
    "时装筛选" to WikiRoute.Costumes(),
    "武器外观筛选" to WikiRoute.WeaponSkins(),
    "活动" to WikiRoute.Activities,
    "公告资讯" to WikiRoute.Announcements,
    "战斗模式" to WikiRoute.GameModes,
    "剧情故事" to WikiRoute.Story,
    "游戏历史" to WikiRoute.GameHistory,
    "游戏Tips" to WikiRoute.GameTips,
    "游戏设置PC端" to WikiRoute.PcGameSettings,
    "新人入坑指南" to WikiRoute.BeginnerGuide,
    "弦化" to WikiRoute.Mechanics(MechanicsKind.STRINGIFY),
    "超弦体定位" to WikiRoute.Mechanics(MechanicsKind.ROLES),
    "弦能增幅网络" to WikiRoute.Mechanics(MechanicsKind.AUGMENTATION),
    "壁纸" to WikiRoute.Wallpapers,
    "表情包" to WikiRoute.Stickers,
    "官方四格漫画" to WikiRoute.Comics,
    "梗百科" to WikiRoute.Memes,
    "联动" to WikiRoute.Collaborations,
    "喵言喵语" to WikiRoute.MeowLanguage,
    "BGM" to WikiRoute.Bgm,
    "投稿作品" to WikiRoute.Submissions,
    "誓约" to WikiRoute.Oath,
    "印迹" to WikiRoute.Imprints,
    "成就" to WikiRoute.Achievements,
    "玩家等级" to WikiRoute.PlayerLevels,
    // 好友 includes more than the native interaction-item subset, so it remains web.
    "基板" to WikiRoute.Baseplates,
    "个性名片" to WikiRoute.PersonalCards,
    "封装" to WikiRoute.Encasings,
    "勋章" to WikiRoute.Medals,
    "喷漆" to WikiRoute.Sprays,
    "聊天气泡" to WikiRoute.ChatBubbles,
    "头套" to WikiRoute.Headgear,
    "超弦体动作" to WikiRoute.StringerActions,
    "房间外观" to WikiRoute.RoomAppearances,
    "休息室手办" to WikiRoute.LoungeFigures,
    "头像框" to WikiRoute.AvatarFrames,
    "极限推进模式载具外观" to WikiRoute.VehicleSkins,
    "超弦体天赋" to WikiRoute.StringerTalents,
    "战斗模式/晶源感染/PC端卡牌筛选" to WikiRoute.BioCards,
    "战斗模式/晶源感染/移动端卡牌筛选" to WikiRoute.BioMobileCards,
    "战斗模式/弦溯战线/物品图鉴" to WikiRoute.ExtractionCatalog(ExtractionCatalogKind.LOOT), // verified former title
    "战斗模式/弦溯战线/掉落图鉴" to WikiRoute.ExtractionCatalog(ExtractionCatalogKind.DROPS)
) + (extractionEquipmentLinks + extractionGuideLinks).mapNotNull { link -> link.route?.let { link.article to it } }
