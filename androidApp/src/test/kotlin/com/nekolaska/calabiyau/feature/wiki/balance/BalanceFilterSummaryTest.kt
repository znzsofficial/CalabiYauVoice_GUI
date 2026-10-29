package com.nekolaska.calabiyau.feature.wiki.balance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceFilterSummaryTest {
    @Test
    fun defaultsStayOnOneLineUntilAComparisonIsChosen() {
        val summary = balanceFilterSummary(
            mode = "排位爆破",
            season = "敕魂命途",
            map = null,
            rankNames = emptyList(),
            compareSeason = null
        )

        assertEquals("敕魂命途 · 排位爆破 · 全地图 · 全段位", summary.headline)
        assertNull(summary.compareLabel)
    }

    @Test
    fun namedMapAndSeveralRanksCollapseInsteadOfListingEveryChip() {
        val summary = balanceFilterSummary(
            mode = "排位爆破",
            season = "敕魂命途",
            map = "404基地",
            rankNames = listOf("传说", " 神话 ", ""),
            compareSeason = " 赤刃行动 "
        )

        assertEquals("敕魂命途 · 排位爆破 · 404基地 · 2 个段位", summary.headline)
        assertEquals("对比 赤刃行动", summary.compareLabel)
    }
}
