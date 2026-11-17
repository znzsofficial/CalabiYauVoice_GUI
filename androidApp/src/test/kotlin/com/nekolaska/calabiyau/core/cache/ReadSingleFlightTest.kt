package com.nekolaska.calabiyau.core.cache

import androidx.compose.runtime.mutableStateOf
import com.nekolaska.calabiyau.core.ui.LoadState
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class ReadSingleFlightTest {
    @Test fun departingOwnerDoesNotCancelActiveReader() = runBlocking {
        val single = ReadSingleFlight<String, String>()
        val gate = CompletableDeferred<String>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { single.run("a") { gate.await() } }
        val reader = async(start = CoroutineStart.UNDISPATCHED) { single.run("a") { "replacement" } }
        owner.cancelAndJoin()
        assertEquals("replacement", reader.await())
        assertEquals("next", single.run("a") { "next" })
    }
    @Test fun cancellingFollowerDoesNotCancelOwnerOrAnotherReader() = runBlocking {
        val single = ReadSingleFlight<String, String>()
        val gate = CompletableDeferred<String>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { single.run("a") { gate.await() } }
        val follower = async(start = CoroutineStart.UNDISPATCHED) { single.run("a") { error("must share") } }
        val survivor = async(start = CoroutineStart.UNDISPATCHED) { single.run("a") { error("must share") } }
        follower.cancelAndJoin()
        gate.complete("shared")
        assertEquals("shared", owner.await())
        assertEquals("shared", survivor.await())
    }
    @Test fun loadStateEndsAndCanRetryAfterUpstreamCancellationOrException() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var mode = 0
            val state = LoadState("old", scope, mutableStateOf<suspend (Boolean) -> ApiResult<String>>({
                when (mode) { 0 -> throw CancellationException("upstream"); 1 -> error("failed"); else -> ApiResult.Success("new") }
            }))
            state.reload()
            assertFalse(state.isLoading); assertNotNull(state.error); assertEquals("old", state.data)
            mode = 1; state.reload()
            assertFalse(state.isLoading); assertNotNull(state.error)
            mode = 2; state.reload()
            assertFalse(state.isLoading); assertNull(state.error); assertEquals("new", state.data)
        } finally { scope.cancel() }
    }
    @Test fun oldRequestFinallyCannotEndNewRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<ApiResult<String>>()
            val state = LoadState("", scope, mutableStateOf<suspend (Boolean) -> ApiResult<String>>({ gate.await() }))
            state.reload(); state.reload(true)
            assertTrue(state.isLoading)
            gate.complete(ApiResult.Success("done"))
            assertFalse(state.isLoading); assertEquals("done", state.data)
        } finally { scope.cancel() }
    }
}
