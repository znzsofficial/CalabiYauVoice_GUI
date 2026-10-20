package com.nekolaska.calabiyau.core.cache

import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class CacheConsistencyTest {
    @Test fun oldSingletonRequestCannotOverwriteForceRefresh() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val api = object : CachedWikiApi<String>("test.singleton.race") {
            override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<String> {
                if (forceRefresh) return ApiResult.Success("new")
                started.complete(Unit); finish.await()
                return ApiResult.Success("old")
            }
        }
        val old = async { api.fetch() }
        started.await(); api.fetch(forceRefresh = true)
        finish.complete(Unit); old.await()
        assertEquals("new", (api.fetch() as ApiResult.Success).value)
    }

    @Test fun oldKeyedRequestCannotOverwriteRefreshAndKeysRemainIndependent() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val api = object : KeyedCachedWikiApi<String, String>("test.keyed.race") {
            override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<String> {
                if (key == "B") return ApiResult.Success("B")
                if (forceRefresh) return ApiResult.Success("new")
                started.complete(Unit); finish.await()
                return ApiResult.Success("old")
            }
        }
        val old = async { api.fetch("A") }
        started.await(); api.fetch("A", forceRefresh = true); api.fetch("B")
        finish.complete(Unit); old.await()
        assertEquals("new", (api.fetch("A") as ApiResult.Success).value)
        assertEquals("B", (api.fetch("B") as ApiResult.Success).value)
    }

    @Test fun clearPreventsPendingWritersFromRefillingEitherCache() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val api = object : KeyedCachedWikiApi<String, String>("test.keyed.clear") {
            override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<String> {
                calls++; started.complete(Unit); finish.await()
                return ApiResult.Success("value")
            }
        }
        val pending = async { api.fetch("A") }
        started.await(); MemoryCacheRegistry.clearAll()
        finish.complete(Unit); pending.await(); api.fetch("A")
        assertEquals(2, calls)
        val cache = VersionedMemoryCache<String, String>()
        val token = cache.begin("A"); cache.clear(); cache.publish("A", token, "stale")
        assertNull(cache.get("A"))
    }

    @Test fun offlineFallbackDoesNotPreventReconnectFetchForBothApis() = runBlocking {
        var online = false
        var singletonCalls = 0
        var keyedCalls = 0
        val singleton = object : CachedWikiApi<String>("test.singleton.offline") {
            override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<String> {
                singletonCalls++; return ApiResult.Success(if (online) "fresh" else "disk", isOffline = !online)
            }
        }
        val keyed = object : KeyedCachedWikiApi<String, String>("test.keyed.offline") {
            override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<String> {
                keyedCalls++; return ApiResult.Success(if (online) "fresh" else "disk", isOffline = !online)
            }
        }
        singleton.fetch(); keyed.fetch("A"); online = true
        assertEquals("fresh", (singleton.fetch() as ApiResult.Success).value)
        assertEquals("fresh", (keyed.fetch("A") as ApiResult.Success).value)
        singleton.fetch(); keyed.fetch("A")
        assertEquals(2, singletonCalls); assertEquals(2, keyedCalls)
    }

    @Test fun invalidParseResponsesPreserveGoodDiskPayloadAndFallBack() = runBlocking {
        val good = """{"parse":{"text":{"*":"old"}}}"""
        for (bad in listOf("""{"error":{"code":"maxlag"}}""", "{}", "<html>blocked</html>", "not json",
            """{"parse":{"wikitext":{"*":"wrong prop"}}}""",
            """{"parse":{"text":{"*":123}}}""", """{"parse":[]}""")) {
            var disk = good
            val result = fetchValidatedCachePayload(true, { bad },
                { OfflineCache.CacheResult(disk, true, 123) }, { disk = it },
                { WikiParseSource.isValidParsePayload(it, "text") })
            assertEquals(good, disk); assertEquals(good, result?.payload)
            assertEquals(true, result?.isFromCache)
        }
        assertTrue(WikiParseSource.isValidParsePayload("""{"parse":{"text":{"*":""}}}""", "text"))
        assertFalse(WikiParseSource.isValidParsePayload(good, "text|wikitext"))
    }

    @Test fun failedRefreshAndOfflineReadRetainTheLastDiskCopy() = runBlocking {
        var disk = "old"
        for (online in listOf(true, false)) {
            val result = fetchValidatedCachePayload(online, { null },
                { OfflineCache.CacheResult(disk, true, 10) }, { disk = it }, { it.isNotEmpty() })
            assertEquals("old", result?.payload); assertEquals("old", disk)
        }
        val fresh = fetchValidatedCachePayload(true, { "new" },
            { OfflineCache.CacheResult(disk, true, 10) }, { disk = it }, { true })
        assertEquals("new", disk); assertEquals(false, fresh?.isFromCache)
    }
}
