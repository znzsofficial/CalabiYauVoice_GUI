package com.nekolaska.calabiyau.feature.wiki.submission.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.submission.model.SUBMISSION_PAGE_NAME

typealias SubmissionPageSourceResult = WikiHtmlPageSourceResult

object SubmissionRemoteSource {
    private const val CACHE_KEY = "submission_page"

    suspend fun fetchPage(forceRefresh: Boolean): SubmissionPageSourceResult? {
        return fetchWikiHtmlPage(
            pageName = SUBMISSION_PAGE_NAME,
            cacheType = OfflineCache.Type.SUBMISSIONS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): SubmissionPageSourceResult? {
        return loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.SUBMISSIONS,
            cacheKey = CACHE_KEY
        )
    }
}
