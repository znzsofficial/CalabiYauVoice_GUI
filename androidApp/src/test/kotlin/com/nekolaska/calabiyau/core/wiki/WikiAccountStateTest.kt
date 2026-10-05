package com.nekolaska.calabiyau.core.wiki

import data.ApiResult
import data.CustomUserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiAccountStateTest {
    @Test
    fun temporaryIdentityFailureCanRetryWithoutChangingCookiesAndRespectsBackoff() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var time = 0L
            var calls = 0
            val state = WikiAccountState(scope, { "same-cookie" }, {
                if (++calls == 1) ApiResult.Error("offline") else ApiResult.Success(WikiUserApi.UserInfo(1, "A"))
            }, { ApiResult.Success(null) }, { time })
            state.syncAccount(); assertFalse(state.loading)
            time = 19_999; state.syncAccount(); assertEquals(1, calls)
            time = 20_000; state.syncAccount(); assertEquals(2, calls)
            assertEquals(1L, state.userInfo!!.id); assertTrue(state.profileLoaded)
        } finally { scope.cancel() }
    }

    @Test
    fun failedProfileLoadIsNotMistakenForANewUserWithEmptyProfile() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var calls = 0
            val state = WikiAccountState(scope, { "A" }, { ApiResult.Success(WikiUserApi.UserInfo(1, "A")) }, {
                if (++calls == 1) ApiResult.Error("offline") else ApiResult.Success(CustomUserProfile("A", wikiUserId = 1, customName = "kept"))
            })
            state.syncAccount()
            assertFalse(state.profileLoaded)
            state.refreshProfile()
            assertTrue(state.profileLoaded); assertEquals("kept", state.profile!!.customName)
        } finally { scope.cancel() }
    }

    @Test
    fun switchedAccountImmediatelyClearsOldUserAndRejectsAnUncancellableResponse() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var cookie: String? = "A"
            val a = CompletableDeferred<ApiResult<WikiUserApi.UserInfo?>>()
            val b = CompletableDeferred<ApiResult<WikiUserApi.UserInfo?>>()
            val state = WikiAccountState(scope, { cookie }, { value ->
                withContext(NonCancellable) { if (value == "A") a.await() else b.await() }
            }, { ApiResult.Success(CustomUserProfile("B", wikiUserId = it)) })
            state.syncAccount()
            cookie = "B"; state.syncAccount()
            a.complete(ApiResult.Success(WikiUserApi.UserInfo(1, "A")))
            assertNull(state.userInfo); assertTrue(state.loading)
            b.complete(ApiResult.Success(WikiUserApi.UserInfo(2, "B")))
            assertEquals(2L, state.userInfo!!.id); assertEquals(2L, state.profile!!.wikiUserId)
            assertFalse(state.acceptSavedProfile(CustomUserProfile("A", wikiUserId = 1)))
            assertEquals(2L, state.profile!!.wikiUserId)
            cookie = null; state.syncAccount()
            assertNull(state.userInfo); assertNull(state.profile); assertFalse(state.loading)
        } finally { scope.cancel() }
    }

    @Test
    fun aLateProfileRefreshCannotOverwriteServerConfirmedSavedData() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            val late = CompletableDeferred<ApiResult<CustomUserProfile?>>()
            var calls = 0
            val state = WikiAccountState(scope, { "A" }, { ApiResult.Success(WikiUserApi.UserInfo(1, "A")) }, {
                if (++calls == 1) ApiResult.Success(CustomUserProfile("A", wikiUserId = 1, customName = "old"))
                else late.await()
            })
            state.syncAccount(); state.refreshProfile()
            assertTrue(state.acceptSavedProfile(CustomUserProfile("A", wikiUserId = 1, customName = "saved")))
            late.complete(ApiResult.Success(CustomUserProfile("A", wikiUserId = 1, customName = "old")))
            assertEquals("saved", state.profile!!.customName)
        } finally { scope.cancel() }
    }
}
