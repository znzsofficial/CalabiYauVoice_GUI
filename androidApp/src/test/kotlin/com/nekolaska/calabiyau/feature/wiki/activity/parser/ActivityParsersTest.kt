package com.nekolaska.calabiyau.feature.wiki.activity.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityParsersTest {

    @Test
    fun parsesKlbqEventCards() {
        // 2026-09 活动页从 klbqtable 表格改为 klbq-event-card 卡片网格
        val items = ActivityParsers.parseActivities(
            """
            <div class="klbq-event-summary">
              <div class="klbq-event-card">
                <div class="klbq-event-card__imagebox"><a href="/klbq/错季花园签到" title="错季花园签到"><img src="https://patchwiki.biligame.com/images/klbq/thumb/2/26/x.jpg/300px-x.jpg"/></a></div>
                <div class="klbq-event-card__captionbox">
                  <div class="klbq-event-card__title"><a href="/klbq/错季花园签到" title="错季花园签到">错季花园签到</a></div>
                  <div class="klbq-event-card__time-group">
                    <div class="klbq-event-card__time-item"><span class="klbq-event-card__label">开始时间：</span><span class="klbq-event-card__value">2026年9月15日 12:00</span></div>
                    <div class="klbq-event-card__time-item"><span class="klbq-event-card__label">结束时间：</span><span class="klbq-event-card__value">2026年10月13日 05:59</span></div>
                  </div>
                  <div class="klbq-event-card__desc">活动期间登录游戏签到领取奖励~</div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, items.size)
        val entry = items.single().entry
        assertEquals("错季花园签到", entry.title)
        assertEquals("2026年9月15日 12:00", entry.startTime)
        assertEquals("2026年10月13日 05:59", entry.endTime)
        assertTrue(entry.description.contains("签到"))
        assertEquals(
            "https://wiki.biligame.com/klbq/错季花园签到",
            entry.wikiUrl
        )
        assertEquals("错季花园签到", items.single().detailPageTitle)
    }

    @Test
    fun extractFirstFileTitleFromDetailLinks() {
        assertEquals(
            "文件:活动封面.png",
            ActivityParsers.extractFirstFileTitle(
                """<p><a href="/klbq/活动页">活动</a><a href="/klbq/文件:活动封面.png">图</a></p>"""
            )
        )
        assertNull(ActivityParsers.extractFirstFileTitle("""<a href="/klbq/活动页">活动</a>"""))
    }
}
