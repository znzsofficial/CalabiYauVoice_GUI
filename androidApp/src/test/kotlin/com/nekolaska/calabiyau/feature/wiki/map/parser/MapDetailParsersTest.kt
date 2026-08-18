package com.nekolaska.calabiyau.feature.wiki.map.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDetailParsersTest {

    @Test
    fun missingMapTemplateReturnsNull() {
        assertNull(MapDetailParsers.parseMapWikitext("404基地", "{{面包屑|地图}}", "<p></p>"))
    }

    @Test
    fun extractsTemplateFieldsAndSectionImages() {
        val detail = MapDetailParsers.parseMapWikitext(
            name = "404基地",
            wikitext = """
                {{面包屑|地图}}
                {{地图
                |简介=秘密基地。
                |支持模式=排位爆破、一般爆破
                |上线平台=PC端、移动端
                }}
            """.trimIndent(),
            html = """
                <h2><span class="mw-headline">地形图</span></h2>
                <p><img src="https://patchwiki.biligame.com/images/klbq/thumb/1/11/terrain.png/600px-terrain.png" /></p>
                <h2><span class="mw-headline">地图概览</span></h2>
                <p>
                  <img src="https://patchwiki.biligame.com/images/klbq/thumb/2/22/a.png/300px-a.png" />
                  <img src="https://patchwiki.biligame.com/images/klbq/thumb/3/33/b.png/300px-b.png" />
                </p>
                <h2><span class="mw-headline">旧版地图概览</span></h2>
                <p><img src="https://patchwiki.biligame.com/images/klbq/thumb/2/22/a.png/300px-a.png" /></p>
                <h2><span class="mw-headline">地图音乐</span></h2>
                <p><img src="https://patchwiki.biligame.com/images/klbq/thumb/9/99/skip.png/300px-skip.png" /></p>
            """.trimIndent()
        )!!

        assertEquals("404基地", detail.name)
        assertEquals("秘密基地。", detail.description)
        assertEquals("排位爆破、一般爆破", detail.supportedModes)
        assertEquals("PC端、移动端", detail.platforms)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/1/11/terrain.png",
            detail.terrainMapUrl
        )
        assertEquals(
            listOf(
                "https://patchwiki.biligame.com/images/klbq/2/22/a.png",
                "https://patchwiki.biligame.com/images/klbq/3/33/b.png"
            ),
            detail.galleryUrls
        )
        assertTrue(detail.updateHistory.isEmpty())
    }

    @Test
    fun nestedTemplatesDoNotBreakFieldExtraction() {
        val detail = MapDetailParsers.parseMapWikitext(
            name = "风曳镇",
            wikitext = """
                {{地图
                |简介=简介{{信息|class=info|text=旁注}}正文
                |支持模式=极限推进
                |上线平台=移动端
                }}
            """.trimIndent(),
            html = "<div></div>"
        )!!

        assertEquals("简介{{信息|class=info|text=旁注}}正文", detail.description)
        assertEquals("极限推进", detail.supportedModes)
        assertEquals("移动端", detail.platforms)
        assertNull(detail.terrainMapUrl)
        assertTrue(detail.galleryUrls.isEmpty())
    }
}
