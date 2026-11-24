package com.nekolaska.calabiyau.feature.wiki.activity

import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.activity.api.ActivityDetailApi
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.hub.WikiNavigationPolicy
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeNotNull
import java.io.File
import java.net.URI
import kotlin.test.*

class ArchivedActivityAuditTest {
    @Test fun sectionOpensTheSameArticleWithTheExactDomId() {
        val url = "https://wiki.biligame.com/klbq/index.php?title=旧活动#奖励"
        val id = "签到奖励_2 + 第3档#"
        val target = activitySectionUrl(url, id)
        assertTrue(WikiNavigationPolicy.mayLoadDocument(target))
        assertEquals(URI(url).rawQuery, URI(target).rawQuery)
        assertEquals(id, URI(target).fragment)
    }

    @Test fun oldHtmlOnlySignupCacheOpensWithoutNetworkingButWrongArticleDoesNot() = runBlocking {
        val path = System.getenv("WIKI_ARCHIVED_ACTIVITY_DIR")
        assumeNotNull(path)
        val page = SharedJson.parseToJsonElement(File(requireNotNull(path), "item-109.json").readText())
            .jsonObject.getValue("parse").jsonObject
        val title = page.getValue("title").jsonPrimitive.content
        assertEquals("时空之音", title)
        val html = page.getValue("text").jsonObject.getValue("*").jsonPrimitive.content
        val old = ArticlePublicationFixture(null).apply { disk = articlePayload(title, null, html) }
        val result = ActivityDetailApi.fetch(title, cacheOnly = true, load = old.load)
        assertTrue(result is ApiResult.Success && result.isOffline)
        assertContains(result.value.filterIsInstance<TableRow>().map { it.cells }, listOf("7", "记忆序列 ×10"))
        val wrong = ActivityDetailApi.fetch("另一活动", cacheOnly = true, load = old.load)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(wrong).kind)
        assertEquals(0, old.requests)
    }

    /** Full action=parse responses for archived events, read via the same list, API and disk policy as the app. */
    @Test fun archivedEventsCanBeOpenedFromTheirActualIndexEntries() = runBlocking {
        val path = System.getenv("WIKI_ARCHIVED_ACTIVITY_DIR")
        assumeNotNull(path)
        val dir = File(requireNotNull(path))
        val catalog = dir.resolve("activity.html").readText()
        val entries = ActivityParsers.parseActivities(catalog)
        val links = Jsoup.parse(catalog, "https://wiki.biligame.com/klbq/活动").select(".klbq-event-card__title a[href]")
        assertEquals(110, links.size) // Size of this snapshot, never enforced by the production reader.
        assertEquals(links.map { it.attr("title") to it.absUrl("href") }, entries.map { it.detailPageTitle to it.entry.wikiUrl })
        val index = entries.associateBy { it.detailPageTitle }
        val verified = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        dir.listFiles().orEmpty().filter { it.name.startsWith("item-") && it.extension == "json" }.forEach { file ->
            val raw = file.readText()
            val page = SharedJson.parseToJsonElement(raw).jsonObject.getValue("parse").jsonObject
            val title = page.getValue("title").jsonPrimitive.content
            val entry = assertNotNull(index[title], title).entry
            assertTrue(WikiNavigationPolicy.mayLoadDocument(entry.wikiUrl), title)
            val store = ArticlePublicationFixture(raw)
            val online = ActivityDetailApi.fetch(title, load = store.load)
            if (online !is ApiResult.Success) { problems += "$title: $online"; return@forEach }
            assertFalse(online.isOffline, title)
            val blocks = online.value
            assertTrue(blocks.any { it is Paragraph || it is TableRow || it is Image }, title)
            val article = Jsoup.parse(page.getValue("text").jsonObject.getValue("*").jsonPrimitive.content)
            val displayed = blocks.filterIsInstance<Heading>().map { it.text }.toSet()
            val sourceHeadings = article.select(".mw-parser-output > h2 > .mw-headline").eachText()
            if (!displayed.containsAll(sourceHeadings)) problems += "$title: missing ${sourceHeadings - displayed}"
            for (section in blocks.filterIsInstance<WikiSection>()) {
                val target = activitySectionUrl(entry.wikiUrl, section.anchor)
                assertNotNull(article.getElementById(requireNotNull(URI(target).fragment)), "$title → $target")
            }
            val rows = blocks.filterIsInstance<TableRow>().map { it.cells }
            // Independent values taken from each published event, not just a non-empty parser result.
            when (title) {
                "时空之音" -> {
                    assertTrue(blocks.any { it is Image }, "Missing the old signup illustration")
                    assertContains(rows, listOf("7", "记忆序列 ×10"))
                }
                "1周年版本活动汇总" -> assertContains(rows, listOf("入园打卡LV.1", "累计登录游戏1天", "40"))
                "2周年版本活动汇总" -> assertContains(rows, listOf("累计登录游戏1天", "50"))
                "冬夜庄园与神秘客" -> assertContains(rows, listOf("登录游戏", "20"))
            }
            val offline = ActivityDetailApi.fetch(title, cacheOnly = true, load = store.load)
            assertTrue(offline is ApiResult.Success && offline.isOffline && offline.value == blocks, title)
            assertEquals(1, store.requests, title)
            verified += title
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertTrue(verified.containsAll(listOf("时空之音", "夏日音乐节", "深空开拓", "1周年版本活动汇总", "冬夜庄园与神秘客", "2周年版本活动汇总", "弦海星歌签到")), "Missing cross-year fixtures: $verified")
    }
}
