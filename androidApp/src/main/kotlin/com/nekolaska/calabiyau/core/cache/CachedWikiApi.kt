package com.nekolaska.calabiyau.core.cache

import data.ApiResult

/**
 * Wiki API 对象的通用缓存基类。
 *
 * 封装了 [MemoryCacheRegistry] 注册、带请求版本保护的线程安全内存缓存、
 * 以及 fetch 的缓存优先策略。子类只需实现 [fetchFromNetwork]，
 * 需要离线缓存支持时额外实现 [fetchFromCache]。
 */
abstract class CachedWikiApi<T>(private val name: String) {

    private val cache = VersionedMemoryCache<Unit, CachedValue<T>>()

    private data class CachedValue<T>(
        val value: T,
        val isOffline: Boolean,
        val cacheAgeMs: Long
    )

    init {
        MemoryCacheRegistry.register(name) { cache.clear() }
    }

    protected fun getCachedValue(): T? = cache.get(Unit)?.value

    protected fun updateCache(value: T?) {
        cache.put(Unit, value?.let { CachedValue(it, isOffline = false, cacheAgeMs = 0L) })
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
            cache.get(Unit)?.let {
                return ApiResult.Success(it.value, isOffline = it.isOffline, cacheAgeMs = it.cacheAgeMs)
            }
        }
        val token = if (cacheOnly) null else cache.begin(Unit)
        val result = if (cacheOnly) fetchFromCache() else fetchFromNetwork(forceRefresh)
        if (token != null && result is ApiResult.Success && !result.isOffline) {
            cache.publish(Unit, token, CachedValue(result.value, false, 0L))
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
