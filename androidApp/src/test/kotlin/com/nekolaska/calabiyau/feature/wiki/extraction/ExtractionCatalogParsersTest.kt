package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import kotlin.test.*

class ExtractionCatalogParsersTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/extraction/$name.html")).readText(Charsets.UTF_8)

    @Test fun lootQualityLabelsPreferTheModeFilterNamesOverGenericCatalogNames() {
        val source = fixture("loot").replace("data-quality=\"2\"", "data-quality=\"5\"")
        val entry = ExtractionCatalogParsers.parse(source, ExtractionCatalogKind.LOOT).single()
        assertEquals("臻藏", entry.qualityLabel)
        val zero = fixture("loot").replace("data-sort-value=\"3000\"", "data-sort-value=\"0\"")
        assertEquals(0.0, ExtractionCatalogParsers.parse(zero, ExtractionCatalogKind.LOOT).single().numericValues[ExtractionSort.VALUE])
    }

    @Test fun fullDropPoolAndExactDuplicateCardsAreRetainedOnlyOnce() {
        val table = """<table class="klbqtable"><tr><th>名称</th><th>来源类别</th><th>子类</th><th>掉落档次</th><th>件数概率</th><th>必掉物品（完整）</th><th>掉落池条目（完整）</th></tr>
            <tr><td>标准医疗格</td><td>场景容器</td><td>医疗</td><td>10</td><td>1 件 100%</td><td>—</td><td>4 项：弦-5针剂、标准护甲充能球、测试A、测试B</td></tr></table>"""
        val rows = ExtractionCatalogParsers.parse(fixture("drops") + table + fixture("drops") + table, ExtractionCatalogKind.DROPS)
        assertEquals(2, rows.size)
        assertTrue(rows.single { it.name == "标准医疗格" }.matches("测试B"))
        assertNull(rows.single { it.name == "标准医疗格" }.quality)
    }

    @Test fun sameIdentityWithChangedLootIsNotSilentlyOverwritten() {
        val rows = ExtractionCatalogParsers.parse(fixture("drops") + fixture("drops").replace("33.9%", "40%"), ExtractionCatalogKind.DROPS)
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.id }.distinct().size)
    }

    @Test fun lazyImagesFallBackFromAnInvalidPlaceholderWithoutExtraImageQueries() {
        val source = fixture("loot").replace("src=\"https://example.test/images/klbq/thumb/a/ab/item.png/120px-item.png\"",
            "src=\"data:image/gif;base64,AA\" srcset=\"//example.test/icon.png 2x\"")
        assertEquals("https://example.test/icon.png", ExtractionCatalogParsers.parse(source, ExtractionCatalogKind.LOOT).single().imageUrl)
    }

    @Test fun lootKeepsRenderedUnitsAndNumericSortSeparate() {
        val entry = ExtractionCatalogParsers.parse(fixture("loot"), ExtractionCatalogKind.LOOT).single()
        assertEquals("4030003", entry.id)
        assertEquals("标准护甲充能球", entry.name)
        assertEquals("消耗品", entry.category)
        assertEquals("精致", entry.qualityLabel)
        assertEquals("使用后恢复中量护甲", entry.description)
        assertEquals("0.17s", entry.fields.single { it.label == "搜刮耗时" }.value)
        assertEquals(170.0, entry.numericValues[ExtractionSort.SEARCH_TIME])
        assertEquals(0.149993, entry.numericValues[ExtractionSort.RATE])
        assertTrue(entry.fields.single { it.label == "掉落信息" }.value.contains("15.00%"))
        assertEquals("https://example.test/images/klbq/a/ab/item.png", entry.imageUrl)
    }

    @Test fun attachmentsHaveActualEffectAndCompatibilityWithoutLeakingLabels() {
        val entry = ExtractionCatalogParsers.parse(fixture("attachments"), ExtractionCatalogKind.ATTACHMENTS).single()
        assertEquals("枪口", entry.category)
        assertEquals("-提升16%散射控制\n-提升2%晶源体伤害", entry.description)
        assertEquals("站立最小散射 -16%\n站立最大散射 -16%\n每次开火散射变化 -16%", entry.fields.single { it.label == "实际效果" }.value)
        assertTrue(entry.fields.single { it.label == "适用" }.value.startsWith("突击步枪"))
        assertNull(entry.numericValues[ExtractionSort.RATE])
    }

    @Test fun dropsKeepWeightCountsAndDifficultyDistinctFromQuality() {
        val entry = ExtractionCatalogParsers.parse(fixture("drops"), ExtractionCatalogKind.DROPS).single()
        assertEquals("刺针", entry.name)
        assertEquals("晶源体 · 普通档", entry.category)
        assertNull(entry.quality)
        assertNull(entry.imageUrl)
        assertTrue(entry.fields.single { it.label == "掉落池内最高权重" }.value.contains("33.9%"))
        assertEquals("1 件 50%、2 件 50%", entry.fields.single { it.label == "掉落件数" }.value)
        assertTrue(entry.matches("切割器"))
    }

    @Test fun sameNamesAndSamePoolDoNotMergeDistinctVariants() {
        val source = fixture("attachments")
        val entries = ExtractionCatalogParsers.parse(source + source.replace("4081001", "4081002"), ExtractionCatalogKind.ATTACHMENTS)
        assertEquals(2, entries.size)
        val drops = ExtractionCatalogParsers.parse(fixture("drops") + fixture("drops").replace("刺针", "蜥犬"), ExtractionCatalogKind.DROPS)
        assertEquals(2, drops.size)
        assertEquals(2, drops.map { it.id }.distinct().size)
    }

    @Test fun unknownFieldsAndMissingImagesAreOptionalButNamelessCardsAreSkipped() {
        val html = """<div class="gallerygrid-item sec-card" data-sort-id="x" data-quality="5" data-param1="材料"><div class="sec-card-name">新材料</div></div>
            <div class="gallerygrid-item sec-card" data-sort-id="bad"></div>"""
        val entry = ExtractionCatalogParsers.parse(html, ExtractionCatalogKind.LOOT).single()
        assertEquals("臻藏", entry.qualityLabel)
        assertNull(entry.imageUrl)
        assertTrue(entry.numericValues.isEmpty())
    }

    @Test fun filteringAndSortKeepZeroUnknownAndStableTiesCorrect() {
        fun entry(id: String, value: Double?) = ExtractionEntry(id, id, "材料", tags = listOf("贵重"),
            numericValues = value?.let { mapOf(ExtractionSort.VALUE to it) }.orEmpty())
        val values = listOf(entry("unknown", null), entry("zero", 0.0), entry("two", 2.0), entry("tie", 2.0))
        fun sorted(desc: Boolean) = filterExtractionEntries(values, "", null, null, true, ExtractionSort.VALUE, desc).map { it.id }
        assertEquals(listOf("two", "tie", "zero", "unknown"), sorted(true))
        assertEquals(listOf("zero", "two", "tie", "unknown"), sorted(false))
        assertEquals(listOf("two"), filterExtractionEntries(values, "two", "材料", null, true, ExtractionSort.DEFAULT, true).map { it.id })
    }
}
