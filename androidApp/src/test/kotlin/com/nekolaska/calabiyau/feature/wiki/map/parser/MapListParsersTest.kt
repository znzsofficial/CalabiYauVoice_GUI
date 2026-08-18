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
    fun emptyHtmlReturnsEmptyList() {
        assertTrue(MapListParsers.parseMapsFromHtml("<div class='mw-parser-output'></div>").isEmpty())
    }
}
