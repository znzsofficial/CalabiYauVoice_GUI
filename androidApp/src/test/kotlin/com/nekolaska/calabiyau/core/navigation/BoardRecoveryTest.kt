package com.nekolaska.calabiyau.core.navigation

import data.ProfileComment
import data.ApiResult
import data.ErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BoardRecoveryTest {
    @Test
    fun completionAcknowledgesOriginalAccountAfterSwitch() {
        var disk: String? = null
        val store = BoardDraftStore({ disk }, { disk = it })
        store.useIdentity("wiki:1")
        store.edit(null, "A", null, null)
        val pending = store.prepare(null, null, "wiki:1")
        store.useIdentity("wiki:2")
        store.edit(null, "B", null, null)
        store.acknowledge(null, pending.requestId, "wiki:1")
        assertEquals("B", store.get(null).content)
        store.useIdentity("wiki:1")
        assertEquals("", store.get(null).content)
    }

    @Test
    fun backgroundInvalidationPreservesUncertainPayloadUntilExplicitRetarget() {
        var disk: String? = null
        val store = BoardDraftStore({ disk }, { disk = it })
        store.useIdentity("wiki:1")
        store.edit(10, "uncertain", 12, "Alice")
        val pending = store.prepare(10, null, "wiki:1")
        store.markTargetUnavailable(10)
        val restored = BoardDraftStore({ disk }, { disk = it })
        restored.useIdentity("wiki:1")
        assertEquals(12L, restored.get(10).replyToId)
        assertEquals(pending, restored.get(10).pending)
        assertFailsWith<IllegalStateException> { restored.prepare(10, null, "wiki:1") }
        restored.selectTarget(10, null, null)
        val retargeted = restored.prepare(10, null, "wiki:1")
        assertEquals(10L, retargeted.replyToId)
        assertNotEquals(pending.requestId, retargeted.requestId)
    }

    @Test
    fun paginationNetworkFailuresKeepRootRowsAndCursorButMissingThreadClearsThem() {
        val page = BoardPage(listOf(ProfileComment(2, "Bob", content = "reply")),
            ProfileComment(1, "Alice", content = "root"), "100:2", 30)
        for (error in listOf(ApiResult.Error("timeout", ErrorKind.TIMEOUT),
            ApiResult.Error("offline", ErrorKind.NETWORK), ApiResult.Error("busy", httpStatus = 503))) {
            assertEquals(page, page.afterLoadFailure(error))
        }
        assertEquals(page, page.afterLoadFailure(ApiResult.Error("focus hidden", httpStatus = 404, apiCode = "FOCUS_UNAVAILABLE")))
        assertEquals(BoardPage(), page.afterLoadFailure(ApiResult.Error("hidden", httpStatus = 404, apiCode = "DISCUSSION_UNAVAILABLE")))
    }

    @Test
    fun pendingRequestSurvivesOtherThreadsAndStoreRecreation() {
        var disk: String? = null
        val store = BoardDraftStore({ disk }, { disk = it })
        store.useIdentity("guest:installation")
        store.edit(10, "reply A", 12, "Alice")
        val a = store.prepare(10, "guest", "guest:installation")
        store.edit(20, "reply B", 20, "Bob")
        val b = store.prepare(20, "guest", "guest:installation")
        store.acknowledge(20, b.requestId)
        val restored = BoardDraftStore({ disk }, { disk = it })
        restored.useIdentity("guest:installation")
        assertEquals("reply A", restored.get(10).content)
        assertEquals(12L, restored.get(10).replyToId)
        assertEquals(a.requestId, restored.prepare(10, "guest", "guest:installation").requestId)
        assertNull(restored.get(20).pending)
        assertFalse(disk!!.contains("Cookie"))
    }

    @Test
    fun newPayloadOrIdentityCannotReuseOldRequestKey() {
        var disk: String? = null
        val store = BoardDraftStore({ disk }, { disk = it })
        store.useIdentity("wiki:1")
        store.edit(null, "first", null, null)
        val first = store.prepare(null, null, "wiki:1")
        store.edit(null, "changed", null, null)
        val changed = store.prepare(null, null, "wiki:1")
        assertNotEquals(first.requestId, changed.requestId)
        assertFailsWith<IllegalStateException> { store.prepare(null, null, "wiki:2") }
        store.useIdentity("wiki:2")
        assertEquals("", store.get(null).content)
        store.useIdentity("wiki:1")
        store.acknowledge(null, first.requestId)
        assertEquals("changed", store.get(null).content)
    }

    @Test
    fun expiredUncertainPostRequiresExplicitEditRatherThanSilentDuplicate() {
        var disk: String? = null
        var now = 0L
        val store = BoardDraftStore({ disk }, { disk = it }, { now })
        store.useIdentity("wiki:1")
        store.edit(10, "uncertain", null, null)
        store.prepare(10, null, "wiki:1")
        now = 7L * 24 * 60 * 60 * 1000
        assertFailsWith<IllegalStateException> { store.prepare(10, null, "wiki:1") }
        store.edit(10, "explicitly changed", null, null)
        assertEquals("explicitly changed", store.prepare(10, null, "wiki:1").content)
    }

    @Test
    fun deleteCommitsTombstoneAndQuoteRedactionWithoutARefresh() {
        val root = ProfileComment(1, "Alice", content = "root text", replyCount = 2)
        val reply = ProfileComment(2, "Bob", content = "sensitive", rootId = 1, replyToId = 1)
        val quote = ProfileComment(3, "Carol", content = "answer", rootId = 1, replyToId = 2, replyToName = "Bob", replyToTag = "tag")
        val page = BoardPage(listOf(reply, quote), root, "100:2", 2)
        val deleted = page.afterDelete(reply)
        assertTrue(deleted.comments[0].deleted)
        assertEquals("该留言已删除", deleted.comments[0].content)
        assertEquals("", deleted.comments[0].authorBid)
        assertEquals("已删除留言", deleted.comments[1].replyToName)
        assertNull(deleted.comments[1].replyToTag)
        assertEquals(1, deleted.root!!.replyCount)
        assertEquals("100:2", deleted.nextCursor)
        val closed = deleted.afterDelete(root)
        assertTrue(closed.root!!.deleted)
        assertEquals("该留言已删除", closed.root.content)
        assertEquals(2, closed.comments.size)
    }

    @Test
    fun returningBoardKeepsLoadedPagesAndCursorWhileUpdatingChangedRoot() {
        val rows = (1L..40L).map { ProfileComment(it, "author", content = "post $it") }
        val page = BoardPage(rows, nextCursor = "100:40", total = 60)
        val updated = page.withRoot(rows[25].copy(replyCount = 1))
        assertEquals(40, updated.comments.size)
        assertEquals(1, updated.comments[25].replyCount)
        assertEquals(page.nextCursor, updated.nextCursor)
        val deleted = updated.afterDelete(rows[0])
        assertEquals(39, deleted.comments.size)
        assertEquals(59, deleted.total)
        assertEquals(page.nextCursor, deleted.nextCursor)
    }
}
