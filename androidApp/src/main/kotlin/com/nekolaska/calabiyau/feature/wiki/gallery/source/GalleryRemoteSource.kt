package com.nekolaska.calabiyau.feature.wiki.gallery.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import data.SharedJson
import data.WikiResponse
import data.filePrefixRegex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

typealias GalleryPageSourceResult = WikiHtmlPageSourceResult

object GalleryRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

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
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val distinctNames = fileNames.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return@withContext emptyMap()

        val result = ConcurrentHashMap<String, String>()
        distinctNames.chunked(50).map { chunk ->
            async {
                val titlesParam = chunk.joinToString("|") { URLEncoder.encode("文件:$it", "UTF-8") }
                val url = "$API?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url&format=json"
                val cacheKey = "gallery_image_urls_${chunk.stableHashKey()}"
                val payload = if (cacheOnly) {
                    OfflineCache.getEntry(OfflineCache.Type.GALLERY, cacheKey)?.content
                } else {
                    OfflineCache.fetchWithCache(
                        type = OfflineCache.Type.GALLERY,
                        key = cacheKey,
                        forceRefresh = forceRefresh
                    ) { WikiEngine.safeGet(url) }?.payload
                } ?: return@async

                try {
                    val response = SharedJson.decodeFromString<WikiResponse>(payload)
                    response.query?.pages?.values.orEmpty().forEach { page ->
                        val imageUrl = page.imageinfo?.firstOrNull()?.url ?: return@forEach
                        val name = page.title.replace(filePrefixRegex, "")
                        result[name] = imageUrl
                    }
                } catch (_: Exception) {
                }
            }
        }.awaitAll()

        result
    }

    private fun pageCacheKey(pageName: String): String = "gallery_$pageName"

    private fun List<String>.stableHashKey(): String {
        val raw = sorted().joinToString("|")
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
