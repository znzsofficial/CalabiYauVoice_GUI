package com.nekolaska.calabiyau.feature.wiki.map.parser

import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioCardParsers
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareParsers
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareCodecs
import data.SharedJson
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Uses the complete captured responses, never production requests or account mutations. */
class RiftAndStealRealPageTest {
    @Test fun parsesCompleteRiftAndDocumentsShareIndexGap() {
        val directory = System.getenv("MAP_BIO_AUDIT_DIR")?.let(::File)
        assumeTrue("Set MAP_BIO_AUDIT_DIR to complete local captures", directory?.isDirectory == true)
        val dir = directory!!
        fun root(name: String) = SharedJson.parseToJsonElement(File(dir, "$name.json").readText(Charsets.UTF_8)).jsonObject
        val rift = root("rift").getValue("parse").jsonObject
        val detail = assertNotNull(MapDetailParsers.parseMapWikitext("维里昂裂隙",
            rift.getValue("wikitext").jsonObject.getValue("*").jsonPrimitive.content,
            rift.getValue("text").jsonObject.getValue("*").jsonPrimitive.content))
        assertEquals("弦溯战线", detail.supportedModes)
        assertEquals("PC端", detail.platforms)
        assertNotNull(detail.overviewImageUrl)
        assertNotNull(detail.terrainMapUrl)
        assertTrue(detail.overviewImageUrl != detail.terrainMapUrl)
        val listHtml = root("rift-list").getValue("parse").jsonObject.getValue("text").jsonObject.getValue("*").jsonPrimitive.content
        val map = MapListParsers.parseMapsFromHtml(listHtml).single()
        assertEquals("维里昂裂隙", map.name)
        assertEquals(detail.overviewImageUrl, map.imageUrl)

        val pcHtml = root("pc-cards").getValue("parse").jsonObject.getValue("text").jsonObject.getValue("*").jsonPrimitive.content
        val pcCards = BioCardParsers.parsePcCards(pcHtml, emptyMap())
        val steal = pcCards.single { it.name == "盗取" && it.faction == "晶源体" }
        assertEquals("20351047", steal.cardId)
        val raw = root("deck-cards").getValue("query").jsonObject.getValue("pages").jsonObject.values.first().jsonObject
            .getValue("revisions").jsonArray.first().jsonObject.getValue("slots").jsonObject.getValue("main").jsonObject.getValue("*").jsonPrimitive.content
        val json = raw.trim().replace(Regex("^<pre[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</pre>$", RegexOption.IGNORE_CASE), "").trim()
        val official = SharedJson.parseToJsonElement(json).jsonObject.mapValues { BioDeckShareParsers.parseFactionCards(it.value.jsonArray) }
        val completed = BioDeckShareParsers.completeFromPcCatalog(official, pcCards)
        val card = completed.getValue("晶源体").single { it.cardId == "20351047" }
        assertEquals("卓越", card.normalizedQuality)
        if (official.getValue("晶源体").none { it.cardId == "20351047" }) {
            assertEquals(-1, card.index)
            assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.publicationShareCode(listOf(card), "晶源体", false) }
            assertEquals("", BioDeckShareParsers.publicationShareCode(listOf(card), "晶源体", true))
        } else {
            assertTrue(card.index >= 0)
            assertTrue(BioDeckShareCodecs.normalizeShareCode(
                BioDeckShareParsers.publicationShareCode(listOf(card), "晶源体", false)).isNotBlank())
        }
        println("rift overview + terrain verified; share index for 20351047=${card.index}; supplemented=${completed.mapValues { it.value.size }}")
    }
}
