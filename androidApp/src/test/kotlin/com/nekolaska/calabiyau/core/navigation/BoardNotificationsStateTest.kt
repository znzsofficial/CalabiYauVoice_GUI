package com.nekolaska.calabiyau.core.navigation

import data.ApiResult
import data.ErrorKind
import data.ReplyNotification
import data.ReplyNotificationsResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardNotificationsStateTest {
    @Test
    fun oldResponseCannotReplaceNewAccountOrUnlockItsRequest() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var cookie = "A"
            val a = CompletableDeferred<ApiResult<ReplyNotificationsResponse>>()
            val b = CompletableDeferred<ApiResult<ReplyNotificationsResponse>>()
            val state = BoardNotificationsState(scope, { cookie }, { value, _ ->
                if (value == "A") a.await() else b.await()
            })
            state.refresh()
            cookie = "B"
            state.refresh()
            a.complete(ApiResult.Success(ReplyNotificationsResponse(
                notifications = listOf(ReplyNotification(9, 1, 2)), unreadCount = 1)))
            assertTrue(state.loading)
            assertTrue(state.items.isEmpty())
            b.complete(ApiResult.Success(ReplyNotificationsResponse(
                notifications = listOf(ReplyNotification(10, 1, 3)), unreadCount = 1)))
            assertEquals(10L, state.items.single().id)
            assertFalse(state.loading)
        } finally { scope.cancel() }
    }

    @Test
    fun temporaryFailurePreservesListCursorUnreadAndReadAllBoundary() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var result: ApiResult<ReplyNotificationsResponse> = ApiResult.Success(ReplyNotificationsResponse(
                notifications = listOf(ReplyNotification(9, 1, 2)), unreadCount = 3, latestId = 9, nextCursor = "8"))
            var boundary: Long? = null
            val state = BoardNotificationsState(scope, { "cookie" }, { _, _ -> result }, { _, _, through ->
                boundary = through
                ApiResult.Success(ReplyNotificationsResponse(success = true, unreadCount = 0))
            })
            state.refresh()
            for (failure in listOf(ApiResult.Error("offline", ErrorKind.NETWORK), ApiResult.Error("busy", httpStatus = 503))) {
                result = failure
                state.more()
                assertEquals(3, state.unread)
                assertEquals(9L, state.items.single().id)
                assertEquals("8", state.nextCursor)
                assertTrue(state.stale)
            }
            state.markRead()
            assertEquals(9L, boundary)
            assertEquals(0, state.unread)
            result = ApiResult.Error("expired", httpStatus = 401)
            state.refresh()
            assertTrue(state.items.isEmpty())
            assertEquals(0, state.unread)
            assertFalse(state.stale)
        } finally { scope.cancel() }
    }

    @Test
    fun accountSwitchClearsCachedNotificationsBeforeRequestCompletes() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var cookie = "A"
            val state = BoardNotificationsState(scope, { cookie }, { value, _ ->
                if (value == "A") ApiResult.Success(ReplyNotificationsResponse(
                    notifications = listOf(ReplyNotification(9, 1, 2)), unreadCount = 1))
                else ApiResult.Error("offline", ErrorKind.NETWORK)
            })
            state.refresh()
            assertEquals(1, state.items.size)
            cookie = "B"
            state.refresh()
            assertTrue(state.items.isEmpty())
            assertEquals(0, state.unread)
        } finally { scope.cancel() }
    }

    @Test
    fun suspendedRefreshCannotLandAfterAccountSwitch() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var cookie = "A"
            val gate = CompletableDeferred<ApiResult<ReplyNotificationsResponse>>()
            var calls = 0
            val state = BoardNotificationsState(scope, { cookie }, { value, _ ->
                calls++
                if (value == "B") ApiResult.Error("offline", ErrorKind.NETWORK)
                else if (calls == 1) ApiResult.Success(ReplyNotificationsResponse(
                    notifications = listOf(ReplyNotification(9, 1, 2)), unreadCount = 1))
                else gate.await()
            })
            state.refresh()
            assertEquals(1, state.items.size)
            state.refresh()
            cookie = "B"
            state.refresh()
            assertTrue(state.items.isEmpty())
            assertEquals(0, state.unread)
            gate.complete(ApiResult.Success(ReplyNotificationsResponse(
                notifications = listOf(ReplyNotification(9, 1, 2)), unreadCount = 5)))
            assertTrue(state.items.isEmpty())
            assertEquals(0, state.unread)
        } finally { scope.cancel() }
    }
}
