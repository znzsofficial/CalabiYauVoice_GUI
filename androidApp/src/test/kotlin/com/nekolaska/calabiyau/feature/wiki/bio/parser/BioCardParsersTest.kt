package com.nekolaska.calabiyau.feature.wiki.bio.parser

import com.nekolaska.calabiyau.feature.wiki.bio.model.CardRefreshProbability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BioCardParsersTest {

    @Test
    fun parsesPcCardsWithRolesAndParams() {
        val html = """
        <div class="gallerygrid">
          <div class="gallerygrid-item zombie-card"
               data-param1="4" data-param2="进攻" data-param3="超弦体"
               data-param4="默认" data-param5="商城" data-param6="2026-01-01">
            <div class="zombie-card__name">火力压制</div>
            <div class="zombie-card__roles">输出、控场</div>
            <div class="zombie-card__stat"><span class="zombie-card__value">3级</span></div>
            <div class="zombie-card__desc"><span class="zombie-card__value">对目标造成伤害提升。</span></div>
            <div class="zombie-card__imagebox">
              <img src="https://patchwiki.biligame.com/images/klbq/thumb/b/b7/card.png/200px-card.png" />
            </div>
          </div>
        </div>
        """.trimIndent()

        val cards = BioCardParsers.parsePcCards(html, emptyMap())

        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals("火力压制", card.name)
        assertEquals(4, card.rarity)
        assertEquals("进攻", card.category)
        assertEquals("超弦体", card.faction)
        assertEquals("默认", card.defaultTag)
        assertEquals("商城", card.acquireType)
        assertEquals("2026-01-01", card.releaseDate)
        assertEquals("3级", card.maxLevel)
        assertEquals("对目标造成伤害提升。", card.effect)
        assertEquals(listOf("输出", "控场"), card.roles)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/b/b7/card.png",
            card.imageUrl
        )
        assertNull(card.refreshProbability)
    }

    @Test
    fun attachesRefreshProbabilityByName() {
        val html = """
        <div class="gallerygrid-item zombie-card" data-param1="3" data-param2="防御" data-param3="晶源体">
          <div class="zombie-card__name">护盾强化</div>
        </div>
        """.trimIndent()

        val cards = BioCardParsers.parsePcCards(
            html,
            mapOf("护盾强化" to CardRefreshProbability("10%", "20%", "30%", "40%"))
        )

        assertEquals(1, cards.size)
        assertNotNull(cards.single().refreshProbability)
        assertEquals("10%", cards.single().refreshProbability?.stage1)
    }

    @Test
    fun parsesMobileCards() {
        val html = """
        <div class="gallerygrid">
          <div class="gallerygrid-item zombie-card-mobile" data-param1="超弦体" data-param2="进攻" data-param3="4">
            <div class="zombie-card-mobile__name">极速装填</div>
            <div class="zombie-card-mobile__stat"><span class="zombie-card-mobile__value">5级</span></div>
            <div class="zombie-card-mobile__desc"><span class="zombie-card-mobile__value">缩短换弹时间。</span></div>
            <div class="zombie-card-mobile__imagebox">
              <img src="https://patchwiki.biligame.com/images/klbq/c/c3/mobile.png" />
            </div>
          </div>
        </div>
        """.trimIndent()

        val cards = BioCardParsers.parseMobileCards(html)

        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals("极速装填", card.name)
        assertEquals("超弦体", card.faction)
        assertEquals("进攻", card.category)
        assertEquals(4, card.rarity)
        assertEquals("5级", card.maxLevel)
        assertEquals("缩短换弹时间。", card.effect)
    }
}
