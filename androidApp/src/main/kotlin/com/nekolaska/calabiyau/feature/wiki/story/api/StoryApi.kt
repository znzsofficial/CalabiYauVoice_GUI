package com.nekolaska.calabiyau.feature.wiki.story.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.story.model.StoryEntry
import com.nekolaska.calabiyau.feature.wiki.story.model.StorySection
import com.nekolaska.calabiyau.feature.wiki.story.parser.StoryParsers
import com.nekolaska.calabiyau.feature.wiki.story.source.StoryRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object StoryApi : CachedWikiApi<List<StorySection>>("StoryApi") {

    override suspend fun fetchFromCache(): ApiResult<List<StorySection>> =
        ioApiCall("读取剧情故事缓存失败") {
            val sourceResult = StoryRemoteSource.loadCachedStoryPage()
                ?: return@ioApiCall ApiResult.Error("没有剧情故事缓存", kind = ErrorKind.NETWORK)

            val parsedSections = StoryParsers.parseSections(sourceResult.html)
            val sections = enrichWithImageUrls(parsedSections, cacheOnly = true)

            if (sections.isEmpty()) {
                ApiResult.Error("未找到剧情故事缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<StorySection>> =
        ioApiCall("获取剧情故事失败") {
            val sourceResult = StoryRemoteSource.fetchStoryPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)

            val parsedSections = StoryParsers.parseSections(sourceResult.html)
            val sections = enrichWithImageUrls(parsedSections, cacheOnly = sourceResult.isFromCache)

            if (sections.isEmpty()) {
                ApiResult.Error("未找到剧情故事数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }

    private suspend fun enrichWithImageUrls(
        sections: List<StorySection>,
        cacheOnly: Boolean
    ): List<StorySection> {
        if (sections.isEmpty()) return emptyList()

        val imageFileNames = sections.flatMap { section ->
            section.entries.mapNotNull(StoryEntry::imageFileName)
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
