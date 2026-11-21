package com.nekolaska.calabiyau.feature.wiki.voting

import com.nekolaska.calabiyau.core.wiki.WikiWriteOwner
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioDeckShareApi
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import com.nekolaska.calabiyau.feature.wiki.voting.api.VotingApi
import com.nekolaska.calabiyau.feature.wiki.voting.model.*
import com.nekolaska.calabiyau.feature.wiki.voting.parser.VotingParsers
import com.nekolaska.calabiyau.feature.wiki.voting.source.VoteSubmitResult
import com.nekolaska.calabiyau.feature.wiki.voting.source.VoteSubmitOperation
import com.nekolaska.calabiyau.feature.wiki.voting.source.VotingRemoteSource
import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class WikiMutationContractTest {
    private val config = PollConfig("test", 1, "", listOf(PollCandidate("甲", ""), PollCandidate("乙", "")))
    private val polls = listOf("aa", "bb", "cc").map { PollData(it, 0, false) }
    private val valid = VotingApi.bindPolls(config, polls)!!

    @Test fun invalidSlotsLimitsAndDuplicateIdsCannotStartWrites() = runBlocking {
        val broken = """<div class="ajaxpoll-container"></div><div id="ajaxpoll-container-bb"></div><div id="ajaxpoll-container-cc"></div>"""
        assertNull(VotingApi.bindPolls(config, VotingParsers.parseAjaxPollElements(broken)))
        assertNull(VotingApi.bindPolls(config, polls.drop(1)))
        assertNull(VotingApi.bindPolls(config, polls.dropLast(1) + polls.first()))
        assertNull(VotingApi.bindPolls(config, listOf(polls[0].copy(question = "test-乙")) + polls.drop(1)))
        val editor = VotingScreenState({ true }, { ApiResult.Success(config) }, { ApiResult.Success(valid) })
        editor.load(); editor.toggle("甲"); editor.toggle("乙")
        assertEquals(setOf("甲"), editor.selectedNames)
        for ((state, selected) in listOf(valid to setOf("甲", "乙"), valid.copy(totalPollId = "aa") to setOf("甲"))) {
            var reachedTransport = false
            val r = VotingApi.submit(state, selected, WikiWriteOwner { reachedTransport = true; false },
                { error("token") }, { _, _ -> error("unexpected POST") })
            assertIs<ApiResult.Error>(r); assertFalse(reachedTransport)
        }
        var tokenReads = 0
        val stale = VotingApi.submit(valid, setOf("甲"), WikiWriteOwner { false },
            { tokenReads++; "token" }, { _, _ -> error("unexpected write") })
        assertIs<ApiResult.Error>(stale); assertEquals(0, tokenReads)
    }

    @Test fun voteReplyMustConfirmThisPollAndTheRequestedSelection() {
        val selected = """{"pollsubmitvote":{"result":"<div id='ajaxpoll-id-aa'><div answer='1'><span class='ajaxpoll-our-vote'></span></div></div>"}}"""
        assertTrue(VotingRemoteSource.confirmsVote(selected, VoteSubmitOperation("aa", "1")))
        assertFalse(VotingRemoteSource.confirmsVote(selected, VoteSubmitOperation("bb", "1")))
        assertFalse(VotingRemoteSource.confirmsVote(selected, VoteSubmitOperation("aa", "0")))
        assertFalse(VotingRemoteSource.confirmsVote("{}", VoteSubmitOperation("aa", "1")))
    }

    @Test fun cancellationOrSessionChangeAfterFirstVoteSendsNoTotalVote() = runBlocking {
        for (cancel in listOf(true, false)) {
            var current = true
            val first = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val job = async { VotingApi.submit(valid, setOf("甲"), WikiWriteOwner { current }, { "token" },
                { _, op -> writes += op.pollId; first.complete(Unit)
                    withContext(NonCancellable) { release.await() }; VoteSubmitResult(true, 200) }) }
            first.await(); if (cancel) job.cancel() else current = false
            release.complete(Unit); job.join()
            if (!cancel) assertIs<ApiResult.Error>(job.await())
            assertEquals(listOf("aa"), writes)
        }
    }

    @Test fun leavingWhileGettingDeckTokenCannotDispatchEditAndEmptyReceiptsDoNotConfirmPublication() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var posted = false
        val job = launch { BioDeckShareApi.submit(SubmitDeckPayload("名字", "作者", "", "晶源体", listOf("100")),
            mapOf("晶源体" to listOf(DeckCardOption("卡", "100", "精致", false, 0))), { "A" }, { WikiWriteOwner { true } },
            { started.complete(Unit); withContext(NonCancellable) { release.await() }; "token" },
            { posted = true; error("unexpected edit") }) }
        started.await(); job.cancel(); release.complete(Unit); job.join()
        assertFalse(posted)
        fun receipt(json: String) = BioDeckShareApi.editReceipt(SharedJson.parseToJsonElement(json).jsonObject, "title")
        assertIs<ApiResult.Error>(receipt("{}")); assertIs<ApiResult.Error>(receipt("""{"edit":{}}"""))
        assertFalse(assertIs<ApiResult.Success<SubmitDeckResult>>(receipt("""{"edit":{"result":"Success"}}""")).value.isPending)
        assertTrue(assertIs<ApiResult.Success<SubmitDeckResult>>(receipt("""{"edit":{"moderation":"pending"}}""")).value.isPending)
    }
}
