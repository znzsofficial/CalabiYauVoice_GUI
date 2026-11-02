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
                val rows = ExtractionCatalogParsers.parse(html, kind)
                assertTrue(rows.isNotEmpty())
                if (kind != ExtractionCatalogKind.DROPS) assertEquals(raw.size, rows.size, "$kind skipped/merged a source card")
                assertEquals(rows.size, rows.map { it.id }.distinct().size)
                if (kind == ExtractionCatalogKind.ATTACHMENTS) {
                    assertTrue(rows.any { e -> e.fields.any { it.label == "实际效果" && it.value.contains("散射") } })
                    assertTrue(rows.any { e -> e.fields.any { it.label == "适用" && it.value.contains("弹药") } })
                }
                if (kind == ExtractionCatalogKind.DROPS) {
                    // The live page currently repeats all 53 cards. Exact copies are not
                    // distinct encounters; the summary table is a separate complete-pool view.
                    val cardRows = rows.filterNot { it.category.startsWith("总览表") }
                    assertEquals(raw.map { it.selectFirst(".sec-drop-card__title")?.text().orEmpty() + ":" + it.selectFirst(".sec-drop-card__sub")?.text().orEmpty() }.distinct().size,
                        cardRows.size)
                    assertTrue(rows.any { e -> e.fields.any { it.label == "掉落池条目（完整）" } })
                    assertTrue(rows.any { it.name == "莉莉丝" })
                    assertTrue(rows.all { it.quality == null })
                    assertTrue(rows.any { e -> e.fields.any { it.label == "掉落件数" } })
                }
                putJsonObject(kind.name) {
                    put("count", rows.size)
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
