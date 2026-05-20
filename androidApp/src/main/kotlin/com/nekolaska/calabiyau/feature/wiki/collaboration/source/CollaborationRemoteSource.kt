package com.nekolaska.calabiyau.feature.wiki.collaboration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.COLLABORATION_PAGE_NAME

data class CollaborationPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object CollaborationRemoteSource {
    suspend fun fetchPage(forceRefresh: Boolean): CollaborationPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = COLLABORATION_PAGE_NAME,
            cacheType = OfflineCache.Type.COLLABORATIONS,
            cacheKey = "collaboration_page",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return CollaborationPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
