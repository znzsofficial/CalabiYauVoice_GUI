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

    /** 真实页面（2026-09-21 抓取）的分享串格式：`提示语｜卡组名｜分享码**` */
    @Test
    fun parsesRealPageShareInput() {
        val raw = "谁还没玩过这套？复制代码后点击创建卡组即可生成｜人类核爆｜MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=**"
        assertEquals("人类核爆", BioDeckShareCodecs.extractDeckNameFromShareInput(raw))
        assertEquals("MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=", BioDeckShareCodecs.extractActualShareCode(raw))
        // 纯码粘贴（无分隔符）原样返回
        val bare = "MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU="
        assertEquals(bare, BioDeckShareCodecs.extractActualShareCode(bare))
    }
}
