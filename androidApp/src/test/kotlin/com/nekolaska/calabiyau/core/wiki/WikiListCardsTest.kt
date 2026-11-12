package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.feature.character.list.CharacterListParser
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class WikiListCardsTest {
    private fun payload(html: String) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()

    @Test fun mixedStructuresMissingImagesLazyImagesAndDuplicateCardsKeepAllIdentities() {
        val html = """<div class='klbq-map-card'><a href='/klbq/甲'>别名</a></div>
            <div class='hvr-bounce-out'><a href='https://wiki.biligame.com/klbq/%E4%B9%99' title='错误提示'>乙</a>
            <img src='data:image/gif;base64,x' data-src='//patchwiki.biligame.com/images/klbq/a.png'></div>
            <div class='hvr-bounce-out'><a href='/klbq/甲'><img src='https://patchwiki.biligame.com/images/klbq/b.png'></a></div>"""
        val maps = MapListParsers.parseMapsFromHtml(html)
        assertEquals(listOf("甲", "乙"), maps.map { it.name })
        assertTrue(maps.all { it.imageUrl.startsWith("https://patchwiki") })
        assertTrue(MapListParsers.isValidPayload(payload(html)))
    }

    @Test fun articleLinksAreNotFilesSpecialPagesExternalUrlsOrAmbiguousCards() {
        listOf("https://evil.test/klbq/甲", "/klbq/文件:甲.png", "/klbq/%E7%89%B9%E6%AE%8A:上传文件", "/klbq/index.php?action=edit")
            .forEach { assertNull(wikiArticleTitle(it)) }
        val valid = "<div class='hvr-bounce-out'><a href='/klbq/甲'>甲</a></div>"
        assertTrue(CharacterListParser.isValidPayload(payload(valid)))
        assertEquals("", CharacterListParser.parse(valid).single().imageUrl)
        val broken = valid + "<div class='hvr-bounce-out'><a href='/klbq/乙'>乙</a><a href='/klbq/丙'>丙</a></div>"
        assertFalse(CharacterListParser.isValidPayload(payload(broken)))
        assertFalse(CharacterListParser.isValidPayload("{\"error\":{}}"))
        assertFalse(MapListParsers.isValidPayload(payload("<p>验证访问</p>")))
    }

    @Test fun latestLocalFactionSnapshotKeepsEveryVerifiedCharacter() {
        val file = System.getenv("WIKI_FACTION_AUDIT_FILE")?.let(::File)
        assumeTrue(file?.isFile == true)
        val characters = CharacterListParser.parse(file!!.readText(Charsets.UTF_8))
        assertTrue(CharacterListParser.isValidPayload(payload(file.readText(Charsets.UTF_8))))
        assertEquals(29, characters.size)
        assertEquals(29, characters.map { it.name }.distinct().size)
        assertTrue(characters.all { it.imageUrl.isNotBlank() })
    }
}
