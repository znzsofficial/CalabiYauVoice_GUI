package com.nekolaska.calabiyau.feature.wiki.bio.parser

import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BioDeckShareParsersTest {

    @Test
    fun parsesFactionCardsAndSkipsNameless() {
        val cards = BioDeckShareParsers.parseFactionCards(
            SharedJson.parseToJsonElement(
                """
                [
                  {"name":"火力全开","cardid":"c1","quality":"4","default":"true","index":"2"},
                  {"name":"  ","cardid":"skip"},
                  {"name":"护盾","cardid":"c2","quality":"2","default":"false"}
                ]
                """.trimIndent()
            ).jsonArray
        )

        assertEquals(listOf("火力全开", "护盾"), cards.map { it.name })
        assertTrue(cards[0].isDefault)
        assertEquals(2, cards[0].index)
        assertFalse(cards[1].isDefault)
        assertEquals(-1, cards[1].index)
    }
}
