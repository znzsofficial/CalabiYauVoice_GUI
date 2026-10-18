package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalanceSeasonsTest {
    private data class Option(val code: String, val name: String)

    @Test
    fun mergesHistoryWithoutLosingUpstreamOrFutureMetadata() {
        val seasons = BalanceSeasons.merge(
            listOf(Option("11", "鸣弦之誓"), Option("18", "错季花园"), Option("3", "官方修订名"), Option("20", "未来赛季")),
            Option::code, ::Option
        )
        assertEquals(listOf("20", "18", "11") + (10 downTo 1).map(Int::toString), seasons.map { it.code })
        assertEquals("官方修订名", seasons.single { it.code == "3" }.name)
        assertEquals("敕魂命途", seasons.single { it.code == "10" }.name)
        assertEquals(seasons, BalanceSeasons.merge(seasons, Option::code, ::Option))
    }

    @Test
    fun scoreRequiresSeasonEight() {
        assertFalse(BalanceSeasons.supportsScore("7"))
        assertFalse(BalanceSeasons.supportsScore(""))
        assertTrue(BalanceSeasons.supportsScore("8"))
        assertTrue(BalanceSeasons.supportsScore("18"))
    }
}
