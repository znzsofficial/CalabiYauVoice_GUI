package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.CalabiYauApplication
import com.nekolaska.calabiyau.CrashContextStore
import com.nekolaska.calabiyau.core.network.SafeReadRetryInterceptor
import data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import util.awaitGet
import util.awaitGetToFile
import util.downloadResourceAtomically
import util.executeGet
import kotlin.time.Duration.Companion.milliseconds

object WikiEngine {

    private val browserProfile = WikiUserAgent.randomProfile(desktopMode = false)

    val client: OkHttpClient = OkHttpClient.Builder()
        // Mutations share this client; transport recovery must not silently replay them.
        .retryOnConnectionFailure(false)
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
        .addInterceptor(SafeReadRetryInterceptor())
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
                val root = runCatching { SharedJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
                if (root == null || root["error"] != null) {
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
        WikiEngineCore.searchAndGroupCharacters(keyword, voiceOnly, ::fetchStringSimple, jsonParser, nameCache)

    suspend fun scanCategoryTree(rootCategory: String): List<String> =
        WikiEngineCore.scanCategoryTree(rootCategory, ::fetchStringSimple, jsonParser)

    suspend fun scanCategoryTrees(roots: List<String>): List<String> =
        WikiEngineCore.scanCategoryTrees(roots, ::fetchStringSimple, jsonParser)

    suspend fun fetchFilesInCategory(category: String, audioOnly: Boolean = true): List<Pair<String, String>> =
        WikiEngineCore.fetchFilesInCategory(category, audioOnly, ::fetchStringSimple, jsonParser)

    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>,
        saveDir: File,
        maxConcurrency: Int,
        onLog: (String) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val app = checkNotNull(CalabiYauApplication.instanceOrNull) { "Application is not initialized" }
        WikiEngineCore.downloadSpecificFiles(files, saveDir, maxConcurrency, onLog, onProgress, ::downloadFile,
            manifestFile = privateDownloadManifestFile(app.filesDir, saveDir))
    }

    // === Android 专有功能 ===

    /**
     * Android 专有：带日志回调的文件搜索。
     */
    suspend fun searchFiles(
        keyword: String,
        audioOnly: Boolean,
        onLog: ((String) -> Unit)? = null
    ): List<Pair<String, String>> = WikiEngineCore.searchFiles(keyword, audioOnly,
        { url -> fetchString(url) { onLog?.invoke("[文件搜索] $it") } }, jsonParser)

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
                val response = client.awaitGet(url)
                val result = response.use { if (it.isSuccessful) it.body.string() else { onError?.invoke("HTTP ${it.code}: ${it.message}"); null } }
                if (result != null) return@withContext result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError?.invoke("网络异常: ${e.javaClass.simpleName}: ${e.message}")
            }
            if (attempt == 0) delay(500.milliseconds)
        }
        null
    }

    private suspend fun downloadFile(url: String, targetFile: File) {
        downloadResourceAtomically(url, targetFile,
            fetch = { source, tmp -> client.awaitGetToFile(source, tmp) })
    }
}
