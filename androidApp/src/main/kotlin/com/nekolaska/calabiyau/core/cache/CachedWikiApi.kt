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
    private var cachedValue: T? = null

    init {
        MemoryCacheRegistry.register(name) { cachedValue = null }
    }

    protected fun getCachedValue(): T? = cachedValue

    protected fun updateCache(value: T?) {
        cachedValue = value
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
            cachedValue?.let { return ApiResult.Success(it) }
        }
        val result = if (cacheOnly) fetchFromCache() else fetchFromNetwork(forceRefresh)
        if (result is ApiResult.Success) cachedValue = result.value
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
