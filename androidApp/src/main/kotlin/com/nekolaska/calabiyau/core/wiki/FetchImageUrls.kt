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
                "redirects" to "1",
                "format" to "json"
            )
            val json = fetchJson(url) ?: return@async
            try {
                val res = SharedJson.decodeFromString<WikiResponse>(json)
                val urlByFullTitle = mutableMapOf<String, String>()
                res.query?.pages?.values.orEmpty().forEach { page ->
                    val imageUrl = page.imageinfo?.firstOrNull()?.url ?: return@forEach
                    urlByFullTitle[page.title] = imageUrl
                    val name = page.title.replace(filePrefixRegex, "")
                    result[name] = imageUrl
                }
                // 处理文件重定向（如 文件:武器-大剑.png -> 文件:武器外观图鉴 15701001.png）
                // 使按原始文件名查询的调用方也能命中目标图片的 URL。
                // 注：MediaWiki 在标题需要规范化（下划线/空格互换）时会返回 normalized 数组，
                // 届时 redirects.from 指向规范化后的标题；本项目查询的文件名均为
                // 中文/数字/连字符/扩展名的原样组合，规范化不改变拼写，故无需处理。
                res.query?.redirects.orEmpty().forEach { redirect ->
                    val targetUrl = urlByFullTitle[redirect.to] ?: return@forEach
                    val fromName = redirect.from.replace(filePrefixRegex, "")
                    result[fromName] = targetUrl
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }.awaitAll()
    result
}
