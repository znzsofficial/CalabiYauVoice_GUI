package com.nekolaska.calabiyau.core.cache

import data.ApiResult

/**
 * Wiki API 的参数化缓存基类。
 *
 * 适用于以 pageName / category 之类键值区分结果的 API。
 * 负责统一内存缓存、离线缓存与网络请求的顺序。
 */
abstract class KeyedCachedWikiApi<K, T>(private val name: String) {

    private val cacheMap = linkedMapOf<K, CachedValue<T>>()

    private data class CachedValue<T>(
        val value: T,
        val isOffline: Boolean,
        val cacheAgeMs: Long
    )

    init {
        MemoryCacheRegistry.register(name) { clearMemoryCache() }
    }

    protected fun getCachedValue(key: K): T? = synchronized(cacheMap) { cacheMap[key]?.value }

    protected fun updateCache(key: K, value: T) {
        synchronized(cacheMap) {
            cacheMap[key] = CachedValue(value, isOffline = false, cacheAgeMs = 0L)
        }
    }

    protected fun clearMemoryCache() {
        synchronized(cacheMap) {
            cacheMap.clear()
        }
    }

    suspend fun fetch(
        key: K,
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<T> {
        if (!forceRefresh && !cacheOnly && allowMemoryCache) {
            val cached = synchronized(cacheMap) { cacheMap[key] }
            cached?.let {
                return ApiResult.Success(it.value, isOffline = it.isOffline, cacheAgeMs = it.cacheAgeMs)
            }
        }
        val result = if (cacheOnly) fetchFromCache(key) else fetchFromNetwork(key, forceRefresh)
        if (!cacheOnly && result is ApiResult.Success) {
            synchronized(cacheMap) {
                cacheMap[key] = CachedValue(result.value, result.isOffline, result.cacheAgeMs)
            }
        }
        return result
    }

    protected open suspend fun fetchFromCache(key: K): ApiResult<T> =
        ApiResult.Error("$name 不支持 cacheOnly")

    protected abstract suspend fun fetchFromNetwork(key: K, forceRefresh: Boolean): ApiResult<T>
}
