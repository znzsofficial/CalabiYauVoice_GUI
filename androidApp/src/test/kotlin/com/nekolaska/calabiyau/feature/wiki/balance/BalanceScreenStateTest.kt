package com.nekolaska.calabiyau.feature.wiki.balance

import com.nekolaska.calabiyau.feature.wiki.balance.model.*
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class BalanceScreenStateTest {
    private fun settings(name: String) = BalanceSettings(listOf(FilterOption(name, name)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun query(name: String) = BalanceDataQuery(name, "-255", listOf("-255"), "S8", "0")

    @Test fun olderSettingsCannotOverwriteNewerRequest() = runBlocking {
        val old = CompletableDeferred<ApiResult<BalanceSettings>>()
        val state = BalanceScreenState(fetchSettings = { force -> if (force) ApiResult.Success(settings("new")) else old.await() })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { state.loadSettings(false) }
        state.loadSettings(true)
        old.complete(ApiResult.Success(settings("old")))
        job.join()
        assertEquals("new", state.settings?.modes?.single()?.code)
        assertFalse(state.isLoadingSettings)
    }

    @Test fun aFailedReadDoesNotDestroyGoodSettingsOrRequestDataAutomatically() = runBlocking {
        var calls = 0
        var fail = false
        val state = BalanceScreenState(fetchSettings = { if (fail) ApiResult.Error("offline") else ApiResult.Success(settings("ok")) },
            fetchData = { calls++; ApiResult.Success(BalanceResult(emptyList(), emptyList())) })
        state.loadSettings(false)
        fail = true
        state.loadSettings(true)
        assertEquals("ok", state.settings?.modes?.single()?.code)
        assertNotNull(state.settingsError)
        assertEquals(0, calls)
        state.loadData(query("applied"))
        assertEquals(1, calls)
    }

    @Test fun clearingQueryInvalidatesAnOlderInFlightResult() = runBlocking {
        val pending = CompletableDeferred<ApiResult<BalanceResult>>()
        val state = BalanceScreenState(fetchData = { pending.await() })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { state.loadData(query("old")) }
        state.loadData(null)
        pending.complete(ApiResult.Success(BalanceResult(emptyList(), emptyList())))
        job.join()
        assertNull(state.result)
        assertFalse(state.isLoadingData)
    }

    @Test fun cancellationResetsBusyStateWithoutBecomingAnError() = runBlocking {
        val state = BalanceScreenState(fetchData = { throw CancellationException("closed") })
        assertFailsWith<CancellationException> { state.loadData(query("x")) }
        assertFalse(state.isLoadingData)
        assertNull(state.dataError)
    }
}
