package com.nekolaska.calabiyau.feature.wiki.beginner

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.beginner.api.BeginnerGuideApi
import com.nekolaska.calabiyau.feature.wiki.beginner.model.*
import com.nekolaska.calabiyau.feature.wiki.beginner.parser.BeginnerGuideParser
import com.nekolaska.calabiyau.feature.wiki.beginner.source.BeginnerGuideRemoteSource
import com.nekolaska.calabiyau.feature.wiki.hub.WikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.decodeRoute
import com.nekolaska.calabiyau.feature.wiki.hub.encode
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class BeginnerGuideTest {
    private val html = javaClass.getResource("/fixtures/beginner/guide.html")!!.readText()
    private fun payload(content: String, version2: Boolean = false) = buildJsonObject {
        putJsonObject("parse") {
            put("title", BEGINNER_GUIDE_PAGE)
            if (version2) put("text", content) else putJsonObject("text") { put("*", content) }
        }
    }.toString()

    @Test fun preservesSourceSectionsTablesEmphasisNestedListsAndAllDownloadPanes() {
        val parsed = BeginnerGuideParser.parse(html)
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        assertEquals(listOf("我适合入坑吗", "我该下哪个版本", "下载与安装", "配置需求"), parsed.page.sections.map { it.title })
        val all = parsed.page.sections.flatMap { it.blocks }
        assertEquals(17, all.filterIsInstance<GuideBlock.Record>().size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        val paragraphs = all.filterIsInstance<GuideBlock.Paragraph>()
        assertEquals(8, paragraphs.count { it.depth == 2 })
        assertEquals(1, paragraphs.count { it.content.text == "有朋友一起，或者能接受单排" })
        assertTrue(paragraphs.first().content.spans.any { it.bold && it.text == "PvP 射击" })
        assertTrue(paragraphs.single { it.notice }.content.text.contains("数据不互通"))
        assertFalse(paragraphs.any { it.content.text.contains('×') })
        val links = paragraphs.flatMap { it.content.spans }.mapNotNull { it.url }
        assertEquals(listOf("https://klbq.idreamsky.com/?nav=home", "https://store.steampowered.com/app/1282270/",
            "https://www.strinova.com/", "https://klbqm.idreamsky.com/?nav=home"), links)
        val system = all.filterIsInstance<GuideBlock.Record>().single { it.title.text == "操作系统" }
        assertEquals("最低配置 / 推荐配置", system.fields.single().label)
        assertEquals(1, Regex("商店页还写着").findAll(system.fields.single().value.text).count())
        assertTrue(system.fields.single().value.text.contains("实际得 Win10 以上"))
        val gpu = all.filterIsInstance<GuideBlock.Record>().single { it.title.text == "显卡" }
        assertEquals("Nvidia GeForce GT 730\nAMD R7 240", gpu.fields.first().value.text)
    }

    @Test fun rejectsPartialSectionsTablesFieldsDownloadPanesAndMalformedSpans() {
        val changes: List<(Document) -> Unit> = listOf(
            { it.select("h2").last()!!.remove() },
            { it.select("table").last()!!.remove() },
            { it.select("table")[1].select("tr").last()!!.remove() },
            { it.select("table").first()!!.select("tr")[2].child(1).remove() },
            { it.selectFirst("td[colspan]")!!.attr("colspan", "2000") },
            { it.selectFirst("td[colspan]")!!.attr("rowspan", "2000") },
            { it.select(".resp-tab-content").last()!!.remove() },
            { it.select(".resp-tab-content")[1].select("li").last()!!.remove() },
            { it.selectFirst("li")!!.remove() },
            { it.selectFirst(".alert")!!.remove() },
            { it.selectFirst("h3")!!.remove() },
            { it.select("table")[0].select("tr").forEach { row -> row.child(2).remove() } },
            { it.selectFirst("td")!!.empty() }
        )
        changes.forEachIndexed { index, damage ->
            val broken = Jsoup.parse(html).also(damage).html()
            assertFalse(BeginnerGuideParser.parse(broken).canCache, "damage $index")
            assertNotNull(BeginnerGuideRemoteSource.validationIssue(payload(broken)), "payload $index")
            assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(BeginnerGuideApi.parseSource(WikiHtmlPageSourceResult(broken, true, 1L))).kind)
        }
    }

    @Test fun semanticColumnsAndBoundedRowspansKeepTheirLabels() {
        val document = Jsoup.parse(html)
        val table = document.select("table").first()!!
        table.select("tr").forEach { row -> row.appendChild(row.child(1)) }
        val rows = table.select("tr")
        rows[1].child(1).attr("rowspan", "2")
        rows[2].child(1).remove()
        val parsed = BeginnerGuideParser.parse(document.html())
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        val versions = parsed.page.sections[1].blocks.filterIsInstance<GuideBlock.Record>()
        assertEquals("QQ、微信", versions[1].fields.single { it.label == "登录方式" }.value.text)
        assertEquals("启动器", versions[1].fields.single { it.label == "游玩平台" }.value.text)
    }

    @Test fun allowsAdditionsAndPreservesUnknownTextWithoutDuplicateKeys() {
        val document = Jsoup.parse(html)
        document.selectFirst("h3")!!.after("<p>新增说明</p><p>新增说明</p>")
        document.select("table").first()!!.selectFirst("tbody")!!.append("<tr><th>新版本</th><td>平台</td><td>账号</td><td>说明</td></tr>")
        val parsed = BeginnerGuideParser.parse(document.html())
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        val repeated = parsed.page.sections.flatMap { it.blocks }.filterIsInstance<GuideBlock.Paragraph>().filter { it.content.text == "新增说明" }
        assertEquals(2, repeated.map { it.id }.distinct().size)
    }

    @Test fun urlsRejectExecutableSchemesAndKeepReadableText() {
        listOf("javascript:alert(1)", "data:text/html,x", "file:///tmp/a", "https://user:pass@example.com/",
            "http://game.bilibili.com/linkfilter/?url=javascript%3Aalert(1)").forEach { assertNull(BeginnerGuideParser.safeUrl(it)) }
        assertEquals("https://example.com/a", BeginnerGuideParser.safeUrl("//example.com/a"))
        assertTrue(BeginnerGuideParser.safeUrl("/klbq/新人入坑指南")!!.startsWith("https://wiki.biligame.com/klbq/"))
        val text = BeginnerGuideParser.richText(Jsoup.parse("<p><a href='javascript:alert(1)'>说明</a></p>").selectFirst("p")!!)
        assertEquals("说明", text.text)
        assertNull(text.spans.single().url)
    }

    @Test fun validatesBothEnvelopesAndRejectsWrongPage() {
        listOf(false, true).forEach { assertNull(BeginnerGuideRemoteSource.validationIssue(payload(html, it))) }
        assertNotNull(BeginnerGuideRemoteSource.validationIssue(payload(html).replace(BEGINNER_GUIDE_PAGE, "其他页面")))
        assertNotNull(BeginnerGuideRemoteSource.validationIssue("{\"error\":{}}"))
    }

    @Test fun invalidFreshDataKeepsGoodDiskAndOfflineMetadata() = runBlocking {
        val good = payload(html)
        var disk: String? = null
        val loaded = BeginnerGuideApi.loadSource {
            BeginnerGuideRemoteSource.fetchValidated { validate ->
                fetchValidatedCachePayload(true, { good }, { null }, { disk = it }, validate)
            }
        }
        assertFalse(assertIs<ApiResult.Success<*>>(loaded).isOffline)
        assertEquals(good, disk)
        val broken = payload(Jsoup.parse(html).apply { select("table").last()!!.remove() }.html())
        val fallback = BeginnerGuideApi.loadSource {
            BeginnerGuideRemoteSource.fetchValidated { validate ->
                fetchValidatedCachePayload(true, { broken }, { OfflineCache.CacheResult(disk!!, true, 456L) }, { disk = it }, validate)
            }
        }
        assertTrue(assertIs<ApiResult.Success<*>>(fallback).isOffline)
        assertEquals(456L, fallback.cacheAgeMs)
        assertEquals(good, disk)
        val noCache = BeginnerGuideApi.loadSource {
            BeginnerGuideRemoteSource.fetchValidated { validate ->
                fetchValidatedCachePayload(true, { broken }, { null }, { error("Invalid write") }, validate)
            }
        }
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(noCache).kind)
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(BeginnerGuideApi.parseSource(null)).kind)
    }

    @Test fun cancellationPropagates(): Unit = runBlocking {
        assertFailsWith<CancellationException> { BeginnerGuideApi.loadSource { throw CancellationException() } }
    }

    @Test fun nativeRouteRoundTripsWithoutChangingOldGuideRoutes() {
        assertEquals(WikiRoute.BeginnerGuide, decodeRoute(WikiRoute.BeginnerGuide.encode()))
        assertEquals(WikiRoute.PcGameSettings, decodeRoute("pcGameSettings"))
        assertEquals(WikiRoute.GameTips, decodeRoute("gameTips"))
    }

    @Test fun fullLiveCaptureMatchesContentFixture() {
        val file = System.getenv("BEGINNER_GUIDE_AUDIT_FILE")?.let(::File)
        assumeTrue("Set BEGINNER_GUIDE_AUDIT_FILE to full parse HTML", file?.isFile == true)
        val actual = BeginnerGuideParser.parse(file!!.readText(Charsets.UTF_8))
        assertTrue(actual.canCache, actual.issues.joinToString())
        val expected = BeginnerGuideParser.parse(html)
        // Normalized visible content (capture whitespace may differ around inline tags).
        fun text(page: BeginnerGuidePage) = page.sections.map { section ->
            section.blocks.map { block -> when (block) {
                is GuideBlock.Heading -> block.title
                is GuideBlock.Paragraph -> block.content.text
                is GuideBlock.Record -> block.title.text + block.fields.joinToString { it.label + it.value.text }
            }.replace(Regex("\\s+"), "") }
        }
        assertEquals(text(expected.page), text(actual.page))
    }
}
