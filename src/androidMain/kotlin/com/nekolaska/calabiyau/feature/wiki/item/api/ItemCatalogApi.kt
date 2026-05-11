package com.nekolaska.calabiyau.feature.wiki.item.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.parser.ItemCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.item.source.ItemCatalogRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ItemCatalogApi {

    init {
        MemoryCacheRegistry.register("ItemCatalogApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<ItemInfo>? = null

    fun clearMemoryCache() { cachedData = null }

    suspend fun fetchItems(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false
    ): ApiResult<List<ItemInfo>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }

        return if (cacheOnly) {
            fetchFromCache()
        } else {
            fetchFromNetwork(forceRefresh)
        }
    }

    private suspend fun fetchFromCache(): ApiResult<List<ItemInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = ItemCatalogRemoteSource.loadFromCache()
                    ?: return@withContext ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)

                val items = ItemCatalogParsers.parseItems(sourceResult.html)
                if (items.isEmpty()) {
                    ApiResult.Error("未找到道具图鉴数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedData = items
                    ApiResult.Success(items, isOffline = true, cacheAgeMs = sourceResult.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取道具图鉴失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ItemInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = ItemCatalogRemoteSource.fetchItems(forceRefresh)
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)

                val items = ItemCatalogParsers.parseItems(sourceResult.html)
                if (items.isEmpty()) {
                    ApiResult.Error("未找到道具图鉴数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedData = items
                    ApiResult.Success(
                        items,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取道具图鉴失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
