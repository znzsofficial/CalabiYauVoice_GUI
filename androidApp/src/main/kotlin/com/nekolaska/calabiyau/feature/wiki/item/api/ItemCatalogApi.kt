package com.nekolaska.calabiyau.feature.wiki.item.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.parser.ItemCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.item.source.ItemCatalogRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object ItemCatalogApi : CachedWikiApi<List<ItemInfo>>("ItemCatalogApi") {

    override suspend fun fetchFromCache(): ApiResult<List<ItemInfo>> =
        ioApiCall("获取道具图鉴失败") {
            val sourceResult = ItemCatalogRemoteSource.loadFromCache()
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val items = ItemCatalogParsers.parseItems(sourceResult.html)
            if (items.isEmpty()) {
                ApiResult.Error("未找到道具图鉴数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(items, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ItemInfo>> =
        ioApiCall("获取道具图鉴失败") {
            val sourceResult = ItemCatalogRemoteSource.fetchItems(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val items = ItemCatalogParsers.parseItems(sourceResult.html)
            if (items.isEmpty()) {
                ApiResult.Error("未找到道具图鉴数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(items, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
