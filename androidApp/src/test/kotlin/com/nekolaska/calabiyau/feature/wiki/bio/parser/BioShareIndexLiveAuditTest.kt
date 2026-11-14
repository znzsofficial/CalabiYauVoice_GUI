package com.nekolaska.calabiyau.feature.wiki.bio.parser

import data.SharedJson
import java.io.File
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BioShareIndexLiveAuditTest {
    @Test fun currentFullShareTableHasOneAuthoritativeStealIndex() {
        val path = System.getenv("WIKI_LIST_AUDIT_DIR")
        assumeTrue("Set WIKI_LIST_AUDIT_DIR for the November 14 full response", !path.isNullOrBlank())
        val file = File(requireNotNull(path), "deck-cards.json")
        assertTrue(file.isFile)
        val response = SharedJson.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val revision = response.getValue("query").jsonObject.getValue("pages").jsonObject.values.single()
            .jsonObject.getValue("revisions").jsonArray.single().jsonObject
        assertEquals("119471", revision.getValue("revid").jsonPrimitive.content)
        val text = revision.getValue("slots").jsonObject.getValue("main").jsonObject.getValue("*").jsonPrimitive.content
        val table = SharedJson.parseToJsonElement(text).jsonObject
        val factions = listOf("超弦体", "晶源体").associateWith {
            BioDeckShareParsers.parseFactionCards(table.getValue(it).jsonArray)
        }
        val all = factions.values.flatten()
        val card = factions.getValue("晶源体").single { it.cardId == "20351047" }
        assertEquals("盗取", card.name)
        assertEquals("卓越", card.quality)
        assertEquals(221, card.index)
        assertEquals(listOf(card), all.filter { it.index == 221 || it.cardId == card.cardId })
        val code = BioDeckShareParsers.publicationShareCode(listOf(card), "晶源体", false)
        assertEquals(listOf(card.cardId) to "晶源体", BioDeckShareCodecs.decodeShareCode(code,
            "晶源体", factions.getValue("晶源体").associate { it.index to it.cardId }))
    }
}
