package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import data.SharedJson
import data.WikiResponse
import data.filePrefixRegex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val distinctNames = fileNames.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return@withContext emptyMap()

        val result = ConcurrentHashMap<String, String>()
        distinctNames.chunked(50).map { chunk ->
            async {
                val titlesParam = chunk.joinToString("|") { URLEncoder.encode("文件:$it", "UTF-8") }
                val url = "$API?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url&format=json"
                val cacheKey = "decoration_image_urls_${chunk.stableHashKey()}"
                val payload = if (cacheOnly) {
                    OfflineCache.getEntry(OfflineCache.Type.DECORATIONS, cacheKey)?.content
                } else {
                    OfflineCache.fetchWithCache(
                        type = OfflineCache.Type.DECORATIONS,
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

    private fun pageCacheKey(pageName: String): String = "decoration_$pageName"

    private fun List<String>.stableHashKey(): String {
        val raw = sorted().joinToString("|")
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
