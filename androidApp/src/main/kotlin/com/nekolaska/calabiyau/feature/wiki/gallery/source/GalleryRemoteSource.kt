package com.nekolaska.calabiyau.feature.wiki.gallery.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchBatchImageUrls
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage

typealias GalleryPageSourceResult = WikiHtmlPageSourceResult

object GalleryRemoteSource {

    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): GalleryPageSourceResult? {
        return fetchWikiHtmlPage(
            pageName = pageName,
            cacheType = OfflineCache.Type.GALLERY,
            cacheKey = pageCacheKey(pageName),
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPageHtml(pageName: String): GalleryPageSourceResult? = loadCachedWikiHtmlPage(
        cacheType = OfflineCache.Type.GALLERY,
        cacheKey = pageCacheKey(pageName)
    )

    suspend fun fetchImageUrls(
        fileNames: List<String>,
        forceRefresh: Boolean,
        cacheOnly: Boolean = false
    ): Map<String, String> {
        return fetchBatchImageUrls(fileNames) { url ->
            val cacheKey = "gallery_image_urls_${url.hashCode().toString(16)}"
            if (cacheOnly) {
                OfflineCache.getEntry(OfflineCache.Type.GALLERY, cacheKey)?.content
            } else {
                OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.GALLERY,
                    key = cacheKey,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }?.payload
            }
        }
    }

    private fun pageCacheKey(pageName: String): String = "gallery_$pageName"
}
