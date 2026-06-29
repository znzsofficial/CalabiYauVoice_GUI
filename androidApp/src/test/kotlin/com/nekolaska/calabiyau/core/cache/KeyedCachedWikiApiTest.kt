package com.nekolaska.calabiyau.core.cache

import data.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class KeyedCachedWikiApiTest {

    @Test
    fun cacheOnlyDoesNotReadMemory() = runBlocking {
        val api = TestKeyedCachedWikiApi()
        api.primeMemory("a", "memory-a")

        val result = api.fetch("a", cacheOnly = true)

        assertTrue(result is ApiResult.Success)
        assertEquals("disk-a", result.value)
        assertEquals(1, api.cacheFetchCount)
        assertEquals(0, api.networkFetchCount)
    }

    @Test
    fun allowMemoryCacheFalseBypassesMemory() = runBlocking {
        val api = TestKeyedCachedWikiApi()
        api.primeMemory("a", "memory-a")

        val result = api.fetch("a", allowMemoryCache = false)

        assertTrue(result is ApiResult.Success)
        assertEquals("network-a", result.value)
        assertEquals(0, api.cacheFetchCount)
        assertEquals(1, api.networkFetchCount)
    }

    @Test
    fun forceRefreshBypassesMemory() = runBlocking {
        val api = TestKeyedCachedWikiApi()
        api.primeMemory("a", "memory-a")

        val result = api.fetch("a", forceRefresh = true)

        assertTrue(result is ApiResult.Success)
        assertEquals("network-a", result.value)
        assertEquals(0, api.cacheFetchCount)
        assertEquals(1, api.networkFetchCount)
    }

    @Test
    fun successUpdatesMemoryPerKey() = runBlocking {
        val api = TestKeyedCachedWikiApi()

        val resultA = api.fetch("a")
        val resultB = api.fetch("b")

        assertTrue(resultA is ApiResult.Success)
        assertTrue(resultB is ApiResult.Success)
        assertEquals("network-a", resultA.value)
        assertEquals("network-b", resultB.value)
        assertEquals("network-a", api.cachedValue("a"))
        assertEquals("network-b", api.cachedValue("b"))
    }

    @Test
    fun cancellationIsNotWrapped() = runBlocking {
        val api = object : KeyedCachedWikiApi<String, String>("CancelledKeyedApi") {
            override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<String> {
                throw CancellationException("cancel-$key")
            }
        }

        try {
            api.fetch("a")
        } catch (e: CancellationException) {
            assertEquals("cancel-a", e.message)
            return@runBlocking
        }

        error("CancellationException was not thrown")
    }

    private class TestKeyedCachedWikiApi : KeyedCachedWikiApi<String, String>("TestKeyedCachedWikiApi") {
        var cacheFetchCount = 0
        var networkFetchCount = 0

        fun primeMemory(key: String, value: String) {
            updateCache(key, value)
        }

        fun cachedValue(key: String): String? = getCachedValue(key)

        override suspend fun fetchFromCache(key: String): ApiResult<String> {
            cacheFetchCount++
            return ApiResult.Success("disk-$key")
        }

        override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<String> {
            networkFetchCount++
            return ApiResult.Success("network-$key")
        }
    }
}
