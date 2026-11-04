package com.nekolaska.calabiyau.feature.wiki.voting

import com.nekolaska.calabiyau.feature.wiki.voting.model.*
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class VotingScreenStateTest {
    private val config = PollConfig("test", 0, "", listOf(PollCandidate("甲", ""), PollCandidate("乙", "")))
    private fun votes(selected: String? = null) = VoteState(config, 2, "total", selected != null,
        config.candidates.associate { it.name to PollData(it.name, 1, it.name == selected) })

    @Test fun guestsLoadConfigurationWithoutAuthenticatedReadsOrWrites() = runBlocking {
        val state = VotingScreenState(loggedIn = { false }, fetchConfig = { ApiResult.Success(config) },
            fetchVotes = { error("guest read") }, writeVotes = { _, _ -> error("guest write") })
        state.load()
        assertEquals(config, state.config)
        assertNull(state.submit())
    }

    @Test fun duplicateSubmissionAndRefreshCannotStartAnotherMutation() = runBlocking {
        val pending = CompletableDeferred<ApiResult<Unit>>()
        var writes = 0
        var reads = 0
        val state = VotingScreenState(loggedIn = { true }, fetchConfig = { ApiResult.Success(config) },
            fetchVotes = { reads++; ApiResult.Success(votes(if (reads > 1) "甲" else null)) },
            writeVotes = { _, chosen -> writes++; assertEquals(setOf("甲"), chosen); pending.await() })
        state.load()
        state.toggle("甲")
        val job = async(start = CoroutineStart.UNDISPATCHED) { state.submit() }
        state.toggle("乙")
        assertNull(state.submit())
        state.load()
        assertEquals(1, writes)
        pending.complete(ApiResult.Success(Unit))
        assertEquals("投票提交成功！", job.await())
        assertEquals(setOf("甲"), state.selectedNames)
        assertFalse(state.isSubmitting)
        assertEquals(2, reads)
    }

    @Test fun failedWriteIsNeverRetriedAndReconcilesAuthoritativePartialSuccess() = runBlocking {
        var reads = 0
        var writes = 0
        val state = VotingScreenState(loggedIn = { true }, fetchConfig = { ApiResult.Success(config) },
            fetchVotes = { reads++; ApiResult.Success(votes(if (reads > 1) "乙" else null)) },
            writeVotes = { _, _ -> writes++; ApiResult.Error("partial") })
        state.load()
        state.toggle("甲")
        assertTrue(state.submit().orEmpty().contains("已同步服务器状态"))
        assertEquals(1, writes)
        assertEquals(setOf("乙"), state.selectedNames)
    }

    @Test fun cancellationPropagatesAndAlwaysReleasesSubmitGuard() = runBlocking {
        val state = VotingScreenState(loggedIn = { true }, fetchConfig = { ApiResult.Success(config) },
            fetchVotes = { ApiResult.Success(votes()) }, writeVotes = { _, _ -> throw CancellationException("closed") })
        state.load()
        assertFailsWith<CancellationException> { state.submit() }
        assertFalse(state.isSubmitting)
        assertFalse(state.isLoadingData)
    }

    @Test fun successfulWriteWithFailedRefreshRetainsOptimisticServerSelectionAndNeverRewrites() = runBlocking {
        var reads = 0
        var writes = 0
        val state = VotingScreenState(loggedIn = { true }, fetchConfig = { ApiResult.Success(config) },
            fetchVotes = { reads++; if (reads == 1) ApiResult.Success(votes()) else ApiResult.Error("offline") },
            writeVotes = { _, _ -> writes++; ApiResult.Success(Unit) })
        state.load()
        state.toggle("甲")
        assertTrue(state.submit().orEmpty().contains("投票已提交，但状态刷新失败"))
        assertTrue(state.votes?.pollDataMap?.get("甲")?.userVoted == true)
        assertEquals(1, writes)
    }
}
