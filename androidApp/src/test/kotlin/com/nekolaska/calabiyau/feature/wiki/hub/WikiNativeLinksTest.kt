package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.mechanics.model.MechanicsKind
import kotlin.test.*

class WikiNativeLinksTest {
    @Test fun resolvesOnlyExactKnownArticleDestinations() {
        assertEquals(WikiRoute.PcGameSettings, nativeWikiRoute("https://wiki.biligame.com/klbq/游戏设置PC端"))
        assertEquals(WikiRoute.BeginnerGuide, nativeWikiRoute("/klbq/index.php?title=%E6%96%B0%E4%BA%BA%E5%85%A5%E5%9D%91%E6%8C%87%E5%8D%97"))
        assertEquals(WikiRoute.Mechanics(MechanicsKind.STRINGIFY), nativeWikiRoute("/klbq/%E5%BC%A6%E5%8C%96"))
        assertEquals(WikiRoute.CharDetail("奥黛丽·格罗夫", null), nativeWikiRoute("/klbq/奥黛丽·格罗夫", characters = setOf("奥黛丽·格罗夫")))
        assertNull(nativeWikiRoute("/klbq/奥黛丽", characters = setOf("奥黛丽·格罗夫")))
        assertEquals(WikiRoute.MapDetail("测试地图", null), nativeWikiRoute("/klbq/测试地图", maps = setOf("测试地图")))
        assertEquals(WikiRoute.WeaponDetail("静风"), nativeWikiRoute("/klbq/静风", weapons = setOf("静风")))
        assertNull(nativeWikiRoute("/klbq/静风"))
    }
    @Test fun anchorsActionsOldRevisionsAndOtherHostsStayWeb() {
        listOf("/klbq/游戏设置PC端#画面", "/klbq/游戏设置PC端?oldid=123", "/klbq/index.php?title=游戏设置PC端&action=edit",
            "/klbq/index.php?title=游戏设置PC端&title=弦化", "https://example.com/klbq/弦化", "https://wiki.biligame.com.evil.com/klbq/弦化",
            "https://user@wiki.biligame.com/klbq/弦化", "javascript:alert(1)", "/other/弦化", "/klbq/好友",
            "/klbq/战斗模式/超弦推进", "/klbq/特别行动", "/klbq/Special:UserLogin", "/klbq/游戏设置PC端/未知")
            .forEach { assertNull(nativeWikiRoute(it), it) }
    }
    @Test fun mechanicsRouteRoundTripsAndUnknownKindsFailClosed() {
        MechanicsKind.entries.forEach { kind -> assertEquals(WikiRoute.Mechanics(kind), decodeRoute(WikiRoute.Mechanics(kind).encode())) }
        assertNull(decodeRoute("mechanics|UNKNOWN"))
        assertEquals(WikiRoute.GameTips, decodeRoute("gameTips"))
    }
}
