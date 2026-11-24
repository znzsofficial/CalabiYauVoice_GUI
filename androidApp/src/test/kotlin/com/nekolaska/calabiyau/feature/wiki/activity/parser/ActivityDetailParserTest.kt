package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityDetailApi
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityDetailParserTest {
    // Reduced from 欢迎光临卡邦尼！ (120799). The count and the decoration type are not text nodes.
    private val title = "欢迎光临卡邦尼！"
    private val article = """<div class="mw-parser-output"><div class="klbq-event-header">
        <div class="klbq-event-header&#95;_title">欢迎光临卡邦尼！</div></div>
        <div class="toc">目录</div><h2><span class="mw-headline">奖励</span><span class="mw-editsection">[编辑]</span></h2>
        <table class="klbqtable"><tr><th>场景</th><th>奖励</th></tr>
        <tr><td>累计获得100个邦尼特大汉堡玩具</td><td><span class="item-icon"><span class="item-icon__img" data-count="500"></span><span class="item-icon__text">活动凭录</span></span></td></tr>
        <tr><td>累计获得800个邦尼特大汉堡玩具</td><td><span class="item-icon"><span class="item-icon__text" data-type="聊天气泡">邦邦快送</span></span></td></tr></table>
        <h2>邦尼秘闻</h2><div class="main-line-wrap"><div class="resp-tabs"><ul class="resp-tabs-list"><li>汐【打工吧！引航者大人！】</li><li>诺诺【天才败北事件簿】</li></ul>
        <div class="resp-tabs-container"><div class="resp-tab-content game-story"><div class="poem"><p>Here’s punny！——<br>随着引航者推开卡邦尼汉堡店的大门</p></div></div>
        <div class="resp-tab-content game-story"><div class="poem"><p>敬请到店就餐，充满真心的服务能带来最愉悦的体验。</p></div></div></div></div></div>
        <p><style>.playerBox {}</style><script>alert(1)</script></p></div>"""

    @Test fun preservesRewardCountsAndPairsEachStoryWithItsOwnTab() {
        assertEquals(listOf(Heading("奖励"), TableRow(listOf("场景", "奖励"), true),
            TableRow(listOf("累计获得100个邦尼特大汉堡玩具", "活动凭录 ×500"), false),
            TableRow(listOf("累计获得800个邦尼特大汉堡玩具", "聊天气泡：邦邦快送"), false),
            Heading("邦尼秘闻"), Heading("汐【打工吧！引航者大人！】"), Paragraph("Here’s punny！——\n随着引航者推开卡邦尼汉堡店的大门"),
            Heading("诺诺【天才败北事件簿】"), Paragraph("敬请到店就餐，充满真心的服务能带来最愉悦的体验。")),
            ActivityDetailParser.parse(article, title))
    }

    @Test fun doesNotPublishWrongEventOrListAsDetail() {
        assertTrue(ActivityDetailParser.parse(article, "弦海星歌签到").isEmpty())
        assertTrue(ActivityDetailParser.parse("<div class='mw-parser-output'><div class='klbq-event-card'>$title</div></div>", title).isEmpty())
    }

    @Test fun onlyCompleteExplicitTablesContributeDeclaredRows() {
        val source = """== 奖励 ==
            |<!-- {| class="klbqtable"
            || 隐藏的行
            ||} -->
            |{| class="klbqtable table-hover"
            ||+ 奖励清单
            ||-
            |! 场景 !! 奖励
            ||-
            || 累计获得100个邦尼特大汉堡玩具
            || {{道具图标|活动凭录|数量=500}}
            ||-
            || 累计获得800个邦尼特大汉堡玩具 || 聊天气泡：邦邦快送
            ||}""".trimMargin().replace("\n", "\r\n")
        val expected = ActivityDetailParser.parse(article, title)
        assertEquals(expected, ActivityDetailParser.parse(article, title, source + "\r\n<!-- 未发布\r\n== 新奖励 =="))
        assertTrue(ActivityDetailParser.parse(article, title, source.removeSuffix("|}")).isEmpty())
        assertTrue(ActivityDetailParser.parse(article, title, source.replace("|}", "|-\r\n| 新档 || 道具\r\n|}")).isEmpty())
    }

    @Test fun sectionlessShortArticleAndTransportFailuresHaveDistinctOutcomes() = runBlocking {
        val short = "<div class='mw-parser-output'><div class='klbq-event-header'><b class='klbq-event-header__title'>$title</b></div><p>参与即可领取。</p></div>"
        val store = ArticlePublicationFixture(articlePayload(title, "{{活动}}\n参与即可领取。", short))
        suspend fun read() = ActivityDetailApi.fetch(title, load = store.load)
        assertEquals(listOf(Paragraph("参与即可领取。")), assertIs<ApiResult.Success<*>>(read()).value)
        store.disk = null
        store.response = articlePayload(title, "{{活动}}\n== 参与说明 ==\n参与即可领取。", short)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read()).kind)
        // An old HTML-only cache can also disprove its own completeness through its surviving TOC.
        store.disk = articlePayload(title, null, short.replace("<p>", "<div class='toc'><span class='toctext'>奖励</span></div><p>"))
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(ActivityDetailApi.fetch(title, cacheOnly = true, load = store.load)).kind)
        store.disk = null
        store.response = null
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(read()).kind)
        assertFailsWith<CancellationException> { ActivityDetailApi.fetch(title, load = { _, _ -> throw CancellationException() }) }
        Unit
    }
}
