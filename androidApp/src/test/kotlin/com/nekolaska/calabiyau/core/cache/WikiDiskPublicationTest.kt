package com.nekolaska.calabiyau.core.cache

import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailApi
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi
import com.nekolaska.calabiyau.feature.wiki.announcement.source.AnnouncementRemoteSource
import kotlinx.coroutines.*
import kotlin.test.*

class WikiDiskPublicationTest {
    @Test fun bad200CannotReplaceTheLastReadableDetailOrAnnouncement() = runBlocking {
        fun doc(wiki: String) = """{"parse":{"wikitext":{"*":"$wiki"},"text":{"*":""}}}"""
        val validators = listOf<Pair<String, (String) -> Boolean>>(
            doc("{{武器|类型=主武器}}") to { WeaponDetailApi.validDetailPayload("静风", it) },
            doc("{{超弦体|定位=支援}}") to { CharacterDetailApi.validDetailPayload("角色", it) },
            """{"query":{"results":{"公告":{"printouts":{}}}}}""" to AnnouncementRemoteSource::validPayload)
        for ((good, validator) in validators) {
            assertTrue(validator(good))
            var disk = good
            for (online in listOf(true, false)) {
                val result = fetchValidatedCachePayload(online, { doc("建设中") },
                    { OfflineCache.CacheResult(disk, true, 42) }, { disk = it }, validator)
                assertEquals(OfflineCache.CacheResult(good, true, 42), result)
                assertEquals(good, disk)
            }
            assertFalse(validator("{}"))
        }
        assertTrue(AnnouncementRemoteSource.validPayload("""{"query":{"results":[]}}"""))
    }

    @Test fun pendingReadAndQueuedForceCannotPublishAfterClear() = runBlocking {
        val t = OfflineCache.Type.CHARACTER_LIST; val key = "publication-test"
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val old = async(start = CoroutineStart.UNDISPATCHED) { OfflineCache.fetchWithCache(t, key) {
            entered.complete(Unit); release.await(); "old"
        } }
        entered.await()
        var queuedCalls = 0
        val queued = async(start = CoroutineStart.UNDISPATCHED) { OfflineCache.fetchWithCache(t, key, true) {
            queuedCalls++; "stale"
        } }
        val derived = OfflineCache.captureWrite(t, "derived-$key")
        OfflineCache.clear(t); release.complete(Unit); old.await(); assertNull(queued.await()); assertEquals(0, queuedCalls)
        var disk: String? = null
        OfflineCache.publishIfCurrent(derived) { disk = "stale" }; assertNull(disk)
        val fresh = OfflineCache.captureWrite(t, "derived-$key")
        OfflineCache.publishIfCurrent(fresh) { disk = "fresh" }; assertEquals("fresh", disk)
        OfflineCache.invalidate(t, "derived-$key")
        OfflineCache.publishIfCurrent(fresh) { fail("invalidated writer must not publish") }
    }

    @Test fun forceReaderCanTakeOverFromCancelledOwnerWithinSameEpoch() = runBlocking {
        val t = OfflineCache.Type.CHARACTER_LIST; val key = "force-takeover-test"
        val started = CompletableDeferred<Unit>()
        val owner = launch(start = CoroutineStart.UNDISPATCHED) { OfflineCache.fetchWithCache(t, key, true) {
            started.complete(Unit); awaitCancellation()
        } }
        started.await()
        val next = async(start = CoroutineStart.UNDISPATCHED) { OfflineCache.fetchWithCache(t, key, true) { "recovered" } }
        owner.cancelAndJoin(); assertEquals("recovered", next.await()?.payload)
    }
}
