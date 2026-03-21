package com.nekolaska.calabiyau.data

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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object WikiEngine {
    private const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://wiki.biligame.com/klbq/")
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

    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // === 数据结构 ===
    data class CharacterGroup(
        val characterName: String,
        val rootCategory: String,
        val subCategories: List<String>
    )

    @Serializable
    data class WikiResponse(
        val query: WikiQuery? = null,
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

    @Serializable
    private data class AiItem(val name: String, val url: String? = null, val mime: String? = null)

    @Serializable
    private data class AiQuery(val allimages: List<AiItem>? = null)

    @Serializable
    private data class AiResponse(
        val query: AiQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    private val categoryPrefixRegex = Regex("^(Category:|分类:)")
    private val filePrefixRegex = Regex("^(File:|文件:)")
    private val sanitizeRegex = Regex("[\\\\/:*?\"<>|]")
    fun sanitizeFileName(name: String) = name.replace(sanitizeRegex, "_").trim()

    // 角色名缓存
    private val characterNameCache = ConcurrentHashMap.newKeySet<String>()
    private val characterNameCacheLoading = AtomicBoolean(false)

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

    suspend fun getAllCharacterNames(): List<String> = withContext(Dispatchers.IO) {
        val (a, b) = awaitAll(
            async { getCharacterNames("Category:晶源体") },
            async { getCharacterNames("Category:超弦体") }
        )
        (a + b).toSortedSet().toList()
    }

    private suspend fun getCharacterNames(categoryName: String): List<String> =
        fetchPagedCategoryMembers(categoryName, 0, "page")

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
                val rawList = jsonParser.decodeFromString<WikiResponse>(responseString).query?.search?.map { it.title } ?: emptyList()
                val filteredList = if (voiceOnly) rawList.filter { it.endsWith("语音") } else rawList
                val result = groupCategories(filteredList, voiceOnly)
                if (result.isNotEmpty()) return@withContext result
            } catch (_: Exception) {
                if (attempt < 2) Thread.sleep(1000L + Random.nextLong(2000))
            }
        }
        return@withContext emptyList()
    }

    suspend fun scanCategoryTree(rootCategory: String): List<String> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap.newKeySet<String>()
        found.add(rootCategory)
        var currentLayer = listOf(rootCategory)
        while (currentLayer.isNotEmpty()) {
            val nextLayer = currentLayer.map { cat ->
                async { getCategoryMembers(cat, 14) }
            }.awaitAll().flatten().filter { found.add(it) }
            currentLayer = nextLayer
        }
        val list = found.toMutableList()
        list.sort()
        if (list.remove(rootCategory)) list.add(0, rootCategory)
        list
    }

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
            val url = "$API_BASE_URL?action=query&list=allimages&aiprefix=$encoded&aiprop=url|mime&ailimit=500&format=json$cArg"
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
            val url = "$API_BASE_URL?action=query&list=search&srsearch=$encoded&srnamespace=6&format=json&srlimit=100&sroffset=$sroffset"
            val json = fetchString(url, onError = { onLog?.invoke("[文件搜索] 全文搜索: $it") })
            if (json == null) { onLog?.invoke("[文件搜索] 全文搜索请求失败"); break }
            if (json.trimStart().startsWith("<")) { onLog?.invoke("[文件搜索] 全文搜索被WAF拦截"); break }
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
                    val infoUrl = "$API_BASE_URL?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url|mime&format=json"
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

    suspend fun fetchCharacterAvatars(characterNames: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (characterNames.isEmpty()) return@withContext emptyMap()
        val result = ConcurrentHashMap<String, String>()
        characterNames.chunked(50).map { chunk ->
            async {
                val titlesParam = chunk.joinToString("|") { URLEncoder.encode("文件:${it}头像.png", "UTF-8") }
                val url = "$API_BASE_URL?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url&format=json"
                val json = fetchString(url) ?: return@async
                try {
                    val res = jsonParser.decodeFromString<WikiResponse>(json)
                    res.query?.pages?.values?.forEach { page ->
                        val avatarUrl = page.imageinfo?.firstOrNull()?.url ?: return@forEach
                        val name = page.title.replace(filePrefixRegex, "").removeSuffix("头像.png")
                        result[name] = avatarUrl
                    }
                } catch (_: Exception) {}
            }
        }.awaitAll()
        result
    }

    suspend fun fetchFilesInCategory(category: String, audioOnly: Boolean = true): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        getCategoryFilesDetail(category, audioOnly)
    }

    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>,
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
                    if (!safeName.contains('.')) {
                        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
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
                rawList.filter { raw -> val cl = cleanMap[raw]!!; cl.startsWith(coreName) && cl.endsWith("语音") }
            } else {
                rawList.filter { raw -> cleanMap[raw]!!.startsWith(coreName) }
            }
            val resolvedName = if (characterNameCache.isNotEmpty()) {
                when {
                    characterNameCache.contains(coreName) -> coreName
                    else -> characterNameCache.firstOrNull { coreName.startsWith(it) }
                        ?: characterNameCache.firstOrNull { it.startsWith(coreName) }
                        ?: coreName
                }
            } else coreName
            groups.add(CharacterGroup(resolvedName, originalName, familyMembers))
            assigned.addAll(familyMembers)
        }
        return groups.sortedBy { it.characterName }
    }

    private suspend fun fetchPagedCategoryMembers(category: String, namespace: Int, cmtype: String): List<String> {
        val list = mutableListOf<String>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&cmcontinue=$token" else ""
            val url = "$API_BASE_URL?action=query&list=categorymembers&cmtitle=$encoded&cmnamespace=$namespace&cmtype=$cmtype&cmlimit=500&format=json$cArg"
            val json = fetchString(url) ?: break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.categorymembers?.forEach { list.add(it.title) }
                token = res.continuation?.get("cmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) { break }
        } while (token != null)
        return list
    }

    private suspend fun getCategoryMembers(category: String, namespace: Int): List<String> =
        fetchPagedCategoryMembers(category, namespace, if (namespace == 14) "subcat" else "file")

    private suspend fun getCategoryFilesDetail(category: String, audioOnly: Boolean = true): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&gcmcontinue=$token" else ""
            val url = "$API_BASE_URL?action=query&generator=categorymembers&gcmtitle=$encoded&gcmnamespace=6&prop=imageinfo&iiprop=url|mime&format=json&gcmlimit=500$cArg"
            val json = fetchString(url) ?: break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.pages?.values?.forEach { p ->
                    val i = p.imageinfo?.firstOrNull()
                    if (i?.url != null) {
                        val cleanUrl = i.url.substringBefore('?')
                        val isAudio = i.mime?.startsWith("audio/") == true ||
                            cleanUrl.endsWith(".wav") || cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".ogg")
                        if (!audioOnly || isAudio) list.add(p.title.replace(filePrefixRegex, "") to i.url)
                    }
                }
                token = res.continuation?.get("gcmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) { break }
        } while (token != null)
        val seenUrls = HashSet<String>()
        return list.filter { seenUrls.add(it.second) }
    }

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
