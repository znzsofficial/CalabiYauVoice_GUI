package com.nekolaska.calabiyau.feature.wiki.activity

import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityDetailApi
import data.ApiResult
import kotlinx.coroutines.runBlocking
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityDetailParser
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationHtmlParser
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationLuaCatalogParser
import java.io.File
import org.jsoup.Jsoup
import org.junit.Assume.assumeNotNull
import kotlin.test.*

/** Full 120799–120802 event, index and catalogs. No network or Android runtime required. */
class November24ActivityAuditTest {
    private fun read(name: String): String {
        val path = System.getenv("WIKI_ACTIVITY_NOV24_AUDIT_DIR")
        assumeNotNull(path)
        return File(path!!, name).readText(Charsets.UTF_8)
    }

    @Test fun followsIndexToTheTwoRewardCatalogs() {
        val card = ActivityParsers.parseActivities(read("activity.html")).first().entry
        val homepage = assertNotNull(Jsoup.parse(read("header.html")).selectFirst(".home-page-card"))
        val link = assertNotNull(homepage.selectFirst(".home-page-card__title a"))
        val timer = assertNotNull(homepage.selectFirst(".eventTimer"))
        val title = assertNotNull(card.detailPageTitle)
        assertEquals("欢迎光临卡邦尼！", title)
        assertEquals(title, link.text()); assertEquals(card.wikiUrl, "https://wiki.biligame.com" + link.attr("href"))
        assertEquals("2026/11/24 06:00", timer.attr("data-start")); assertEquals("2026/12/29 05:59", timer.attr("data-end"))
        assertEquals("2026年11月24日 06:00", card.startTime); assertEquals("2026年12月29日 05:59", card.endTime)

        val blocks = ActivityDetailParser.parse(read("detail.html"), title)
        assertEquals(listOf("活动规则", "每周任务", "奖励", "邦尼秘闻", "汐【打工吧！引航者大人！】", "诺诺【天才败北事件簿】", "活动壁纸"),
            blocks.filterIsInstance<Heading>().map { it.text })
        fun section(name: String) = blocks.dropWhile { it != Heading(name) }.drop(1).takeWhile { it !is Heading }
        assertEquals(3, section("活动规则").size)
        val tasks = section("每周任务").filterIsInstance<TableRow>().filterNot { it.isHeader }
        assertEquals(10, tasks.size); assertEquals(listOf("累计登录1天", "50"), tasks.first().cells)
        val rewards = section("奖励").filterIsInstance<TableRow>().filterNot { it.isHeader }.map { it.cells }
        assertEquals(30, rewards.size)
        assertEquals(listOf("累计获得100个邦尼特大汉堡玩具", "活动凭录 ×500"), rewards.first())
        assertEquals(listOf("累计获得800个邦尼特大汉堡玩具", "聊天气泡：邦邦快送"), rewards[7])
        assertEquals(listOf("累计获得1600个邦尼特大汉堡玩具", "喷漆：鹅了么"), rewards[15])

        val ways = "【欢迎光临卡邦尼！】活动奖励"
        val chat = DecorationLuaCatalogParser.parse(DecorationCatalogs.forPage("聊天气泡").source as DecorationCatalogSource.Lua,
            read("chatdata.txt"), read("120800.txt"))
        assertTrue(chat.isComplete, chat.issues.toString())
        val bubble = chat.sections.flatMap { it.items }.single { it.id == 70001040 }
        assertEquals("邦邦快送", bubble.name); assertEquals(ways, bubble.source)
        val spray = DecorationHtmlParser.parseResult(read("decal.html"))
        assertTrue(spray.isComplete, spray.issues.toString())
        val decal = spray.sections.single { it.title == "活动奖励喷漆" }.items.single { it.id == 30002321 }
        assertEquals("鹅了么", decal.name); assertEquals(ways, decal.source)
    }

    @Test fun introWithoutItsTocOrDeclaredSectionsCannotReplace120799() = runBlocking {
        val title = "欢迎光临卡邦尼！"
        val good = read("detail.json")
        val store = ArticlePublicationFixture(good)
        suspend fun refresh() = ActivityDetailApi.fetch(title, forceRefresh = true, load = store.load)
        val original = assertIs<ApiResult.Success<*>>(refresh()).value
        assertEquals(65, (original as List<*>).size)
        val article = Jsoup.parse(read("detail.html"))
        val intro = article.select(".klbq-event-header ~ p").first()!!
        intro.nextElementSiblings().remove()
        val partial = article.outerHtml()
        assertEquals(1, ActivityDetailParser.parse(partial, title).size) // No HTML-only clue distinguishes this from a short article.
        store.response = articlePayload(title, read("120799.txt"), partial)
        val fallback = assertIs<ApiResult.Success<*>>(refresh())
        assertEquals(original, fallback.value); assertTrue(fallback.isOffline); assertEquals(123L, fallback.cacheAgeMs)
        assertEquals(good, store.disk)
        // A real edit can legitimately retire the sections. No block-count threshold or TOC requirement.
        store.response = articlePayload(title, "{{活动}}\n" + intro.text(), partial)
        val shortened = refresh()
        assertTrue(shortened is ApiResult.Success && !shortened.isOffline)
        assertEquals(1, shortened.value.size); assertEquals(store.response, store.disk)
    }
}
