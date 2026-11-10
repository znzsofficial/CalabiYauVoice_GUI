package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionDropArtworkTest {
    private fun item(id: String, quality: Int = 1, label: String = "优秀", url: String? = "https://images.test/$id.png") =
        ExtractionEntry(id, "同名物品", "材料", quality, label, url)

    private fun source(vararg images: ExtractionDropImage) = ExtractionEntry(
        "source", "晶源体", "来源", type = ExtractionEntryType.DROP_SOURCE, dropImages = images.toList()
    )

    @Test fun exactNameAndModeQualityAreRequiredAndQ8IsNotQ5() {
        val index = buildDropArtworkIndex(listOf(item("one"), item("two", 2, "精致"), item("five", 5, "臻藏")))
        assertEquals("https://images.test/one.png", index.imageFor("同名物品", "Q1 优秀"))
        assertEquals("https://images.test/two.png", index.imageFor("同名物品", "Q2 精致"))
        assertEquals("https://images.test/five.png", index.imageFor("同名物品", "Q5 臻藏"))
        assertNull(index.imageFor("同名物品", "Q8 装备"))
        assertNull(index.imageFor("同名物品", ""))
        assertNull(index.imageFor("同名", "Q1 优秀"))
        assertNull(buildDropArtworkIndex(listOf(item("other-mode", 8, "臻藏"))).imageFor("同名物品", "Q5 臻藏"))
    }

    @Test fun distinctIdsOrConflictingUrlsFailClosedEvenWhenOneDuplicateLacksAnImage() {
        val original = item("one")
        assertNotNull(buildDropArtworkIndex(listOf(original, original)).imageFor("同名物品", "优秀"))
        for (conflict in listOf(item("two", url = original.imageUrl), item("two", url = null),
            original.copy(imageUrl = "https://images.test/other.png"))) {
            assertNull(buildDropArtworkIndex(listOf(original, conflict)).imageFor("同名物品", "Q1 优秀"))
        }
    }

    @Test fun unqualifiedInlineImageNeverFillsAQualifiedQueryOrConflictingName() {
        val image = ExtractionDropImage("物品", imageUrl = "https://images.test/item.png")
        val entry = source(image)
        val index = buildDropArtworkIndex(listOf(entry))
        assertEquals(image.imageUrl, index.imageFor("物品", ""))
        assertNull(index.imageFor("物品", "Q1 优秀"))
        assertNull(buildDropArtworkIndex(listOf(source(image, image.copy(imageUrl = "https://images.test/other.png"))))
            .imageFor("物品", ""))
        assertNull(buildDropArtworkIndex(listOf(entry.copy(dropPoolGroups = listOf(ExtractionDropPoolGroup("Q2 精致", listOf("物品"))))))
            .imageFor("物品", ""))
    }

    @Test fun sourcePortraitsAndMissingItemPicturesAreNotArtworkCandidates() {
        val entries = listOf(source().copy(name = "同名物品", qualityLabel = "优秀", imageUrl = "https://images.test/source.png"), item("missing", url = null))
        assertNull(buildDropArtworkIndex(entries).imageFor("同名物品", "优秀"))
        assertNull(DropArtworkIndex.Empty.imageFor("任何物品", "Q8 装备"))
    }

    @Test fun inlineWeightedRowsKeepExplicitQualityAndNeverBecomeSourcePortraits() {
        val result = parse("""<div class="sec-drop-card" data-tier="5">
            <div class="sec-drop-card__title">阶段 1</div>
            <div class="sec-drop-card__label">掉落材料（按权重抽）</div>
            <div class="sec-drop-card__must"><ul>
            <li><img src="//images.test/images/klbq/thumb/a/ab/item.png/18px-item.png"> 标准弦能节点（★★★ 卓越）· 35.6%</li>
            <li><img data-src="https://images.test/cutter.png" src="data:image/png;base64,abc"> 切割器 · 13.5%</li>
            <li>缺图物品 · 1.0%</li>
            <li><img src="https://images.test/a.png"><img src="https://images.test/b.png"> 混合物品 · 3%</li>
            <li><img src="https://images.test/a.png"> 错误品质（★★ 臻藏）· 1%</li>
            </ul></div></div>""")
        assertTrue(result.canCache, result.issues.toString())
        val entry = result.entries.single()
        assertNull(entry.imageUrl)
        assertEquals(listOf(
            ExtractionDropImage("标准弦能节点", "Q3 卓越", "https://images.test/images/klbq/a/ab/item.png"),
            ExtractionDropImage("切割器", imageUrl = "https://images.test/cutter.png")
        ), entry.dropImages)
        assertTrue(entry.fields.any { it.label == "掉落材料（按权重抽）" && "缺图物品" in it.value })
        assertNull(buildDropArtworkIndex(result.entries).imageFor("切割器", "Q2 精致"))
    }

    @Test fun poolChipArtworkUsesItsOwnGroupAndMissingImagesDoNotInvalidateThePool() {
        val result = parse("""<div class="sec-drop-pool"><div class="sec-drop-pool__head">物品池<b>2 项</b></div>
            <div class="sec-drop-pool__q"><span class="sec-drop-pool__tag">Q8 装备</span><span class="sec-drop-pool__cnt">2 项</span></div>
            <div class="sec-drop-pool__chips"><span class="sec-drop-chip"><img src="https://images.test/armor.png">护甲</span>
            <span class="sec-drop-chip">背包</span></div></div>""")
        assertTrue(result.canCache, result.issues.toString())
        assertEquals(listOf("护甲", "背包"), result.entries.single().dropPoolGroups.single().items)
        val index = buildDropArtworkIndex(result.entries)
        assertEquals("https://images.test/armor.png", index.imageFor("护甲", "Q8 装备"))
        assertNull(index.imageFor("护甲", "Q5 臻藏"))
        assertNull(index.imageFor("背包", "Q8 装备"))
    }

    private fun parse(html: String) = ExtractionCatalogParsers.parseResult(html, ExtractionCatalogKind.DROPS)

    @Test fun capturedCatalogsEnrichRealPoolIdentitiesWithoutGuessingUnknownEquipment() {
        val directory = System.getenv("EXTRACTION_CONTAINERS_AUDIT_DIR")?.let(::File)
        assumeTrue("Optional full artwork captures", directory?.resolve("loot.html")?.isFile == true &&
            directory.resolve("attachments.html").isFile)
        val root = requireNotNull(directory)
        val loot = ExtractionCatalogParsers.parse(root.resolve("loot.html").readText(Charsets.UTF_8), ExtractionCatalogKind.LOOT)
        val attachments = ExtractionCatalogParsers.parse(root.resolve("attachments.html").readText(Charsets.UTF_8), ExtractionCatalogKind.ATTACHMENTS)
        val drops = ExtractionCatalogParsers.parse(root.resolve("drops.html").readText(Charsets.UTF_8), ExtractionCatalogKind.DROPS)
        val containers = ExtractionContainerParser.parseResult(root.resolve("drop-table.html").readText(Charsets.UTF_8))
        assertTrue(containers.canCache, containers.issues.toString())
        val lookup = buildDropLookup(containers.entries)
        val artwork = buildDropArtworkIndex(loot + attachments + drops)
        assertEquals(426, lookup.size)
        assertEquals(249, lookup.keys.count { artwork.imageFor(it.name, it.qualityLabel) != null })
        assertTrue(lookup.keys.filter { it.qualityCode == 8 }.all { artwork.imageFor(it.name, it.qualityLabel) == null })
        assertTrue(drops.filter { it.type == ExtractionEntryType.DROP_SOURCE }.all { it.imageUrl == null })
    }
}
