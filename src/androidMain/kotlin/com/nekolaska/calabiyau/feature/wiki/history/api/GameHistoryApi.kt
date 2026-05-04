package com.nekolaska.calabiyau.feature.wiki.history.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistoryEntry
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistorySection
import com.nekolaska.calabiyau.feature.wiki.history.parser.GameHistoryParsers
import com.nekolaska.calabiyau.feature.wiki.history.source.GameHistoryRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object GameHistoryApi {

    init {
        MemoryCacheRegistry.register("GameHistoryApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<GameHistorySection>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchGameHistory(forceRefresh: Boolean = false): ApiResult<List<GameHistorySection>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameHistorySection>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = GameHistoryRemoteSource.fetchGameHistoryPage(forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val parsedSections = GameHistoryParsers.parseSections(sourceResult.html)
                val sections = enrichWithImageUrls(parsedSections)

                if (sections.isEmpty()) {
                    ApiResult.Error("未找到游戏历史数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取游戏历史失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun enrichWithImageUrls(
        sections: List<GameHistorySection>
    ): List<GameHistorySection> {
        if (sections.isEmpty()) return emptyList()

        val imageFileNames = sections.flatMap { section ->
            section.entries.mapNotNull(GameHistoryEntry::imageFileName)
        }.distinct()

        val imageUrlMap = if (imageFileNames.isNotEmpty()) {
            WikiEngine.fetchImageUrls(imageFileNames)
        } else {
            emptyMap()
        }

        return coroutineScope {
            sections.map { section ->
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
}