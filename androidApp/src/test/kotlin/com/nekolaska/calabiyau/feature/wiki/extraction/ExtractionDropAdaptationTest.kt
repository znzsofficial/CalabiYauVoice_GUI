package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import kotlinx.serialization.json.*
import kotlin.test.*

class ExtractionDropAdaptationTest {
    @Test fun hierarchicalHeaderChoosesLeafColumnsInsteadOfTheFirstSpanningHeader() {
        val html = """<table><thead>
            <tr><th rowspan="2">名称</th><th colspan="2">来源信息</th><th rowspan="2">掉落池条目（完整）</th></tr>
            <tr><th>来源类别</th><th>子类</th></tr></thead>
            <tbody><tr><td>测试箱</td><td>容器</td><td>医疗</td><td>1 项：针剂</td></tr></tbody></table>"""
        val result = parse(html)
        assertTrue(result.canCache, result.issues.toString())
        assertEquals("容器", result.entries.single().filterCategory)
        assertEquals(listOf("针剂"), result.entries.single().dropPoolGroups.single().items)
    }

    @Test fun explicitlyEmptySharedPoolIsValidButUnspecifiedEmptyPoolIsNot() {
        val empty = """<div class="sec-drop-pool" id="empty"><div class="sec-drop-pool__head">空投<b>0 项</b></div></div>"""
        val result = parse(empty)
        assertTrue(result.canCache, result.issues.toString())
        assertTrue(result.entries.single().isDropPool)
        assertTrue(result.entries.single().dropPoolGroups.isEmpty())
        assertFalse(parse(empty.replace("<b>0 项</b>", "")).canCache)
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/extraction/$name.html")).readText(Charsets.UTF_8)
    private fun parse(html: String) = ExtractionCatalogParsers.parseResult(html, ExtractionCatalogKind.DROPS)
    private fun payload(html: String) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()

    @Test fun sharedPoolsIncludeCollapsedItemsAndSectionNotesButNeverLeakNotesAcrossHeadings() {
        val result = parse(fixture("drop_shared_pools"))
        assertEquals(emptyList(), result.issues)
        assertTrue(result.canCache)
        val sources = extractionEntriesForView(result.entries, ExtractionDropView.SOURCES)
        val pools = extractionEntriesForView(result.entries, ExtractionDropView.POOLS)
        assertEquals(2, sources.size)
        assertEquals(2, pools.size)
        assertTrue(sources.first().fields.any { it.label == "本节掉落说明" && it.value.contains("1 件 50%") })
        assertFalse(sources.last().fields.any { it.label == "本节掉落说明" })
        val pool = pools.first()
        assertEquals("pool:sec-pool-20101", pool.id)
        assertEquals("晶源体 · 普通档 · 共用掉落池", pool.name)
        assertEquals(listOf("「银河」", "切割器", "标准护甲充能球"), pool.dropPoolGroups.flatMap { it.items })
        assertTrue(pool.matches("标准护甲充能球"))
        assertEquals("Q8 装备", pools.last().dropPoolGroups.single().label)
        assertTrue(pools.all { it.quality == null })
    }

    @Test fun bossWeightedMaterialsRetainTheirSourceLabelInsteadOfBecomingGuaranteedDrops() {
        val stage = parse(fixture("drop_shared_pools")).entries.single { it.name == "阶段 1" }
        assertTrue(stage.fields.any { it.label == "掉落材料（按权重抽）" && it.value.contains("35.6%") })
        assertFalse(stage.fields.any { it.label == "必掉物" })
    }

    @Test fun stageComparisonIsNotASourceOrACompletePoolAndDoesNotInventStageSixItems() {
        val entries = parse(fixture("drop_shared_pools")).entries
        val stages = extractionEntriesForView(entries, ExtractionDropView.STAGES)
        assertEquals(2, stages.size)
        assertEquals("0", stages.first().fields.single { it.label == "阈值" }.value)
        assertEquals("—", stages.last().fields.single { it.label == "候选池" }.value)
        assertTrue(stages.all { it.dropPoolGroups.isEmpty() && !it.isDropPool })
        assertTrue(stages.last().matches("无通用池"))
    }

    @Test fun poolTypeIsIndependentOfDisplayLabelsAndEmptyPoolStillBelongsToPoolView() {
        val entry = ExtractionEntry("empty", "空投", "空投箱", type = ExtractionEntryType.DROP_POOL)
        assertTrue(entry.isDropPool)
        assertEquals(listOf(entry), extractionEntriesForView(listOf(entry), ExtractionDropView.POOLS))
        assertFalse(ExtractionEntry("item", "道具", "材料", fields = listOf(ExtractionField("掉落池条目（完整）", "附注"))).isDropPool)
    }

    @Test fun missingCollapsedItemsOrWrongDeclaredCountsCannotReplaceGoodDiskCache() {
        val html = fixture("drop_shared_pools")
        for (broken in listOf(html.replace("<span class=\"sec-drop-chip\">标准护甲充能球</span>", ""),
            html.replace("<b>3 项</b>", "<b>300 项</b>"), html.replace("Q2 精致", ""))) {
            val result = parse(broken)
            assertTrue(result.entries.isNotEmpty()) // Surviving cards do not make a complete response.
            assertFalse(result.canCache)
            assertFalse(ExtractionCatalogRemoteSource.isValidPayload(payload(broken), ExtractionCatalogKind.DROPS))
        }
    }

    @Test fun reorderedColumnsMultiRowHeadersAndRenamedTableClassesKeepTheSameRecord() {
        val html = """<table class="wikitable"><thead><tr><th colspan="3">完整掉落资料</th></tr>
          <tr><th rowspan="2">掉落池条目<br>（按稀有度分行）</th><th colspan="2">来源信息</th></tr>
          <tr><th>来源类别</th><th>名称</th></tr></thead>
          <tbody><tr><td>Q8 装备：硬化超弦装甲</td><td>场景容器</td><th scope="row">医疗格</th></tr></tbody></table>"""
        val result = parse(html)
        assertEquals(emptyList(), result.issues)
        val entry = result.entries.single()
        assertEquals("医疗格", entry.name)
        assertEquals("场景容器", entry.filterCategory)
        assertEquals(listOf("硬化超弦装甲"), entry.dropPoolGroups.single().items)
    }

    @Test fun rowSpansAreBoundedByTheirTbodyIncludingHtmlRowspanZero() {
        val html = """<table><thead><tr><th>名称</th><th>掉落池条目（按稀有度分行）</th></tr></thead>
          <tbody><tr><td rowspan="0">甲</td><td>Q5 臻藏：物品A</td></tr><tr><td>Q1 优秀：物品B</td></tr></tbody>
          <tbody><tr><td>乙</td><td>Q2 精致：物品C</td></tr></tbody></table>"""
        val result = parse(html)
        assertEquals(emptyList(), result.issues)
        assertEquals(listOf("甲", "乙"), result.entries.map { it.name })
        assertEquals(listOf("物品A", "物品B"), result.entries.first().dropPoolGroups.flatMap { it.items })
    }

    @Test fun repeatedHeadersAndNestedDecorativeTablesDoNotBecomePoolItems() {
        val html = """<table><tr><th>名称</th><th>掉落池条目（完整）</th></tr>
          <tr><td>甲</td><td>1 项：物品A</td></tr>
          <tr><th>名称</th><th>掉落池条目（完整）</th></tr>
          <tr><td>乙</td><td>1 项：物品B</td></tr></table>
          <table><tr><td><table><tr><td>无关数据</td></tr></table></td></tr></table>"""
        val result = parse(html)
        assertEquals(emptyList(), result.issues)
        assertEquals(listOf("甲", "乙"), result.entries.map { it.name })
    }

    @Test fun malformedRowSpansAndColspansAreDiagnosedWithoutAllocatingUnboundedGrids() {
        val html = """<table><tr><th>名称</th><th>掉落池条目（完整）</th></tr>
          <tr><td rowspan="999999999">坏数据</td><td colspan="999999999">1 项：不可信</td></tr>
          <tr><td>正常池</td><td>1 项：正常物品</td></tr></table>"""
        val result = parse(html)
        assertFalse(result.canCache)
        assertTrue(result.entries.none { it.matches("不可信") })
        assertEquals(listOf("正常物品"), result.entries.single().dropPoolGroups.single().items)
    }

    @Test fun unsupportedPoolHeaderAndIncompleteRowsFailValidationEvenWhenSourceCardsStillParse() {
        val source = fixture("drops")
        val table = fixture("drop_summary_20261103")
        for (html in listOf(source + table.replace("掉落池条目（按稀有度分行）", "掉落池条目（新版未知）"),
            source + table.replace("<td>Q2 精致：标准护甲充能球、弦-25针剂</td>", ""))) {
            assertFalse(parse(html).canCache)
        }
    }

    @Test fun repeatedGroupLabelsKeepDistinctKeysAfterFilteringAndRepeatedItemsAreNotDeleted() {
        val html = fixture("drop_shared_pools").replace("Q5 臻藏", "Q2 精致")
            .replace("「银河」", "切割器")
        val groups = parse(html).entries.first { it.isDropPool }.dropPoolGroups
        assertEquals(listOf("Q2 精致", "Q2 精致"), groups.map { it.label })
        assertEquals(2, groups.map { it.key }.distinct().size)
        val filtered = filterDropPoolGroups(groups, "标准护甲充能球")
        assertEquals(groups.last().key, filtered.single().key)
        assertEquals(2, groups.flatMap { it.items }.count { it == "切割器" })
    }

    @Test fun aRecognizedTableInsideADecorativeWrapperIsParsedOnlyOnce() {
        val html = "<table><tr><td>" + fixture("drop_summary_20261103") + "</td></tr></table>"
        val result = parse(html)
        assertEquals(emptyList(), result.issues)
        assertEquals(3, result.entries.size)
    }

    @Test fun verticallyMergedPoolCellsAreReadOnlyOnce() {
        val html = """<table><tr><th>名称</th><th>掉落池条目（按稀有度分行）</th><th>来源类别</th></tr>
          <tr><td rowspan="2">医疗格</td><td rowspan="2">Q2 精致：针剂</td><td rowspan="2">容器</td></tr><tr></tr></table>"""
        val result = parse(html)
        assertEquals(emptyList(), result.issues)
        assertEquals(listOf("针剂"), result.entries.single().dropPoolGroups.single().items)
    }
}
