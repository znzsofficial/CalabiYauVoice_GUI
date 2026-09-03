package com.nekolaska.calabiyau.feature.wiki.tips.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTipsParsersTest {

    @Test
    fun groupsTipsAndDropsMarkupNoise() {
        val sections = GameTipsParsers.parseSections(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">战斗</span></h2>
              <p>弦化可以躲避子弹伤害。</p>
              <ul>
                <li>文件:教学.png</li>
                <li>短</li>
                <li>保持移动可以降低被狙击点名的概率。</li>
              </ul>
              <h3><span class="mw-headline">爆破</span></h3>
              <p>安装炸弹后不要提前庆祝。</p>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("战斗", "爆破"), sections.map { it.title })
        assertEquals(
            listOf("弦化可以躲避子弹伤害。", "保持移动可以降低被狙击点名的概率。"),
            sections[0].tips
        )
        assertEquals(listOf("安装炸弹后不要提前庆祝。"), sections[1].tips)
    }

    @Test
    fun emptyRootReturnsEmpty() {
        assertTrue(GameTipsParsers.parseSections("<div></div>").isEmpty())
    }
}
