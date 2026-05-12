package com.nekolaska.calabiyau.feature.wiki.story.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.story.model.STORY_PAGE_NAME

data class StoryPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object StoryRemoteSource {

    suspend fun fetchStoryPage(forceRefresh: Boolean): StoryPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = STORY_PAGE_NAME,
            cacheType = OfflineCache.Type.GAME_HISTORY,
            cacheKey = "story_page",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return StoryPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
