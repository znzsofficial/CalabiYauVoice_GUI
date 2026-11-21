package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionContainerNovember21Test {
    private fun fixture(date: String = "20261121") =
        checkNotNull(javaClass.getResource("/fixtures/extraction/drop_containers_$date.html")).readText(Charsets.UTF_8)

    private fun parse(html: String) = ExtractionContainerParser.parseResult(html).also {
        assertTrue(it.canCache, it.issues.toString())
    }.entries

    private fun validPayload(html: String) = ExtractionCatalogRemoteSource.isValidContainersPayload(
        buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()
    )

    @Test fun currentAndCachedLayoutsPublishTheirOwnFieldsAndCounts() {
        val rows = parse(fixture())
        assertTrue(validPayload(fixture()))
        assertEquals(listOf("15001", "20101", "80001"), rows.map { it.containerId })
        val scatter = rows.first()
        assertEquals(listOf("地面散落"), scatter.tags)
        assertEquals(listOf(ExtractionField("子类", "地面散落"), ExtractionField("件数概率", "0 件 30%（合计 30%，数据不完整）")), scatter.fields)
        assertEquals(DropCountState.UNKNOWN, scatter.dropCountState)
        assertEquals(DropCountState.POSITIVE, rows[1].dropCountState)
        assertEquals("空投", rows.last().name)
        assertEquals(DropCountState.POSITIVE, rows.last().dropCountState)
        assertEquals(4, rows.last().dropPoolGroups.sumOf { it.items.size })

        val old = parse(fixture("20261106"))
        assertEquals(listOf("子类", "档次", "件数概率", "专属池"), old.first().fields.map { it.label })
        assertEquals(DropCountState.ZERO, old.single { it.containerId == "15001" }.dropCountState)
        assertNull(old.single { it.name == "空投" }.containerId)
        assertTrue(old.single { it.name == "空投" }.dropPoolGroups.isEmpty())
    }

    @Test fun unknownCountIsNotExcludedAndLookupIdentityIncludesQuality() {
        val lookup = buildDropLookup(parse(fixture()))
        assertEquals(5, lookup.keys.distinctBy { it.name }.size)
        assertEquals(6, lookup.size)
        val detective = lookup.filterKeys { it.name == "警探" }
        assertEquals(setOf("Q8 特殊" to 8, "Q2 精致" to 2), detective.keys.map { it.qualityLabel to it.qualityCode }.toSet())
        detective.values.forEach { hits ->
            assertEquals(listOf("散点", "空投"), hits.filter { it.source.dropCountState != DropCountState.ZERO }.map { it.source.name })
            assertTrue(hits.all { it.source.numericValues.isEmpty() })
        }
        assertEquals(listOf("刺针晶骸"), lookup.getValue(ExtractionDropItem("激化晶体", "Q3 卓越", 3)).map { it.source.name })
        assertEquals(DropCountState.UNKNOWN, dropCountState("0 件 99.9999%"))
        assertEquals(DropCountState.POSITIVE, dropCountState("0 件 99.9999%、1 件 0.0001%"))
    }

    @Test fun reorderedFiveColumnsAndMovedPanelsPreserveData() {
        val doc = Jsoup.parse(fixture())
        doc.select("tr").filter { it.childrenSize() == 5 }.forEach { it.prependChild(it.child(4)) }
        doc.selectFirst("tbody")!!.appendChild(doc.getElementById("mw-customcollapsible-sec-c-15001")!!.closest("tr")!!)
        assertEquals(parse(fixture()), parse(doc.html()))
    }

    @Test fun blankRequiredMetadataIsNotAnExplicitUnknownCount() {
        for (version in listOf("20261121", "20261106")) {
            val doc = Jsoup.parse(fixture(version))
            val headers = doc.select("tr").first()!!.children().eachText()
            val cell = doc.select("tr").first { row -> row.child(0).text() == "空投" }.child(headers.indexOf("件数概率"))
            cell.text("—")
            val unknown = parse(doc.html()).single { it.name == "空投" }
            assertEquals(DropCountState.UNKNOWN, unknown.dropCountState)
            assertEquals("—", unknown.fields.single { it.label == "件数概率" }.value)
            cell.html("&nbsp;<br>")
            assertFalse(validPayload(doc.html()), "$version cannot publish an empty count in place of the old row")
        }
    }

    @Test fun partialOrAmbiguousFiveColumnTablesCannotReplaceCache() {
        val damage: Map<String, (Document) -> Unit> = mapOf(
            "partial return of retired columns" to { it.selectFirst("tr")!!.appendElement("th").text("专属池") },
            "duplicate semantic column" to { it.select("th")[2].text("名称") },
            "missing metadata cell" to { it.select("tr")[1].child(2).remove() },
            "missing pair" to { it.select("tr").takeLast(2).forEach { row -> row.remove() } },
            "missing published airdrop panel" to { it.getElementById("mw-customcollapsible-sec-c-80001")!!.closest("tr")!!.remove() },
            "panel of wrong width" to { it.selectFirst("td[colspan]")!!.attr("colspan", "7") },
            "panels incorrectly combined into one row" to {
                val panel = it.getElementById("mw-customcollapsible-sec-c-80001")!!
                val row = panel.closest("tr")!!
                it.selectFirst("td[colspan]")!!.appendChild(panel)
                row.remove()
            },
            "wrong toggle total" to { it.selectFirst(".mw-customtoggle-sec-c-80001")!!.text("展开 8 项") },
            "chip outside panel" to { it.selectFirst("td[colspan]")!!.appendElement("span").addClass("sec-drop-chip").text("切割器") },
            "ambiguous quality" to { it.selectFirst(".sec-drop-pool__q")!!.appendElement("span").addClass("sec-drop-pool__tag").text("Q1 优秀") },
            "truncated group" to { it.select(".sec-drop-chip").last()!!.remove() }
        )
        damage.forEach { (case, mutation) ->
            val html = Jsoup.parse(fixture()).also(mutation).html()
            assertFalse(validPayload(html), case)
        }
    }

    @Test fun fullNovember21TableVerifiesAllPanelsGroupsAndLookupIdentities() {
        val directory = System.getenv("WIKI_NOV21_AUDIT_DIR")
        assumeTrue("Set WIKI_NOV21_AUDIT_DIR to audit local revision 120525", !directory.isNullOrBlank())
        val html = File(directory!!, "table.html").readText(Charsets.UTF_8)
        val doc = Jsoup.parse(html)
        val rows = parse(html)
        assertEquals(52, rows.size)
        assertEquals(52, rows.mapNotNull { it.containerId }.distinct().size)
        assertEquals(274, rows.sumOf { it.dropPoolGroups.size })
        assertEquals(10_957, rows.sumOf { entry -> entry.dropPoolGroups.sumOf { it.items.size } })
        rows.forEach { entry ->
            val panel = assertNotNull(doc.getElementById("mw-customcollapsible-sec-c-${entry.containerId}"))
            assertEquals(panel.select(".sec-drop-pool__tag").eachText(), entry.dropPoolGroups.map { it.label })
            assertEquals(panel.select(".sec-drop-pool__chips").map { it.select(".sec-drop-chip").eachText() }, entry.dropPoolGroups.map { it.items })
            assertEquals(listOf("子类", "件数概率"), entry.fields.map { it.label })
        }
        assertEquals(listOf("15001"), rows.filter { it.dropCountState != DropCountState.POSITIVE }.map { it.containerId })
        val scatter = rows.single { it.containerId == "15001" }
        assertEquals(DropCountState.UNKNOWN, scatter.dropCountState)
        assertEquals(378, scatter.dropPoolGroups.sumOf { it.items.size })
        assertEquals(347, rows.single { it.containerId == "80001" && it.name == "空投" }.dropPoolGroups.sumOf { it.items.size })
        val lookup = buildDropLookup(rows)
        assertEquals(360, lookup.keys.distinctBy { it.name }.size)
        assertEquals(528, lookup.size)
        val detective = lookup.filterKeys { it.name == "警探" }
        assertEquals(setOf(1, 2, 3, 4, 5, 8), detective.keys.map { it.qualityCode }.toSet())
        detective.values.forEach { hits ->
            assertEquals(9, hits.size)
            assertTrue(hits.any { it.source.containerId == "80001" })
            assertTrue(hits.any { it.source.containerId == "15001" })
        }
    }
}
