package com.nekolaska.calabiyau.feature.wiki.bio.parser

import com.nekolaska.calabiyau.feature.wiki.bio.BioDeckCardImageIndex
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class BioDeckPoolCompletionTest {
    private fun steal() = BioCardParsers.parsePcCards(checkNotNull(javaClass.getResource("/fixtures/bio/steal_pc.html")).readText(Charsets.UTF_8), emptyMap()).single()
    private val known = DeckCardOption("已知卡", "known", "精致", false, 117)
    private fun pool() = mapOf("超弦体" to emptyList<DeckCardOption>(), "晶源体" to listOf(known))

    @Test fun missingStealUsesVerifiedPcIdentityAndNoInventedBitIndex() {
        val pc = steal()
        assertEquals("20351047", pc.cardId)
        val cards = BioDeckShareParsers.completeFromPcCatalog(pool(), listOf(pc))
        val card = cards.getValue("晶源体").single { it.name == "盗取" }
        assertEquals("20351047", card.cardId)
        assertEquals("卓越", card.quality)
        assertFalse(card.isDefault)
        assertEquals(-1, card.index)
        assertTrue(cards.getValue("超弦体").isEmpty())
        assertEquals(pc.imageUrl, BioDeckCardImageIndex(listOf(pc), emptyList()).imageUrl(card, "晶源体"))
    }

    @Test fun updatedOfficialIndexWinsAndRepeatedCatalogRowsDoNotDuplicateCards() {
        val official = DeckCardOption("盗取", "20351047", "卓越", false, 250) // Synthetic future index, not game data.
        val pc = steal()
        val map = mapOf("晶源体" to listOf(known, official))
        assertEquals(map, BioDeckShareParsers.completeFromPcCatalog(map, listOf(pc, pc)))
        assertEquals(2, BioDeckShareParsers.completeFromPcCatalog(pool(), listOf(pc, pc)).getValue("晶源体").size)
    }

    @Test fun publishedStealIndex221ReplacesUnknownCompletionAndEncodesTheOfficialBit() {
        // Reduced MediaWiki:ZombieCardList.json revision 119471, published 2026-11-04T05:02:50Z.
        val json = SharedJson.parseToJsonElement(checkNotNull(javaClass.getResource(
            "/fixtures/bio/steal_share_20261104.json")).readText(Charsets.UTF_8)).jsonObject
        val official = mapOf("晶源体" to BioDeckShareParsers.parseFactionCards(json.getValue("晶源体").jsonArray))
        val completed = BioDeckShareParsers.completeFromPcCatalog(official, listOf(steal(), steal()))
        val card = completed.getValue("晶源体").single()
        assertEquals("20351047", card.cardId)
        assertEquals(221, card.index)
        val selected = BioDeckShareParsers.resolveSelectedCards(listOf(card.cardId), completed.getValue("晶源体"))
        val code = BioDeckShareParsers.publicationShareCode(selected, "晶源体", allowMissingShareCode = false)
        val raw = String(java.util.Base64.getDecoder().decode(code.removeSuffix("**")), Charsets.UTF_8)
        assertEquals("000B|0003|2" + "0".repeat(55), raw)
        assertEquals(listOf("20351047") to "晶源体",
            BioDeckShareCodecs.decodeShareCode(code, "晶源体", mapOf(221 to "20351047")))
    }

    @Test fun talentsMissingIdsAndOtherFactionIdsCannotBeClaimed() {
        val pc = steal()
        val variants = listOf(pc.copy(cardId = ""), pc.copy(category = "天赋"), pc.copy(rarity = 1), pc.copy(name = "未知卡牌"))
        assertEquals(pool(), BioDeckShareParsers.completeFromPcCatalog(pool(), variants))
        val map = mapOf("超弦体" to listOf(known.copy(cardId = pc.cardId)), "晶源体" to emptyList())
        assertEquals(map, BioDeckShareParsers.completeFromPcCatalog(map, listOf(pc)))
        assertTrue(BioDeckShareParsers.completeFromPcCatalog(emptyMap(), listOf(pc)).isEmpty())
    }

    @Test fun unindexedSelectionRequiresConsentAndProducesNoPartialShareCode() {
        val unindexed = DeckCardOption("盗取", "20351047", "卓越", false, -1)
        val cards = BioDeckShareParsers.resolveSelectedCards(listOf("known", "20351047"), listOf(known, unindexed))
        assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.publicationShareCode(cards, "晶源体", false) }
        assertEquals("", BioDeckShareParsers.publicationShareCode(cards, "晶源体", true))
        assertEquals("", BioDeckShareParsers.publicationShareCode(listOf(unindexed, unindexed.copy(cardId = "other")), "晶源体", true))
    }

    @Test fun completeSelectionsStillGenerateCompatibleCodesAndInvalidIndexesStayRejected() {
        assertEquals(BioDeckShareCodecs.encodeShareCode(listOf(117), "晶源体"),
            BioDeckShareParsers.publicationShareCode(listOf(known), "晶源体", true))
        for (cards in listOf(listOf(known, known.copy(cardId = "collision")), listOf(known.copy(index = 4096)), listOf(known.copy(index = -2)))) {
            assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.publicationShareCode(cards, "晶源体", true) }
        }
        assertFalse(SubmitDeckPayload("x", "", "", "晶源体", listOf("known")).allowMissingShareCode)
    }
}
