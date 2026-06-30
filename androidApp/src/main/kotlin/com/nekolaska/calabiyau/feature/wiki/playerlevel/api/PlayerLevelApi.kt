package com.nekolaska.calabiyau.feature.wiki.playerlevel.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelPage
import com.nekolaska.calabiyau.feature.wiki.playerlevel.parser.PlayerLevelParsers
import com.nekolaska.calabiyau.feature.wiki.playerlevel.source.PlayerLevelRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object PlayerLevelApi : CachedWikiApi<PlayerLevelPage>("PlayerLevelApi") {

    override suspend fun fetchFromCache(): ApiResult<PlayerLevelPage> =
        ioApiCall("读取玩家等级缓存失败") {
            val result = PlayerLevelRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("没有玩家等级缓存", kind = ErrorKind.NETWORK)
            val page = PlayerLevelParsers.parseHtml(result.html, result.wikitext)
            if (page.levels.isEmpty() && page.rewards.isEmpty()) {
                ApiResult.Error("未找到玩家等级缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<PlayerLevelPage> =
        ioApiCall("获取玩家等级数据失败") {
            val result = PlayerLevelRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取玩家等级数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = PlayerLevelParsers.parseHtml(result.html, result.wikitext)
            if (page.levels.isEmpty() && page.rewards.isEmpty()) {
                ApiResult.Error("未找到玩家等级数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
