package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionDrop120524Test {
    private fun fixture() = checkNotNull(javaClass.getResource("/fixtures/extraction/drop_distribution_120524.html")).readText(Charsets.UTF_8)
    private fun parse(html: String) = ExtractionCatalogParsers.parseResult(html, ExtractionCatalogKind.DROPS)
    private fun entries(html: String = fixture()) = parse(html).also { assertTrue(it.canCache, it.issues.toString()) }.entries
    private fun List<ExtractionEntry>.source(name: String) = single { it.type == ExtractionEntryType.DROP_SOURCE && it.name == name }
    private fun ExtractionEntry.field(name: String) = fields.single { it.label == name }.value

    @Test fun stageRowsRetainPerDrawQualitySharesThresholdAndCountProbability() {
        val doc = Jsoup.parse(fixture())
        // Schema uses semantic keys, not the order in the snapshot.
        doc.select("table tr").forEach { row -> row.prependChild(row.children().last()!!) }
        val data = entries(doc.html())
        val stages = data.filter { it.type == ExtractionEntryType.DROP_STAGE }
        assertEquals((1..6).map { "阶段 $it · 品质占比" }, stages.map { it.name })
        assertEquals(listOf("0", "10", "20", "30", "40", "50"), stages.map { it.field("阈值") })
        assertTrue(stages.all { it.field("件数概率") == "2件30%、3件40%、4件20%、5件10%" && it.dropPoolGroups.isEmpty() })
        val last = stages.last()
        assertEquals(listOf("1.9%", "12.7%", "43.9%", "41.4%"), listOf("Q5 臻藏", "Q4 完美", "Q3 卓越", "Q2 及以下").map { last.field(it) })
        assertTrue(last.description.contains("基点") && last.description.contains("升变 / 危峰"))
        assertTrue(stages.none { it.matches("候选池") || it.fields.any { f -> f.label == "通用掉落" } })
        assertFalse(data.source("散点").matches("基点"))
        assertTrue(data.source("阶段 1").field("抽中率最高").contains("阵列礼盒Ⅰ · 4.5%"))
        assertFalse(data.source("阶段 1").matches("4103001.png"))
        assertTrue(data.source("阶段 1").dropImages.isEmpty())
    }

    @Test fun explicitItemListsDoNotPromoteTierOrQualityOrIncompleteCountsIntoItemRates() {
        val data = entries()
        val elite = data.source("锋爪")
        assertEquals("0 件 10%、1 件 50%、2 件 40%", data.source("刺针").field("掉落件数"))
        assertEquals("1 件 40%、2 件 50%、3 件 10%", elite.field("掉落件数"))
        assertTrue(elite.matches("只是各物品的单件抽中率不同"))
        assertEquals("pool:sec-pool-20101", elite.sharedPoolId)
        val eliteItems = sourcePoolDetail(elite, linkedDropSharedPool(elite, data)!!)
        assertEquals(listOf("激化晶体"), eliteItems.dropPoolGroups.flatMap { it.items })
        assertEquals("1 件 40%、2 件 50%、3 件 10%", eliteItems.field("掉落件数"))
        assertFalse(eliteItems.matches("0 件 10%"))
        assertEquals(elite.category, eliteItems.category)
        assertEquals("pool:sec-bpool-1", data.source("阶段 6").sharedPoolId)
        assertEquals("pool:sec-cpool-1", data.source("弹药仓").sharedPoolId)
        assertEquals(data.source("弹药仓").sharedPoolId, data.source("标准装备箱").sharedPoolId)
        val scatter = data.source("散点")
        assertEquals("0 件 30%（合计 30%，数据不完整）", scatter.field("掉落件数"))
        assertEquals(DropCountState.UNKNOWN, scatter.dropCountState)
        assertEquals("pool:sec-cpool-2", scatter.sharedPoolId)
        assertTrue(data.all { it.containerId == null && it.quality == null })
        assertTrue(data.none { it.fields.any { f -> f.label == "必掉物" || f.label.startsWith("掉落材料") } })
    }

    @Test fun anUnlabelledMaterialBlockDoesNotImplyGuaranteedDropsOrBorrowThePreviousListsLabel() {
        val html = """<div class="sec-drop-card"><span class="sec-drop-card__title">首领</span>
            <div class="sec-drop-card__label">抽中率最高</div><div class="sec-drop-card__list">激化晶体 · 30.0%</div>
            <div class="sec-drop-card__must">标准弦能节点 · 35.6%</div></div>"""
        val boss = entries(html).source("首领")
        assertEquals("标准弦能节点 · 35.6%", boss.field("掉落物品"))
        assertEquals("激化晶体 · 30.0%", boss.field("抽中率最高"))
        assertFalse(boss.fields.any { it.label == "必掉物" })
    }

    @Test fun sharedListRankingDoesNotTravelWithSourceAndSectionNavigationIsNotSearchEvidence() {
        val html = fixture().replace("各容器的概率并不相同。", "各容器的概率并不相同。空投箱见本节末尾。")
            .replace("按品质分组</span>", "按品质分组，组内按单件抽中率从高到低</span>")
        val data = entries(html)
        val ammo = data.source("弹药仓")
        assertContains(ammo.sectionIntroduction, "空投箱见本节末尾")
        assertNull(DropSourceRecord(ammo, false).matchingRecord("空投"))
        assertNull(extractionDropMatchEvidence(ammo, "空投"))
        assertNotNull(extractionDropMatchEvidence(ammo, "弹药仓"))

        val pool = assertNotNull(linkedDropSharedPool(ammo, data))
        val rawNote = pool.field("物品池说明")
        assertContains(rawNote, "从高到低")
        val record = dropSourceRecords(emptyList(), data).single { it.entry == ammo }
        assertEquals(ammo, record.matchingRecord("伏闪", data)?.entry)
        assertEquals("候选物品：伏闪（Q8 特殊）", extractionDropMatchEvidence(ammo, "伏闪", pool.dropPoolGroups)?.caption)
        assertNull(record.matchingRecord("从高到低", data))
        assertNull(record.matchingRecord("空投", data))
        val view = sourcePoolDetail(ammo, pool)
        assertEquals(pool.dropPoolGroups, view.dropPoolGroups)
        assertFalse(view.matches("从高到低"))
        assertFalse(view.fields.any { it.value == rawNote })
        assertEquals(ammo.sectionIntroduction, view.sectionIntroduction)
    }

    @Test fun crossTierMembershipRequiresUniqueSectionsAndPoolAndMatchingAffirmativeDeclarations() {
        val html = fixture()
        val duplicateTarget = Jsoup.parse(html).apply {
            val target = getElementById("sec-pool-20101")!!
            target.after(target.clone().id("another"))
        }.html()
        for (broken in listOf(duplicateTarget, html + "<h2>晶源体 · 普通档</h2>", html + "<h2>晶源体 · 精英档</h2>",
            html.replace("（同为 1 项）", "（同为 166 项）"), html.replaceFirst("同一套 1 项", "同一套 2 项"),
            html.replace("物品完全相同</b>", "物品基本相同</b>"), html.replace("本档与普通档", "本档与其他档"))) {
            // Contradictory/missing declared pools can also block publication; inspect the diagnostic records.
            assertNull(parse(broken).entries.source("锋爪").sharedPoolId)
        }
    }

    @Test fun descriptionsNamesakesAndCompetingItemListsDoNotDeclareMembership() {
        val html = fixture()
        val prefix = "共用物品清单 · 标准装备箱、弹药仓"
        for (changed in listOf("可能$prefix", "$prefix（除外）", "$prefix。另见储运箱", "物品清单 · 标准装备箱改、弹药仓改")) {
            assertNull(entries(html.replace(prefix, changed)).source("标准装备箱").sharedPoolId)
        }
        val inProse = entries(html.replace("按品质分组</span>", "按品质分组，也适用于散点</span>、散点"))
        assertEquals("pool:sec-cpool-2", inProse.source("散点").sharedPoolId)
        assertEquals("pool:sec-cpool-1", inProse.source("弹药仓").sharedPoolId)
        assertNull(entries(html.replace("物品清单 · 散点<b>", "共用物品清单 · 散点、弹药仓<b>")).source("弹药仓").sharedPoolId)
        val nextSection = html + "<h2>另一区域</h2><div class='sec-drop-card'><div class='sec-drop-card__title'>弹药仓</div></div>"
        assertNull(entries(nextSection).last().sharedPoolId)
    }

    @Test fun brokenDistributionOrPoolCannotPublishTheSurvivingCards() {
        val html = fixture()
        val mutations = listOf(
            html.replace("<th>Q3 卓越</th>", "<th>品质</th>"), html.replace("<th>阈值</th>", "<th>Q5 臻藏</th>"),
            html.replace("<td>43.9%</td>", ""), html.replace("<td>41.4%</td>", "<td>41.4 项</td>"),
            html.replace("<td>41.4%</td>", "<td>1.4%</td>"), html.replace("5件10%", "5件1%"),
            html.replace("<td>6</td><td>50</td>", "<td>2</td><td>50</td>"),
            Jsoup.parse(html).apply { select("table tr").last()!!.remove() }.html(),
            Jsoup.parse(html).apply { select("table").remove() }.html(),
            html.replace("<span class=\"sec-drop-chip\">伏闪</span>", ""),
            html.replace("<b>1 项</b>", "<b>若干</b>")
        )
        for ((index, broken) in mutations.withIndex()) {
            val result = parse(broken)
            assertTrue(result.entries.any { it.type == ExtractionEntryType.DROP_SOURCE })
            assertFalse(result.canCache, "published broken fixture $index")
        }
    }

    @Test fun fullNovember21DropPagePreservesEverySourcePoolChipAndStageValue() {
        val dir = System.getenv("WIKI_NOV21_AUDIT_DIR")?.let(::File)
        assumeTrue("Set WIKI_NOV21_AUDIT_DIR to the November 21 captures", dir != null)
        val html = File(dir!!, "drops.html").readText(Charsets.UTF_8)
        val data = entries(html)
        val doc = Jsoup.parse(html)
        val sources = data.filter { it.type == ExtractionEntryType.DROP_SOURCE }
        assertEquals(53, sources.size)
        assertEquals(doc.select(".sec-drop-card__title").map { it.text() }, sources.map { it.name })
        assertEquals(52, sources.count { linkedDropSharedPool(it, data) != null })
        assertEquals(listOf("莉莉丝 ／ KONG"), sources.filter { it.sharedPoolId == null }.map { it.name })
        assertTrue(data.all { it.containerId == null })
        val pools = data.filter { it.isDropPool }
        assertEquals(9, pools.size)
        assertEquals(listOf(166, 166, 486, 378, 304, 138, 111, 161, 347), pools.map { e -> e.dropPoolGroups.sumOf { it.items.size } })
        val mapContainers = sources.filter { it.category == "地图场景容器（34 个）" }
        assertEquals(34, mapContainers.size)
        mapContainers.forEach { source ->
            val list = linkedDropSharedPool(source, data)!!
            assertTrue(source.sectionIntroduction.contains("物品清单相同") && source.sectionIntroduction.contains("各容器的概率并不相同"))
            assertEquals(source.field("掉落池规模"), list.field("池规模"))
        }
        assertTrue(mapContainers.none { it.matches("空投") || extractionDropMatchEvidence(it, "首领核心") != null })
        val ammo = data.source("弹药仓")
        val reference = assertNotNull(linkedDropSharedPool(ammo, data))
        assertContains(reference.field("物品池说明"), "按单件抽中率从高到低")
        val ammoView = sourcePoolDetail(ammo, reference)
        assertFalse(ammoView.matches("从高到低"))
        assertEquals(ammo.field("抽中率最高"), ammoView.field("抽中率最高"))
        doc.select(".sec-drop-pool").zip(pools).forEach { (raw, parsed) ->
            assertEquals(raw.select(".sec-drop-pool__tag").map { it.text() }, parsed.dropPoolGroups.map { it.label })
            assertEquals(raw.select(".sec-drop-chip").map { it.text() }, parsed.dropPoolGroups.flatMap { it.items })
        }
        val stages = data.filter { it.type == ExtractionEntryType.DROP_STAGE }
        val table = doc.select("table").single { it.select("th").map { h -> h.text() }.containsAll(listOf("阶段", "阈值", "Q2 及以下")) }
        val headers = table.select("th").map { it.text() }.drop(1)
        assertEquals(6, stages.size)
        table.select("tr").drop(1).zip(stages).forEach { (row, stage) ->
            assertEquals(row.select("td").map { it.text() }.drop(1), headers.map { stage.field(it) })
            assertTrue(stage.description.contains("升变 / 危峰"))
        }
        assertTrue(sources.all { it.fields.none { f -> f.label == "必掉物" || f.label.startsWith("掉落材料") } })
        assertEquals("0 件 10%、1 件 50%、2 件 40%", data.source("刺针").field("掉落件数"))
        assertEquals(DropCountState.UNKNOWN, data.source("散点").dropCountState)
        assertEquals("0 件 30%（合计 30%，数据不完整）", data.source("散点").field("掉落件数"))
        assertEquals(347, linkedDropSharedPool(data.source("空投"), data)!!.dropPoolGroups.sumOf { it.items.size })
    }
}
