package data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayDeque
import kotlin.random.Random

object WikiEngine {
    private const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_CACHE_ENTRIES = 256

    // UA池
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    )
    private val currentUA = USER_AGENTS.random()

    // 暴露给外部的 Client
    val client: OkHttpClient = OkHttpClient.Builder()
        // 自动管理 Cookie
        .cookieJar(JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }))
        // 增加超时时间
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // 核心拦截器：伪装头信息
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", currentUA)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://wiki.biligame.com/klbq/")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        // 重试拦截器：遇到风控歇一会儿
        .addInterceptor { chain ->
            var response = chain.proceed(chain.request())
            var tryCount = 0
            val maxLimit = 3

            // 如果遇到 429 (Too Many Requests) 或 503 (Service Unavailable)，尝试重试
            while (!response.isSuccessful && (response.code == 429 || response.code == 503 || response.code == 403) && tryCount < maxLimit) {
                tryCount++
                response.close() // 关闭旧响应

                // 指数退避：第一次歇 2s，第二次歇 4s...
                val sleepTime = 2000L * tryCount + Random.nextLong(500)
                //println("触发风控 (${response.code})，等待 ${sleepTime}ms 后重试 ($tryCount/$maxLimit)...")
                Thread.sleep(sleepTime)

                // 重新发起请求
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

    /**
     * 在 File 命名空间（ns=6）中搜索文件，返回 List<文件名 to CDN URL>。
     *
     * 策略：
     * 1. 先用 list=allimages&aiprefix 做前缀匹配（精确、完整翻页）
     * 2. 同时用 list=search&srnamespace=6 做全文匹配（补充前缀未命中的结果）
     * 两路结果去重后返回。
     */
    suspend fun searchFiles(keyword: String, audioOnly: Boolean): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, String>() // name -> url，保持顺序、自动去重

        // --- 路径 1：allimages 前缀搜索（完整翻页）---
        run {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            var aicontinue: String? = null
            do {
                val cArg = if (aicontinue != null) "&aicontinue=${URLEncoder.encode(aicontinue!!, "UTF-8")}" else ""
                val url = "$API_BASE_URL?action=query&list=allimages&aiprefix=$encoded" +
                        "&aiprop=url|mime&ailimit=500&format=json$cArg"
                val json = fetchString(url) ?: break
                if (json.trimStart().startsWith("<")) break
                try {
                    @Serializable data class AiItem(val name: String, val url: String? = null, val mime: String? = null)
                    @Serializable data class AiQuery(val allimages: List<AiItem>? = null)
                    @Serializable data class AiResponse(
                        val query: AiQuery? = null,
                        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
                    )
                    val res = jsonParser.decodeFromString<AiResponse>(json)
                    res.query?.allimages?.forEach { item ->
                        if (item.url != null) {
                            val isAudio = item.mime?.startsWith("audio/") == true
                                    || item.url.endsWith(".wav") || item.url.endsWith(".mp3")
                            if (!audioOnly || isAudio) {
                                result[item.name] = item.url
                            }
                        }
                    }
                    aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
                } catch (_: Exception) { break }
            } while (aicontinue != null)
        }

        // --- 路径 2：search 全文搜索（补充未被前缀命中的结果）---
        run {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
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
                                if (info?.url != null) {
                                    val isAudio = info.mime?.startsWith("audio/") == true
                                            || info.url.endsWith(".wav") || info.url.endsWith(".mp3")
                                    if (!audioOnly || isAudio) {
                                        val name = page.title.replace(Regex("^(File:|文件:)"), "")
                                        result.putIfAbsent(name, info.url)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // sroffset 翻页
                    val nextOffset = res.continuation?.get("sroffset")?.jsonPrimitive?.content?.toIntOrNull()
                    if (nextOffset == null || nextOffset <= sroffset) break
                    sroffset = nextOffset
                } catch (_: Exception) { break }
            } while (result.size < 1000)
        }

        result.map { (name, url) -> name to url }
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
                    if (!safeName.contains(".")) {
                        if (url.endsWith(".ogg")) safeName += ".ogg"
                        else if (url.endsWith(".mp3")) safeName += ".mp3"
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

        var retryCount = 0
        var resultList: List<CharacterGroup> = emptyList()

        while (retryCount < 3) {
            try {
                val request = Request.Builder().url(url).build()
                val responseString = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }

                // HTML 检测
                if (responseString.trimStart().startsWith("<")) {
                    throw IOException("Blocked by WAF")
                }

                val rawList = jsonParser.decodeFromString<WikiResponse>(responseString).query?.search?.map { it.title }
                    ?: emptyList()
                // voiceOnly 模式下只保留"语音"分类，否则全量传入
                val filteredList = if (voiceOnly) rawList.filter { it.endsWith("语音") } else rawList

                resultList = groupCategories(filteredList, voiceOnly)
                if (resultList.isNotEmpty()) break

            } catch (_: Exception) {
                retryCount++
                Thread.sleep(1000L + Random.nextLong(2000))
            }
        }

        return@withContext resultList
    }

    /**
     * 2. 扫描指定分类的所有子分类 (展开树)
     */
    suspend fun scanCategoryTree(rootCategory: String): List<String> = withContext(Dispatchers.IO) {
        val foundCategories = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        val queue = ArrayDeque<String>()
        queue.add(rootCategory)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!foundCategories.add(current)) continue

            // 获取子分类
            getCategoryMembers(current, 14).forEach {
                if (!foundCategories.contains(it)) queue.add(it)
            }
        }
        // 主分类排在前面
        val list = foundCategories.toMutableList()
        list.sort()
        // 简单把主分类提出来放第一个(如果存在)
        if (list.remove(rootCategory)) list.add(0, rootCategory)
        return@withContext list
    }

    // === 内部工具函数 ===

    private fun groupCategories(rawList: List<String>, voiceOnly: Boolean = true): List<CharacterGroup> {
        val cleanMap = rawList.associateWith { it.replace(Regex("^(Category:|分类:)"), "") }
        val sortedItems = cleanMap.entries.sortedBy { it.value.length }
        val groups = mutableListOf<CharacterGroup>()
        val assigned = mutableSetOf<String>()

        for ((originalName, cleanName) in sortedItems) {
            if (assigned.contains(originalName)) continue

            // voiceOnly 模式：coreName = 去掉"语音"后缀；通用模式：coreName = 整个 cleanName
            val coreName = if (voiceOnly) cleanName.removeSuffix("语音") else cleanName
            if (coreName.isBlank()) continue

            val familyMembers = if (voiceOnly) {
                // 原有逻辑：只归并以 coreName 开头且以"语音"结尾的分类
                rawList.filter { raw ->
                    val cl = cleanMap[raw]!!
                    cl.startsWith(coreName) && cl.endsWith("语音")
                }
            } else {
                // 通用模式：归并所有以 coreName 开头的分类（不限制后缀）
                rawList.filter { raw -> cleanMap[raw]!!.startsWith(coreName) }
            }

            // 优先从角色名缓存表中找到与 coreName 完全匹配或最接近前缀匹配的真实名称
            val resolvedName = if (characterNameCache.isNotEmpty()) {
                characterNameCache.firstOrNull { it == coreName }
                    ?: characterNameCache.firstOrNull { coreName.startsWith(it) }
                    ?: characterNameCache.firstOrNull { it.startsWith(coreName) }
                    ?: coreName
            } else {
                coreName
            }

            groups.add(CharacterGroup(resolvedName, originalName, familyMembers))
            assigned.addAll(familyMembers)
        }
        return groups.sortedBy { it.characterName }
    }

    private suspend fun getCategoryMembers(category: String, namespace: Int): List<String> {
        val list = mutableListOf<String>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val typeArg = if (namespace == 14) "&cmtype=subcat" else "&cmtype=file"
            val cArg = if (token != null) "&cmcontinue=$token" else ""
            val url =
                "$API_BASE_URL?action=query&list=categorymembers&cmtitle=$encoded&cmnamespace=$namespace$typeArg&format=json&cmlimit=500$cArg"
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
                        val isAudio = i.mime?.startsWith("audio/") == true
                                || i.url.endsWith(".wav")
                                || i.url.endsWith(".mp3")
                        if (!audioOnly || isAudio) {
                            list.add(p.title.replace(Regex("^(File:|文件:)"), "") to i.url)
                        }
                    }
                }
                token = res.continuation?.get("gcmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) {
                break
            }
        } while (token != null)
        return list
    }

    // 获取指定分类下的所有页面标题（namespace=0，即主命名空间的角色页）
    private suspend fun getCharacterNames(categoryName: String): List<String> {
        val list = mutableListOf<String>()
        val encoded = URLEncoder.encode(categoryName, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&cmcontinue=$token" else ""
            val url =
                "$API_BASE_URL?action=query&list=categorymembers&cmtitle=$encoded&cmnamespace=0&cmtype=page&format=json&cmlimit=500$cArg"
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

    // 获取所有角色名（合并两个分类，去重）
    suspend fun getAllCharacterNames(): List<String> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        result.addAll(getCharacterNames("Category:晶源体"))
        result.addAll(getCharacterNames("Category:超弦体"))
        result.sorted()
    }

    // 角色名缓存表（从晶源体/超弦体分类获取）
    private val characterNameCache = ConcurrentHashMap.newKeySet<String>()

    /** 预加载角色名缓存表，后续 groupCategories 和头像加载都依赖它 */
    suspend fun preloadCharacterNames() = withContext(Dispatchers.IO) {
        if (characterNameCache.isNotEmpty()) return@withContext
        val names = getAllCharacterNames()
        characterNameCache.addAll(names)
    }

    /** 判断某个名字是否在缓存表中；缓存表为空时返回 true（降级：未初始化时不拦截） */
    fun isCharacterNameValid(name: String): Boolean =
        characterNameCache.isEmpty() || characterNameCache.contains(name)

    // 头像与图片加载委托给 ImageLoader
    suspend fun getCharacterAvatarUrl(characterName: String): String? =
        ImageLoader.getCharacterAvatarUrl(client, API_BASE_URL, jsonParser, characterName)

    suspend fun loadNetworkImage(url: String): ImageBitmap? =
        ImageLoader.loadNetworkImage(client, url)

    suspend fun loadRawBytes(url: String): ByteArray? =
        ImageLoader.loadRawBytes(client, url)

    private suspend fun fetchString(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute()
                .use { if (it.isSuccessful) it.body.string() else null }
        } catch (_: Exception) {
            null
        }
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

    fun sanitizeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
}