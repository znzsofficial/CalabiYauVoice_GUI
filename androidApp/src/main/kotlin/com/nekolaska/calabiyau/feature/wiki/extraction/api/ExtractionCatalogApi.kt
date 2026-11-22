package com.nekolaska.calabiyau.feature.wiki.extraction.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object ExtractionCatalogApi {
    private val catalogs = ExtractionCatalogKind.entries.associateWith { Catalog(it) }

    suspend fun fetch(kind: ExtractionCatalogKind, forceRefresh: Boolean = false, cacheOnly: Boolean = false,
                      allowMemoryCache: Boolean = true): ApiResult<List<ExtractionEntry>> =
        catalogs.getValue(kind).fetch(forceRefresh, cacheOnly, allowMemoryCache)
}

internal class Catalog(
    private val kind: ExtractionCatalogKind,
    private val cache: suspend (ExtractionCatalogKind) -> WikiHtmlPageSourceResult? = ExtractionCatalogRemoteSource::readCache,
    private val network: suspend (ExtractionCatalogKind, Boolean) -> WikiHtmlPageSourceResult? = ExtractionCatalogRemoteSource::fetch
) : CachedWikiApi<List<ExtractionEntry>>("ExtractionCatalog.${kind.name}") {
    override suspend fun fetchFromCache(): ApiResult<List<ExtractionEntry>> = ioApiCall("读取${kind.title}失败") {
        parse(cache(kind), cached = true)
    }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ExtractionEntry>> = ioApiCall("获取${kind.title}失败") {
        parse(network(kind, forceRefresh), cached = false)
    }

    private fun parse(source: WikiHtmlPageSourceResult?, cached: Boolean): ApiResult<List<ExtractionEntry>> {
        source ?: return ApiResult.Error(if (cached) "暂无${kind.title}离线副本" else "${kind.title}加载失败，暂无离线副本", kind = ErrorKind.NETWORK)
        val result = ExtractionCatalogParsers.parseResult(source.html, kind)
        return if (!result.canCache) ApiResult.Error("${kind.title}内容不完整", kind = ErrorKind.PARSE)
        else ApiResult.Success(result.entries, isOffline = cached || source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
