package data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object WikiEngine {
    private const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"

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

    // 暴露给外部的 Client
    val client: OkHttpClient = OkHttpClient.Builder()
        // 自动管理 Cookie
        .cookieJar(JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }))
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

    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // === 数据结构 ===
    data class CharacterGroup(
        val characterName: String,
        val rootCategory: String,
        val subCategories: List<String> // 初步归类的列表
    )

    // === API 模型 ===
    @Serializable
    data class WikiResponse(
        val query: WikiQuery? = null,
        // 使用 JsonElement 以兼容数字类型的 offset
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    @Serializable
    data class WikiQuery(
        val search: List<SearchItem>? = null,
        val categorymembers: List<CategoryMember>? = null,
        val pages: Map<String, WikiPage>? = null
    )

    @Serializable
    data class SearchItem(val title: String)

    @Serializable
    data class CategoryMember(val ns: Int, val title: String)

    @Serializable
    data class WikiPage(val title: String, val imageinfo: List<ImageInfo>? = null)

    @Serializable
    data class ImageInfo(val url: String? = null, val mime: String? = null)

    // allimages API 专用模型（避免在循环体内重复定义局部类）
    @Serializable
    private data class AiItem(val name: String, val url: String? = null, val mime: String? = null)

    @Serializable
    private data class AiQuery(val allimages: List<AiItem>? = null)

    @Serializable
    private data class AiResponse(
        val query: AiQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    /**
     * 在 File 命名空间（ns=6）中搜索文件，返回 List<文件名 to CDN URL>。
     *
     * 策略：
     * 1. 先用 list=allimages&aiprefix 做前缀匹配（精确、完整翻页）
     * 2. 再用 list=search&srnamespace=6 做全文匹配（补充前缀未命中的结果）
     * 两路各自独立翻页，最终合并去重后返回。
     */
    suspend fun searchFiles(keyword: String, audioOnly: Boolean): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        fun matchesFilter(url: String, mime: String?): Boolean {
            if (!audioOnly) return true
            val clean = url.substringBefore('?')
            return mime?.startsWith("audio/") == true || clean.endsWith(".wav") || clean.endsWith(".mp3")
        }
        val encoded = URLEncoder.encode(keyword, "UTF-8")

        // --- 路径 1：allimages 前缀搜索（完整翻页）---
        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val cArg = if (aicontinue != null) "&aicontinue=${URLEncoder.encode(aicontinue, "UTF-8")}" else ""
            val url = "$API_BASE_URL?action=query&list=allimages&aiprefix=$encoded" +
                    "&aiprop=url|mime&ailimit=500&format=json$cArg"
            val json = fetchString(url) ?: break
            if (json.trimStart().startsWith("<")) break
            try {
                val res = jsonParser.decodeFromString<AiResponse>(json)
                res.query?.allimages?.forEach { item ->
                    if (item.url != null && matchesFilter(item.url, item.mime)) path1[item.name] = item.url
                }
                aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
            } catch (_: Exception) { break }
        } while (aicontinue != null)

        // --- 路径 2：search 全文搜索（补充未被前缀命中的结果）---
        val path2 = LinkedHashMap<String, String>()
        var sroffset = 0
        do {
            val url = "$API_BASE_URL?action=query&list=search&srsearch=$encoded&srnamespace=6" +
                    "&format=json&srlimit=100&sroffset=$sroffset"
            val json = fetchString(url) ?: break
            if (json.trimStart().startsWith("<")) break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break

                // 批量查询 imageinfo（每次最多 50 个标题）
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
                    val infoUrl = "$API_BASE_URL?action=query&titles=$titlesParam" +
                            "&prop=imageinfo&iiprop=url|mime&format=json"
                    val infoJson = fetchString(infoUrl) ?: return@forEach
                    try {
                        val infoRes = jsonParser.decodeFromString<WikiResponse>(infoJson)
                        infoRes.query?.pages?.values?.forEach { page ->
                            val info = page.imageinfo?.firstOrNull()
                            if (info?.url != null && matchesFilter(info.url, info.mime)) {
                                path2.putIfAbsent(page.title.replace(filePrefixRegex, ""), info.url)
                            }
                        }
                    } catch (_: Exception) {}
                }

                val nextOffset = res.continuation?.get("sroffset")?.jsonPrimitive?.content?.toIntOrNull()
                if (nextOffset == null || nextOffset <= sroffset) break
                sroffset = nextOffset
            } catch (_: Exception) { break }
        } while (path2.size < 1000)

        // 路径1优先，路径2补充未命中的条目
        val merged = LinkedHashMap<String, String>(path1)
        path2.forEach { (k, v) -> merged.putIfAbsent(k, v) }

        // 按 URL 去重：不同文件名可能指向同一个 CDN 地址，下游用 URL 作为选择标识需要唯一
        val seenUrls = HashSet<String>()
        merged.entries.filter { seenUrls.add(it.value) }.map { (k, v) -> k to v }
    }
    suspend fun fetchFilesInCategory(category: String, audioOnly: Boolean = true): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        getCategoryFilesDetail(category, audioOnly)
    }

    /**
     * 直接下载给定的文件列表 (跳过分类扫描步骤)
     */
    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>, // List<Name, Url>
        saveDir: File,
        maxConcurrency: Int,
        onLog: (String) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = files.size
        if (total == 0) return@withContext
        if (!saveDir.exists()) saveDir.mkdirs()

        val semaphore = Semaphore(maxConcurrency)
        val counter = AtomicInteger(0)

        files.map { (name, url) ->
            launch(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    var safeName = sanitizeFileName(name)
                    // MediaWiki 文件名通常自带扩展名；若缺失则从 URL 中提取
                    if (!safeName.contains('.')) {
                        val ext = url.substringBefore('?').substringAfterLast('.', "")
                            .lowercase().takeIf { it.isNotEmpty() }
                        if (ext != null) safeName += ".$ext"
                    }
                    val targetFile = File(saveDir, safeName)

                    downloadFile(url, targetFile)

                    val current = counter.incrementAndGet()
                    onProgress(current, total, safeName)
                } catch (e: Exception) {
                    onLog("[错误] ${e.message}")
                } finally {
                    semaphore.release()
                }
            }
        }.joinAll()
    }

    /**
     * 1. 搜索并分组角色
     * @param voiceOnly true = 仅保留以"语音"结尾的分类（原有行为）；false = 返回所有匹配分类
     */
    suspend fun searchAndGroupCharacters(keyword: String, voiceOnly: Boolean = true): List<CharacterGroup> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$API_BASE_URL?action=query&list=search&srsearch=$encoded&srnamespace=14&format=json&srlimit=200"

        repeat(3) { attempt ->
            try {
                val responseString = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }

                if (responseString.trimStart().startsWith("<")) throw IOException("Blocked by WAF")

                val rawList = jsonParser.decodeFromString<WikiResponse>(responseString).query?.search?.map { it.title }
                    ?: emptyList()
                val filteredList = if (voiceOnly) rawList.filter { it.endsWith("语音") } else rawList
                val result = groupCategories(filteredList, voiceOnly)
                if (result.isNotEmpty()) return@withContext result

            } catch (_: Exception) {
                if (attempt < 2) Thread.sleep(1000L + Random.nextLong(2000))
            }
        }

        return@withContext emptyList()
    }

    /**
     * 2. 扫描指定分类的所有子分类 (展开树)，按层并发发出请求
     */
    suspend fun scanCategoryTree(rootCategory: String): List<String> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap.newKeySet<String>()
        found.add(rootCategory)
        var currentLayer = listOf(rootCategory)

        while (currentLayer.isNotEmpty()) {
            // 当前层所有节点并发查子分类
            val nextLayer = currentLayer.map { cat ->
                async { getCategoryMembers(cat, 14) }
            }.awaitAll().flatten().filter { found.add(it) } // add() 返回 false 说明已访问，过滤掉
            currentLayer = nextLayer
        }

        val list = found.toMutableList()
        list.sort()
        if (list.remove(rootCategory)) list.add(0, rootCategory)
        list
    }

    // === 内部工具函数 ===

    private val categoryPrefixRegex = Regex("^(Category:|分类:)")
    private val filePrefixRegex = Regex("^(File:|文件:)")

    private suspend fun groupCategories(rawList: List<String>, voiceOnly: Boolean = true): List<CharacterGroup> {
        ensureCharacterNameCache()
        val cleanMap = rawList.associateWith { it.replace(categoryPrefixRegex, "") }
        val sortedItems = cleanMap.entries.sortedBy { it.value.length }
        val groups = mutableListOf<CharacterGroup>()
        val assigned = mutableSetOf<String>()

        for ((originalName, cleanName) in sortedItems) {
            if (assigned.contains(originalName)) continue

            val coreName = if (voiceOnly) cleanName.removeSuffix("语音") else cleanName
            if (coreName.isBlank()) continue

            val familyMembers = if (voiceOnly) {
                rawList.filter { raw ->
                    val cl = cleanMap[raw]!!
                    cl.startsWith(coreName) && cl.endsWith("语音")
                }
            } else {
                rawList.filter { raw -> cleanMap[raw]!!.startsWith(coreName) }
            }

            // 优先从角色名缓存表中精确匹配，再做前缀匹配（O(1) 精确 + O(n) 前缀兜底）
            val resolvedName = if (characterNameCache.isNotEmpty()) {
                when {
                    characterNameCache.contains(coreName) -> coreName
                    else -> characterNameCache.firstOrNull { coreName.startsWith(it) }
                        ?: characterNameCache.firstOrNull { it.startsWith(coreName) }
                        ?: coreName
                }
            } else {
                coreName
            }

            groups.add(CharacterGroup(resolvedName, originalName, familyMembers))
            assigned.addAll(familyMembers)
        }
        return groups.sortedBy { it.characterName }
    }

    /**
     * 通用翻页帮助：持续调用 categorymembers API 直到无更多数据，收集所有 title。
     */
    private suspend fun fetchPagedCategoryMembers(
        category: String,
        namespace: Int,
        cmtype: String  // "subcat" | "file" | "page"
    ): List<String> {
        val list = mutableListOf<String>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&cmcontinue=$token" else ""
            val url = "$API_BASE_URL?action=query&list=categorymembers&cmtitle=$encoded" +
                    "&cmnamespace=$namespace&cmtype=$cmtype&format=json&cmlimit=500$cArg"
            val json = fetchString(url) ?: break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.categorymembers?.forEach { list.add(it.title) }
                token = res.continuation?.get("cmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) {
                break
            }
        } while (token != null)
        return list
    }

    private suspend fun getCategoryMembers(category: String, namespace: Int): List<String> =
        fetchPagedCategoryMembers(category, namespace, if (namespace == 14) "subcat" else "file")

    // 获取指定分类下的所有页面标题（namespace=0，即主命名空间的角色页）
    private suspend fun getCharacterNames(categoryName: String): List<String> =
        fetchPagedCategoryMembers(categoryName, 0, "page")

    private suspend fun getCategoryFilesDetail(category: String, audioOnly: Boolean = true): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&gcmcontinue=$token" else ""
            val url =
                "$API_BASE_URL?action=query&generator=categorymembers&gcmtitle=$encoded&gcmnamespace=6&prop=imageinfo&iiprop=url|mime&format=json&gcmlimit=500$cArg"
            val json = fetchString(url) ?: break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.pages?.values?.forEach { p ->
                    val i = p.imageinfo?.firstOrNull()
                    if (i?.url != null) {
                        val cleanUrl = i.url.substringBefore('?')
                        val isAudio = i.mime?.startsWith("audio/") == true
                                || cleanUrl.endsWith(".wav")
                                || cleanUrl.endsWith(".mp3")
                        if (!audioOnly || isAudio) {
                            list.add(p.title.replace(filePrefixRegex, "") to i.url)
                        }
                    }
                }
                token = res.continuation?.get("gcmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) {
                break
            }
        } while (token != null)
        // 按 URL 去重，保证下游以 URL 作为选择标识时唯一
        val seenUrls = HashSet<String>()
        return list.filter { seenUrls.add(it.second) }
    }

    // 获取所有角色名（合并两个分类，去重，并行请求）
    suspend fun getAllCharacterNames(): List<String> = withContext(Dispatchers.IO) {
        val (a, b) = awaitAll(
            async { getCharacterNames("Category:晶源体") },
            async { getCharacterNames("Category:超弦体") }
        )
        (a + b).toSortedSet().toList()
    }

    // 角色名缓存表（从晶源体/超弦体分类获取）
    private val characterNameCache = ConcurrentHashMap.newKeySet<String>()
    private val characterNameCacheLoading = AtomicBoolean(false)

    /**
     * 按需加载角色名缓存，保证只发起一次网络请求。
     * 内部调用，外部不再需要手动预热。
     */
    private suspend fun ensureCharacterNameCache() = withContext(Dispatchers.IO) {
        if (characterNameCache.isNotEmpty()) return@withContext
        if (!characterNameCacheLoading.compareAndSet(false, true)) return@withContext
        try {
            val names = getAllCharacterNames()
            characterNameCache.addAll(names)
        } finally {
            if (characterNameCache.isEmpty()) characterNameCacheLoading.set(false)
        }
    }

    /** 判断某个名字是否在角色名缓存表中，会自动触发缓存加载。 */
    suspend fun isCharacterNameValid(name: String): Boolean {
        ensureCharacterNameCache()
        // 加载后若仍为空（网络失败），降级放行
        return characterNameCache.isEmpty() || characterNameCache.contains(name)
    }

    // 头像与图片加载委托给 ImageLoader
    suspend fun getCharacterAvatarUrl(characterName: String): String? {
        ensureCharacterNameCache()
        return ImageLoader.getCharacterAvatarUrl(client, API_BASE_URL, jsonParser, characterName)
    }

    suspend fun loadNetworkImage(url: String): ImageBitmap? =
        ImageLoader.loadNetworkImage(client, url)

    suspend fun loadRawBytes(url: String): ByteArray? =
        ImageLoader.loadRawBytes(client, url)

    private suspend fun fetchString(url: String): String? = withContext(Dispatchers.IO) {
        // 最多尝试 2 次，应对偶发网络波动
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

    private val sanitizeRegex = Regex("[\\\\/:*?\"<>|]")
    fun sanitizeFileName(name: String) = name.replace(sanitizeRegex, "_").trim()
}