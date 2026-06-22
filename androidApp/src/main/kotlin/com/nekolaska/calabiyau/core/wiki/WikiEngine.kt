package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.CalabiYauApplication
import com.nekolaska.calabiyau.CrashContextStore
import data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import util.buildWikiUrl
import util.bodyToFile
import util.executeGet

object WikiEngine {

    private val browserProfile = WikiUserAgent.randomProfile(desktopMode = false)

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder()
                .apply {
                    if (original.header("User-Agent") == null) header("User-Agent", browserProfile.userAgent)
                    if (original.header("Accept") == null) header("Accept", browserProfile.accept)
                    if (original.header("Accept-Language") == null) header("Accept-Language", browserProfile.acceptLanguage)
                    if (original.header("Referer") == null) header("Referer", "https://wiki.biligame.com/klbq/")
                    if (original.header("Sec-Fetch-Dest") == null) header("Sec-Fetch-Dest", "empty")
                    if (original.header("Sec-Fetch-Mode") == null) header("Sec-Fetch-Mode", "cors")
                    if (original.header("Sec-Fetch-Site") == null) header("Sec-Fetch-Site", "same-origin")
                }
                .apply {
                    if (browserProfile.secChUa != null) {
                        if (original.header("Sec-CH-UA") == null) header("Sec-CH-UA", browserProfile.secChUa)
                        if (original.header("Sec-CH-UA-Mobile") == null) header("Sec-CH-UA-Mobile", browserProfile.secChUaMobile)
                        if (original.header("Sec-CH-UA-Platform") == null) header("Sec-CH-UA-Platform", browserProfile.secChUaPlatform)
                    }
                }
                .build()
            chain.proceed(req)
        }
        .addInterceptor { chain ->
            var response = chain.proceed(chain.request())
            var tryCount = 0
            while (!response.isSuccessful && tryCount < 3 && response.code in setOf(429, 403, 503)) {
                tryCount++
                response.close()
                Thread.sleep((1000L shl tryCount) + Random.nextLong(0, 500))
                response = chain.proceed(chain.request())
            }
            response
        }
        .build()

    private val jsonParser = SharedJson
    private val nameCache = WikiEngineCore.CharacterNameCache()

    /**
     * 安全的 JSON GET 请求。
     * - 自动检测 CDN 拦截页面（返回 HTML 而非 JSON）
     * - 捕获所有异常，失败返回 null
     */
    fun safeGet(url: String): String? {
        return try {
            client.executeGet(url).use { response ->
                if (!response.isSuccessful) {
                    CalabiYauApplication.instanceOrNull?.let {
                        CrashContextStore.recordWikiRequest(
                            it,
                            "WikiEngine.safeGet",
                            url,
                            outcome = "http=${response.code}"
                        )
                    }
                    return null
                }
                val body = response.body.string()
                if (!body.trimStart().startsWith("{")) {
                    CalabiYauApplication.instanceOrNull?.let {
                        CrashContextStore.recordWikiRequest(
                            it,
                            "WikiEngine.safeGet",
                            url,
                            outcome = "non-json response"
                        )
                    }
                    return null
                }
                body
            }
        } catch (e: Exception) {
            CalabiYauApplication.instanceOrNull?.let {
                CrashContextStore.recordWikiRequest(
                    it,
                    "WikiEngine.safeGet",
                    url,
                    outcome = "exception=${e::class.java.simpleName}"
                )
            }
            null
        }
    }

    // === 委托给 WikiEngineCore 的公共 API ===

    suspend fun getAllCharacterNames(): List<String> =
        WikiEngineCore.getAllCharacterNames(::fetchStringSimple, jsonParser)

    suspend fun searchAndGroupCharacters(keyword: String, voiceOnly: Boolean = true): List<CharacterGroup> =
        WikiEngineCore.searchAndGroupCharacters(keyword, voiceOnly, client, ::fetchStringSimple, jsonParser, nameCache)

    suspend fun scanCategoryTree(rootCategory: String): List<String> =
        WikiEngineCore.scanCategoryTree(rootCategory, ::fetchStringSimple, jsonParser)

    suspend fun fetchFilesInCategory(category: String, audioOnly: Boolean = true): List<Pair<String, String>> =
        WikiEngineCore.fetchFilesInCategory(category, audioOnly, ::fetchStringSimple, jsonParser)

    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>,
        saveDir: File,
        maxConcurrency: Int,
        onLog: (String) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ) = WikiEngineCore.downloadSpecificFiles(files, saveDir, maxConcurrency, onLog, onProgress, ::downloadFile)

    // === Android 专有功能 ===

    /**
     * Android 专有：带日志回调的文件搜索。
     */
    suspend fun searchFiles(
        keyword: String,
        audioOnly: Boolean,
        onLog: ((String) -> Unit)? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        fun matchesFilter(url: String, mime: String?): Boolean {
            if (!audioOnly) return true
            val clean = url.substringBefore('?')
            return mime?.startsWith("audio/") == true ||
                clean.endsWith(".wav") || clean.endsWith(".mp3") || clean.endsWith(".ogg")
        }
        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val url = buildWikiUrl(WikiEngineCore.API_BASE_URL, "action" to "query", "list" to "allimages", "aiprefix" to keyword, "aiprop" to "url|mime", "ailimit" to "500", "format" to "json", *(if (aicontinue != null) arrayOf("aicontinue" to aicontinue) else emptyArray()))
            val json = fetchString(url, onError = { onLog?.invoke("[文件搜索] 前缀搜索: $it") })
            if (json == null) { onLog?.invoke("[文件搜索] 前缀搜索请求失败"); break }
            if (json.trimStart().startsWith("<")) { onLog?.invoke("[文件搜索] 前缀搜索被WAF拦截"); break }
            try {
                val res = jsonParser.decodeFromString<AiResponse>(json)
                res.query?.allimages?.forEach { item ->
                    if (item.url != null && matchesFilter(item.url!!, item.mime)) path1[item.name] = item.url!!
                }
                aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
            } catch (e: Exception) { onLog?.invoke("[文件搜索] 前缀搜索解析失败: ${e.message}"); break }
        } while (aicontinue != null)
        onLog?.invoke("[文件搜索] 前缀搜索找到 ${path1.size} 个文件")

        val path2 = LinkedHashMap<String, String>()
        var sroffset = 0
        do {
            val url = buildWikiUrl(WikiEngineCore.API_BASE_URL, "action" to "query", "list" to "search", "srsearch" to keyword, "srnamespace" to "6", "format" to "json", "srlimit" to "100", "sroffset" to sroffset.toString())
            val json = fetchString(url, onError = { onLog?.invoke("[文件搜索] 全文搜索: $it") })
            if (json == null) { onLog?.invoke("[文件搜索] 全文搜索请求失败"); break }
            if (json.trimStart().startsWith("<")) { onLog?.invoke("[文件搜索] 全文搜索被WAF拦截"); break }
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|")
                    val infoUrl = buildWikiUrl(WikiEngineCore.API_BASE_URL, "action" to "query", "titles" to titlesParam, "prop" to "imageinfo", "iiprop" to "url|mime", "format" to "json")
                    val infoJson = fetchString(infoUrl, onError = { onLog?.invoke("[文件搜索] imageinfo请求: $it") }) ?: return@forEach
                    try {
                        val infoRes = jsonParser.decodeFromString<WikiResponse>(infoJson)
                        infoRes.query?.pages?.values?.forEach { page ->
                            val info = page.imageinfo?.firstOrNull()
                            if (info?.url != null && matchesFilter(info.url!!, info.mime)) {
                                path2.putIfAbsent(page.title.replace(filePrefixRegex, ""),
                                    info.url!!
                                )
                            }
                        }
                    } catch (e: Exception) { onLog?.invoke("[文件搜索] imageinfo解析失败: ${e.message}") }
                }
                val nextOffset = res.continuation?.get("sroffset")?.jsonPrimitive?.content?.toIntOrNull()
                if (nextOffset == null || nextOffset <= sroffset) break
                sroffset = nextOffset
            } catch (e: Exception) { onLog?.invoke("[文件搜索] 全文搜索解析失败: ${e.message}"); break }
        } while (path2.size < 1000)
        onLog?.invoke("[文件搜索] 全文搜索找到 ${path2.size} 个文件")

        val merged = LinkedHashMap<String, String>(path1)
        path2.forEach { (k, v) -> merged.putIfAbsent(k, v) }
        val seenUrls = HashSet<String>()
        merged.entries.filter { seenUrls.add(it.value) }.map { (k, v) -> k to v }
    }

    /**
     * 通用：批量获取文件名→图片 URL 映射。
     * @param fileNames 不含 "文件:" 前缀的文件名列表（如 "壁纸1.png"）
     * @return Map<fileName, url>
     */
    suspend fun fetchImageUrls(fileNames: List<String>): Map<String, String> {
        return fetchBatchImageUrls(fileNames, fetchJson = ::fetchStringSimple)
    }

    /**
     * Android 专有：批量获取角色头像 URL（基于 fetchImageUrls）。
     */
    suspend fun fetchCharacterAvatars(characterNames: List<String>): Map<String, String> {
        if (characterNames.isEmpty()) return emptyMap()
        val fileNames = characterNames.map { "${it}头像.png" }
        val urlMap = fetchImageUrls(fileNames)
        // 将 "xxx头像.png" → url 映射回 "xxx" → url
        return urlMap.mapKeys { (k, _) -> k.removeSuffix("头像.png") }
    }

    // === 平台特定的网络方法 ===

    /** 简单版本 fetchString（无日志），供 WikiEngineCore 委托使用 */
    private suspend fun fetchStringSimple(url: String): String? = fetchString(url, onError = null)

    /** 带错误回调的 fetchString */
    private suspend fun fetchString(url: String, onError: ((String) -> Unit)? = null): String? = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            try {
                val response = client.executeGet(url)
                val result = response.use { if (it.isSuccessful) it.body.string() else { onError?.invoke("HTTP ${it.code}: ${it.message}"); null } }
                if (result != null) return@withContext result
            } catch (e: Exception) {
                onError?.invoke("网络异常: ${e.javaClass.simpleName}: ${e.message}")
            }
            if (attempt == 0) Thread.sleep(500)
        }
        null
    }

    private fun downloadFile(url: String, targetFile: File) {
        if (targetFile.exists() && targetFile.length() > 0) return
        client.executeGet(url).use { response ->
            if (response.isSuccessful) {
                val tmp = File(targetFile.parent, targetFile.name + ".tmp")
                response.bodyToFile(tmp)
                if (tmp.exists()) tmp.renameTo(targetFile)
            }
        }
    }
}
