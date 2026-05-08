package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.CalabiYauApplication
import com.nekolaska.calabiyau.CrashContextStore
import data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object WikiEngine {

    // UA 池：移动端浏览器配置，每次请求随机选取
    private data class MobileBrowserProfile(
        val userAgent: String,
        val accept: String,
        val acceptLanguage: String,
        val secChUa: String? = null,          // Chromium 系专有
        val secChUaMobile: String = "?1",     // 移动端标记
        val secChUaPlatform: String? = null
    )

    private val MOBILE_PROFILES = listOf(
        // Chrome 131 / Android 14 (Pixel)
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Android\""
        ),
        // Chrome 131 / Android 15 (Samsung)
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; Android 15; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Android\""
        ),
        // Chrome 131 / Android 14 (Xiaomi)
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; Android 14; 23127PN0CC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Android\""
        ),
        // Edge 131 / Android 14
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 EdgA/131.0.0.0",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            secChUa = "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Android\""
        ),
        // Firefox 133 / Android
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Android 14; Mobile; rv:133.0) Gecko/133.0 Firefox/133.0",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            acceptLanguage = "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2"
            // Firefox 不发送 sec-ch-ua 系列头
        ),
        // Samsung Internet 26 / Android 15
        MobileBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; Android 15; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8",
            secChUa = "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Samsung Internet\";v=\"26\"",
            secChUaPlatform = "\"Android\""
        )
    )

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val profile = MOBILE_PROFILES.random()
            val original = chain.request()
            val req = original.newBuilder()
                .apply {
                    if (original.header("User-Agent") == null) header("User-Agent", profile.userAgent)
                    if (original.header("Accept") == null) header("Accept", profile.accept)
                    if (original.header("Accept-Language") == null) header("Accept-Language", profile.acceptLanguage)
                    if (original.header("Referer") == null) header("Referer", "https://wiki.biligame.com/klbq/")
                    if (original.header("Sec-Fetch-Dest") == null) header("Sec-Fetch-Dest", "empty")
                    if (original.header("Sec-Fetch-Mode") == null) header("Sec-Fetch-Mode", "cors")
                    if (original.header("Sec-Fetch-Site") == null) header("Sec-Fetch-Site", "same-origin")
                }
                .apply {
                    if (profile.secChUa != null) {
                        if (original.header("Sec-CH-UA") == null) header("Sec-CH-UA", profile.secChUa)
                        if (original.header("Sec-CH-UA-Mobile") == null) header("Sec-CH-UA-Mobile", profile.secChUaMobile)
                        if (original.header("Sec-CH-UA-Platform") == null) header("Sec-CH-UA-Platform", profile.secChUaPlatform ?: "\"Android\"")
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
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
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
        val encoded = URLEncoder.encode(keyword, "UTF-8")

        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val cArg = if (aicontinue != null) "&aicontinue=${URLEncoder.encode(aicontinue, "UTF-8")}" else ""
            val url = "${WikiEngineCore.API_BASE_URL}?action=query&list=allimages&aiprefix=$encoded&aiprop=url|mime&ailimit=500&format=json$cArg"
            val json = fetchString(url, onError = { onLog?.invoke("[文件搜索] 前缀搜索: $it") })
            if (json == null) { onLog?.invoke("[文件搜索] 前缀搜索请求失败"); break }
            if (json.trimStart().startsWith("<")) { onLog?.invoke("[文件搜索] 前缀搜索被WAF拦截"); break }
            try {
                val res = jsonParser.decodeFromString<AiResponse>(json)
                res.query?.allimages?.forEach { item ->
                    if (item.url != null && matchesFilter(item.url, item.mime)) path1[item.name] = item.url
                }
                aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
            } catch (e: Exception) { onLog?.invoke("[文件搜索] 前缀搜索解析失败: ${e.message}"); break }
        } while (aicontinue != null)
        onLog?.invoke("[文件搜索] 前缀搜索找到 ${path1.size} 个文件")

        val path2 = LinkedHashMap<String, String>()
        var sroffset = 0
        do {
            val url = "${WikiEngineCore.API_BASE_URL}?action=query&list=search&srsearch=$encoded&srnamespace=6&format=json&srlimit=100&sroffset=$sroffset"
            val json = fetchString(url, onError = { onLog?.invoke("[文件搜索] 全文搜索: $it") })
            if (json == null) { onLog?.invoke("[文件搜索] 全文搜索请求失败"); break }
            if (json.trimStart().startsWith("<")) { onLog?.invoke("[文件搜索] 全文搜索被WAF拦截"); break }
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
                    val infoUrl = "${WikiEngineCore.API_BASE_URL}?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url|mime&format=json"
                    val infoJson = fetchString(infoUrl, onError = { onLog?.invoke("[文件搜索] imageinfo请求: $it") }) ?: return@forEach
                    try {
                        val infoRes = jsonParser.decodeFromString<WikiResponse>(infoJson)
                        infoRes.query?.pages?.values?.forEach { page ->
                            val info = page.imageinfo?.firstOrNull()
                            if (info?.url != null && matchesFilter(info.url, info.mime)) {
                                path2.putIfAbsent(page.title.replace(filePrefixRegex, ""), info.url)
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
    suspend fun fetchImageUrls(fileNames: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (fileNames.isEmpty()) return@withContext emptyMap()
        val result = ConcurrentHashMap<String, String>()
        fileNames.chunked(50).map { chunk ->
            async {
                val titlesParam = chunk.joinToString("|") { URLEncoder.encode("文件:$it", "UTF-8") }
                val url = "${WikiEngineCore.API_BASE_URL}?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url&format=json"
                val json = fetchStringSimple(url) ?: return@async
                try {
                    val res = jsonParser.decodeFromString<WikiResponse>(json)
                    res.query?.pages?.values?.forEach { page ->
                        val imageUrl = page.imageinfo?.firstOrNull()?.url ?: return@forEach
                        val name = page.title.replace(filePrefixRegex, "")
                        result[name] = imageUrl
                    }
                } catch (_: Exception) {}
            }
        }.awaitAll()
        result
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
                val response = client.newCall(Request.Builder().url(url).build()).execute()
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
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) {
                val tmp = File(targetFile.parent, targetFile.name + ".tmp")
                response.body.byteStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                if (tmp.exists()) tmp.renameTo(targetFile)
            }
        }
    }
}
