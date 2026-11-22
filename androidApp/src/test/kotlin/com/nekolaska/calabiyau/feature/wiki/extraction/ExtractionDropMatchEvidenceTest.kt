package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import kotlin.test.*

class ExtractionDropMatchEvidenceTest {
    @Test fun poolItemsTakePrecedenceAndKeepTheirActualQuality() {
        val entry = ExtractionEntry("box", "切割器箱", "容器", fields = listOf(ExtractionField("说明", "切割器相关")),
            dropPoolGroups = listOf(
                ExtractionDropPoolGroup("Q8 装备", listOf("切割器", "护甲"), 8),
                ExtractionDropPoolGroup("Q2 精致", listOf("切割器"), 2)
            ))
        val evidence = assertNotNull(extractionDropMatchEvidence(entry, " 切割器 "))
        assertEquals("候选物品：切割器（Q8 装备）、切割器（Q2 精致）", evidence.caption)
        assertEquals(listOf("Q8 装备", "Q2 精致"), evidence.items.map { it.qualityLabel })
        assertFalse(evidence.snippet.contains("护甲"))
        val qualityMatch = assertNotNull(extractionDropMatchEvidence(entry, "Q8"))
        assertEquals("物品品质：Q8 装备", qualityMatch.caption)
        assertTrue(qualityMatch.items.isEmpty())
    }

    @Test fun metadataEvidenceIncludesTheActualMatchWithoutClaimingItemMembership() {
        val entry = ExtractionEntry("box", "医疗箱", "容器", fields = listOf(
            ExtractionField("本节掉落说明", "普通前文".repeat(30) + "Boss阶段需确认" + "普通后文".repeat(30))
        ))
        val evidence = assertNotNull(extractionDropMatchEvidence(entry, "boss"))
        assertEquals("本节掉落说明：${evidence.snippet}", evidence.caption)
        assertTrue(evidence.snippet.contains("Boss阶段需确认"))
        assertTrue(evidence.snippet.length < 100)
        assertTrue(evidence.items.isEmpty())
        val name = assertNotNull(extractionDropMatchEvidence(entry, "医疗"))
        assertTrue(name.isNameMatch)
        assertNull(name.caption)
        val sameLabel = entry.copy(fields = listOf(ExtractionField("名称", "另一名称")))
        assertEquals("名称：另一名称", extractionDropMatchEvidence(sameLabel, "另一")?.caption)
        assertNull(extractionDropMatchEvidence(entry, "切割器"))
        assertNull(extractionDropMatchEvidence(entry, " "))
    }

    @Test fun searchPreviewUsesThePicturedQualityAndNamesItFirst() {
        val name = "自动维护"
        val entry = ExtractionEntry("box", "储运箱", "容器", dropPoolGroups = listOf(
            ExtractionDropPoolGroup("Q8 特殊", listOf(name), 8), ExtractionDropPoolGroup("Q4 完美", listOf(name), 4)
        ))
        val artwork = buildDropArtworkIndex(listOf(entry,
            ExtractionEntry("q8", name, "配件", quality = 8, qualityLabel = "Q8", imageUrl = "https://example.org/q8.png"),
            ExtractionEntry("q4", name, "配件", quality = 4, qualityLabel = "完美", imageUrl = "https://example.org/q4.png")))
        val evidence = assertNotNull(extractionDropMatchEvidence(entry, name))
        val (pictured, image) = assertNotNull(dropPreviewArtwork(name, evidence, entry.dropPoolGroups, artwork))
        assertEquals("https://example.org/q4.png", image)
        assertEquals("Q4 完美", pictured.qualityLabel)
        assertEquals("候选物品：自动维护（Q4 完美）、自动维护（Q8 特殊）", evidence.captionWithThumbnail(pictured))
        assertNull(dropPreviewArtwork("储运箱", extractionDropMatchEvidence(entry, "储运箱"), entry.dropPoolGroups, artwork))
    }

    @Test fun browsingSkipsGroupsWithNoQualifiedArtworkAndKeepsFirstPicturedItemInSourceOrder() {
        val unavailable = object : AbstractList<String>() {
            override val size = 350
            override fun get(index: Int): String = error("No picture of this quality exists")
        }
        val groups = listOf(ExtractionDropPoolGroup("Q8 特殊", unavailable, 8),
            ExtractionDropPoolGroup("Q4 完美", listOf("缺图", "乙", "甲"), 4))
        val artwork = buildDropArtworkIndex(listOf("甲", "乙").map { name ->
            ExtractionEntry(name, name, "配件", quality = 4, qualityLabel = "完美", imageUrl = "https://example.org/$name.png")
        })
        assertNull(dropPreviewArtwork("", null, groups, DropArtworkIndex.Empty))
        assertEquals(ExtractionDropItem("乙", "Q4 完美", 4) to "https://example.org/乙.png", dropPreviewArtwork("", null, groups, artwork))
    }
}
