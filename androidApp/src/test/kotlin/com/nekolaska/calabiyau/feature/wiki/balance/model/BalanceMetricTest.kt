package com.nekolaska.calabiyau.feature.wiki.balance.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceMetricTest {
    @Test
    fun formatsMetricsWithoutInventingMissingValues() {
        assertEquals("47.61%", BalanceMetric.WIN_RATE.format(47.61))
        assertEquals("0.90", BalanceMetric.KD.format(0.9))
        assertEquals("256.5", BalanceMetric.DAMAGE.format(256.52))
        assertEquals("—", BalanceMetric.SCORE.format(null))
        assertEquals("—", BalanceMetric.SCORE.format(Double.NaN))
    }

    @Test
    fun differencesUsePercentagePointsAndIgnoreRoundedNoise() {
        assertEquals("+2.50 pp", BalanceMetric.WIN_RATE.difference(52.5, 50.0))
        assertEquals("−2.00 pp", BalanceMetric.SELECT_RATE.difference(48.0, 50.0))
        assertEquals("持平", BalanceMetric.KD.difference(1.001, 1.0))
        assertEquals("持平", BalanceMetric.KD.difference(0.999, 1.0))
        assertNull(BalanceMetric.SCORE.difference(200.0, null))
        assertNull(BalanceMetric.SCORE.difference(null, 200.0))
    }
}
