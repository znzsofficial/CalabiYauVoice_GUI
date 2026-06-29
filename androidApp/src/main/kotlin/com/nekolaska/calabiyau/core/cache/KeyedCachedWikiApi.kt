package com.nekolaska.calabiyau.core.cache

import data.ApiResult

/**
 * Wiki API 的参数化缓存基类。
 *
 * 适用于以 pageName / category 之类键值区分结果的 API。
 * 负责统一内存缓存、离线缓存与网络请求的顺序。
 */
abstract class KeyedCachedWikiApi<K, T>(private val name: String) {

    private val cacheMap = linkedMapOf<K, T>()

    init {
        MemoryCacheRegistry.register(name) { clearMemoryCache() }
    }

    protected fun getCachedValue(key: K): T? = synchronized(cacheMap) { cacheMap[key] }

    protected fun updateCache(key: K, value: T) {
        synchronized(cacheMap) {
            cacheMap[key] = value
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
            getCachedValue(key)?.let { return ApiResult.Success(it) }
        }
        val result = if (cacheOnly) fetchFromCache(key) else fetchFromNetwork(key, forceRefresh)
        if (result is ApiResult.Success) updateCache(key, result.value)
        return result
    }

    protected open suspend fun fetchFromCache(key: K): ApiResult<T> =
        ApiResult.Error("$name 不支持 cacheOnly")

    protected abstract suspend fun fetchFromNetwork(key: K, forceRefresh: Boolean): ApiResult<T>
}
