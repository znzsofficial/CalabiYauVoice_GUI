package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import util.buildWikiUrl
import util.executeGet

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
        val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "search", "srsearch" to keyword, "srnamespace" to "14", "format" to "json", "srlimit" to "200")

        repeat(3) { attempt ->
            try {
                val responseString = client.executeGet(url).use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }
                if (responseString.trimStart().startsWith("<")) throw IOException("Blocked by WAF")
                val rawList = jsonParser.decodeFromString<WikiResponse>(responseString).query?.search?.map { it.title } ?: emptyList()
                val filteredList = if (voiceOnly) rawList.filter { it.endsWith("语音") } else rawList
                val result = groupCategories(filteredList, voiceOnly, fetchStringFn, jsonParser, nameCache)
                if (result.isNotEmpty()) return@withContext result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < 2) delay(1000L + Random.nextLong(2000))
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
        // --- 路径 1：allimages 前缀搜索 ---
        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "allimages", "aiprefix" to keyword, "aiprop" to "url|mime", "ailimit" to "500", "format" to "json", *(if (aicontinue != null) arrayOf("aicontinue" to aicontinue) else emptyArray()))
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
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "search", "srsearch" to keyword, "srnamespace" to "6", "format" to "json", "srlimit" to "100", "sroffset" to sroffset.toString())
            val json = fetchStringFn(url) ?: break
            if (json.trimStart().startsWith("<")) break
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|")
                    val infoUrl = buildWikiUrl(API_BASE_URL, "action" to "query", "titles" to titlesParam, "prop" to "imageinfo", "iiprop" to "url|mime", "format" to "json")
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
        require(maxConcurrency > 0) { "maxConcurrency must be greater than 0" }
        val total = files.size
        if (total == 0) return@withContext
        if (!saveDir.exists()) saveDir.mkdirs()

        val usedNames = HashSet<String>()
        val downloads = files.map { (name, url) ->
            var safeName = sanitizeFileName(name)
            if (!safeName.contains('.')) {
                val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
                if (ext != null) safeName += ".$ext"
            }

            val dotIndex = safeName.lastIndexOf('.').takeIf { it > 0 } ?: safeName.length
            val baseName = safeName.substring(0, dotIndex)
            val extension = safeName.substring(dotIndex)
            var targetName = safeName
            var suffix = 2
            while (!usedNames.add(targetName.lowercase())) {
                targetName = "$baseName ($suffix)$extension"
                suffix++
            }
            url to targetName
        }

        val semaphore = Semaphore(maxConcurrency)
        val counter = AtomicInteger(0)
        val failures = ConcurrentLinkedQueue<String>()
        downloads.map { (url, safeName) ->
            launch(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    val targetFile = File(saveDir, safeName)
                    downloadFileFn(url, targetFile)
                    val current = counter.incrementAndGet()
                    onProgress(current, total, safeName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: e::class.simpleName.orEmpty()
                    failures.add("$safeName: $message")
                    onLog("[错误] $safeName: $message")
                } finally {
                    semaphore.release()
                }
            }
        }.joinAll()
        if (failures.isNotEmpty()) {
            throw IOException("${failures.size} of $total downloads failed")
        }
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
        var token: String? = null
        do {
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "categorymembers", "cmtitle" to category, "cmnamespace" to namespace.toString(), "cmtype" to cmtype, "cmlimit" to "500", "format" to "json", *(if (token != null) arrayOf("cmcontinue" to token) else emptyArray()))
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
        var token: String? = null
        do {
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "generator" to "categorymembers", "gcmtitle" to category, "gcmnamespace" to "6", "prop" to "imageinfo", "iiprop" to "url|mime", "format" to "json", "gcmlimit" to "500", *(if (token != null) arrayOf("gcmcontinue" to token) else emptyArray()))
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
