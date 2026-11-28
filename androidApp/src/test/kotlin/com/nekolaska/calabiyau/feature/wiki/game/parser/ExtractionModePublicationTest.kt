package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock
import com.nekolaska.calabiyau.feature.wiki.game.model.sections
import data.SharedJson
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.junit.Assume.assumeNotNull
import java.io.File
import kotlin.test.*

class ExtractionModePublicationTest {
    private val mode = GameModeApi.EXTRACTION_MODE
    private fun payload(wt: String?, html: String?) = articlePayload(mode.pageName, wt, html)
    private suspend fun read(store: ArticlePublicationFixture, diskOnly: Boolean = false) =
        GameModeApi.fetchFromSource(focusedMode = mode, forceRefresh = !diskOnly, cacheOnly = diskOnly, load = store.load)

    @Test fun requiresOnlyTheChaptersAndTableRowsThisRevisionDeclares() = runBlocking {
        val wt = """介绍。
            |== 任务系统 ==
            |=== 新的分支 ===
            |{| class="klbqtable"
            |! 名称 !! 条件
            ||-
            || 甲 || 登录
            ||-
            || 乙 || 登录
            ||}""".trimMargin()
        val table = "<table class='klbqtable'><tr><th>名称</th><th>条件</th></tr><tr><td>甲</td><td>登录</td></tr><tr><td>乙</td><td>登录</td></tr></table>"
        val html = "<div class='mw-parser-output'><h2><span class='mw-headline'>任务系统</span></h2>" +
            "<h3><span class='mw-headline'>新的分支</span></h3><p>说明。</p>$table</div>"
        val good = payload(wt, html)
        val store = ArticlePublicationFixture(good)
        val complete = assertIs<ApiResult.Success<*>>(read(store)); assertFalse(complete.isOffline)
        for (broken in listOf(html.replace(table, ""), html.replace("<tr><td>乙</td><td>登录</td></tr>", ""),
            html.replace("<td>乙</td>", ""), html.replace("新的分支", "另一分支"), html.replace("任务系统", "模式音乐"))) {
            store.response = payload(wt, broken)
            val fallback = assertIs<ApiResult.Success<*>>(read(store))
            assertTrue(fallback.isOffline); assertEquals(123L, fallback.cacheAgeMs)
            assertEquals(complete.value, fallback.value); assertEquals(good, store.disk)
        }
        store.disk = null
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store)).kind)
        store.disk = store.response
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store, diskOnly = true)).kind)
    }

    @Test fun legacyWikitextIsForDiskOnlyAndCancellationIsNotAMiss() = runBlocking {
        val legacy = payload("== 模式设定 ==\n* 模式地图：[[维里昂裂隙]]", null)
        val store = ArticlePublicationFixture(legacy)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store)).kind)
        assertNull(store.disk)
        store.response = null
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(read(store)).kind)
        store.disk = legacy
        val before = store.requests
        val cached = read(store, diskOnly = true)
        assertTrue(cached is ApiResult.Success && cached.isOffline && cached.cacheAgeMs == 123L)
        assertEquals(listOf("维里昂裂隙"), cached.value.single().maps); assertTrue(cached.value.single().chapters.isEmpty())
        assertEquals(before, store.requests)
        store.response = payload("== 任务系统 ==\n说明", "<div class='mw-parser-output'></div>")
        assertEquals(cached, read(store)); assertEquals(legacy, store.disk)
        assertFailsWith<CancellationException> { GameModeApi.fetchFromSource(focusedMode = mode, load = { _, _ -> throw CancellationException() }) }
        Unit
    }

    @Test fun chapterRenameCannotSilentlyPublishOnlyThePreviouslyKnownTitles() = runBlocking {
        val titles = listOf("玩家等级", "角色等级", "角色天赋", "任务", "局内事件", "新玩法")
        val wt = titles.joinToString("\n") { "== $it ==\n说明。" }
        fun html(chapters: List<String>) = chapters.joinToString("", "<div class='mw-parser-output'>", "</div>") {
            "<h2><span class='mw-headline'>$it</span></h2><p>说明。</p>"
        }
        val full = payload(wt, html(titles))
        val store = ArticlePublicationFixture(full)
        val fresh = read(store)
        assertTrue(fresh is ApiResult.Success && !fresh.isOffline)
        assertEquals(titles, fresh.value.single().chapters.map { it.title })
        store.response = payload(wt, html(listOf("角色天赋", "局内事件")))
        val fallback = read(store)
        assertTrue(fallback is ApiResult.Success && fallback.isOffline && fallback.cacheAgeMs == 123L)
        assertEquals(full, store.disk)
        store.disk = null
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store)).kind)
    }

    @Test fun revision121029ReadsTheFiveChaptersAndBothBranchesFromDiskAndNetwork() = runBlocking {
        val path = System.getenv("WIKI_MODE_NOV27_AUDIT_FILE")
        assumeNotNull(path)
        val full = File(path!!).readText()
        val store = ArticlePublicationFixture(full)
        val fresh = read(store)
        assertTrue(fresh is ApiResult.Success && !fresh.isOffline)
        val chapters = fresh.value.single().chapters
        assertEquals(listOf("玩家等级", "角色等级", "角色天赋", "任务", "局内事件"), chapters.map { it.title })
        assertEquals(listOf(54, 19, 10, 52, 18), chapters.map { c -> c.blocks.count { it.kind == FieldGuideBlock.Kind.RECORD } })
        assertTrue(chapters[0].blocks.any { "等级：Lv50" in it.content.text && "累计经验：9000" in it.content.text })
        assertTrue(chapters[2].blocks.any { "解锁等级：Lv18" in it.content.text && "附加前置：\n• 生存等级 1\n• 工坊等级 1" in it.content.text })
        val tasks = chapters[3]
        val battle = tasks.sections().single { it.label == "战斗线任务" }
        val rows = battle.blocks.map { tasks.blocks[it] }.filter { it.kind == FieldGuideBlock.Kind.RECORD }
        assertEquals(12, rows.size)
        for (id in listOf("4-1", "4-2")) assertTrue(rows.any { "序号：$id\n" in it.content.text && "支链" in it.content.text })
        val cached = read(store, diskOnly = true)
        assertTrue(cached is ApiResult.Success && cached.isOffline)
        assertEquals(chapters, cached.value.single().chapters)

        val page = SharedJson.parseToJsonElement(full).jsonObject.getValue("parse").jsonObject
        val wt = page.getValue("wikitext").jsonObject.getValue("*").jsonPrimitive.content
        val doc = Jsoup.parse(page.getValue("text").jsonObject.getValue("*").jsonPrimitive.content)
        doc.getElementById("玩家等级")!!.parent()!!.nextElementSiblings().first { it.tagName() == "table" }
            .selectFirst("tr:has(td)")!!.remove()
        store.response = payload(wt, doc.outerHtml())
        val fallback = read(store)
        assertTrue(fallback is ApiResult.Success && fallback.isOffline)
        assertEquals(full, store.disk)
    }

    @Test fun captured120798LosingEveryChapterOrOneTableRowRetainsTheFullRevision() = runBlocking {
        val path = System.getenv("WIKI_MAP_MODE_AUDIT_DIR")
        assumeNotNull(path)
        val wt = File(path!!, "120798.txt").readText()
        val html = File(path, "120798.html").readText()
        val good = payload(wt, html)
        val store = ArticlePublicationFixture(good)
        val fresh = read(store)
        assertTrue(fresh is ApiResult.Success && !fresh.isOffline)
        val chapters = fresh.value.single().chapters
        assertEquals(listOf(112, 18, 54, 18), chapters.map { c -> c.blocks.count { it.kind == FieldGuideBlock.Kind.RECORD } })
        val withoutRow = Jsoup.parse(html).also { it.selectFirst("table.klbqtable tr:has(td)")!!.remove() }.outerHtml()
        for (broken in listOf("<div class='mw-parser-output'><p>弦溯战线</p></div>", withoutRow)) {
            store.response = payload(wt, broken)
            val fallback = read(store)
            assertTrue(fallback is ApiResult.Success && fallback.isOffline && fallback.cacheAgeMs == 123L)
            assertEquals(chapters, fallback.value.single().chapters); assertEquals(good, store.disk)
        }
    }
}
