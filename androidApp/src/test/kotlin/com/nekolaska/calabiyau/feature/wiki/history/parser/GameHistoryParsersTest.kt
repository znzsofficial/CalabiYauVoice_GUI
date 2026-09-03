package com.nekolaska.calabiyau.feature.wiki.history.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class GameHistoryParsersTest {

    @Test
    fun nestsHeadingsAndExtractsJumpLinks() {
        val sections = GameHistoryParsers.parseSections(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">版本</span></h2>
              <div class="alert">公测说明<button class="close">x</button></div>
              <h3><span class="mw-headline">2026</span></h3>
              <div class="wiki-jump-btn"><a href="/klbq/更新/八月">八月更新</a></div>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("版本", "版本 / 2026"), sections.map { it.title })
        assertEquals("公测说明", sections[0].description)
        assertEquals("八月更新", sections[1].entries.single().title)
        assertEquals("https://wiki.biligame.com/klbq/更新/八月", sections[1].entries.single().url)
    }
}
