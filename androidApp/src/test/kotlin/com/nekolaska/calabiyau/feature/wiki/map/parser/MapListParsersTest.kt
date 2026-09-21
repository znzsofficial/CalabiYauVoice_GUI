package com.nekolaska.calabiyau.feature.wiki.map.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapListParsersTest {

    @Test
    fun parsesCardsAndPrefers600pxOriginal() {
        val maps = MapListParsers.parseMapsFromHtml(
            """
            <div class="hvr-bounce-out">
              <a href="/klbq/404基地" title="404基地">
                <img src="https://patchwiki.biligame.com/images/klbq/thumb/b/b7/hash.png/300px-map.png"
                     srcset="https://patchwiki.biligame.com/images/klbq/thumb/b/b7/hash.png/450px-map.png 1.5x, https://patchwiki.biligame.com/images/klbq/thumb/b/b7/hash.png/600px-map.png 2x" />
              </a>
            </div>
            """.trimIndent()
        )

        assertEquals(1, maps.size)
        assertEquals("404基地", maps.single().name)
        assertEquals("https://wiki.biligame.com/klbq/404基地", maps.single().wikiUrl)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/b/b7/hash.png",
            maps.single().imageUrl
        )
    }

    @Test
    fun fallsBackToSrcWhenSrcsetHasNo600px() {
        val maps = MapListParsers.parseMapsFromHtml(
            """
            <div class="hvr-bounce-out">
              <a href="/klbq/风曳镇" title="风曳镇">
                <img src="https://patchwiki.biligame.com/images/klbq/thumb/8/8f/hash.png/300px-map.png"
                     srcset="https://patchwiki.biligame.com/images/klbq/thumb/8/8f/hash.png/450px-map.png 1.5x" />
              </a>
            </div>
            """.trimIndent()
        )

        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/8/8f/hash.png",
            maps.single().imageUrl
        )
    }

    @Test
    fun deduplicatesByNameAndSkipsCardsWithoutImage() {
        val maps = MapListParsers.parseMapsFromHtml(
            """
            <div class="hvr-bounce-out">
              <a href="/klbq/88区" title="88区">
                <img src="https://patchwiki.biligame.com/images/klbq/2/28/first.png" />
              </a>
            </div>
            <div class="hvr-bounce-out">
              <a href="/klbq/88区" title="88区">
                <img src="https://patchwiki.biligame.com/images/klbq/2/28/second.png" />
              </a>
            </div>
            <div class="hvr-bounce-out">
              <a href="/klbq/欧拉港口" title="欧拉港口">港口</a>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("88区"), maps.map { it.name })
        assertEquals("https://patchwiki.biligame.com/images/klbq/2/28/first.png", maps.single().imageUrl)
    }

    @Test
    fun parsesNewKlbqMapCardStructure() {
        val maps = MapListParsers.parseMapsFromHtml(
            """
            <div class="klbq-map">
              <div class="klbq-map-card">
                <a href="/klbq/莱布伦城" title="莱布伦城"><span class="klbq-map-card__link"></span></a>
                <div class="klbq-map-card__image">
                  <a href="/klbq/%E6%96%87%E4%BB%B6:%E5%9C%B0%E5%9B%BE-%E8%8E%B1%E5%B8%83%E4%BC%A6%E5%9F%8E.png" class="image">
                    <img alt="地图-莱布伦城.png"
                         src="https://patchwiki.biligame.com/images/klbq/e/e3/hash.png"
                         width="1920" height="1080" />
                  </a>
                </div>
                <div class="klbq-map-card__floating-name">
                  <a href="/klbq/莱布伦城" title="莱布伦城">莱布伦城</a>
                </div>
              </div>
              <div class="klbq-map-card">
                <a href="/klbq/404基地" title="404基地"><span class="klbq-map-card__link"></span></a>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, maps.size)
        assertEquals("莱布伦城", maps.single().name)
        assertEquals("https://wiki.biligame.com/klbq/莱布伦城", maps.single().wikiUrl)
        assertEquals("https://patchwiki.biligame.com/images/klbq/e/e3/hash.png", maps.single().imageUrl)
    }

    @Test
    fun emptyHtmlReturnsEmptyList() {
        assertTrue(MapListParsers.parseMapsFromHtml("<div class='mw-parser-output'></div>").isEmpty())
    }
}
