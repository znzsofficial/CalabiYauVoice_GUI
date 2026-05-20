package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
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

data class DecorationHtmlSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object PlayerDecorationRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): DecorationHtmlSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = pageName,
            cacheType = OfflineCache.Type.DECORATIONS,
            cacheKey = "decoration_$pageName",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return DecorationHtmlSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun fetchModuleWikitext(modulePage: String, forceRefresh: Boolean): String? {
        return WikiParseSource.fetchWikitext(
            pageName = modulePage,
            cacheType = OfflineCache.Type.DECORATIONS,
            cacheKey = "decoration_module_$modulePage",
            forceRefresh = forceRefresh
        )?.wikitext
    }

    suspend fun fetchImageUrls(fileNames: List<String>, forceRefresh: Boolean): Map<String, String> = withContext(Dispatchers.IO) {
        val distinctNames = fileNames.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return@withContext emptyMap()

        val result = ConcurrentHashMap<String, String>()
        distinctNames.chunked(50).map { chunk ->
            async {
                val titlesParam = chunk.joinToString("|") { URLEncoder.encode("文件:$it", "UTF-8") }
                val url = "$API?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url&format=json"
                val cacheKey = "decoration_image_urls_${chunk.stableHashKey()}"
                val cacheResult = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.DECORATIONS,
                    key = cacheKey,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) } ?: return@async

                try {
                    val response = SharedJson.decodeFromString<WikiResponse>(cacheResult.payload)
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

    private fun List<String>.stableHashKey(): String {
        val raw = sorted().joinToString("|")
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
