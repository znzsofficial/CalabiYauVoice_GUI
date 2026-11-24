package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.core.wiki.UnusableWikiHtml
import com.nekolaska.calabiyau.core.wiki.WikiArticleLoader
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import com.nekolaska.calabiyau.feature.wiki.map.source.MapRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object MapDetailApi : KeyedCachedWikiApi<String, MapDetail>("MapDetailApi") {

    suspend fun fetchMapDetail(mapName: String, forceRefresh: Boolean = false, cacheOnly: Boolean = false): ApiResult<MapDetail> =
        fetch(mapName, forceRefresh = forceRefresh, cacheOnly = cacheOnly)

    override suspend fun fetchFromCache(key: String): ApiResult<MapDetail> =
        if (key == RIFT_MAP) read(key, forceRefresh = false, cacheOnly = true) else super.fetchFromCache(key)

    override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean): ApiResult<MapDetail> =
        read(key, forceRefresh, cacheOnly = false)

    internal suspend fun read(key: String, forceRefresh: Boolean, cacheOnly: Boolean,
        load: WikiArticleLoader = WikiParseSource::loadArticle): ApiResult<MapDetail> =
        ioApiCall("获取地图详情失败") {
            val sourceResult = try {
                MapRemoteSource.readMapDetail(key, forceRefresh, cacheOnly, load)
            } catch (e: UnusableWikiHtml) {
                return@ioApiCall ApiResult.Error(e.message.orEmpty(), kind = ErrorKind.PARSE)
            } ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val detail = sourceResult.value
                ?: return@ioApiCall ApiResult.Error("未找到地图信息模板", kind = ErrorKind.NOT_FOUND)
            ApiResult.Success(detail, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
        }
}
