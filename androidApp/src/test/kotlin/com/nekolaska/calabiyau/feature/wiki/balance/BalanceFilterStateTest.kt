package com.nekolaska.calabiyau.feature.wiki.balance

import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import com.nekolaska.calabiyau.feature.wiki.balance.model.FilterOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BalanceFilterStateTest {
    private val settings = BalanceSettings(
        modes = listOf(FilterOption("casual", "匹配"), FilterOption("ranked", "排位爆破")),
        maps = listOf(FilterOption("map-a", "地图甲"), FilterOption("map-b", "地图乙")),
        ranks = listOf(FilterOption("gold", "黄金"), FilterOption("diamond", "钻石")),
        seasons = listOf(FilterOption("18", "当前赛季"), FilterOption("17", "上个赛季"), FilterOption("7", "旧赛季")),
        positions = emptyList(),
        characters = emptyList()
    )
    private val chosen = BalanceFilterCodes("casual", "map-b", "17", "18", listOf("diamond"))

    @Test
    fun firstLoadUsesDefaultsAndRefreshPreservesValidCodesDespiteNewLabelsAndNewSeason() {
        assertEquals(
            BalanceFilterCodes(mode = "ranked", season = "18"),
            BalanceFilterState().reconcile(settings).applied
        )
        val state = BalanceFilterState(chosen, showAttackers = true, sortField = BalanceMetric.KD, sortOrder = BalanceSortOrder.ASC)
        val refreshed = settings.copy(
            modes = settings.modes.map { it.copy(name = "新名称 ${it.code}") },
            seasons = listOf(FilterOption("19", "新赛季")) + settings.seasons,
            maps = settings.maps.reversed()
        )
        assertEquals(state, state.reconcile(refreshed))
        assertEquals(chosen.queryOrNull(), state.reconcile(refreshed).applied.queryOrNull())
    }

    @Test
    fun savedStateRoundTripsAppliedDraftSideAndSortBeforeSettingsAreLoaded() {
        val state = BalanceFilterState(
            applied = chosen,
            draft = chosen.copy(map = "map-a", ranks = listOf("gold", "diamond")),
            showAttackers = true,
            sortField = BalanceMetric.DAMAGE,
            sortOrder = BalanceSortOrder.ASC
        )
        val restored = assertNotNull(BalanceFilterState.restore(state.savedValues()))
        assertEquals(state, restored)
        assertEquals(state, restored.reconcile(settings))
        assertEquals(state.copy(draft = null), restored.cancelDraft())
        assertEquals(BalanceFilterState(), BalanceFilterState.restore(BalanceFilterState().savedValues()))
    }

    @Test
    fun invalidCodesAreCorrectedIndividuallyAndValidRanksAreDeduplicated() {
        val filters = BalanceFilterCodes("gone", "gone", "gone", "18", listOf("diamond", "gone", "gold", "diamond"))
        val corrected = BalanceFilterState(filters, draft = chosen.copy(compareSeason = "gone")).reconcile(settings)
        assertEquals(BalanceFilterCodes("ranked", null, "18", null, listOf("gold", "diamond")), corrected.applied)
        assertEquals(chosen.copy(compareSeason = null), corrected.draft)
        // Removing one option must not clear unrelated, still-valid conditions.
        val removed = BalanceFilterState(chosen).reconcile(settings.copy(maps = emptyList(), ranks = emptyList()))
        assertEquals(chosen.copy(map = null, ranks = emptyList()), removed.applied)
    }

    @Test
    fun missingMandatoryOptionsCannotProduceARequest() {
        val corrected = BalanceFilterState(chosen).reconcile(settings.copy(modes = emptyList(), seasons = emptyList()))
        assertNull(corrected.applied.queryOrNull())
        assertNull(corrected.applied.compareSeason)
        assertEquals(listOf("diamond"), corrected.applied.ranks)
        assertNull(BalanceFilterCodes().queryOrNull())
    }

    @Test
    fun dismissingOrResettingDraftDoesNotApplyChangesOrChangeDisplayState() {
        val original = BalanceFilterState(chosen, showAttackers = true, sortField = BalanceMetric.SCORE, sortOrder = BalanceSortOrder.ASC)
        val edited = original.beginEditing(settings).changeDraft(chosen.copy(season = "7", ranks = listOf("gold")), settings)
        assertEquals(original.applied, edited.applied)
        assertEquals(BalanceMetric.SCORE, edited.sortField)
        // All dismissal paths (cancel, outside click, system back, swipe) call cancelDraft.
        assertEquals(original, edited.cancelDraft())
        assertEquals(chosen, edited.cancelDraft().beginEditing(settings).draft)
        val reset = edited.resetDraft(settings)
        assertEquals(original.applied, reset.applied)
        assertEquals(BalanceFilterCodes(mode = "ranked", season = "18"), reset.draft)
        assertEquals(original, reset.cancelDraft())
        assertEquals(original.copy(applied = reset.draft!!), reset.applyDraft(settings))
    }

    @Test
    fun comparisonIsMutuallyExclusiveAndOldSeasonScoreIsCorrectedOnlyOnApply() {
        val original = BalanceFilterState(chosen, sortField = BalanceMetric.SCORE, sortOrder = BalanceSortOrder.ASC)
        val conflict = original.beginEditing(settings).changeDraft(chosen.copy(season = "18"), settings)
        assertNull(conflict.draft?.compareSeason)
        val applied = conflict.changeDraft(conflict.draft!!.copy(season = "7", compareSeason = "17"), settings).applyDraft(settings)
        assertEquals("17", applied.applied.compareSeason)
        assertEquals(BalanceMetric.WIN_RATE, applied.sortField)
        assertEquals(BalanceSortOrder.DESC, applied.sortOrder)
        assertNull(applied.draft)
        assertEquals(BalanceMetric.WIN_RATE, original.copy(applied = chosen.copy(season = "7")).reconcile(settings).sortField)
        assertEquals(original, original.reconcile(settings))
    }

    @Test
    fun batchDraftEditsProduceOneQueryChangeAndNeverIntermediateRequests() {
        var state = BalanceFilterState().reconcile(settings)
        val fake = FakeRequests()
        fake.observe(state)
        assertEquals(1, fake.requests.size)
        state = state.beginEditing(settings)
        fake.observe(state)
        val edits = listOf(
            state.draft!!.copy(mode = "casual"),
            chosen.copy(ranks = listOf("gold")),
            chosen.copy(ranks = listOf("diamond", "gold"))
        )
        edits.forEach { draft ->
            state = state.changeDraft(draft, settings)
            fake.observe(state)
        }
        assertEquals(1, fake.requests.size)
        state = state.applyDraft(settings)
        fake.observe(state)
        assertEquals(2, fake.requests.size)
        assertEquals(chosen.copy(ranks = listOf("gold", "diamond")).queryOrNull(), fake.requests.last())
        fake.observe(state.applyDraft(settings)) // Duplicate apply is inert once draft is closed.
        fake.observe(state.copy(showAttackers = true, sortField = BalanceMetric.KD))
        assertEquals(2, fake.requests.size)
        // Applying unchanged filters, including a different rank selection order, is also inert.
        fake.observe(state.beginEditing(settings).changeDraft(state.applied.copy(ranks = listOf("diamond", "gold")), settings).applyDraft(settings))
        assertEquals(2, fake.requests.size)
        fake.observe(state.beginEditing(settings).changeDraft(chosen, settings).cancelDraft())
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun refreshHasOneExplicitReloadEvenWhenReconciliationChangesRequestCodes() {
        val original = BalanceFilterState(chosen)
        val fake = FakeRequests()
        fake.observe(original)
        fake.observe(original.reconcile(settings), retry = 1)
        assertEquals(2, fake.requests.size)
        val refreshed = original.reconcile(settings.copy(maps = emptyList()))
        fake.observe(refreshed, retry = 2)
        assertEquals(3, fake.requests.size)
        assertEquals("-255", fake.requests.last().map)
    }

    @Test
    fun malformedSavedStateFailsClosedAndUnknownSortFallsBack() {
        val saved = BalanceFilterState(chosen).savedValues()
        assertNull(BalanceFilterState.restore(emptyList()))
        assertNull(BalanceFilterState.restore(saved.dropLast(1)))
        assertNull(BalanceFilterState.restore(saved + "unexpected"))
        assertNull(BalanceFilterState.restore(saved.toMutableList().also { it[9] = "-1" }))
        val fallback = assertNotNull(BalanceFilterState.restore(saved.toMutableList().also { it[2] = "REMOVED"; it[3] = "REMOVED" }))
        assertEquals(BalanceMetric.WIN_RATE, fallback.sortField)
        assertEquals(BalanceSortOrder.DESC, fallback.sortOrder)
        assertFalse(fallback.showAttackers)
        assertTrue(fallback.savedValues().all { it.length < 64 })
    }

    /** Local fake of the screen's LaunchedEffect(query, retry) key; no API/network/live fixtures. */
    private class FakeRequests {
        private var previous: Pair<BalanceDataQuery?, Int>? = null
        val requests = mutableListOf<BalanceDataQuery>()

        fun observe(state: BalanceFilterState, retry: Int = 0) {
            val next = state.applied.queryOrNull() to retry
            if (next != previous) next.first?.let(requests::add)
            previous = next
        }
    }
}
