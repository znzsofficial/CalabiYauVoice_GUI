package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.ArticlePublicationFixture
import com.nekolaska.calabiyau.core.wiki.WikiArticleRequest
import com.nekolaska.calabiyau.core.wiki.articlePayload
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock.Kind.RECORD
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assume.assumeNotNull
import java.io.File
import kotlin.test.*

class RiftMapPublicationTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/maps/$name")).readText(Charsets.UTF_8)
    private suspend fun read(store: ArticlePublicationFixture, diskOnly: Boolean = false) =
        MapDetailApi.read(RIFT_MAP, forceRefresh = !diskOnly, cacheOnly = diskOnly) { request, valid ->
            assertEquals(WikiArticleRequest(RIFT_MAP, OfflineCache.Type.MAP_DETAIL, RIFT_MAP, !diskOnly, diskOnly), request)
            store.load(request, valid)
        }

    private suspend fun checkMissingRow(wt: String, html: String, expectedRecords: List<Int>) {
        val good = articlePayload(RIFT_MAP, wt, html)
        val store = ArticlePublicationFixture(good)
        val fresh = read(store)
        assertTrue(fresh is ApiResult.Success && !fresh.isOffline && fresh.cacheAgeMs == 0L)
        assertEquals(expectedRecords, fresh.value.chapters.map { chapter -> chapter.blocks.count { it.kind == RECORD } })
        val document = Jsoup.parse(html)
        document.select("table.klbqtable tr").single { it.selectFirst("> td")?.text() == "侵蚀等级" }.remove()
        store.response = articlePayload(RIFT_MAP, wt, document.outerHtml())
        val fallback = read(store)
        assertTrue(fallback is ApiResult.Success && fallback.isOffline && fallback.cacheAgeMs == 123L)
        assertEquals(fresh.value, fallback.value)
        assertEquals(good, store.disk)
        assertEquals(2, store.requests)
        assertEquals(fallback, read(store, diskOnly = true))
        assertEquals(2, store.requests)

        store.disk = null
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store)).kind)
        assertNull(store.disk)
        store.disk = store.response
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(read(store, diskOnly = true)).kind)
        assertEquals(3, store.requests)
    }

    @Test fun missingDeclaredRowCannotReplaceGoodDisk() = runBlocking {
        val wt = """{{地图|简介=维里昂}}
            |== 难度与准入 ==
            |{| class="klbqtable"
            |! 维度 !! 基点
            ||-
            || 侵蚀等级 || 侵蚀等级-Ⅰ
            ||-
            || 单局时长 || 25 分钟
            ||}""".trimMargin()
        val html = """<div class="mw-parser-output"><h2><span class="mw-headline">难度与准入</span></h2>
            <table class="klbqtable"><tr><th>维度</th><th>基点</th></tr><tr><td>侵蚀等级</td><td>侵蚀等级-Ⅰ</td></tr>
            <tr><td>单局时长</td><td>25 分钟</td></tr></table></div>"""
        checkMissingRow(wt, html, listOf(2))
    }

    @Test fun captured120781LosingErosionRowCannotReplaceGoodDisk() = runBlocking {
        val path = System.getenv("WIKI_MAP_MODE_AUDIT_DIR")
        assumeNotNull(path)
        fun capture(ext: String) = File(path!!, "120781.$ext").readText(Charsets.UTF_8)
        checkMissingRow(capture("txt"), capture("html"), listOf(0, 11, 8, 4, 10))
    }

    @Test fun chapterlessLegacyDiskKeepsItsAgeAndDoesNotSwallowCancellation() = runBlocking {
        val store = ArticlePublicationFixture(null)
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(read(store, diskOnly = true)).kind)
        assertEquals(0, store.requests)
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(read(store)).kind)
        val wt = fixture("rift.wikitext")
        val html = fixture("rift.html")
        val legacy = articlePayload(RIFT_MAP, wt, html)
        store.disk = legacy
        val cached = read(store, diskOnly = true)
        assertTrue(cached is ApiResult.Success && cached.isOffline && cached.cacheAgeMs == 123L)
        assertTrue(cached.value.chapters.isEmpty()); assertContains(cached.value.description, "独立城市位面")
        assertEquals(1, store.requests)
        for (invalid in listOf(articlePayload(RIFT_MAP, "$wt\n== 难度与准入 ==\n说明", html),
            articlePayload(RIFT_MAP, wt, null), articlePayload("404基地", wt, html))) {
            store.response = invalid
            assertEquals(cached, read(store)); assertEquals(legacy, store.disk)
        }
        for (diskOnly in listOf(true, false)) {
            assertFailsWith<CancellationException> {
                MapDetailApi.read(RIFT_MAP, !diskOnly, diskOnly) { _, _ -> throw CancellationException() }
            }
        }
    }
}
