package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
