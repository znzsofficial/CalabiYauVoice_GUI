package com.nekolaska.calabiyau.feature.wiki.gamesettings.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcGameSettingsPage
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object PcGameSettingsApi : CachedWikiApi<PcGameSettingsPage>("PcGameSettingsApi") {
    override suspend fun fetchFromCache(): ApiResult<PcGameSettingsPage> = ioApiCall("读取PC游戏设置缓存失败") {
        parseSource(PcGameSettingsRemoteSource.readCache())
    }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<PcGameSettingsPage> = ioApiCall("获取PC游戏设置失败") {
        parseSource(PcGameSettingsRemoteSource.fetch(forceRefresh))
    }

    internal fun parseSource(source: WikiHtmlPageSourceResult?): ApiResult<PcGameSettingsPage> {
        if (source == null) return ApiResult.Error("PC游戏设置请求失败，且无可用离线缓存", kind = ErrorKind.NETWORK)
        val parsed = PcGameSettingsParser.parse(source.html)
        if (!parsed.canCache) return ApiResult.Error(
            "PC游戏设置页面结构不完整：${parsed.issues.take(2).joinToString("；")}", kind = ErrorKind.PARSE
        )
        return ApiResult.Success(parsed.page, isOffline = source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
