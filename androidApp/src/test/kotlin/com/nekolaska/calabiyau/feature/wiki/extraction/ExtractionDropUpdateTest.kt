package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionDropUpdateTest {
    private fun fixture() = checkNotNull(javaClass.getResource("/fixtures/extraction/drop_updates_20261110.html")).readText(Charsets.UTF_8)
    private fun parse(html: String = fixture()) = ExtractionCatalogParsers.parseResult(html, ExtractionCatalogKind.DROPS)
        .also { assertTrue(it.canCache, it.issues.toString()) }.entries
    private fun List<ExtractionEntry>.source(name: String) = single { it.type == ExtractionEntryType.DROP_SOURCE && it.name == name }

    @Test fun explicitSharedPoolDeclarationsRestoreLinksWithoutInventingContainerIds() {
        val entries = parse()
        val needle = entries.source("刺针")
        assertEquals("pool:sec-pool-20101", needle.sharedPoolId)
        assertEquals(needle.sharedPoolId, entries.source("蜥犬").sharedPoolId)
        assertEquals("pool:sec-bpool-3", entries.source("阶段 4").sharedPoolId)
        assertEquals(entries.source("阶段 4").sharedPoolId, entries.source("阶段 5").sharedPoolId)
        assertNull(entries.source("阶段 6").sharedPoolId)
        assertNull(entries.source("空投").sharedPoolId)
        assertEquals("掉落池 · 据点箱子", linkedDropSharedPool(entries.source("据点箱子"), entries)?.name)
        assertTrue(entries.filter { it.type == ExtractionEntryType.DROP_SOURCE }.all { it.containerId == null })
        assertNull(linkedDropContainer(needle, listOf(ExtractionEntry("fake", "刺针", "晶骸", containerId = "20101"))))
    }

    @Test fun newStageHeadersAndWeightedLabelsRetainTheirActualMeaning() {
        val entries = parse()
        val stages = extractionEntriesForView(entries, ExtractionDropView.STAGES)
        assertEquals(2, stages.size)
        assertEquals("阶段 4 · 通用掉落", stages.first().name)
        assertEquals("377 项", stages.first().fields.single { it.label == "通用掉落" }.value)
        assertEquals("—", stages.last().fields.single { it.label == "通用掉落" }.value)
        assertTrue(stages.all { it.dropPoolGroups.isEmpty() })
        assertTrue(entries.source("阶段 4").fields.any { it.label == "掉落材料（按概率抽）" })
        assertFalse(entries.source("阶段 4").fields.any { it.label == "必掉物" })
        val group = linkedDropSharedPool(entries.source("阶段 4"), entries)!!.dropPoolGroups.single()
        assertEquals("Q8 特殊", group.label)
        assertEquals(8, group.qualityCode)
    }

    @Test fun unknownStageHeadersDoNotSilentlyLoseAllStageRecords() {
        val result = ExtractionCatalogParsers.parseResult(fixture().replace("<th>通用掉落</th>", "<th>新版未知</th>"), ExtractionCatalogKind.DROPS)
        assertFalse(result.canCache)
        assertTrue(result.issues.any { "unsupported headers" in it })
    }

    @Test fun nearbyPoolsSimilarNamesAndRepeatedHeadingsCannotCreateAssociations() {
        val original = parse()
        val pool = linkedDropSharedPool(original.source("据点箱子"), original)!!
        assertNull(linkedDropSharedPool(original.source("据点箱子"), listOf(pool, pool)))
        val document = Jsoup.parse(fixture())
        // The title names this source explicitly; merely sharing its grid with air drops is insufficient.
        document.select(".sec-drop-pool__head").last()!!.prependText("疑似")
        assertNull(parse(document.html()).source("据点箱子").sharedPoolId)
        val acrossSections = fixture() + "<h2>据点箱与空投</h2><div class='sec-drop-card'><div class='sec-drop-card__title'>据点箱子</div></div>"
        assertNull(parse(acrossSections).last().sharedPoolId)
        assertNull(parse(fixture().replace("本档 6 只晶源体共用同一套", "本档 6 只晶源体分别使用"))
            .source("刺针").sharedPoolId)
        assertNull(parse(fixture().replace("共用同一套", "不共用同一套")).source("刺针").sharedPoolId)
    }

    @Test fun competingPoolDeclarationsAndRepeatedSourceNamesFailClosed() {
        val document = Jsoup.parse(fixture())
        val pool = document.select(".sec-drop-pool").last()!!
        pool.after(pool.clone().id("another-pool"))
        assertNull(parse(document.html()).source("据点箱子").sharedPoolId)
        val duplicate = Jsoup.parse(fixture())
        val source = duplicate.select(".sec-drop-card").first()!!
        source.after(source.clone())
        assertTrue(parse(duplicate.html()).filter { it.name == "刺针" }.all { it.sharedPoolId == null })
    }

    @Test fun sharedPoolDoesNotInheritZeroCountsFromOneOfItsSources() {
        val entries = parse()
        val zero = entries.source("散点")
        assertEquals(DropCountState.ZERO, zero.dropCountState)
        val pool = linkedDropSharedPool(zero, entries)!!
        assertEquals(DropCountState.UNKNOWN, pool.dropCountState)
        assertEquals(pool.id, entries.source("储运箱").sharedPoolId)
        assertEquals(DropCountState.POSITIVE, entries.source("空投").dropCountState)
    }

    @Test fun q8RenameRestoresOnlyKnownLabelsWithoutChangingOtherQualityIdentities() {
        assertEquals("Q8 特殊", resolveDropQualityLabel("Q8 装备", listOf("Q1 优秀", "Q8 特殊")))
        assertEquals("Q8 装备", resolveDropQualityLabel("Q8 特殊", listOf("Q8 装备")))
        assertNull(resolveDropQualityLabel("Q8 装备", listOf("Q8", "Q8 特殊")))
        assertNull(resolveDropQualityLabel("Q8 未知", listOf("Q8 特殊")))
        assertNull(resolveDropQualityLabel("Q5 装备", listOf("Q5 臻藏")))
        assertEquals("Q8 装备", resolveDropQualityLabel("Q8 装备", listOf("Q8 装备", "Q8 特殊")))
    }

    @Test fun fullNovember12SnapshotRetainsAllPoolsStagesAndExplicitlyScopedLinks() {
        val directory = System.getenv("EXTRACTION_UPDATED_AUDIT_DIR")?.let(::File)
        assumeTrue("Set EXTRACTION_UPDATED_AUDIT_DIR to the November 12 captures", directory?.isDirectory == true)
        val html = File(directory!!, "drops.html").readText(Charsets.UTF_8)
        val entries = parse(html)
        assertEquals(53, entries.count { it.type == ExtractionEntryType.DROP_SOURCE })
        assertEquals(21, entries.count { it.isDropPool })
        assertEquals(6, entries.count { it.type == ExtractionEntryType.DROP_STAGE })
        assertEquals(50, entries.count { linkedDropSharedPool(it, entries) != null })
        assertEquals(setOf("莉莉丝 ／ KONG", "阶段 6", "空投"), entries.filter {
            it.type == ExtractionEntryType.DROP_SOURCE && it.sharedPoolId == null
        }.map { it.name }.toSet())
        assertTrue(entries.all { it.containerId == null })
        val document = Jsoup.parse(html)
        val rawPools = document.select(".sec-drop-pool")
        rawPools.zip(entries.filter { it.isDropPool }).forEach { (raw, parsed) ->
            assertEquals(raw.select(".sec-drop-chip").map { it.text() }, parsed.dropPoolGroups.flatMap { it.items })
            assertEquals(raw.select(".sec-drop-pool__tag").map { it.text() }, parsed.dropPoolGroups.map { it.label })
        }
        val containers = ExtractionContainerParser.parseResult(File(directory, "drop-table.html").readText(Charsets.UTF_8))
        assertTrue(containers.canCache, containers.issues.toString())
        assertEquals(52, containers.entries.size)
        assertEquals(249, containers.entries.sumOf { it.dropPoolGroups.size })
        assertEquals(426, buildDropLookup(containers.entries).size)
        assertTrue(containers.entries.flatMap { it.dropPoolGroups }.filter { it.qualityCode == 8 }.all { it.label == "Q8 特殊" })
        val attachments = ExtractionCatalogParsers.parseResult(File(directory, "attachments.html").readText(Charsets.UTF_8), ExtractionCatalogKind.ATTACHMENTS)
        assertTrue(attachments.canCache, attachments.issues.toString())
        assertNotNull(attachments.entries.single { it.name == "稳定枪托 Ⅲ" }.imageUrl)
        println("November 12: 53 sources, 21 pools, 6 stages, 50 declared shared-pool links; 52 containers / 426 identities")
    }
}
