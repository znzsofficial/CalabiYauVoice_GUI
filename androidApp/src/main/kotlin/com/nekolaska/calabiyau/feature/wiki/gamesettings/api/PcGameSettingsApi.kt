package com.nekolaska.calabiyau.feature.wiki.gamesettings.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcGameSettingsPage
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcSettingsFailure
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object PcGameSettingsApi : CachedWikiApi<PcGameSettingsPage>("PcGameSettingsApi") {
    override suspend fun fetchFromCache(): ApiResult<PcGameSettingsPage> = loadSource(PcGameSettingsRemoteSource::readCache)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<PcGameSettingsPage> = loadSource {
        PcGameSettingsRemoteSource.fetch(forceRefresh)
    }

    internal suspend fun loadSource(fetch: suspend () -> WikiHtmlPageSourceResult?): ApiResult<PcGameSettingsPage> =
        ioApiCall("获取PC游戏设置失败") {
            try { parseSource(fetch()) }
            catch (e: PcSettingsFailure) { e.error }
        }

    internal fun parseSource(source: WikiHtmlPageSourceResult?): ApiResult<PcGameSettingsPage> {
        if (source == null) return ApiResult.Error("暂无PC游戏设置缓存", kind = ErrorKind.NOT_FOUND)
        val parsed = PcGameSettingsParser.parse(source.html)
        if (!parsed.canCache) return ApiResult.Error(
            "PC游戏设置页面结构不完整：${parsed.issues.take(2).joinToString("；")}", kind = ErrorKind.PARSE
        )
        return ApiResult.Success(parsed.page, isOffline = source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
