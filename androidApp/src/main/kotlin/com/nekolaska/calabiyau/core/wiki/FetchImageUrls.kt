package com.nekolaska.calabiyau.core.wiki

import data.SharedJson
import data.WikiEngineCore
import data.WikiResponse
import data.filePrefixRegex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import util.buildWikiUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 批量获取文件名 → CDN URL 映射。
 *
 * @param fileNames 不含 "文件:" 前缀的文件名列表（如 "壁纸1.png"）
 * @param api MediaWiki API 地址
 * @param fetchJson 获取 JSON 响应的函数，接受 URL 返回 JSON 字符串
 * @return Map<fileName, cdnUrl>
 */
suspend fun fetchBatchImageUrls(
    fileNames: List<String>,
    api: String = WikiEngineCore.API_BASE_URL,
    fetchJson: suspend (String) -> String?
): Map<String, String> = withContext(Dispatchers.IO) {
    val distinctNames = fileNames.filter { it.isNotBlank() }.distinct()
    if (distinctNames.isEmpty()) return@withContext emptyMap()

    val result = ConcurrentHashMap<String, String>()
    distinctNames.chunked(50).map { chunk ->
        async {
            val titlesParam = chunk.joinToString("|") { "文件:$it" }
            val url = buildWikiUrl(api,
                "action" to "query", "titles" to titlesParam,
                "prop" to "imageinfo", "iiprop" to "url",
                "format" to "json"
            )
            val json = fetchJson(url) ?: return@async
            try {
                val res = SharedJson.decodeFromString<WikiResponse>(json)
                res.query?.pages?.values.orEmpty().forEach { page ->
                    val imageUrl = page.imageinfo?.firstOrNull()?.url ?: return@forEach
                    val name = page.title.replace(filePrefixRegex, "")
                    result[name] = imageUrl
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }.awaitAll()
    result
}
