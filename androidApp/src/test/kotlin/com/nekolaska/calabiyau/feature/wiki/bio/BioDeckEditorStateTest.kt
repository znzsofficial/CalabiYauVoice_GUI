package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class BioDeckEditorStateTest {
    @Test fun emptyDependencyRefreshAndSameFactionReconciliationPreserveAllDraftFields() {
        val state = editor(cards()).apply { intro = "未发布介绍"; author = "作者"; shareCodeInput = "未导入文本" }
        val saved = state.savedValues()
        repeat(3) {
            state.reconcileFactions(emptyList())
            state.reconcileFactions(listOf("超弦体", "晶源体"))
        }
        assertEquals(saved, state.savedValues())
    }
    private fun cards() = listOf("完美" to 8, "卓越" to 11, "精致" to 11).flatMap { (q, n) ->
        (1..n).map { DeckCardOption("$q$it", "$q$it", q, false, it + when(q) { "完美" -> 0; "卓越" -> 20; else -> 40 }) }
    }
    private fun editor(options: List<DeckCardOption>) = BioDeckEditorState().apply {
        deckName = "测试卡组"; selectFaction("晶源体"); selectableIds.addAll(options.map { it.cardId })
    }

    @Test fun restoredDraftRetainsFactionAndSelectedCardsWhenSourcesBecomeAvailable() {
        val original = editor(cards()).apply { author = "作者"; intro = "介绍"; shareCodeInput = "输入" }
        val restored = assertNotNull(BioDeckEditorState.restore(original.savedValues()))
        restored.reconcileFactions(emptyList())
        restored.reconcileFactions(listOf("超弦体", "晶源体"))
        assertEquals(original.savedValues(), restored.savedValues())
        restored.selectFaction("超弦体")
        assertTrue(restored.selectableIds.isEmpty())
    }

    @Test fun missingShareIndexRequiresExplicitConsentAndPayloadRemainsComplete() {
        val options = cards().mapIndexed { index, c -> if (index == 0) c.copy(index = -1) else c }
        val state = editor(options)
        assertFailsWith<IllegalArgumentException> { state.payload(options, true, false) }
        val payload = state.payload(options, true, true)
        assertEquals(options.map { it.cardId }, payload.selectedCardIds)
        assertTrue(payload.allowMissingShareCode)
    }

    @Test fun removedSelectionsAndRarityUnderflowFailClosed() {
        val options = cards()
        val state = editor(options)
        assertFailsWith<IllegalArgumentException> { state.payload(options.drop(1), true, true) }
        state.selectableIds.removeAt(0)
        assertFailsWith<IllegalArgumentException> { state.payload(options, true, false) }
        assertFailsWith<IllegalArgumentException> { state.payload(options, false, true) }
    }

    @Test fun malformedImportDoesNotDestroyExistingDraftAndDefaultsCountOnce() {
        val options = cards()
        val state = editor(options)
        val before = state.selectableIds.toList()
        state.shareCodeInput = "not a code"
        assertFailsWith<IllegalArgumentException> { state.importCode(options) }
        assertEquals(before, state.selectableIds)
        val default = options.first().copy(isDefault = true)
        assertEquals(options.size, state.selection(listOf(default) + options.drop(1)).size)
        assertNull(BioDeckEditorState.restore(listOf("bad")))
    }

    @Test fun submissionIsSingleFlightAndCancellationReleasesTheGuard() = runBlocking {
        val pending = CompletableDeferred<ApiResult<SubmitDeckResult>>()
        var writes = 0
        val submission = BioDeckSubmissionState(invalidate = {}) { _, _ -> writes++; pending.await() }
        val payload = editor(cards()).payload(cards(), true, false)
        val job = async(start = CoroutineStart.UNDISPATCHED) { submission.publish(payload, emptyMap()) }
        assertNull(submission.publish(payload, emptyMap()))
        assertEquals(1, writes)
        job.cancelAndJoin()
        assertFalse(submission.isSubmitting)
    }

    @Test fun selectionLimitDefaultsAndUnknownCardsCannotBeBypassedByToggle() {
        val options = (1..21).map { DeckCardOption("卡$it", "id$it", "精致", it == 1, it) }
        val state = BioDeckEditorState().apply { selectFaction("晶源体") }
        assertEquals("卡牌不可选", state.toggle(options.first(), options))
        options.drop(1).take(19).forEach { assertNull(state.toggle(it, options)) }
        assertEquals(20, state.rarityCounts(options)["精致"])
        assertNotNull(state.toggle(options.last(), options))
        assertEquals(19, state.selectableIds.size)
        assertEquals("卡牌不可选", state.toggle(options.last().copy(cardId = "unknown"), options))
    }

    @Test fun failedPublicationIsReturnedOnceAndDoesNotLeaveEditorBusy() = runBlocking {
        var writes = 0
        var invalidations = 0
        val state = BioDeckSubmissionState(invalidate = { invalidations++ }) { _, _ -> writes++; ApiResult.Error("network uncertain") }
        val payload = editor(cards()).payload(cards(), true, false)
        assertIs<ApiResult.Error>(state.publish(payload, emptyMap()))
        assertEquals(1, writes)
        assertFalse(state.isSubmitting)
        assertEquals(2, invalidations)
    }
}
