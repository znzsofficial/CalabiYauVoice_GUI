package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Wiki API 核心业务逻辑 —— 纯函数集合，不持有状态。
 *
 * 每个函数接收 [client] / [jsonParser] / [fetchStringFn] 等参数，
 * 由各平台的 WikiEngine 负责提供具体实现。
 */
object WikiEngineCore {

    const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"

    // ========== 角色名缓存（双平台共享语义，但各自持有实例） ==========

    /**
     * 可共享的角色名缓存持有者。
     * 各平台的 WikiEngine 应各自持有一个实例（object 天然单例）。
     */
    class CharacterNameCache {
        val cache: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val loading = AtomicBoolean(false)

        suspend fun ensure(getAllNames: suspend () -> List<String>) = withContext(Dispatchers.IO) {
            if (cache.isNotEmpty()) return@withContext
            if (!loading.compareAndSet(false, true)) return@withContext
            try {
                val names = getAllNames()
                cache.addAll(names)
            } finally {
                if (cache.isEmpty()) loading.set(false)
            }
        }
    }

    // ========== 公共业务函数 ==========

    /**
     * 获取所有角色名（合并 "晶源体" 与 "超弦体" 分类），并行请求。
     */
    suspend fun getAllCharacterNames(
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> = withContext(Dispatchers.IO) {
        val (a, b) = awaitAll(
            async { getCharacterNames("Category:晶源体", fetchStringFn, jsonParser) },
            async { getCharacterNames("Category:超弦体", fetchStringFn, jsonParser) }
        )
        (a + b).toSortedSet().toList()
    }

    private suspend fun getCharacterNames(
        categoryName: String,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> = fetchPagedCategoryMembers(categoryName, 0, "page", fetchStringFn, jsonParser)

    /**
     * 搜索并分组角色分类。
     */
    suspend fun searchAndGroupCharacters(
        keyword: String,
        voiceOnly: Boolean = true,
        client: OkHttpClient,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json,
        nameCache: CharacterNameCache
    ): List<CharacterGroup> = withContext(Dispatchers.IO) {
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
                val result = groupCategories(filteredList, voiceOnly, fetchStringFn, jsonParser, nameCache)
                if (result.isNotEmpty()) return@withContext result
            } catch (_: Exception) {
                if (attempt < 2) Thread.sleep(1000L + Random.nextLong(2000))
            }
        }
        return@withContext emptyList()
    }

    /**
     * 扫描指定分类的所有子分类（展开树），按层并发发出请求。
     */
    suspend fun scanCategoryTree(
        rootCategory: String,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap.newKeySet<String>()
        found.add(rootCategory)
        var currentLayer = listOf(rootCategory)
        while (currentLayer.isNotEmpty()) {
            val nextLayer = currentLayer.map { cat ->
                async { getCategoryMembers(cat, 14, fetchStringFn, jsonParser) }
            }.awaitAll().flatten().filter { found.add(it) }
            currentLayer = nextLayer
        }
        val list = found.toMutableList()
        list.sort()
        if (list.remove(rootCategory)) list.add(0, rootCategory)
        list
    }

    /**
     * 在 File 命名空间中搜索文件（前缀 + 全文双路径），返回 List<文件名 to CDN URL>。
     */
    suspend fun searchFiles(
        keyword: String,
        audioOnly: Boolean,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        fun matchesFilter(url: String, mime: String?): Boolean {
            if (!audioOnly) return true
            val clean = url.substringBefore('?')
            return mime?.startsWith("audio/") == true ||
                clean.endsWith(".wav") || clean.endsWith(".mp3") || clean.endsWith(".ogg")
        }
        val encoded = URLEncoder.encode(keyword, "UTF-8")

        // --- 路径 1：allimages 前缀搜索 ---
        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val cArg = if (aicontinue != null) "&aicontinue=${URLEncoder.encode(aicontinue, "UTF-8")}" else ""
            val url = "$API_BASE_URL?action=query&list=allimages&aiprefix=$encoded&aiprop=url|mime&ailimit=500&format=json$cArg"
            val json = fetchStringFn(url) ?: break
            if (json.trimStart().startsWith("<")) break
            try {
                val res = jsonParser.decodeFromString<AiResponse>(json)
                res.query?.allimages?.forEach { item ->
                    if (item.url != null && matchesFilter(item.url, item.mime)) path1[item.name] = item.url
                }
                aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
            } catch (_: Exception) { break }
        } while (aicontinue != null)

        // --- 路径 2：search 全文搜索 ---
        val path2 = LinkedHashMap<String, String>()
        var sroffset = 0
        do {
            val url = "$API_BASE_URL?action=query&list=search&srsearch=$encoded&srnamespace=6&format=json&srlimit=100&sroffset=$sroffset"
            val json = fetchStringFn(url) ?: break
            if (json.trimStart().startsWith("<")) break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
                    val infoUrl = "$API_BASE_URL?action=query&titles=$titlesParam&prop=imageinfo&iiprop=url|mime&format=json"
                    val infoJson = fetchStringFn(infoUrl) ?: return@forEach
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

        val merged = LinkedHashMap<String, String>(path1)
        path2.forEach { (k, v) -> merged.putIfAbsent(k, v) }
        val seenUrls = HashSet<String>()
        merged.entries.filter { seenUrls.add(it.value) }.map { (k, v) -> k to v }
    }

    /**
     * 获取分类下所有文件的详细信息。
     */
    suspend fun fetchFilesInCategory(
        category: String,
        audioOnly: Boolean = true,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        getCategoryFilesDetail(category, audioOnly, fetchStringFn, jsonParser)
    }

    /**
     * 直接下载给定的文件列表。
     */
    suspend fun downloadSpecificFiles(
        files: List<Pair<String, String>>,
        saveDir: File,
        maxConcurrency: Int,
        onLog: (String) -> Unit,
        onProgress: (Int, Int, String) -> Unit,
        downloadFileFn: (String, File) -> Unit
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
                    downloadFileFn(url, targetFile)
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

    // ========== 内部工具函数 ==========

    internal suspend fun groupCategories(
        rawList: List<String>,
        voiceOnly: Boolean = true,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json,
        nameCache: CharacterNameCache
    ): List<CharacterGroup> {
        nameCache.ensure { getAllCharacterNames(fetchStringFn, jsonParser) }
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
            val resolvedName = if (nameCache.cache.isNotEmpty()) {
                when {
                    nameCache.cache.contains(coreName) -> coreName
                    else -> nameCache.cache.firstOrNull { coreName.startsWith(it) }
                        ?: nameCache.cache.firstOrNull { it.startsWith(coreName) }
                        ?: coreName
                }
            } else coreName
            groups.add(CharacterGroup(resolvedName, originalName, familyMembers))
            assigned.addAll(familyMembers)
        }
        return groups.sortedBy { it.characterName }
    }

    internal suspend fun fetchPagedCategoryMembers(
        category: String,
        namespace: Int,
        cmtype: String,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> {
        val list = mutableListOf<String>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&cmcontinue=$token" else ""
            val url = "$API_BASE_URL?action=query&list=categorymembers&cmtitle=$encoded&cmnamespace=$namespace&cmtype=$cmtype&cmlimit=500&format=json$cArg"
            val json = fetchStringFn(url) ?: break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.categorymembers?.forEach { list.add(it.title) }
                token = res.continuation?.get("cmcontinue")?.jsonPrimitive?.content
            } catch (_: Exception) { break }
        } while (token != null)
        return list
    }

    internal suspend fun getCategoryMembers(
        category: String,
        namespace: Int,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> =
        fetchPagedCategoryMembers(category, namespace, if (namespace == 14) "subcat" else "file", fetchStringFn, jsonParser)

    internal suspend fun getCategoryFilesDetail(
        category: String,
        audioOnly: Boolean = true,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val encoded = URLEncoder.encode(category, "UTF-8")
        var token: String? = null
        do {
            val cArg = if (token != null) "&gcmcontinue=$token" else ""
            val url = "$API_BASE_URL?action=query&generator=categorymembers&gcmtitle=$encoded&gcmnamespace=6&prop=imageinfo&iiprop=url|mime&format=json&gcmlimit=500$cArg"
            val json = fetchStringFn(url) ?: break
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
}
