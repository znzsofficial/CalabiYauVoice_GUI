package com.nekolaska.calabiyau.feature.wiki.meow.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class MeowLanguageParsersTest {

    @Test
    fun parsesIntroAndCatLanguageGroups() {
        val sections = MeowLanguageParsers.parseSections(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">喵语入门</span></h2>
              <p>喵语是角色之间的暗号。</p>
              <div class="CatLanguage">
                <dl><dt>问候</dt></dl>
                <ul>
                  <li>喵呜</li>
                  <li>喵喵</li>
                </ul>
                <dl><dt>战斗</dt></dl>
                <ul><li>哈！</li></ul>
              </div>
            </div>
            """.trimIndent()
        )

        val section = sections.single()
        assertEquals("喵语入门", section.title)
        assertEquals(listOf("喵语是角色之间的暗号。"), section.intro)
        assertEquals(listOf("问候", "战斗"), section.groups.map { it.title })
        assertEquals(listOf("喵呜", "喵喵"), section.groups[0].lines)
        assertEquals(listOf("哈！"), section.groups[1].lines)
    }
}
