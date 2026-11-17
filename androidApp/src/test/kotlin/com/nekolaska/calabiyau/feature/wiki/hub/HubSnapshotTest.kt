package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class HubSnapshotTest {
    @Test fun onlyCompleteOnlineDataPublishes() = runBlocking {
        var published: List<Int>? = null
        HubListDataSnapshot.capture("test", { ApiResult.Success(listOf(1), isOffline = true) }, { true }, { published = it })
        assertNull(published)
        HubListDataSnapshot.capture("test", { ApiResult.Success(listOf(1)) }, { false }, { published = it })
        assertNull(published)
        HubListDataSnapshot.capture("test", { ApiResult.Success(listOf(2)) }, { true }, { published = it })
        assertEquals(listOf(2), published)
        HubListDataSnapshot.capture("test", { ApiResult.Success(listOf(1), isOffline = true) }, { true }, { published = it })
        assertNull(published)
    }
    @Test fun clearInvalidatesAlreadyRunningSnapshotPublication() = runBlocking {
        val gate = CompletableDeferred<ApiResult<List<Int>>>()
        var published: List<Int>? = null
        val work = async(start = CoroutineStart.UNDISPATCHED) { HubListDataSnapshot.capture("test", { gate.await() }, { true }, { published = it }) }
        val epoch = HubListDataSnapshot.generation
        MemoryCacheRegistry.clearAll()
        gate.complete(ApiResult.Success(listOf(1)))
        work.await()
        assertTrue(HubListDataSnapshot.generation > epoch); assertNull(published)
        assertNull(HubListDataSnapshot.factions); assertNull(HubListDataSnapshot.weaponCategories)
    }
    @Test fun hostLifecycleIncludesNewPopupsWithoutGlobalTimers() {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val owner = WikiWebViewLifetime<String>(setResumed = { view, resumed -> calls += view to resumed }, destroy = { _, _ -> })
        owner.setHostResumed(false); owner.attach("main"); owner.addPopup("main", "popup")
        assertEquals(listOf("main" to false, "popup" to false), calls)
        owner.setHostResumed(true)
        assertEquals(listOf("main" to true, "popup" to true).toSet(), calls.takeLast(2).toSet())
        owner.close(); calls.clear(); owner.setHostResumed(false); assertTrue(calls.isEmpty())
    }
    @Test fun lateSnapshotCannotReplaceNewerRequest() = runBlocking {
        val gate = CompletableDeferred<ApiResult<List<Int>>>()
        var published: List<Int>? = null
        val old = async(start = CoroutineStart.UNDISPATCHED) { HubListDataSnapshot.capture("race", { gate.await() }, { true }, { published = it }) }
        HubListDataSnapshot.capture("race", { ApiResult.Success(listOf(2)) }, { true }, { published = it })
        gate.complete(ApiResult.Success(listOf(1))); old.await()
        assertEquals(listOf(2), published)
    }
}
