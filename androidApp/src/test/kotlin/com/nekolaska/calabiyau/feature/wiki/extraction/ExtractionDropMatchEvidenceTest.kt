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
        assertEquals("包含匹配物品", evidence.label)
        assertEquals(listOf("Q8 装备", "Q2 精致"), evidence.items.map { it.qualityLabel })
        assertFalse(evidence.snippet.contains("护甲"))
        val qualityMatch = assertNotNull(extractionDropMatchEvidence(entry, "Q8"))
        assertEquals("池品质匹配", qualityMatch.label)
        assertTrue(qualityMatch.items.isEmpty())
    }

    @Test fun metadataEvidenceIncludesTheActualMatchWithoutClaimingItemMembership() {
        val entry = ExtractionEntry("box", "医疗箱", "容器", fields = listOf(
            ExtractionField("本节掉落说明", "普通前文".repeat(30) + "Boss阶段需确认" + "普通后文".repeat(30))
        ))
        val evidence = assertNotNull(extractionDropMatchEvidence(entry, "boss"))
        assertEquals("本节掉落说明匹配", evidence.label)
        assertTrue(evidence.snippet.contains("Boss阶段需确认"))
        assertTrue(evidence.snippet.length < 100)
        assertTrue(evidence.items.isEmpty())
        assertEquals("来源名称匹配", extractionDropMatchEvidence(entry, "医疗")?.label)
        assertNull(extractionDropMatchEvidence(entry, "切割器"))
        assertNull(extractionDropMatchEvidence(entry, " "))
    }
}
