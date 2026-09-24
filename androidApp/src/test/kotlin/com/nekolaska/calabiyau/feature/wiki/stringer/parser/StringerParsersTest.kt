package com.nekolaska.calabiyau.feature.wiki.stringer.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class StringerParsersTest {

    @Test
    fun parsesPushCardsAndSkipsMobileNotice() {
        val page = StringerPushCardParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <p>此页面内容为【移动端】独有内容。</p>
              <p>超弦推进由极限推进升级而来。</p>
              <h2><span id="卡牌" class="mw-headline">卡牌</span></h2>
              <div class="gallerygrid">
                <div class="gallerygrid-item mobile-card" data-param1="输出" data-param2="4">
                  <img src="https://x/card.png"/>
                  <div class="mobile-card-name">火力全开</div>
                  <div class="mobile-card-item"><span class="card-item-key">分类：</span><span class="card-item-value">伤害</span></div>
                  <div class="mobile-card-role"><span class="card-item-key">适用角色：</span><span class="card-item-value">米雪儿、信</span></div>
                  <div class="mobile-card-desc"><span class="card-item-value">提升武器伤害</span></div>
                </div>
                <div class="gallerygrid-item mobile-card" data-param1="" data-param2="3">
                  <img src="https://x/card2.png"/>
                  <div class="mobile-card-name">护盾强化</div>
                  <div class="mobile-card-item"><span class="card-item-key">分类：</span><span class="card-item-value">守护</span></div>
                  <div class="mobile-card-role"><span class="card-item-key">适用角色：</span><span class="card-item-value">信</span></div>
                  <div class="mobile-card-desc"><span class="card-item-value">提升护盾值</span></div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals("超弦推进由极限推进升级而来。", page.summary)
        // data-param1（"输出"）与行内标签（"伤害"）刻意不同值：
        // 断言取的是属性值，若属性读取损坏而回退到行标签，此断言必须失败
        assertEquals(listOf("火力全开" to "输出", "护盾强化" to "守护"), page.cards.map { it.name to it.category })
        val card = page.cards.first()
        assertEquals(4, card.rarity)
        assertEquals("提升武器伤害", card.effect)
        assertEquals(listOf("米雪儿", "信"), card.roles)
        // data-param1 为空时回退到行内标签（分类：守护）
        assertEquals(3, page.cards[1].rarity)
        assertEquals(listOf("信"), page.cards[1].roles)
    }

    @Test
    fun parsesTalentTablesBySection() {
        val page = StringerTalentParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">机能</span></h2>
              <table>
                <tr><th>天赋</th><th>解锁等级</th><th>最大等级</th><th>天赋等级详情</th></tr>
                <tr>
                  <td><img src="https://x/t.png"/>暴击强化.png暴击强化</td>
                  <td>1</td>
                  <td>3</td>
                  <td>1级:加5% 2级:加10%</td>
                </tr>
              </table>
              <h2><span class="mw-headline">其他</span></h2>
              <table>
                <tr><th>天赋</th><th>解锁等级</th><th>最大等级</th><th>天赋等级详情</th></tr>
                <tr><td>不该出现</td><td>1</td><td>1</td><td>忽略</td></tr>
              </table>
            </div>
            """.trimIndent()
        )

        val section = page.sections.single()
        assertEquals("机能", section.title)
        val talent = section.items.single()
        assertEquals("暴击强化", talent.name)
        assertEquals("1", talent.unlockLevel)
        assertEquals(listOf("1级:加5%", "2级:加10%"), talent.details)
    }
}
