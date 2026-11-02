package com.nekolaska.calabiyau.feature.wiki.map.parser

import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import kotlin.test.*

class RiftMapParsingTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/maps/$name")).readText(Charsets.UTF_8)

    @Test fun riftModeAndItsMapAreInTheNativeList() {
        assertTrue(MapListApi.GAME_MODES.contains("弦溯战线" to "弦溯战线"))
        val map = MapListParsers.parseMapsFromHtml(fixture("rift_list.html")).single()
        assertEquals("维里昂裂隙", map.name)
        assertTrue(map.imageUrl.endsWith("pwnxujluq4ixee68nk1fki4kdq1x4li.png"))
    }

    @Test fun parsesRealRiftOverviewTerrainAndPlatformsWithoutInventingGalleryOrHistory() {
        val detail = assertNotNull(MapDetailParsers.parseMapWikitext("维里昂裂隙", fixture("rift.wikitext"), fixture("rift.html")))
        assertTrue(detail.description.contains("独立城市位面"))
        assertEquals("弦溯战线", detail.supportedModes)
        assertEquals("PC端", detail.platforms)
        assertEquals("https://patchwiki.biligame.com/images/klbq/a/af/pwnxujluq4ixee68nk1fki4kdq1x4li.png", detail.overviewImageUrl)
        assertEquals("https://patchwiki.biligame.com/images/klbq/0/06/9zo7new3kqy4prdxmzuwhnim3gljn0p.png", detail.terrainMapUrl)
        assertTrue(detail.galleryUrls.isEmpty())
        assertTrue(detail.updateHistory.isEmpty())
    }

    @Test fun prefixedMapTemplatesCannotMaskTheRealMapTemplate() {
        val detail = assertNotNull(MapDetailParsers.parseMapWikitext("维里昂裂隙",
            "{{地图导航|简介=错误}}\n" + fixture("rift.wikitext"), fixture("rift.html")))
        assertTrue(detail.description.contains("维里昂"))
        assertNull(MapDetailParsers.parseMapWikitext("x", "{{地图音乐|名称=x}}", ""))
    }

    @Test fun absentOrForeignBackgroundDoesNotInventAnOverviewUrl() {
        val wt = "{{地图|简介=简介|支持模式=弦溯战线}}"
        assertNull(MapDetailParsers.parseMapWikitext("x", wt, "<style>body{background:url(https://example.test/unrelated.png)}</style>")?.overviewImageUrl)
        assertNull(MapDetailParsers.parseMapWikitext("x", "{{背景|地图-x.png}}$wt",
            "<style>body{background:url(https://example.test/unrelated.png)}</style>")?.overviewImageUrl)
        assertNull(MapDetailParsers.parseMapWikitext("x", "{{背景|地图-x.png}}$wt",
            "<style>.navbox{background:url(https://patchwiki.biligame.com/images/unrelated.png)}</style>")?.overviewImageUrl)
    }
}
