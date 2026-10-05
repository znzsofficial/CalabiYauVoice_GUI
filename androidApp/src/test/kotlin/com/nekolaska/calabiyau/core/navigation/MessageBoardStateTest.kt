package com.nekolaska.calabiyau.core.navigation

import data.ApiResult
import data.ErrorKind
import data.ProfileComment
import data.ProfileCommentsResponse
import data.SharedJson
import data.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class TestBoardPreferences : BoardPreferences {
    override var nickname: String? = null
    override var postAsGuest = true
    override var lastIdentity: String? = null
    override var guestId: String? = "00000000-0000-4000-8000-000000000001"
    override var draftsJson: String? = null
    override fun flushDrafts() = true
}

class MessageBoardStateTest {
    @Test
    fun lateDiskCacheCannotResurrectRowsAfterAConfirmedEmptyServerResponse() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val disk = CompletableDeferred<String?>()
            val networkReached = CompletableDeferred<Unit>()
            val state = MessageBoardState(scope, { disk.await() }, {}, TestBoardPreferences(), { null },
                fetchPage = { _, _, _ -> networkReached.complete(Unit); ApiResult.Success(ProfileCommentsResponse()) })
            networkReached.await()
            disk.complete(SharedJson.encodeToString(ProfileCommentsResponse(
                comments = listOf(ProfileComment(1, "A", content = "hidden old row")), total = 1)))
            yield()
            assertTrue(state.comments.isEmpty()); assertTrue(state.pinned.isEmpty())
            assertEquals(0, state.total); assertFalse(state.loading)
        } finally { scope.cancel() }
    }

    @Test
    fun anonymousTypingAndSendingWorkDespiteWikiIdentityFailureAndRetryKeepsItsCredential() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val prefs = TestBoardPreferences()
            val sent = mutableListOf<Pair<PendingBoardPost, String?>>()
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val state = MessageBoardState(scope, { null }, {}, prefs, { "invalid Wiki cookie" },
                fetchSession = { ApiResult.Error("Wiki unavailable", httpStatus = 503) },
                fetchPage = { _, _, _ -> ApiResult.Success(ProfileCommentsResponse()) },
                post = { _, pending, cookie, _ ->
                    sent.add(pending to cookie)
                    if (sent.size == 1) first.complete(Unit) else second.complete(Unit)
                    ApiResult.Error("response lost", ErrorKind.TIMEOUT)
                })
            yield()
            assertFalse(state.identityLoaded)
            state.draft = "anonymous draft"
            assertEquals("anonymous draft", state.draft)
            state.send(); withTimeout(5000) { first.await() }; yield()
            state.togglePostingAsGuest()
            assertTrue(state.postingAsGuest)
            assertTrue(state.error!!.contains("尚未确认"))
            state.send(); withTimeout(5000) { second.await() }; yield()
            assertEquals(sent[0].first.requestId, sent[1].first.requestId)
            assertEquals(listOf<String?>(null, null), sent.map { it.second })
            assertTrue(sent.all { it.first.submissionActor == "guest:${prefs.guestId}" })
        } finally { scope.cancel() }
    }

    @Test
    fun recoveringIdentityDoesNotEraseAnonymousTypingAndRetriesAreBackedOff() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            var time = 0L
            var calls = 0
            val state = MessageBoardState(scope, { null }, {}, TestBoardPreferences(), { "cookie" },
                fetchSession = {
                    if (++calls == 1) ApiResult.Error("unavailable", httpStatus = 503)
                    else ApiResult.Success(UserSession("A", 7))
                }, fetchProfile = { ApiResult.Success(null) },
                fetchPage = { _, _, _ -> ApiResult.Success(ProfileCommentsResponse()) }, now = { time })
            yield()
            state.draft = "still typing"
            time = 19_999; state.syncIdentity(); yield(); assertEquals(1, calls)
            time = 20_000; state.syncIdentity(); yield()
            assertEquals(2, calls); assertTrue(state.identityLoaded)
            assertEquals("still typing", state.draft)
            state.togglePostingAsGuest()
            state.draft = "signed draft"
            state.togglePostingAsGuest()
            assertEquals("still typing", state.draft)
            state.togglePostingAsGuest()
            assertEquals("signed draft", state.draft)
        } finally { scope.cancel() }
    }
}
