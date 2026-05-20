package com.nekolaska.calabiyau.feature.wiki.story.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.story.model.StoryEntry
import com.nekolaska.calabiyau.feature.wiki.story.model.StorySection
import com.nekolaska.calabiyau.feature.wiki.story.parser.StoryParsers
import com.nekolaska.calabiyau.feature.wiki.story.source.StoryRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StoryApi {

    init {
        MemoryCacheRegistry.register("StoryApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<StorySection>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchStory(forceRefresh: Boolean = false): ApiResult<List<StorySection>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<StorySection>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = StoryRemoteSource.fetchStoryPage(forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val parsedSections = StoryParsers.parseSections(sourceResult.html)
                val sections = enrichWithImageUrls(parsedSections)

                if (sections.isEmpty()) {
                    ApiResult.Error("未找到剧情故事数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取剧情故事失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun enrichWithImageUrls(
        sections: List<StorySection>
    ): List<StorySection> {
        if (sections.isEmpty()) return emptyList()

        val imageFileNames = sections.flatMap { section ->
            section.entries.mapNotNull(StoryEntry::imageFileName)
        }.distinct()

        val imageUrlMap = if (imageFileNames.isNotEmpty()) {
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
