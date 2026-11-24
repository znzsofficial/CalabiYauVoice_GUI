package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.EquipmentKind
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentParser
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import org.junit.Assume.assumeNotNull
import java.io.File
import kotlin.test.*

class November24CatalogAuditTest {
    private fun html(name: String): String {
        val path = System.getenv("EXTRACTION_NOV24_CATALOG_DIR")
        assumeNotNull(path)
        return File(path, "$name.html").readText()
    }

    @Test fun equipmentUsesExactValueAcrossAllRoundedCards() {
        val guns = EquipmentParser.parse(EquipmentKind.GUNS, html("guns")).series.flatMap { it.variants }
        val armor = EquipmentParser.parse(EquipmentKind.ARMOR, html("armor")).series.flatMap { it.variants }
        assertEquals(180, guns.size); assertEquals(16, armor.size)
        val gun = guns.single { it.id == 4000030 }; val suit = armor.single { it.id == 4074002 }
        assertEquals("2万", gun.value); assertEquals("2万", suit.value)
        assertEquals(19844L, gun.numericValue); assertEquals(19831L, suit.numericValue)
        assertEquals("物资中心在售", gun.sourceTags["流通"])
        assertContains(gun.lootText, "共 9 个容器可出")
    }

    @Test fun suppliesAndAttachmentsRetainLootContextsAndEffects() {
        val loot = ExtractionCatalogParsers.parseResult(html("loot"), ExtractionCatalogKind.LOOT)
        val attachments = ExtractionCatalogParsers.parseResult(html("attachments"), ExtractionCatalogKind.ATTACHMENTS)
        assertTrue(loot.canCache); assertTrue(attachments.canCache)
        assertEquals(188, loot.entries.size); assertEquals(142, attachments.entries.size)
        val ammo = loot.entries.single { it.id == "4010001" }
        assertContains(ammo.fields.single { it.label == "掉落信息" }.value, "共 30 个容器可出")
        assertContains(ammo.tags, "物资中心在售")
        val muzzle = attachments.entries.single { it.id == "4080001" }
        assertContains(muzzle.fields.single { it.label == "实际效果" }.value, "站立最小散射 -8%")
        assertContains(muzzle.fields.single { it.label == "适用" }.value, "狙击枪")
    }
}
