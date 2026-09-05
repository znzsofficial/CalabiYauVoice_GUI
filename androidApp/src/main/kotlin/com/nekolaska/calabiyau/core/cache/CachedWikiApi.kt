package com.nekolaska.calabiyau.core.cache

import data.ApiResult

/**
 * Wiki API 对象的通用缓存基类。
 *
 * 封装了 [MemoryCacheRegistry] 注册、`@Volatile` 内存缓存、
 * 以及 fetch 的缓存优先策略。子类只需实现 [fetchFromNetwork]，
 * 需要离线缓存支持时额外实现 [fetchFromCache]。
 */
abstract class CachedWikiApi<T>(private val name: String) {

    @Volatile
    private var cachedValue: CachedValue<T>? = null

    private data class CachedValue<T>(
        val value: T,
        val isOffline: Boolean,
        val cacheAgeMs: Long
    )

    init {
        MemoryCacheRegistry.register(name) { cachedValue = null }
    }

    protected fun getCachedValue(): T? = cachedValue?.value

    protected fun updateCache(value: T?) {
        cachedValue = value?.let { CachedValue(it, isOffline = false, cacheAgeMs = 0L) }
    }

    /**
     * 带缓存优先策略的 fetch 方法。
     *
     * @param forceRefresh 忽略内存缓存，强制从网络获取
     * @param cacheOnly 仅从离线缓存加载，不发起网络请求
     * @param allowMemoryCache 是否允许内存缓存；cacheOnly 为 true 时始终跳过内存缓存
     */
    suspend fun fetch(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<T> {
        if (!forceRefresh && !cacheOnly && allowMemoryCache) {
            cachedValue?.let {
                return ApiResult.Success(it.value, isOffline = it.isOffline, cacheAgeMs = it.cacheAgeMs)
            }
        }
        val result = if (cacheOnly) fetchFromCache() else fetchFromNetwork(forceRefresh)
        if (!cacheOnly && result is ApiResult.Success) {
            cachedValue = CachedValue(result.value, result.isOffline, result.cacheAgeMs)
        }
        return result
    }

    /**
     * 从离线缓存加载数据。默认返回不支持 cacheOnly 的错误结果。
     * 需要 cacheOnly 支持的子类应覆写此方法。
     */
    protected open suspend fun fetchFromCache(): ApiResult<T> =
        ApiResult.Error("$name 不支持 cacheOnly")

    protected abstract suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<T>
}
