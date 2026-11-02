package com.nekolaska.calabiyau.core.wiki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WikiPortraitPrefetchPolicyTest {
    @Test
    fun offscreenAndUnmeasuredPreviewsDoNotPrefetch() {
        assertEquals(emptyList(), portraitPrefetchIndices(20, listOf(0, 1, 2), isVisible = false))
        assertEquals(emptyList(), portraitPrefetchIndices(20, emptyList()))
        assertEquals(emptyList(), portraitPrefetchIndices(0, listOf(0)))
    }

    @Test
    fun visibleItemsComeBeforeAtMostTwoNeighbours() {
        assertEquals(listOf(4, 5, 6, 7, 3), portraitPrefetchIndices(30, listOf(6, 4, 5, 4)))
        assertEquals(listOf(0, 1, 2, 3), portraitPrefetchIndices(30, listOf(0, 1)))
        assertEquals(listOf(28, 29, 27, 26), portraitPrefetchIndices(30, listOf(28, 29)))
        assertEquals(listOf(1, 2, 3, 0), portraitPrefetchIndices(30, listOf(1, 2), neighbourCount = 100))
    }

    @Test
    fun invalidAndSparseIndicesCannotSeedTheEntireFaction() {
        assertEquals(listOf(2, 8, 9, 1), portraitPrefetchIndices(12, listOf(-1, 2, 8, 15)))
        assertEquals(emptyList(), portraitPrefetchIndices(3, listOf(-1, 3)))
        assertEquals(listOf(0), portraitPrefetchIndices(1, listOf(0)))
        assertEquals(listOf(2), portraitPrefetchIndices(12, listOf(2), neighbourCount = -1))
    }

    @Test
    fun successAndFailureBothReleaseInflightSoEvictedImagesCanBeRequestedAgain() = runBlocking {
        val budget = PortraitPrefetchBudget()
        var calls = 0
        budget.run("portrait") { calls++ }
        budget.run("portrait") { calls++ }
        assertFailsWith<IllegalStateException> {
            budget.run("portrait") { error("network failure") }
        }
        budget.run("portrait") { calls++ }
        assertEquals(3, calls)
    }

    @Test
    fun concurrentCallersShareOneTwoRequestBudget() = runBlocking {
        val budget = PortraitPrefetchBudget()
        val finish = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        var started = 0
        val jobs = (0 until 8).map { index ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                budget.run("portrait-$index") {
                    active++
                    started++
                    peak = maxOf(peak, active)
                    finish.await()
                    active--
                }
            }
        }
        assertEquals(2, started)
        finish.complete(Unit)
        jobs.joinAll()
        assertEquals(8, started)
        assertEquals(2, peak)
    }

    @Test
    fun duplicateCallerDoesNotReleaseTheRealOwnersClaimAndCancellationAllowsRetry() = runBlocking {
        val budget = PortraitPrefetchBudget()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            budget.run("portrait") { calls++; finish.await() }
        }
        repeat(2) { budget.run("portrait") { calls++ } }
        assertEquals(1, calls)
        owner.cancelAndJoin()
        budget.run("portrait") { calls++ }
        assertEquals(2, calls)
    }

    @Test
    fun cancellingAQueuedRequestReleasesOnlyItsOwnClaim() = runBlocking {
        val budget = PortraitPrefetchBudget(concurrency = 1)
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            budget.run("active") { calls++; finish.await() }
        }
        val queued = launch(start = CoroutineStart.UNDISPATCHED) {
            budget.run("queued") { calls++ }
        }
        queued.cancelAndJoin()
        budget.run("active") { calls++ }
        assertEquals(1, calls)
        finish.complete(Unit)
        owner.join()
        budget.run("queued") { calls++ }
        assertEquals(2, calls)
    }
}
