package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionContainersApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionContainerTest {
    private fun fixture() = checkNotNull(javaClass.getResource("/fixtures/extraction/drop_containers_20261106.html")).readText(Charsets.UTF_8)
    private fun parsed() = ExtractionContainerParser.parseResult(fixture()).also { assertTrue(it.canCache, it.issues.toString()) }.entries
    private fun payload(html: String) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()

    @Test fun pairedPanelsPreserveIdsQualitiesCountRulesAndUnpublishedAirdrop() {
        val rows = parsed()
        assertEquals(5, rows.size)
        assertEquals(listOf("10001", "13010", "15001", "20306", null), rows.map { it.containerId })
        assertEquals(listOf("Q5 臻藏", "Q2 精致", "Q1 优秀"), rows.first().dropPoolGroups.map { it.label })
        assertEquals(9, rows[3].dropPoolGroups.sumOf { it.items.size })
        assertEquals(DropCountState.ZERO, rows[2].dropCountState)
        assertEquals(DropCountState.POSITIVE, rows[1].dropCountState)
        assertTrue(rows.last().dropPoolGroups.isEmpty())
    }

    @Test fun panelsJoinOnlyByExactIdEvenWhenReordered() {
        val document = Jsoup.parse(fixture())
        val panel = document.getElementById("mw-customcollapsible-sec-c-13010")!!.closest("tr")!!
        document.selectFirst("tbody")!!.appendChild(panel)
        val result = ExtractionContainerParser.parseResult(document.html())
        assertTrue(result.canCache, result.issues.toString())
        assertEquals(parsed(), result.entries)
    }

    @Test fun changedColumnOrderAndNestedDecorativeTableDoNotMixRows() {
        val document = Jsoup.parse(fixture())
        document.select("tr").filter { it.childrenSize() == 7 }.forEach { row ->
            row.appendChild(row.child(0))
        }
        assertTrue(ExtractionContainerParser.parseResult(document.html()).canCache)
        assertTrue(ExtractionContainerParser.parseResult("<table><tr><td>${fixture()}</td></tr></table>").canCache)
    }

    @Test fun missingPanelChipRowOrCountCannotOverwriteDisk() {
        val changes: List<(org.jsoup.nodes.Document) -> Unit> = listOf(
            { it.getElementById("mw-customcollapsible-sec-c-13010")!!.remove() },
            { it.selectFirst(".sec-drop-chip")!!.remove() },
            { it.selectFirst(".sec-drop-pool__cnt")!!.text("999 项") },
            { it.select("tr")[1].remove() },
            { it.select("tr")[1].child(1).remove() },
            { it.selectFirst(".sec-drop-tog")!!.remove() },
            { it.selectFirst(".sec-drop-pool__q")!!.remove() }
        )
        changes.forEachIndexed { index, change ->
            val document = Jsoup.parse(fixture()).also(change)
            val result = ExtractionContainerParser.parseResult(document.html())
            assertFalse(result.canCache, "damage $index was accepted")
            assertFalse(ExtractionCatalogRemoteSource.isValidContainersPayload(payload(document.html())))
        }
        assertTrue(ExtractionCatalogRemoteSource.isValidContainersPayload(payload(fixture())))
        assertFalse(ExtractionCatalogRemoteSource.isValidContainersPayload("<html>WAF</html>"))
    }

    @Test fun duplicateIdsAndOrphanPanelsFailClosed() {
        val document = Jsoup.parse(fixture())
        document.getElementById("mw-customcollapsible-sec-c-13010")!!.id("mw-customcollapsible-sec-c-10001")
        assertFalse(ExtractionContainerParser.parseResult(document.html()).canCache)
        assertFalse(ExtractionContainerParser.parseResult(fixture() + "<div id='mw-customcollapsible-sec-c-99999'></div>").canCache)
    }

    @Test fun absentOrAmbiguousPageTotalCannotPublishAPartialContainerTable() {
        val missing = Jsoup.parse(fixture()).also { it.select("h2").remove() }
        assertFalse(ExtractionContainerParser.parseResult(missing.html()).canCache)
        val duplicate = Jsoup.parse(fixture())
        duplicate.body().appendChild(duplicate.selectFirst("h2")!!.clone())
        assertFalse(ExtractionContainerParser.parseResult(duplicate.html()).canCache)
    }

    @Test fun explicitlyPublishedEmptyPoolIsDistinctFromUnpublishedPool() {
        val document = Jsoup.parse(fixture())
        document.getElementById("mw-customcollapsible-sec-c-10001")!!.empty()
        document.selectFirst(".mw-customtoggle-sec-c-10001")!!.text("展开 0 项")
        val result = ExtractionContainerParser.parseResult(document.html())
        assertTrue(result.canCache, result.issues.toString())
        val published = result.entries.single { it.containerId == "10001" }
        assertTrue(published.dropPoolGroups.isEmpty())
        assertNull(result.entries.last().containerId)
    }

    @Test fun distributionsDoNotTreatMissingOrMalformedAsZero() {
        assertEquals(DropCountState.ZERO, dropCountState("0 件 100%"))
        assertEquals(DropCountState.ZERO, dropCountState("0 件 100%、1 件 0%"))
        assertEquals(DropCountState.POSITIVE, dropCountState("0 件 20%、1 件 60%、2 件 20%"))
        listOf("", "—", "0 件 90%", "0 件 100% 未确认", "1 件 -5%", "0 件 50%、0 件 50%", "9999999999999 件 100%")
            .forEach { assertEquals(DropCountState.UNKNOWN, dropCountState(it), it) }
    }

    @Test fun reverseLookupKeepsQualityAndZeroCandidateDistinctWithoutInventingWeights() {
        val index = buildDropLookup(parsed())
        val detective = index.filterKeys { it.name == "警探" }
        assertEquals(setOf(1, 2), detective.keys.map { it.qualityCode }.toSet())
        detective.values.forEach { hits ->
            assertEquals(2, hits.size)
            assertEquals(listOf("大眼宝"), hits.filter { it.source.dropCountState != DropCountState.ZERO }.map { it.source.name })
            assertEquals(listOf("散点"), hits.filter { it.source.dropCountState == DropCountState.ZERO }.map { it.source.name })
            assertTrue(hits.all { it.source.numericValues.isEmpty() })
        }
        assertTrue(index.keys.none { it.name == "空投" })
    }

    @Test fun cardIdentityNeverConvertsDropGroupOrMatchesByName() {
        fun card(foot: String) = ExtractionCatalogParsers.parse("""
            <h2>Boss</h2><div class="sec-drop-card"><div class="sec-drop-card__title">同名</div>
            <div class="sec-drop-card__foot">$foot</div></div>
        """, ExtractionCatalogKind.DROPS).single()
        assertEquals("20306", card("晶骸核心 20306 · 掉落组 120306101").containerId)
        assertEquals("20101", card("晶骸容器 20101 · 完整掉落池见下方").containerId)
        assertNull(card("掉落组 110001101 · 完整掉落池见下方").containerId)
        assertNull(card("晶骸容器 20101 / 晶骸容器 20102").containerId)
        assertNotNull(linkedDropContainer(card("晶骸核心 20306"), parsed()))
        assertNull(linkedDropContainer(card("掉落组 120306101"), parsed()))
        assertNull(linkedDropContainer(card("晶骸核心 20306"), parsed() + parsed()))
    }

    @Test fun cacheOnlyIsNetworkFreeAndOfflineFallbackNeverBecomesPermanentMemoryHit() = runBlocking {
        var calls = 0
        val api = ExtractionContainersApi(disk = { WikiHtmlPageSourceResult(fixture(), true, 123) }, network = {
            calls++; WikiHtmlPageSourceResult(fixture(), true, 456)
        })
        assertEquals(123, assertIs<ApiResult.Success<*>>(api.fetch(cacheOnly = true)).cacheAgeMs)
        assertEquals(0, calls)
        repeat(2) { assertTrue(assertIs<ApiResult.Success<*>>(api.fetch()).isOffline) }
        assertEquals(2, calls)
    }

    @Test fun localLookupAndFilterEditsDoNotLoadDependenciesAndRefreshKeepsGoodMemory() = runBlocking {
        var calls = 0
        var html = fixture()
        val api = ExtractionContainersApi(disk = { null }, network = { calls++; WikiHtmlPageSourceResult(html, false, 0) })
        val first = assertIs<ApiResult.Success<List<ExtractionEntry>>>(api.fetch()).value
        val index = buildDropLookup(first)
        listOf("警", "警探", "切割器", "不存在", "").forEach { query ->
            index.keys.filter { it.name.contains(query) }
            first.filter { it.matches(query) }
        }
        assertEquals(1, calls)
        html = "<p>结构失效</p>"
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(api.fetch(forceRefresh = true)).kind)
        assertEquals(first, assertIs<ApiResult.Success<List<ExtractionEntry>>>(api.fetch()).value)
        assertEquals(2, calls)
    }

    @Test fun cancellationPropagatesAndMissingDependencyIsAnError() = runBlocking {
        assertIs<ApiResult.Error>(ExtractionContainersApi(disk = { null }, network = { null }).fetch())
        assertFailsWith<CancellationException> {
            ExtractionContainersApi(disk = { null }, network = { throw CancellationException("cancel") }).fetch()
        }
        Unit
    }

    @Test fun fullLocalSnapshotVerifiesEveryContainerPanelAndOnlyExplicitCardLinks() {
        val directory = System.getenv("EXTRACTION_CONTAINERS_AUDIT_DIR")?.let(::File)
        assumeTrue("Set EXTRACTION_CONTAINERS_AUDIT_DIR to the local November 6 captures", directory?.isDirectory == true)
        val html = File(directory!!, "drop-table.html").readText(Charsets.UTF_8)
        val document = Jsoup.parse(html)
        val parsed = ExtractionContainerParser.parseResult(html)
        assertTrue(parsed.canCache, parsed.issues.toString())
        assertEquals(52, parsed.entries.size)
        parsed.entries.filter { it.containerId != null }.forEach { entry ->
            val panel = document.getElementById("mw-customcollapsible-sec-c-${entry.containerId}")!!
            assertEquals(panel.select(".sec-drop-chip").map { it.text() }, entry.dropPoolGroups.flatMap { it.items })
            assertEquals(panel.select(".sec-drop-pool__tag").map { it.text() }, entry.dropPoolGroups.map { it.label })
        }
        val sources = ExtractionCatalogParsers.parse(File(directory, "drops.html").readText(Charsets.UTF_8), ExtractionCatalogKind.DROPS)
            .filter { it.type == ExtractionEntryType.DROP_SOURCE }
        assertEquals(51, sources.size)
        assertEquals(17, sources.count { linkedDropContainer(it, parsed.entries) != null })
        assertEquals(34, sources.count { it.containerId == null })
        assertEquals(listOf("15001"), parsed.entries.filter { it.dropCountState == DropCountState.ZERO }.map { it.containerId })
        println("Containers=${parsed.entries.size}, groups=${parsed.entries.sumOf { it.dropPoolGroups.size }}, explicit card links=17, item/quality records=${buildDropLookup(parsed.entries).size}")
    }
}
