package data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object WikiEngine {

    // UA 池：按浏览器类型分组，各自匹配对应的请求头特征
    private data class BrowserProfile(
        val userAgent: String,
        val accept: String,
        val acceptLanguage: String,
        val secFetchDest: String = "document",
        val secFetchMode: String = "navigate",
        val secFetchSite: String = "same-origin",
        val secChUa: String? = null,          // Chrome/Edge 专有
        val secChUaMobile: String = "?0",
        val secChUaPlatform: String? = null
    )

    private val BROWSER_PROFILES = listOf(
        // Chrome 131 / Windows
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Windows\""
        ),
        // Chrome 131 / macOS
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"macOS\""
        ),
        // Edge 131 / Windows
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            acceptLanguage = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            secChUa = "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Windows\""
        ),
        // Firefox 133 / Windows
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            acceptLanguage = "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2",
            secFetchDest = "document",
            secFetchMode = "navigate",
            secFetchSite = "none"
            // Firefox 不发送 sec-ch-ua
        ),
        // Firefox 133 / Linux
        BrowserProfile(
            userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            acceptLanguage = "zh-CN,zh;q=0.9,en-US;q=0.7,en;q=0.5",
            secFetchDest = "document",
            secFetchMode = "navigate",
            secFetchSite = "none"
        )
    )

    // 可注入 Cookie 的 CookieJar 实现
    private val cookieStore = ConcurrentHashMap<String, CopyOnWriteArrayList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val list = cookieStore.getOrPut(key) { CopyOnWriteArrayList() }
            // 更新或追加
            cookies.forEach { newCookie ->
                list.removeIf { it.name == newCookie.name && it.path == newCookie.path }
                list.add(newCookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host]?.filter { it.matches(url) } ?: emptyList()
        }
    }

    /**
     * 向指定域的 CookieJar 注入 Cookie 列表。
     * 由 WikiCookieManager 调用，外部请勿直接调用。
     */
    fun injectCookies(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        val list = cookieStore.getOrPut(key) { CopyOnWriteArrayList() }
        // 先移除同名 Cookie，再追加
        cookies.forEach { newCookie ->
            list.removeIf { it.name == newCookie.name }
        }
        list.addAll(cookies)
    }

    // 暴露给外部的 Client
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // 核心拦截器：每次请求随机选一个 Profile，动态匹配完整请求头
        .addInterceptor { chain ->
            val profile = BROWSER_PROFILES.random()
            val req = chain.request().newBuilder()
                .header("User-Agent", profile.userAgent)
                .header("Accept", profile.accept)
                .header("Accept-Language", profile.acceptLanguage)
                // Accept-Encoding 由 OkHttp 自动管理（只声明它能解压的格式），不手动设置
                .header("Referer", "https://wiki.biligame.com/klbq/")
                .header("Sec-Fetch-Dest", profile.secFetchDest)
                .header("Sec-Fetch-Mode", profile.secFetchMode)
                .header("Sec-Fetch-Site", profile.secFetchSite)
                // Sec-Fetch-User: ?1 仅用于用户主动触发的导航，API 请求不应携带
                .apply {
                    // Chrome/Edge 专有头，Firefox 不发送
                    if (profile.secChUa != null) {
                        header("Sec-CH-UA", profile.secChUa)
                        header("Sec-CH-UA-Mobile", profile.secChUaMobile)
                        header("Sec-CH-UA-Platform", profile.secChUaPlatform ?: "\"Windows\"")
                    }
                }
                .build()
            chain.proceed(req)
        }
        // 重试拦截器：遇到风控时指数退避重试
        .addInterceptor { chain ->
            var response = chain.proceed(chain.request())
            var tryCount = 0
            val maxRetries = 3
            while (!response.isSuccessful
                && tryCount < maxRetries
                && response.code in setOf(429, 403, 503)
            ) {
                tryCount++
                response.close()
                // 指数退避 + 随机抖动，避免多线程同时重试
                val backoff = (1000L shl tryCount) + Random.nextLong(0, 500)
                Thread.sleep(backoff)
                response = chain.proceed(chain.request())
            }
            response
        }
        .build()

    private val jsonParser = SharedJson

    // 角色名缓存
    private val nameCache = WikiEngineCore.CharacterNameCache()

    // === 委托给 WikiEngineCore 的公共 API ===

    suspend fun searchFiles(keyword: String, audioOnly: Boolean): List<Pair<String, String>> =
        WikiEngineCore.searchFiles(keyword, audioOnly, ::fetchString, jsonParser)

    suspend fun fetchFilesInCategory(category: String, audioOnly: Boolean = true): List<Pair<String, String>> =
        WikiEngineCore.fetchFilesInCategory(category, audioOnly, ::fetchString, jsonParser)

    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>,
        saveDir: File,
        maxConcurrency: Int,
        onLog: (String) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ) = WikiEngineCore.downloadSpecificFiles(files, saveDir, maxConcurrency, onLog, onProgress, ::downloadFile)

    suspend fun searchAndGroupCharacters(keyword: String, voiceOnly: Boolean = true): List<CharacterGroup> =
        WikiEngineCore.searchAndGroupCharacters(keyword, voiceOnly, client, ::fetchString, jsonParser, nameCache)

    suspend fun scanCategoryTree(rootCategory: String): List<String> =
        WikiEngineCore.scanCategoryTree(rootCategory, ::fetchString, jsonParser)

    suspend fun getAllCharacterNames(): List<String> =
        WikiEngineCore.getAllCharacterNames(::fetchString, jsonParser)

    fun sanitizeFileName(name: String) = data.sanitizeFileName(name)

    // === Desktop 专有功能 ===

    /** 判断某个名字是否在角色名缓存表中，会自动触发缓存加载。 */
    suspend fun isCharacterNameValid(name: String): Boolean {
        nameCache.ensure { getAllCharacterNames() }
        return nameCache.cache.isEmpty() || nameCache.cache.contains(name)
    }

    // 头像与图片加载委托给 ImageLoader
    suspend fun getCharacterAvatarUrl(characterName: String): String? {
        nameCache.ensure { getAllCharacterNames() }
        return ImageLoader.getCharacterAvatarUrl(client, WikiEngineCore.API_BASE_URL, jsonParser, characterName)
    }

    suspend fun loadNetworkImage(url: String): ImageBitmap? =
        ImageLoader.loadNetworkImage(client, url)

    suspend fun loadRawBytes(url: String): ByteArray? =
        ImageLoader.loadRawBytes(client, url)

    // === 平台特定的网络方法 ===

    private suspend fun fetchString(url: String): String? = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            try {
                val result = client.newCall(Request.Builder().url(url).build()).execute()
                    .use { if (it.isSuccessful) it.body.string() else null }
                if (result != null) return@withContext result
            } catch (_: Exception) { }
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
                if (tmp.exists()) Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}