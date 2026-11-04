package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import data.SharedJson
import java.io.File
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import kotlin.test.*

class ExtractionRealPageTest {
    @Test fun parsesModernSharedPoolsAndStageTableWhenCaptureIsProvided() {
        val file = System.getenv("EXTRACTION_DROPS_AUDIT_FILE")?.let(::File)
        assumeTrue("Set EXTRACTION_DROPS_AUDIT_FILE to a local parse response", file?.isFile == true)
        val html = SharedJson.parseToJsonElement(file!!.readText(Charsets.UTF_8)).jsonObject
            .getValue("parse").jsonObject.getValue("text").jsonObject.getValue("*").jsonPrimitive.content
        val document = Jsoup.parse(html)
        val result = ExtractionCatalogParsers.parseResult(html, ExtractionCatalogKind.DROPS)
        assertEquals(emptyList(), result.issues)
        assertTrue(result.canCache)
        val pools = extractionEntriesForView(result.entries, ExtractionDropView.POOLS)
        val rawPools = document.select(".sec-drop-pool")
        assertTrue(rawPools.isNotEmpty())
        assertEquals(rawPools.size, pools.size)
        rawPools.forEach { raw ->
            val entry = pools.single { it.id == "pool:${raw.id()}" }
            assertEquals(raw.select(".sec-drop-chip").map { it.text() }, entry.dropPoolGroups.flatMap { it.items }, raw.id())
            assertEquals(raw.select(".sec-drop-pool__tag").map { it.text() }, entry.dropPoolGroups.map { it.label }, raw.id())
        }
        assertEquals(document.select(".sec-drop-card").size, extractionEntriesForView(result.entries, ExtractionDropView.SOURCES).size)
        val stages = extractionEntriesForView(result.entries, ExtractionDropView.STAGES)
        assertEquals(6, stages.size)
        assertEquals("—", stages.last().fields.single { it.label == "候选池" }.value)
        assertTrue(pools.any { e -> e.dropPoolGroups.any { it.label == "Q8 装备" } })
        val boss = result.entries.single { it.type == ExtractionEntryType.DROP_SOURCE && it.name == "阶段 1" }
        assertTrue(boss.fields.any { it.label == "掉落材料（按权重抽）" })
        assertFalse(boss.fields.any { it.label == "必掉物" })
        println("Modern drops: sources=${result.entries.count { it.type == ExtractionEntryType.DROP_SOURCE }}, pools=${pools.size}, groups=${pools.sumOf { it.dropPoolGroups.size }}, stages=${stages.size}")
    }

    @Test fun parsesCompleteLocalCapturesWhenProvidedWithoutFetchingAnything() {
        val directory = System.getenv("EXTRACTION_AUDIT_DIR")?.let(::File)
        assumeTrue("Set EXTRACTION_AUDIT_DIR to full local parse responses", directory?.isDirectory == true)
        val dir = directory!!
        val report = buildJsonObject {
            for ((kind, stem) in listOf(ExtractionCatalogKind.LOOT to "loot", ExtractionCatalogKind.ATTACHMENTS to "attachments", ExtractionCatalogKind.DROPS to "drops")) {
                val parse = SharedJson.parseToJsonElement(File(dir, "$stem.json").readText(Charsets.UTF_8)).jsonObject.getValue("parse").jsonObject
                val html = parse.getValue("text").jsonObject.getValue("*").jsonPrimitive.content
                val selector = when (kind) {
                    ExtractionCatalogKind.LOOT -> ".gallerygrid-item.sec-card"
                    ExtractionCatalogKind.ATTACHMENTS -> ".gallerygrid-item.sec-att-card"
                    ExtractionCatalogKind.DROPS -> ".sec-drop-card"
                }
                val raw = Jsoup.parse(html).select(selector)
                val parsed = ExtractionCatalogParsers.parseResult(html, kind)
                assertEquals(emptyList(), parsed.issues)
                val rows = parsed.entries
                assertTrue(rows.isNotEmpty())
                if (kind != ExtractionCatalogKind.DROPS) assertEquals(raw.size, rows.size, "$kind skipped/merged a source card")
                assertEquals(rows.size, rows.map { it.id }.distinct().size)
                if (kind == ExtractionCatalogKind.ATTACHMENTS) {
                    assertTrue(rows.any { e -> e.fields.any { it.label == "实际效果" && it.value.contains("散射") } })
                    assertTrue(rows.any { e -> e.fields.any { it.label == "适用" && it.value.contains("弹药") } })
                }
                if (kind == ExtractionCatalogKind.DROPS) {
                    // Older snapshots repeat source cards. The current complete-pool
                    // table uses one rowspan group per source and one row per rarity.
                    val cardRows = extractionEntriesForView(rows, ExtractionDropView.SOURCES)
                    assertEquals(raw.map { it.selectFirst(".sec-drop-card__title")?.text().orEmpty() + ":" + it.selectFirst(".sec-drop-card__sub")?.text().orEmpty() }.distinct().size,
                        cardRows.size)
                    assertTrue(rows.any { e -> e.fields.any { it.label == "掉落池条目（完整）" } })
                    assertTrue(rows.any { it.name == "莉莉丝" })
                    assertTrue(rows.all { it.quality == null })
                    assertTrue(rows.any { e -> e.fields.any { it.label == "掉落件数" } })
                    val poolRows = extractionEntriesForView(rows, ExtractionDropView.POOLS)
                    val table = Jsoup.parse(html).select("table.klbqtable").first {
                        it.select("tr").first()!!.text().contains("掉落池条目")
                    }
                    val tableRows = table.select("tr").drop(1)
                    val poolStarts = tableRows.filter { it.select("> td").size == 7 }
                    assertEquals(poolStarts.size, poolRows.size, "Every complete pool must survive, including identical-name Boss variants")
                    if (table.select("tr").first()!!.text().contains("按稀有度分行")) {
                        val qualityRows = tableRows.mapNotNull { it.select("> td").last()?.text() }
                            .filter { Regex("""^Q\d+[\s：:].*""").matches(it) }
                        assertEquals(qualityRows.size, poolRows.sumOf { it.dropPoolGroups.size })
                        assertTrue(poolRows.any { pool -> pool.dropPoolGroups.any { it.label == "Q8" } })
                        qualityRows.forEach { text ->
                            val contents = text.substringAfter('：')
                            contents.split('、').map { it.trim() }.filter { it.isNotEmpty() }.forEach { item ->
                                assertTrue(poolRows.any { pool -> pool.dropPoolGroups.any { item in it.items } }, "Missing full-pool item: $item")
                            }
                        }
                        poolStarts.zip(poolRows).forEach { (start, pool) ->
                            val cells = start.select("> td")
                            assertEquals(cells.first()!!.text(), pool.name)
                            val height = cells.first()!!.attr("rowspan").toIntOrNull() ?: 1
                            val offset = tableRows.indexOf(start)
                            val expected = tableRows.drop(offset).take(height).map { it.select("> td").last()!!.text() }
                            assertEquals(expected.joinToString("\n"), pool.fields.single { it.label == "掉落池条目（完整）" }.value,
                                "Pool rows leaked between sources: ${pool.name}")
                            assertEquals(cells[4].text(), pool.fields.single { it.label == "件数概率" }.value)
                        }
                    }
                }
                putJsonObject(kind.name) {
                    put("count", rows.size)
                    put("completePools", rows.count { it.isDropPool })
                    put("rarityGroups", rows.sumOf { it.dropPoolGroups.size })
                    putJsonArray("categories") { rows.map { it.category }.distinct().forEach { add(it) } }
                    putJsonArray("names") { rows.forEach { add(it.name) } }
                }
            }
            val overview = SharedJson.parseToJsonElement(File(dir, "overview.json").readText(Charsets.UTF_8)).jsonObject.getValue("parse").jsonObject
            val detail = GameModeParsers.parseModeWikitext(ModeEntry("弦溯战线", EXTRACTION_MODE_PAGE), overview.getValue("wikitext").jsonObject.getValue("*").jsonPrimitive.content, emptyList())
            assertTrue(detail.settings.contains("完美闪避"))
            assertTrue(detail.settings.contains("首领"))
            assertEquals(4, detail.summary.lines().size)
            put("overview", detail.summary)
        }
        File(dir, "parsed-catalogs.json").writeText(report.toString(), Charsets.UTF_8)
        println(report)
    }
}
