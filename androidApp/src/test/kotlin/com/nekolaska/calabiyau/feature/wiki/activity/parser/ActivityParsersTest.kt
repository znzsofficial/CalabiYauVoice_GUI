package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.core.wiki.wikiListHtml
import com.nekolaska.calabiyau.feature.wiki.activity.model.ACTIVITY_PAGE_URL
import org.jsoup.Jsoup
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
    fun archivedCardsKeepEveryArticleAndOriginalUrl() {
        val json = checkNotNull(javaClass.getResource("/fixtures/activity_parse.json")).readText().removePrefix("\uFEFF")
        val html = checkNotNull(wikiListHtml(json))
        val links = Jsoup.parse(html).select(".klbq-event-card__title a[href]")
        assertEquals(98, links.size)
        assertEquals(links.map { it.attr("title") to "https://wiki.biligame.com${it.attr("href")}" },
            ActivityParsers.parseActivities(html).map { it.detailPageTitle to it.entry.wikiUrl })
    }

    @Test
    fun legacyTableRecognizesHeadersWithoutDroppingEventProse() {
        val entries = ActivityParsers.parseActivities("""
            <table class="klbqtable">
            <tr><th>活动</th><th>开始</th><th>结束</th><th>简介</th></tr>
            <tr><td>活动名称</td><td>开始时间</td><td>结束时间</td><td>活动说明</td></tr>
            <tr><th>时空之音</th><td>开服后开始</td><td>常驻</td><td>签到活动</td></tr>
            <tr><td>枪王特训</td><td>更新后</td><td>赛季结束时</td><td>完成任务领取奖励</td></tr>
            <tr><td>异星来客</td><td>更新后</td><td>常驻</td><td>详见活动说明和奖励简介</td></tr>
            </table>""").map { it.entry }
        assertEquals(listOf("时空之音", "枪王特训", "异星来客"), entries.map { it.title })
        assertEquals("详见活动说明和奖励简介", entries.last().description)
    }

    @Test
    fun legacyCoverIsNotTheArticleAndCaptionIsNotItsPageName() {
        val path = "/klbq/%E6%97%B6%E7%A9%BA%E4%B9%8B%E9%9F%B3"
        val entry = legacyEntry("""<a class="image" href="/klbq/文件:公告-不删档上线活动8.jpg"><img src="cover.jpg"></a><br>
            <a href="$path">开服七日签到</a>""")
        assertEquals("开服七日签到", entry.title)
        assertEquals("时空之音", entry.detailPageTitle)
        assertEquals("https://wiki.biligame.com$path", entry.wikiUrl)
        assertEquals("cover.jpg", entry.imageUrl)
    }

    @Test
    fun viewQueryUsesItsTitleAndKeepsTheOriginalUrl() {
        val href = "/klbq/index.php?title=%E6%97%B6%E7%A9%BA%E4%B9%8B%E9%9F%B3"
        val entry = legacyEntry("""<a href="$href">开服七日签到</a>""")
        assertEquals("时空之音", entry.detailPageTitle)
        assertEquals("https://wiki.biligame.com$href", entry.wikiUrl)
    }

    @Test
    fun absentOrNonArticleDestinationsDoNotInventDetailPages() {
        for (href in listOf("", "#", "/klbq/活动", "/klbq/index.php", "/klbq/index.php?title=时空之音&amp;action=edit&amp;redlink=1",
            "/klbq/File:Cover.jpg", "/klbq/特殊:上传文件", "javascript:void(0)")) {
            val entry = legacyEntry("""<a title="时空之音" href="$href">签到</a>""")
            assertNull(entry.detailPageTitle, href)
            assertEquals(ACTIVITY_PAGE_URL, entry.wikiUrl, href)
        }
        val plain = legacyEntry("仅有活动名称")
        assertNull(plain.detailPageTitle)
        assertEquals(ACTIVITY_PAGE_URL, plain.wikiUrl)
        val official = "https://klbq.idreamsky.com/newsDetails?id=XXcLBl5s1c6IFfRbCguHCA"
        for (url in listOf(official, "https://example.org/klbq/时空之音")) {
            val external = legacyEntry("""<a href="$url" title="官网公告">官网活动</a>""")
            assertNull(external.detailPageTitle)
            assertEquals(url, external.wikiUrl)
        }
    }

    @Test
    fun cardFallbackUsesAnArticleCoverButNotADescriptionLink() {
        fun card(imageHref: String) = ActivityParsers.parseActivities("""
            <div class="klbq-event-card"><div class="klbq-event-card__imagebox"><a href="$imageHref"><img src="cover.jpg"></a></div>
            <div class="klbq-event-card__title">七日签到</div>
            <div class="klbq-event-card__desc"><a href="/klbq/道具">奖励道具</a></div></div>""").single().entry
        assertEquals("时空之音", card("/klbq/时空之音").detailPageTitle)
        val coverOnly = card("/klbq/文件:封面.jpg")
        assertNull(coverOnly.detailPageTitle)
        assertEquals(ACTIVITY_PAGE_URL, coverOnly.wikiUrl)
    }

    @Test fun explicitDestinationWinsOverCoverAndRetainsSectionAnchors() {
        val cover = """<a href="/klbq/时空之音"><img src="cover.jpg"></a>"""
        val official = "https://klbq.idreamsky.com/newsDetails?id=XXcLBl5s1c6IFfRbCguHCA"
        val linked = legacyEntry("""$cover<a href="$official">官网活动</a>""")
        assertNull(linked.detailPageTitle)
        assertEquals(official, linked.wikiUrl)
        val section = legacyEntry("""$cover<a href="/klbq/2周年版本活动汇总#签到奖励_2">签到奖励</a>""")
        assertNull(section.detailPageTitle) // The native reader opens at the top, not at a URL fragment.
        assertEquals("https://wiki.biligame.com/klbq/2周年版本活动汇总#签到奖励_2", section.wikiUrl)
    }

    private fun legacyEntry(titleCell: String) = ActivityParsers.parseActivities("""
        <table class="klbqtable"><tr><td>$titleCell</td><td>更新后</td><td>常驻</td><td>签到</td></tr></table>""").single().entry

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
