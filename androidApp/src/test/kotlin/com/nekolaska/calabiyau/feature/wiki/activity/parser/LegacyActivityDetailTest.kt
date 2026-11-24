package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityDetailApi
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Reduced fragments from the local captures; none of these tests requires a snapshot directory. */
class LegacyActivityDetailTest {
    private val title = "2周年版本活动汇总"
    private val set = "{{#set:|活动名=$title|活动图片=壁纸-两周年活动.png}}{{面包屑|活动}}"
    private val crumb = """<div class="klbq-breadcrumb-bar"><a title="活动">活动</a>
        <span class="current-page"><b><a class="mw-selflink selflink">$title</a></b></span></div>"""
    private val banner = "<div class='klbq-event-header'><b class='klbq-event-header__title'>$title</b></div>"
    private fun article(body: String, header: String = banner) = "<div class='mw-parser-output'>$header$body</div>"
    private fun h(text: String, id: String = text) = "<h2><span class='mw-headline' id='$id'>$text</span></h2>"
    private fun icon(name: String, count: Int) = """<span class="item-icon"><span class="item-icon__img" data-count="$count"></span><span class="item-icon__text">$name</span></span>"""

    @Test fun summaryRequiresMatchingSetAndBreadcrumbInThePairedPayload() = runBlocking {
        val html = article(h("踏浪疾行") + "<p>共计15天的签到好礼！</p>", crumb)
        val wt = "$set\n== 踏浪疾行 ==\n共计15天的签到好礼！"
        val good = articlePayload(title, wt, html)
        val store = ArticlePublicationFixture(good)
        suspend fun read(diskOnly: Boolean = false) = ActivityDetailApi.fetch(title, cacheOnly = diskOnly, load = store.load)
        val original = assertIs<ApiResult.Success<*>>(read())
        assertEquals(listOf(Heading("踏浪疾行"), Paragraph("共计15天的签到好礼！")), original.value)
        for (bad in listOf(articlePayload("另一页", wt, html), articlePayload(title, wt.replace(title, "另一页"), html),
            articlePayload(title, wt, html.replace(title, "另一页")), articlePayload(title, wt, html.replace(crumb, crumb + banner.replace(title, "另一页"))),
            articlePayload(title, wt.replace(set, "<!-- $set -->"), html), articlePayload(title, null, html))) {
            store.response = bad
            val fallback = assertIs<ApiResult.Success<*>>(read())
            assertTrue(fallback.isOffline); assertEquals(123L, fallback.cacheAgeMs)
            assertEquals(original.value, fallback.value); assertEquals(good, store.disk)
        }
        store.disk = articlePayload(title, null, html)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(true)).kind)
    }

    @Test fun oldThumbTdLabelsAndNestedRuleQuantitiesAreNotLost() {
        val thumb = """<div class="thumb tright"><div class="thumbinner"><a class="image"><img src="//wiki.example/rule.png"></a>
            <div class="thumbcaption"><div class="magnify"><a title="放大"></a></div>激活乐谱界面</div></div></div>"""
        val html = article(h("活动规则") + thumb + "<ul><li>剩余音符抽奖<ul><li>${icon("基弦", 30)}</li></ul></li></ul>" +
            h("活动奖励") + "<table class='klbqtable'><tr><td>阶段</td><td>奖励</td></tr><tr><td>4</td><td>${icon("记忆序列", 2)}</td></tr></table>")
        val wt = "== 活动规则 ==\n[[File:夏日音乐节翻牌.png|thumb|right|激活乐谱界面]]\n== 活动奖励 ==\n" +
            "{| class=\"klbqtable\"\n| 阶段 || 奖励\n|-\n| 4 || {{道具图标|记忆序列|数量=2}}\n|}"
        val blocks = ActivityDetailParser.parse(html, title, wt)
        assertEquals(Image("激活乐谱界面", "https://wiki.example/rule.png"), blocks.filterIsInstance<Image>().single())
        assertContains(blocks.filterIsInstance<Paragraph>().single().text, "基弦 ×30")
        assertEquals(listOf(TableRow(listOf("阶段", "奖励"), true), TableRow(listOf("4", "记忆序列 ×2"), false)), blocks.filterIsInstance<TableRow>())
        assertTrue(ActivityDetailParser.parse(html.replace(thumb, ""), title, wt).isEmpty())
        assertFalse(ActivityDetailParser.parse(html.replace("<tr><td>阶段</td><td>奖励</td></tr>", ""), title,
            wt.replace("| 阶段 || 奖励\n|-\n", "")).filterIsInstance<TableRow>().single().isHeader)
    }

    @Test fun mergedWikiTableCanLinkOnlyWithItsCompleteDeclaredRowsCellsAndRewards() {
        val rows = "<tr><th>进度</th><th>奖励</th></tr><tr><td rowspan='2'>2</td><td>${icon("基弦", 80)}</td></tr>" +
            "<tr><td>${icon("理想币", 200)}${icon("记忆序列", 1)}</td></tr>"
        val source = "== 奖励道具 ==\n{{Wikitable | class=\"wikitable\"\n! 进度 !! 奖励\n|-\n| rowspan=2 | 2 || {{道具图标|基弦|数量=80}}" +
            "\n|-\n| {{道具图标|理想币|数量=200}}<br>{{道具图标|记忆序列|数量=1}}\n}}"
        val html = article(h("奖励道具", "奖励道具_2") + "<table class='wikitable'>$rows</table>")
        assertEquals(listOf(Heading("奖励道具"), WikiSection("奖励道具", "奖励道具_2")), ActivityDetailParser.parse(html, title, source))
        for (partial in listOf(html.substringBeforeLast("<tr>"), html.replace("<td rowspan='2'>2</td>", ""),
            html.replace(icon("记忆序列", 1), ""), html.replace("data-count=\"80\"", ""), html.replace("id='奖励道具_2'", ""))) {
            assertTrue(ActivityDetailParser.parse(partial, title, source).isEmpty())
        }
        assertTrue(ActivityDetailParser.parse(html, title, source.removeSuffix("}}")).isEmpty())
        val hidden = html.replace("<table", "<p>奖励说明</p><div style='display:none'><table").replace("</table>", "</table></div>")
        assertTrue(ActivityDetailParser.parse(hidden, title, source).isEmpty())
    }

    @Test fun emptyMusicOrTabShellCannotProveASectionOrOverwriteCache() = runBlocking {
        val audio = "<div class='media-audio' data-file='https://wiki.example/deep-space.mp3'></div>"
        val table = "<table class='text-conversation'><tr><td></td><td><span class='char-text'>看群名。</span></td></tr></table>"
        val tabs = "<div class='resp-tabs'><ul class='resp-tabs-list'><li>对话1</li><li>对话2</li></ul>" +
            "<div class='resp-tabs-container KaPhone'><div class='resp-tab-content'>$table</div><div class='resp-tab-content'>$table</div></div></div>"
        val html = article(h("活动剧情") + tabs + "<p>舞台点亮</p>" + h("活动音乐", "活动音乐_2&amp;现场") +
            "<div style='display: none'><a>媒体文件:Bgm-深空开拓.mp3</a></div>" + audio)
        val wt = "== 活动剧情 ==\n{{/卡丘通讯}}\n== 活动音乐 ==\n{{Audio|Bgm-深空开拓.mp3}}"
        val good = articlePayload(title, wt, html)
        val store = ArticlePublicationFixture(good)
        suspend fun read() = ActivityDetailApi.fetch(title, load = store.load)
        val original = assertIs<ApiResult.Success<*>>(read()).value
        assertEquals(listOf(Heading("活动剧情"), WikiSection("活动剧情", "活动剧情"), Paragraph("舞台点亮"),
            Heading("活动音乐"), WikiSection("活动音乐", "活动音乐_2&现场")), original)
        for (partial in listOf(html.replace(audio, ""), html.replace(audio, "<div class='media-audio'></div>"),
            html.replaceFirst(table, ""), html.replace("<li>对话2</li>", ""), html.replace("id='活动剧情'", ""))) {
            store.response = articlePayload(title, wt, partial)
            val fallback = assertIs<ApiResult.Success<*>>(read())
            assertTrue(fallback.isOffline); assertEquals(original, fallback.value); assertEquals(good, store.disk)
        }
    }

    @Test fun parentWithDeclaredProseCannotBorrowAChildsBody() {
        val html = article(h("踏浪疾行") + "<h3>签到奖励</h3><p>每日签到。</p>", crumb)
        assertTrue(ActivityDetailParser.parse(html, title, "$set\n== 踏浪疾行 ==\n共计15天的签到好礼！\n=== 签到奖励 ===\n每日签到。").isEmpty())
    }
}
