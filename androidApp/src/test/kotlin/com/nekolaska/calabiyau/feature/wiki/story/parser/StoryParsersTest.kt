package com.nekolaska.calabiyau.feature.wiki.story.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryParsersTest {

    @Test
    fun nestsHeadingAndExtractsStoryLinks() {
        val sections = StoryParsers.parseSections(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">主线</span></h2>
              <div class="alert">序章说明<button class="close">x</button></div>
              <h3><span class="mw-headline">第一章</span></h3>
              <div class="nav-chara">
                <div class="game-story-box">
                  <a href="/klbq/文件:封面.png"><img src="https://x/c.png" alt="封面.png"/></a>
                  <a href="/klbq/故事/序章">序章</a>
                </div>
              </div>
              <div class="wiki-jump-btn"><a href="故事/续">续篇</a></div>
              <a href="javascript:void(0)">忽略</a>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("主线", "主线 / 第一章"), sections.map { it.title })
        assertEquals("序章说明", sections[0].description)
        assertTrue(sections[0].entries.isEmpty())

        val chapter = sections[1]
        assertEquals(
            listOf("序章", "续篇"),
            chapter.entries.map { it.title }
        )
        assertEquals("https://wiki.biligame.com/klbq/故事/序章", chapter.entries[0].url)
        assertEquals("封面.png", chapter.entries[0].imageFileName)
        assertEquals("https://x/c.png", chapter.entries[0].imageUrl)
        assertEquals("https://wiki.biligame.com/klbq/故事/续", chapter.entries[1].url)
    }

    @Test
    fun emptyRootReturnsEmptyList() {
        assertTrue(StoryParsers.parseSections("<div></div>").isEmpty())
    }
}
