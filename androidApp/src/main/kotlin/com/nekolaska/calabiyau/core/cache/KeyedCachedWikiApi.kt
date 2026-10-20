package com.nekolaska.calabiyau.core.cache

import data.ApiResult

/**
 * Wiki API 的参数化缓存基类。
 *
 * 适用于以 pageName / category 之类键值区分结果的 API。
 * 负责统一内存缓存、离线缓存与网络请求的顺序。
 */
abstract class KeyedCachedWikiApi<K, T>(private val name: String) {

    private val cache = VersionedMemoryCache<K, CachedValue<T>>()

    private data class CachedValue<T>(
        val value: T,
        val isOffline: Boolean,
        val cacheAgeMs: Long
    )

    init {
        MemoryCacheRegistry.register(name) { clearMemoryCache() }
    }

    protected fun getCachedValue(key: K): T? = cache.get(key)?.value

    protected fun updateCache(key: K, value: T) {
        cache.put(key, CachedValue(value, isOffline = false, cacheAgeMs = 0L))
    }

    protected fun clearMemoryCache() {
        cache.clear()
    }

    suspend fun fetch(
        key: K,
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<T> {
        if (!forceRefresh && !cacheOnly && allowMemoryCache) {
            val cached = cache.get(key)
            cached?.let {
                return ApiResult.Success(it.value, isOffline = it.isOffline, cacheAgeMs = it.cacheAgeMs)
            }
        }
        val token = if (cacheOnly) null else cache.begin(key)
        val result = if (cacheOnly) fetchFromCache(key) else fetchFromNetwork(key, forceRefresh)
        if (token != null && result is ApiResult.Success && !result.isOffline) {
            cache.publish(key, token, CachedValue(result.value, false, 0L))
        }
        return result
    }

    protected open suspend fun fetchFromCache(key: K): ApiResult<T> =
        ApiResult.Error("$name 不支持 cacheOnly")

    protected abstract suspend fun fetchFromNetwork(key: K, forceRefresh: Boolean): ApiResult<T>
}
