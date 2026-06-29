package com.nekolaska.calabiyau.core.cache

import data.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class CachedWikiApiTest {

    @Test
    fun cacheOnlyDoesNotReadMemory() = runBlocking {
        val api = TestCachedWikiApi()
        api.primeMemory("memory")

        val result = api.fetch(cacheOnly = true)

        assertTrue(result is ApiResult.Success)
        assertEquals("disk", result.value)
        assertEquals(1, api.cacheFetchCount)
        assertEquals(0, api.networkFetchCount)
    }

    @Test
    fun allowMemoryCacheFalseBypassesMemory() = runBlocking {
        val api = TestCachedWikiApi()
        api.primeMemory("memory")

        val result = api.fetch(allowMemoryCache = false)

        assertTrue(result is ApiResult.Success)
        assertEquals("network", result.value)
        assertEquals(0, api.cacheFetchCount)
        assertEquals(1, api.networkFetchCount)
    }

    @Test
    fun forceRefreshBypassesMemory() = runBlocking {
        val api = TestCachedWikiApi()
        api.primeMemory("memory")

        val result = api.fetch(forceRefresh = true)

        assertTrue(result is ApiResult.Success)
        assertEquals("network", result.value)
        assertEquals(0, api.cacheFetchCount)
        assertEquals(1, api.networkFetchCount)
    }

    @Test
    fun successUpdatesMemory() = runBlocking {
        val api = TestCachedWikiApi()

        val result = api.fetch()

        assertTrue(result is ApiResult.Success)
        assertEquals("network", result.value)
        assertEquals("network", api.cachedValue())
    }

    @Test
    fun cancellationIsNotWrapped() = runBlocking {
        val api = object : CachedWikiApi<String>("CancelledApi") {
            override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<String> {
                throw CancellationException("cancel")
            }
        }

        val exception = assertFailsWith<CancellationException> { api.fetch() }
        assertEquals("cancel", exception.message)
    }

    private class TestCachedWikiApi : CachedWikiApi<String>("TestCachedWikiApi") {
        var cacheFetchCount = 0
        var networkFetchCount = 0

        fun primeMemory(value: String) {
            updateCache(value)
        }

        fun cachedValue(): String? = getCachedValue()

        override suspend fun fetchFromCache(): ApiResult<String> {
            cacheFetchCount++
            return ApiResult.Success("disk")
        }

        override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<String> {
            networkFetchCount++
            return ApiResult.Success("network")
        }
    }
}
