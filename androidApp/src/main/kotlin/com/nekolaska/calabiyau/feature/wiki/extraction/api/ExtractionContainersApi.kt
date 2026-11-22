package com.nekolaska.calabiyau.feature.wiki.extraction.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

internal val extractionContainersApi = ExtractionContainersApi()

internal class ExtractionContainersApi(
    private val disk: suspend () -> WikiHtmlPageSourceResult? = ExtractionCatalogRemoteSource::readContainersCache,
    private val network: suspend (Boolean) -> WikiHtmlPageSourceResult? = ExtractionCatalogRemoteSource::fetchContainers
) : CachedWikiApi<List<ExtractionEntry>>("ExtractionContainers") {
    override suspend fun fetchFromCache(): ApiResult<List<ExtractionEntry>> = ioApiCall("离线掉落表读取失败") {
        parse(disk(), true)
    }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ExtractionEntry>> = ioApiCall("掉落表加载失败") {
        parse(network(forceRefresh), false)
    }

    private fun parse(source: WikiHtmlPageSourceResult?, cached: Boolean): ApiResult<List<ExtractionEntry>> {
        source ?: return ApiResult.Error(if (cached) "暂无掉落表离线副本" else "掉落表加载失败，暂无离线副本", kind = ErrorKind.NETWORK)
        val result = ExtractionContainerParser.parseResult(source.html)
        if (!result.canCache) {
            WikiParseLogger.warnMalformed("ExtractionContainers", result.issues.take(8).joinToString("; "), source.html)
            return ApiResult.Error("掉落表内容不完整", kind = ErrorKind.PARSE)
        }
        return ApiResult.Success(result.entries, isOffline = cached || source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
