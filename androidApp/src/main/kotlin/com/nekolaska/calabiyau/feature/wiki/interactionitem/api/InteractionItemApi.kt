package com.nekolaska.calabiyau.feature.wiki.interactionitem.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.interactionitem.model.InteractionItemInfo
import com.nekolaska.calabiyau.feature.wiki.interactionitem.parser.InteractionItemParsers
import com.nekolaska.calabiyau.feature.wiki.interactionitem.source.InteractionItemRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object InteractionItemApi : CachedWikiApi<List<InteractionItemInfo>>("InteractionItemApi") {

    override suspend fun fetchFromCache(): ApiResult<List<InteractionItemInfo>> =
        ioApiCall("读取互动道具缓存失败") {
            val sourceResult = InteractionItemRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("没有互动道具缓存", kind = ErrorKind.NETWORK)
            val items = InteractionItemParsers.parseItems(sourceResult.html)
            if (items.isEmpty()) {
                ApiResult.Error("未找到互动道具缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(items, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<InteractionItemInfo>> =
        ioApiCall("获取互动道具失败") {
            val sourceResult = InteractionItemRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val items = InteractionItemParsers.parseItems(sourceResult.html)
            if (items.isEmpty()) {
                ApiResult.Error("未找到互动道具数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(items, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
