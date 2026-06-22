package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.fetchBatchImageUrls
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

typealias DecorationHtmlSourceResult = WikiHtmlPageSourceResult

object PlayerDecorationRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): DecorationHtmlSourceResult? {
        return fetchWikiHtmlPage(
            pageName = pageName,
            cacheType = OfflineCache.Type.DECORATIONS,
            cacheKey = pageCacheKey(pageName),
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPageHtml(pageName: String): DecorationHtmlSourceResult? = loadCachedWikiHtmlPage(
        cacheType = OfflineCache.Type.DECORATIONS,
        cacheKey = pageCacheKey(pageName)
    )

    suspend fun fetchModuleWikitext(modulePage: String, forceRefresh: Boolean): String? {
        return WikiParseSource.fetchWikitext(
            pageName = modulePage,
            cacheType = OfflineCache.Type.DECORATIONS,
            cacheKey = "decoration_module_$modulePage",
            forceRefresh = forceRefresh
        )?.wikitext
    }

    suspend fun loadCachedModuleWikitext(modulePage: String): String? {
        val entry = OfflineCache.getEntry(
            type = OfflineCache.Type.DECORATIONS,
            key = "decoration_module_$modulePage"
        ) ?: return null
        val parse = SharedJson.parseToJsonElement(entry.content).jsonObject["parse"]?.jsonObject ?: return null
        return parse["wikitext"]?.jsonObject?.get("*")?.jsonPrimitive?.content
    }

    suspend fun fetchImageUrls(
        fileNames: List<String>,
        forceRefresh: Boolean,
        cacheOnly: Boolean = false
    ): Map<String, String> {
        return fetchBatchImageUrls(fileNames) { url ->
            val cacheKey = "decoration_image_urls_${url.hashCode().toString(16)}"
            if (cacheOnly) {
                OfflineCache.getEntry(OfflineCache.Type.DECORATIONS, cacheKey)?.content
            } else {
                OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.DECORATIONS,
                    key = cacheKey,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }?.payload
            }
        }
    }

    private fun pageCacheKey(pageName: String): String = "decoration_$pageName"
}
