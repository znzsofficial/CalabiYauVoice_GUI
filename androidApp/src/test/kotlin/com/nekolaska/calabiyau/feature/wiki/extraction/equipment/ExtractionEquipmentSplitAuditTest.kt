package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentParser
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.source.EquipmentRemoteSource
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionEquipmentSplitAuditTest {
    private fun directory(): File {
        val path = System.getenv("EXTRACTION_SPLIT_AUDIT_DIR")
        assumeTrue("Set EXTRACTION_SPLIT_AUDIT_DIR to the November 21 evening captures", !path.isNullOrBlank())
        return File(path!!)
    }

    private fun audit(kind: EquipmentKind, dir: File): EquipmentPage {
        val base = kind.name.lowercase()
        val page = EquipmentRemoteSource.decode(kind, File(dir, "$base.json").readText())
        assertEquals(EquipmentParser.parse(kind, File(dir, "$base.html").readText()), page)
        return page
    }

    @Test fun renderedGunFamiliesAgreeWithEveryIndependentWeaponLine() {
        val dir = directory()
        val page = audit(EquipmentKind.GUNS, dir)
        assertEquals(30, page.series.size)
        assertTrue(page.series.all { it.variants.map { v -> v.quality.code } == listOf(1, 2, 3, 4, 8, 5) })
        // External oracle: the rendered gallery does not expose weapon.line itself. Compare sets
        // of variant IDs, not counts, names, order, common art or arithmetic IDs.
        val lua = File(dir, "120541.src").readText().replace("\r\n", "\n")
        val rows = Regex("(?ms)^\\t\\{\n(.*?)^\\t\\},").findAll(lua).map { it.groupValues[1] }.toList()
        fun String.int(key: String) = Regex("(?m)^\\s*$key = (\\d+),").find(this)!!.groupValues[1].toInt()
        assertEquals(180, rows.size)
        val lines = rows.groupBy { it.int("line") }.values.map { group -> group.map { it.int("id") }.toSet() }.toSet()
        assertEquals(lines, page.series.map { it.variants.map(EquipmentVariant::id).toSet() }.toSet())
        val fus = page.series.single { it.name == "伏闪" }.variants
        assertEquals(listOf("90", "108", "129.3", "155.8", "186.4", "223.1"), fus.map { (it.stats as EquipmentStats.Gun).damage })
        assertEquals(listOf("Q8 臻藏", "Q5 传说"), fus.takeLast(2).map { it.quality.label })
        assertEquals("不可交易", fus.last().sourceTags["流通"])
    }

    @Test fun threeArmorFamiliesKeepOnlyPublishedGradesAndEachGradesOwnArtwork() {
        val page = audit(EquipmentKind.ARMOR, directory())
        assertEquals(mapOf("适应性超弦装甲" to 6, "硬化超弦装甲" to 5, "盈能超弦装甲" to 5), page.series.associate { it.name to it.variants.size })
        assertEquals(listOf("适应性超弦装甲"), page.rows("", null, 1, EquipmentSort.DEFAULT).map { it.series.name })
        val suit = page.series.single { it.name == "硬化超弦装甲" }
        val q8 = suit.variants.single { it.quality.label == "Q8 臻藏" }
        val q5 = suit.variants.single { it.quality.label == "Q5 传说" }
        assertNotNull(q8.imageUrl); assertNotNull(q5.imageUrl); assertNotEquals(q8.imageUrl, q5.imageUrl)
        assertEquals(EquipmentStats.Armor("170", "85", "600", "4×3", "10,800"), q8.stats)
        assertEquals(EquipmentStats.Armor("220", "130", "600", "4×4", "14,400"), q5.stats)
        assertTrue(q8.lootText.contains("特种装备箱")); assertTrue(q5.lootText.contains("散点"))
    }
}
