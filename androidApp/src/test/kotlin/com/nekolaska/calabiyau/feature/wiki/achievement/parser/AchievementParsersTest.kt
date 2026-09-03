package com.nekolaska.calabiyau.feature.wiki.achievement.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class AchievementParsersTest {

    @Test
    fun parsesTabbedAndPlainGalleries() {
        val page = AchievementParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">目录</span></h2>
              <ul class="gallery"><li class="gallerybox"><div class="gallerytext"><big>应跳过</big></div></li></ul>
              <h2><span class="mw-headline">战斗</span></h2>
              <div class="resp-tabs">
                <ul class="resp-tabs-list">
                  <li class="bili-list-style"><span class="tab-panel">铜</span></li>
                  <li class="bili-list-style"><span class="tab-panel">银</span></li>
                </ul>
                <div class="resp-tabs-container">
                  <div class="resp-tab-content">
                    <ul class="gallery">
                      <li class="gallerybox">
                        <div class="thumb"><a class="image" href="/klbq/文件:成就铜.png"><img src="https://x/thumb/a/ab/a.png/70px-a.png"/></a></div>
                        <div class="gallerytext"><p><big>首胜</big><br/><small>迈出第一步</small>获得方式：赢得一局</p></div>
                      </li>
                    </ul>
                  </div>
                  <div class="resp-tab-content">
                    <ul class="gallery">
                      <li class="gallerybox">
                        <div class="thumb"><a class="image" href="/klbq/文件:成就银.png"><img src="https://x/b.png"/></a></div>
                        <div class="gallerytext"><p><big>连胜</big>获得方式：连胜三局</p></div>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
              <h2><span class="mw-headline">收集</span></h2>
              <ul class="gallery">
                <li class="gallerybox">
                  <div class="thumb"><a class="image" href="/klbq/文件:收集.png"><img src="https://x/c.png"/></a></div>
                  <div class="gallerytext"><p><big>图鉴达人</big><br/><small>收集控</small>获得方式：解锁全部时装</p></div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("战斗", "收集"), page.sections.map { it.category })
        val battle = page.sections[0].achievements
        assertEquals(listOf("首胜", "连胜"), battle.map { it.name })
        assertEquals("铜", battle[0].level)
        assertEquals("迈出第一步", battle[0].flavorText)
        assertEquals("赢得一局", battle[0].condition)
        assertEquals("https://x/a/ab/a.png", battle[0].imageUrl)
        assertEquals("成就铜.png", battle[0].fileName)
        assertEquals("银", battle[1].level)
        assertEquals(null, page.sections[1].achievements.single().level)
        assertEquals("图鉴达人", page.sections[1].achievements.single().name)
    }
}
