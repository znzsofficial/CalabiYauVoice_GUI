package com.nekolaska.calabiyau.feature.wiki.imprint.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class ImprintParsersTest {

    @Test
    fun parsesCharacterGalleriesAndLevels() {
        val page = ImprintParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <p>公测后印迹可在角色界面查看。</p>
              <h2><span class="mw-headline">目录</span></h2>
              <h2><span class="mw-headline">米雪儿·李</span></h2>
              <ul class="gallery">
                <li class="gallerybox">等级1印迹</li>
                <li class="gallerybox">
                  <img alt="3350012.png" src="https://x/thumb/a/ab/i.png/70px-i.png"/>
                  <b>霜华</b>【静待花开】获得方式：角色升级
                </li>
                <li class="gallerybox">
                  <img alt="other.png" src="https://x/b.png"/>
                  <b>等级 3 印迹·夜航</b>获得方式：活动
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals("公测后印迹可在角色界面查看。", page.notice)
        val section = page.sections.single()
        assertEquals("米雪儿·李", section.character)
        assertEquals(listOf("霜华", "等级 3 印迹·夜航"), section.imprints.map { it.name })
        assertEquals("静待花开", section.imprints[0].quote)
        assertEquals("角色升级", section.imprints[0].obtainMethod)
        assertEquals(2, section.imprints[0].level)
        assertEquals("https://x/a/ab/i.png", section.imprints[0].imageUrl)
        assertEquals(3, section.imprints[1].level)
    }
}
