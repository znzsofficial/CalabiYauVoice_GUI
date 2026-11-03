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
        val group = rows.single { it.name == "标准医疗格" }.dropPoolGroups.single()
        assertEquals("全部掉落物", group.label)
        assertNull(group.qualityCode)
        assertEquals(listOf("弦-5针剂", "标准护甲充能球", "测试A", "测试B"), group.items)
    }

    @Test fun sameIdentityWithChangedLootIsNotSilentlyOverwritten() {
        val rows = ExtractionCatalogParsers.parse(fixture("drops") + fixture("drops").replace("33.9%", "40%"), ExtractionCatalogKind.DROPS)
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.id }.distinct().size)
    }

    @Test fun currentRarityRowsKeepEveryFullPoolIncludingMergedContinuationCells() {
        val rows = ExtractionCatalogParsers.parse(fixture("drop_summary_20261103"), ExtractionCatalogKind.DROPS)
        assertEquals(3, rows.size)
        val medical = rows.single { it.name == "标准医疗格" }
        val pool = medical.fields.single { it.label == "掉落池条目（完整）" }.value
        assertTrue(pool.contains("Q5 臻藏：「银河」"))
        assertTrue(pool.contains("Q2 精致：标准护甲充能球、弦-25针剂"))
        assertTrue(pool.contains("Q1 优秀：基础护甲充能球、弦-5针剂"))
        assertEquals("0 件 20%、1 件 60%、2 件 20%", medical.fields.single { it.label == "件数概率" }.value)
        assertTrue(medical.matches("弦-5针剂"))
        assertTrue(rows.single { it.name == "弹药仓" }.matches("狙击弹药"))
        assertTrue(rows.any { it.name == "空投" })
        assertTrue(rows.all { it.quality == null })
        assertEquals(listOf("Q5 臻藏", "Q2 精致", "Q1 优秀"), medical.dropPoolGroups.map { it.label })
        assertEquals(listOf(5, 2, 1), medical.dropPoolGroups.map { it.qualityCode })
        assertEquals(listOf("基础护甲充能球", "弦-5针剂"), medical.dropPoolGroups.last().items)
        assertTrue(rows.single { it.name == "空投" }.dropPoolGroups.isEmpty())
        assertEquals("0", rows.single { it.name == "空投" }.fields.single { it.label == "掉落档次" }.value)
    }

    @Test fun repeatedNamesAndTiersDoNotMergeDistinctBossCoreDistributions() {
        val source = fixture("drop_summary_20261103")
        val first = source.replace("标准医疗格", "晶骸核心")
        val second = first.replace("0 件 20%、1 件 60%、2 件 20%", "3 件 80%、4 件 15%、5 件 5%")
        val rows = ExtractionCatalogParsers.parse(first + second, ExtractionCatalogKind.DROPS)
        val cores = rows.filter { it.name == "晶骸核心" }
        assertEquals(2, cores.size)
        assertEquals(2, cores.map { it.id }.distinct().size)
        assertTrue(cores.all { it.dropPoolGroups.size == 3 })
        assertEquals(4, rows.size) // Unchanged ammo/airdrop records still deduplicate.
    }

    @Test fun unknownQualityCodesAndUnexpectedPoolTextArePreservedWithoutInventingRarities() {
        val source = fixture("drop_summary_20261103")
            .replace("Q5 臻藏：「银河」、「心海」、「火力大喵」、「科斯迷特」", "Q8：硬化超弦装甲、适应性超弦装甲")
            .replace("Q2 精致：标准护甲充能球、弦-25针剂", "新增分组：测试物品")
        val medical = ExtractionCatalogParsers.parse(source, ExtractionCatalogKind.DROPS).single { it.name == "标准医疗格" }
        assertEquals("Q8", medical.dropPoolGroups.first().label)
        assertEquals(8, medical.dropPoolGroups.first().qualityCode)
        assertEquals(listOf("硬化超弦装甲", "适应性超弦装甲"), medical.dropPoolGroups.first().items)
        assertEquals(listOf("新增分组：测试物品"), medical.dropPoolGroups[1].items)
        assertNull(medical.quality)
        assertTrue(medical.matches("测试物品"))
    }

    @Test fun rowspanEndsAtItsDeclaredBoundaryAndIncompleteRowsCannotLeakIntoTheNextPool() {
        val source = fixture("drop_summary_20261103").replace(
            "<tr>\n <td rowspan=\"1\">空投</td>",
            "<tr><td>Q4 完美：无归属条目</td></tr><tr>\n <td rowspan=\"1\">空投</td>"
        )
        val rows = ExtractionCatalogParsers.parse(source, ExtractionCatalogKind.DROPS)
        assertEquals(3, rows.size)
        assertTrue(rows.none { it.matches("无归属条目") })
        assertTrue(rows.single { it.name == "空投" }.dropPoolGroups.isEmpty())
    }

    @Test fun fullPoolViewsAndItemSearchStaySeparateFromSourceCardsAndTheirWeights() {
        val entries = ExtractionCatalogParsers.parse(fixture("drops") + fixture("drop_summary_20261103"), ExtractionCatalogKind.DROPS)
        val sources = extractionEntriesForView(entries, ExtractionDropView.SOURCES)
        val pools = extractionEntriesForView(entries, ExtractionDropView.POOLS)
        assertEquals(listOf("刺针"), sources.map { it.name })
        assertEquals(3, pools.size)
        assertEquals(listOf("标准医疗格"), filterExtractionEntries(pools, "弦-5针剂", "地图场景容器", null,
            false, ExtractionSort.DEFAULT, true).map { it.name })
        assertTrue(filterExtractionEntries(pools, "弦-5针剂", "空投箱", null, false, ExtractionSort.DEFAULT, true).isEmpty())
        val medical = pools.single { it.name == "标准医疗格" }
        val matches = filterDropPoolGroups(medical.dropPoolGroups, " 弦-5针剂 ")
        assertEquals(1, matches.size)
        assertEquals(listOf("弦-5针剂"), matches.single().items)
        assertEquals(3, medical.dropPoolGroups.size) // Filtering never changes the complete source.
        assertEquals(listOf("Q5 臻藏"), filterDropPoolGroups(medical.dropPoolGroups, "臻藏").map { it.label })
        assertEquals(medical.dropPoolGroups, filterDropPoolGroups(medical.dropPoolGroups, ""))
        assertTrue(filterDropPoolGroups(medical.dropPoolGroups, "不存在的物品").isEmpty())
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
