package com.nekolaska.calabiyau.feature.wiki.collaboration.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class CollaborationParsersTest {

    @Test
    fun parsesTimelineAndEventMetadata() {
        val page = CollaborationParsers.parsePage(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">时间轴</span></h2>
              <ul>
                <li class="year"><span class="year1">2024</span></li>
                <li class="date"><span class="time">01.01</span><span class="content">初号机</span></li>
              </ul>
              <h2><span class="mw-headline">联动具体信息</span></h2>
              <h3><span class="mw-headline">初号机</span></h3>
              <h4><span class="mw-headline">周边</span></h4>
              <p>本内容在PC端和移动端推出<br/>活动时间：2024.01.01-01.31<br/>主题：EVA<br/>正文一行</p>
              <p>活动店铺：</p>
              <p><img src="https://x/thumb/a/ab/ok.png/300px-ok.png"/><img src="https://x/60000038.png"/></p>
            </div>
            """.trimIndent()
        )

        val year = page.timelineYears.single()
        assertEquals("2024", year.year)
        assertEquals("01.01", year.items.single().date)
        assertEquals("初号机", year.items.single().title)

        val event = page.events.single()
        assertEquals("初号机", event.title)
        assertEquals("周边", event.sectionTitle)
        assertEquals("本内容在PC端和移动端推出", event.publishInfo)
        assertEquals("2024.01.01-01.31", event.date)
        assertEquals("EVA", event.theme)
        assertEquals("正文一行", event.content)
        assertEquals(listOf("https://x/a/ab/ok.png"), event.imageUrls)
    }
}
