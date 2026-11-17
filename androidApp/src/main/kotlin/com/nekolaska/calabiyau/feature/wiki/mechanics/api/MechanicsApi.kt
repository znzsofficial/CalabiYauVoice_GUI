package com.nekolaska.calabiyau.feature.wiki.mechanics.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.*
import com.nekolaska.calabiyau.feature.wiki.mechanics.parser.MechanicsParser
import com.nekolaska.calabiyau.feature.wiki.mechanics.source.*
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object MechanicsApi : KeyedCachedWikiApi<MechanicsKind, MechanicsPage>("MechanicsApi") {
    override suspend fun fetchFromCache(key: MechanicsKind) = loadSource(key) { MechanicsRemoteSource.readCache(key) }
    override suspend fun fetchFromNetwork(key: MechanicsKind, forceRefresh: Boolean) = loadSource(key) { MechanicsRemoteSource.fetch(key, forceRefresh) }
    internal suspend fun loadSource(kind: MechanicsKind, load: suspend () -> WikiHtmlPageSourceResult?): ApiResult<MechanicsPage> =
        ioApiCall("${kind.pageName}加载失败") {
            try { parseSource(kind, load()) }
            catch (e: MechanicsPayloadException) {
                WikiParseLogger.warnMalformed("MechanicsApi.${kind.name}", e.message.orEmpty(), "")
                ApiResult.Error("暂时无法显示完整资料", kind = ErrorKind.PARSE)
            }
        }
    internal fun parseSource(kind: MechanicsKind, source: WikiHtmlPageSourceResult?): ApiResult<MechanicsPage> {
        if (source == null) return ApiResult.Error("加载失败，暂无离线资料", kind = ErrorKind.NETWORK)
        val parsed = MechanicsParser.parse(kind, source.html)
        if (!parsed.canCache) {
            WikiParseLogger.warnMalformed("MechanicsApi.${kind.name}", parsed.issues.joinToString("；"), source.html)
            return ApiResult.Error("暂时无法显示完整资料", kind = ErrorKind.PARSE)
        }
        return ApiResult.Success(parsed.page, isOffline = source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
