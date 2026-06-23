package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapDetailParsers
import com.nekolaska.calabiyau.feature.wiki.map.source.MapRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object MapDetailApi {

    suspend fun fetchMapDetail(mapName: String, forceRefresh: Boolean = false): ApiResult<MapDetail> =
        ioApiCall("获取地图详情失败") {
            val sourceResult = MapRemoteSource.fetchMapDetailPayload(mapName, forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val detail = MapDetailParsers.parseMapWikitext(mapName, sourceResult.wikitext, sourceResult.html)
                ?: return@ioApiCall ApiResult.Error("未找到地图信息模板", kind = ErrorKind.NOT_FOUND)
            ApiResult.Success(detail, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
        }
}
