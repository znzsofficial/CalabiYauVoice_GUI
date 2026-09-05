package com.nekolaska.calabiyau.feature.wiki.history.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistoryEntry
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistorySection
import com.nekolaska.calabiyau.feature.wiki.history.parser.GameHistoryParsers
import com.nekolaska.calabiyau.feature.wiki.history.source.GameHistoryRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object GameHistoryApi : CachedWikiApi<List<GameHistorySection>>("GameHistoryApi") {

    override suspend fun fetchFromCache(): ApiResult<List<GameHistorySection>> =
        ioApiCall("读取游戏历史缓存失败") {
            val sourceResult = GameHistoryRemoteSource.loadCachedGameHistoryPage()
                ?: return@ioApiCall ApiResult.Error("没有游戏历史缓存", kind = ErrorKind.NETWORK)

            val parsedSections = GameHistoryParsers.parseSections(sourceResult.html)
            val sections = enrichWithImageUrls(parsedSections, cacheOnly = true)

            if (sections.isEmpty()) {
                ApiResult.Error("未找到游戏历史缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameHistorySection>> =
        ioApiCall("获取游戏历史失败") {
            val sourceResult = GameHistoryRemoteSource.fetchGameHistoryPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)

            val parsedSections = GameHistoryParsers.parseSections(sourceResult.html)
            val sections = enrichWithImageUrls(parsedSections, cacheOnly = sourceResult.isFromCache)

            if (sections.isEmpty()) {
                ApiResult.Error("未找到游戏历史数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }

    private suspend fun enrichWithImageUrls(
        sections: List<GameHistorySection>,
        cacheOnly: Boolean
    ): List<GameHistorySection> {
        if (sections.isEmpty()) return emptyList()

        val imageFileNames = sections.flatMap { section ->
            section.entries.mapNotNull(GameHistoryEntry::imageFileName)
        }.distinct()

        val imageUrlMap = if (!cacheOnly && imageFileNames.isNotEmpty()) {
            WikiEngine.fetchImageUrls(imageFileNames)
        } else {
            emptyMap()
        }

        return sections.map { section ->
            section.copy(
                entries = section.entries.map { entry ->
                    val resolvedImageUrl = entry.imageFileName?.let(imageUrlMap::get)
                        ?: entry.imageUrl
                    entry.copy(imageUrl = resolvedImageUrl)
                }
            )
        }
    }
}
