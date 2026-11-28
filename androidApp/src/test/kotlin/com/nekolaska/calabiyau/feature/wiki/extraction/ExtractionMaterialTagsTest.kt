package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import org.junit.Assume.assumeNotNull
import java.io.File
import kotlin.test.*

class ExtractionMaterialTagsTest {
    @Test fun combinedCaptionIsNotAThirdTagAndUsageIsNotAMaterial() {
        val html = """<li class="cardSelectOption" data-group="3" data-option="3|能量">能量</li>
            <div class="gallerygrid-item sec-card" data-quality="2" data-param1="材料" data-param3="能量,贵重">
            <div class="sec-card-name">复合材料</div><div class="sec-card-tags">
            <span class="sec-card-tag">能量、贵重</span><span class="sec-card-tag" data-tag="用途">科技、工艺</span></div></div>"""
        val entry = ExtractionCatalogParsers.parse(html, ExtractionCatalogKind.LOOT).single()
        assertEquals(listOf("能量", "贵重"), entry.materialTags)
        assertEquals(listOf("能量", "贵重", "科技、工艺"), entry.tags)
        val noTag = ExtractionCatalogParsers.parse(html.replace("data-param3=\"能量,贵重\"", "data-param3=\"\""), ExtractionCatalogKind.LOOT).single()
        assertTrue(noTag.materialTags.isEmpty())
        val attachment = ExtractionCatalogParsers.parse(html.replace("sec-card", "sec-att-card"), ExtractionCatalogKind.ATTACHMENTS).single()
        assertTrue(attachment.materialTags.isEmpty())
    }

    @Test fun legacyCardKeepsNamedMaterialsWithoutBorrowingQualityOrTradeTags() {
        val html = """<div class="gallerygrid-item sec-card" data-param3="贵重"><div class="sec-card-name">材料</div>
            <span class="sec-card-tag sec-qtag" data-q="2">精致</span><span class="sec-card-tag">标准建造材料</span>
            <span class="sec-card-tag" data-tag="贵重">贵重</span><span class="sec-card-tag">弦质、科技</span>
            <span class="sec-card-tag" data-tag="流通">生化</span></div>"""
        val entry = ExtractionCatalogParsers.parse(html, ExtractionCatalogKind.LOOT).single()
        assertEquals(setOf("弦质", "科技", "贵重"), entry.materialTags.toSet())
        assertEquals(listOf("弦质", "科技", "贵重"), availableMaterialTags(listOf(entry)))
    }

    @Test fun bothFullSnapshotsReturnTheSameMaterialsForEachUpgradeFilter() {
        val previous = System.getenv("EXTRACTION_NOV24_CATALOG_DIR")
        val current = System.getenv("EXTRACTION_NOV26_CATALOG_DIR")
        assumeNotNull(previous, current)
        fun load(dir: String?) = ExtractionCatalogParsers.parseResult(File(dir, "loot.html").readText(), ExtractionCatalogKind.LOOT)
            .also { assertTrue(it.canCache); assertEquals(188, it.entries.size) }.entries
        val entries = load(current)
        assertEquals(entries.associate { it.id to it.materialTags.toSet() }, load(previous).associate { it.id to it.materialTags.toSet() })
        assertEquals(listOf("弦质", "能量", "科技", "精械", "生化", "工艺", "贵重"), availableMaterialTags(entries))
        fun select(tag: String, quality: Int? = null) = filterExtractionEntries(entries, "", null, quality, tag, ExtractionSort.DEFAULT, true)
        assertEquals(23, select("贵重").size)
        assertEquals(setOf("晶核储能内芯", "集弦器", "超级能量包", "老式供能机", "空调颈环", "万能饮品机"), select("能量", 2).map { it.name }.toSet())
    }
}
