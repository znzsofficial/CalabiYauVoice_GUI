package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import util.buildWikiUrl
import util.atomicReplaceFile

/**
 * Wiki API 核心业务逻辑 —— 纯函数集合，不持有状态。
 *
 * 每个函数接收 [client] / [jsonParser] / [fetchStringFn] 等参数，
 * 由各平台的 WikiEngine 负责提供具体实现。
 */
class WikiQueryFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

object WikiEngineCore {

    const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"

    private val downloadDirectoryLocks = ConcurrentHashMap<String, Mutex>()
    private const val DOWNLOAD_MANIFEST = ".calabiyau-downloads.tsv"

    // ========== 角色名缓存（双平台共享语义，但各自持有实例） ==========

    /**
     * 可共享的角色名缓存持有者。
     * 各平台的 WikiEngine 应各自持有一个实例（object 天然单例）。
     */
    class CharacterNameCache {
        val cache: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val loadLock = Any()
        private var loadInFlight: CompletableDeferred<Unit>? = null

        suspend fun ensure(getAllNames: suspend () -> List<String>) = withContext(Dispatchers.IO) {
            if (cache.isNotEmpty()) return@withContext
            val deferred: CompletableDeferred<Unit>
            val isLoader: Boolean
            synchronized(loadLock) {
                if (cache.isNotEmpty()) return@withContext
                val current = loadInFlight
                if (current != null) {
                    deferred = current
                    isLoader = false
                } else {
                    deferred = CompletableDeferred()
                    loadInFlight = deferred
                    isLoader = true
                }
            }

            if (!isLoader) {
                deferred.await()
                return@withContext
            }

            try {
                val names = getAllNames()
                cache.addAll(names)
                deferred.complete(Unit)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
                throw e
            } finally {
                synchronized(loadLock) {
                    if (loadInFlight === deferred) loadInFlight = null
                }
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
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json,
        nameCache: CharacterNameCache
    ): List<CharacterGroup> = withContext(Dispatchers.IO) {
        val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "search", "srsearch" to keyword, "srnamespace" to "14", "format" to "json", "srlimit" to "200")

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val responseString = requireWikiJson(fetchStringFn(url), "搜索")
                val rawList = jsonParser.decodeFromString<WikiResponse>(responseString).query?.search?.map { it.title } ?: emptyList()
                val filteredList = if (voiceOnly) rawList.filter { it.endsWith("语音") } else rawList
                return@withContext groupCategories(filteredList, voiceOnly, fetchStringFn, jsonParser, nameCache)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) delay(1000L + Random.nextLong(2000))
            }
        }
        throw lastError ?: WikiQueryFailure("搜索失败")
    }

    /**
     * 扫描指定分类的所有子分类（展开树），按层并发发出请求。
     */
    suspend fun scanCategoryTree(
        rootCategory: String,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> = scanCategoryTrees(listOf(rootCategory), fetchStringFn, jsonParser)

    /** Search families are name-based and need not be linked in the Wiki category tree. */
    suspend fun scanCategoryTrees(
        roots: List<String>,
        fetchStringFn: suspend (String) -> String?,
        jsonParser: Json
    ): List<String> = withContext(Dispatchers.IO) {
        val seeds = roots.distinct()
        val found = ConcurrentHashMap.newKeySet<String>()
        found.addAll(seeds)
        val semaphore = Semaphore(4)
        var currentLayer = seeds
        while (currentLayer.isNotEmpty()) {
            val nextLayer = currentLayer.map { cat ->
                async {
                    semaphore.acquire()
                    try { getCategoryMembers(cat, 14, fetchStringFn, jsonParser) }
                    finally { semaphore.release() }
                }
            }.awaitAll().flatten().filter { found.add(it) }
            currentLayer = nextLayer
        }
        seeds + (found - seeds.toSet()).sorted()
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
            return mime?.startsWith("audio/") == true || util.isAudioFile("", clean)
        }
        // --- 路径 1：allimages 前缀搜索 ---
        val path1 = LinkedHashMap<String, String>()
        var aicontinue: String? = null
        do {
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "allimages", "aiprefix" to keyword, "aiprop" to "url|mime", "ailimit" to "500", "format" to "json", *(if (aicontinue != null) arrayOf("aicontinue" to aicontinue) else emptyArray()))
            val json = requireWikiJson(fetchStringFn(url), "文件前缀搜索")
            try {
                val res = jsonParser.decodeFromString<AiResponse>(json)
                res.query?.allimages?.forEach { item ->
                    if (item.url != null && matchesFilter(item.url, item.mime)) path1[item.name] = item.url
                }
                aicontinue = res.continuation?.get("aicontinue")?.jsonPrimitive?.content
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw WikiQueryFailure("文件前缀搜索解析失败", e)
            }
        } while (aicontinue != null)

        // --- 路径 2：search 全文搜索 ---
        val path2 = LinkedHashMap<String, String>()
        var sroffset = 0
        do {
            val url = buildWikiUrl(API_BASE_URL, "action" to "query", "list" to "search", "srsearch" to keyword, "srnamespace" to "6", "format" to "json", "srlimit" to "100", "sroffset" to sroffset.toString())
            val json = requireWikiJson(fetchStringFn(url), "文件全文搜索")
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                val titles = res.query?.search?.map { it.title } ?: emptyList()
                if (titles.isEmpty()) break
                titles.chunked(50).forEach { chunk ->
                    val titlesParam = chunk.joinToString("|")
                    val infoUrl = buildWikiUrl(API_BASE_URL, "action" to "query", "titles" to titlesParam, "prop" to "imageinfo", "iiprop" to "url|mime", "redirects" to "1", "format" to "json")
                    val infoJson = requireWikiJson(fetchStringFn(infoUrl), "文件详情")
                    val infoRes = jsonParser.decodeFromString<WikiResponse>(infoJson)
                    infoRes.query?.pages?.values?.forEach { page ->
                        val info = page.imageinfo?.firstOrNull()
                        if (info?.url != null && matchesFilter(info.url, info.mime)) {
                            path2.putIfAbsent(page.title.replace(filePrefixRegex, ""), info.url)
                        }
                    }
                }
                val nextOffset = res.continuation?.get("sroffset")?.jsonPrimitive?.content?.toIntOrNull()
                if (nextOffset == null || nextOffset <= sroffset) break
                sroffset = nextOffset
            } catch (e: CancellationException) {
                throw e
            } catch (e: WikiQueryFailure) {
                throw e
            } catch (e: Exception) {
                throw WikiQueryFailure("文件全文搜索解析失败", e)
            }
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
        downloadFileFn: suspend (String, File) -> Unit,
        manifestFile: File? = null
    ): List<File> = withContext(Dispatchers.IO) {
        require(maxConcurrency > 0) { "maxConcurrency must be greater than 0" }
        val uniqueFiles = files.distinctBy { it.second }
        val total = uniqueFiles.size
        if (total == 0) return@withContext emptyList()
        val normalizedDir = saveDir.canonicalFile
        val directoryLock = downloadDirectoryLocks.computeIfAbsent(normalizedDir.path.lowercase()) { Mutex() }

        directoryLock.withLock {
            if (!normalizedDir.exists() && !normalizedDir.mkdirs()) {
                throw IOException("Could not create download directory: $normalizedDir")
            }
            if (!normalizedDir.isDirectory) throw IOException("Download target is not a directory: $normalizedDir")

            val legacyManifest = File(normalizedDir, DOWNLOAD_MANIFEST)
            val recordFile = (manifestFile ?: legacyManifest).canonicalFile
            // Android stores records privately. Import a readable old sidecar once, but never write/delete it.
            val manifest = readDownloadManifest(if (recordFile.exists()) recordFile else legacyManifest)
            val usedNames = normalizedDir.listFiles()
                ?.mapTo(HashSet()) { it.name.lowercase() }
                ?: HashSet()
            var namesChanged = false
            val downloads = uniqueFiles.map { (name, url) ->
                manifest[url]?.let { entry ->
                    val mappedTarget = File(normalizedDir, entry.name).absoluteFile.normalize()
                    if (mappedTarget.parentFile == normalizedDir) {
                        usedNames.add(mappedTarget.name.lowercase())
                        return@map url to mappedTarget
                    }
                    manifest.remove(url)
                    namesChanged = true
                }

                var safeName = sanitizeFileName(name)
                if (!safeName.contains('.')) {
                    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
                        .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
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
                val target = File(normalizedDir, targetName).absoluteFile.normalize()
                if (target.parentFile != normalizedDir) throw IOException("Unsafe download target: $targetName")
                manifest[url] = DownloadManifestEntry(target.name)
                namesChanged = true
                url to target
            }
            // Reserve names once. Skip items and unchanged retries must not republish the record.
            if (namesChanged) writeDownloadManifest(recordFile, manifest)

            val semaphore = Semaphore(maxConcurrency)
            val counter = AtomicInteger(0)
            val failures = ConcurrentLinkedQueue<String>()
            val progressLock = Mutex()
            val verifiedChanged = AtomicBoolean(false)
            val expectedFiles = manifest.toMap()
            val jobs = downloads.map { (url, targetFile) ->
                launch(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val saved = expectedFiles[url]
                        val existingHash = if (saved?.sha256 != null && targetFile.isFile && targetFile.length() > 0 &&
                            targetFile.length() == saved.size) fileSha256(targetFile) else null
                        val valid = existingHash != null && existingHash == saved?.sha256
                        if (!valid) downloadFileFn(url, targetFile)
                        currentCoroutineContext().ensureActive()
                        if (!targetFile.isFile || targetFile.length() == 0L) throw IOException("Download produced no file: ${targetFile.name}")
                        if (!valid) {
                            val verified = DownloadManifestEntry(targetFile.name, targetFile.length(), fileSha256(targetFile))
                            progressLock.withLock {
                                manifest[url] = verified
                                verifiedChanged.set(true)
                            }
                        }
                        progressLock.withLock {
                            val current = counter.incrementAndGet()
                            onProgress(current, total, targetFile.name)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = e.message ?: e::class.simpleName.orEmpty()
                        failures.add("${targetFile.name}: $message")
                        onLog("[错误] ${targetFile.name}: $message")
                    } finally {
                        semaphore.release()
                    }
                }
            }
            var cancellation: CancellationException? = null
            try {
                jobs.joinAll()
            } catch (e: CancellationException) {
                cancellation = e
            }
            // A cancelled joinAll returns before siblings finish. Publish only after they leave the map.
            try {
                if (cancellation != null) {
                    withContext(NonCancellable) {
                        jobs.joinAll()
                        if (verifiedChanged.get()) {
                            val snapshot = progressLock.withLock { LinkedHashMap(manifest) }
                            writeDownloadManifest(recordFile, snapshot)
                        }
                    }
                } else if (verifiedChanged.get()) {
                    val snapshot = progressLock.withLock { LinkedHashMap(manifest) }
                    writeDownloadManifest(recordFile, snapshot)
                }
            } catch (e: Exception) {
                if (cancellation == null) throw e
            }
            cancellation?.let { throw it }
            if (failures.isNotEmpty()) {
                throw IOException("${failures.size} of $total downloads failed")
            }
            downloads.map { it.second }
        }
    }

    private data class DownloadManifestEntry(val name: String, val size: Long? = null, val sha256: String? = null)

    private suspend fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readDownloadManifest(file: File): LinkedHashMap<String, DownloadManifestEntry> {
        val result = LinkedHashMap<String, DownloadManifestEntry>()
        if (!file.isFile) return result
        runCatching {
            file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@forEachLine
                val targetName = parts[0]
                val url = parts[1]
                if (sanitizeFileName(targetName) == targetName && url.isNotBlank()) {
                    val sha = parts.getOrNull(3)?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    result[url] = DownloadManifestEntry(targetName, parts.getOrNull(2)?.toLongOrNull(), sha)
                }
            }
        }
        return result
    }

    private fun writeDownloadManifest(file: File, manifest: Map<String, DownloadManifestEntry>) {
        val parent = checkNotNull(file.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create download records directory: $parent")
        val temp = File(parent, "${file.name}.tmp")
        val content = manifest.entries
            .sortedBy { it.key }
            .joinToString("\n", postfix = if (manifest.isEmpty()) "" else "\n") { (url, target) ->
                require(!url.contains('\t') && !url.contains('\r') && !url.contains('\n')) { "Invalid download URL" }
                "${target.name}\t$url\t${target.size ?: ""}\t${target.sha256 ?: ""}"
        }
        temp.writeText(content)
        try {
            atomicReplaceFile(temp, file)
        } catch (error: IOException) {
            temp.delete()
            throw IOException("Could not publish download manifest", error)
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
                    else -> nameCache.cache.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
                        .firstOrNull { coreName.startsWith(it) }
                        ?: nameCache.cache.sorted().firstOrNull { it.startsWith(coreName) }
                        ?: coreName
                }
            } else coreName
            groups.add(CharacterGroup(resolvedName, originalName, familyMembers))
            assigned.addAll(familyMembers)
        }
        return groups.groupBy { it.characterName }.map { (name, family) ->
            CharacterGroup(name, family.first().rootCategory,
                family.flatMap { listOf(it.rootCategory) + it.subCategories }.distinct())
        }.sortedBy { it.characterName }
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
            val json = requireWikiJson(fetchStringFn(url), "分类成员")
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.categorymembers?.forEach { list.add(it.title) }
                token = res.continuation?.get("cmcontinue")?.jsonPrimitive?.content
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw WikiQueryFailure("分类成员解析失败: $category", e)
            }
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
            val json = requireWikiJson(fetchStringFn(url), "分类文件")
            try {
                val res = jsonParser.decodeFromString<WikiResponse>(json)
                res.query?.pages?.values?.forEach { p ->
                    val i = p.imageinfo?.firstOrNull()
                    if (i?.url != null) {
                        val cleanUrl = i.url.substringBefore('?')
                        val isAudio = i.mime?.startsWith("audio/") == true || util.isAudioFile("", cleanUrl)
                        if (!audioOnly || isAudio) list.add(p.title.replace(filePrefixRegex, "") to i.url)
                    }
                }
                token = res.continuation?.get("gcmcontinue")?.jsonPrimitive?.content
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw WikiQueryFailure("分类文件解析失败: $category", e)
            }
        } while (token != null)
        val seenUrls = HashSet<String>()
        return list.filter { seenUrls.add(it.second) }
    }

    private fun requireWikiJson(body: String?, action: String): String {
        if (body.isNullOrBlank()) throw WikiQueryFailure("${action}请求失败")
        if (body.trimStart().startsWith("<")) throw WikiQueryFailure("${action}被拦截")
        val root = try { SharedJson.parseToJsonElement(body).jsonObject }
            catch (e: Exception) { throw WikiQueryFailure("${action}响应不是有效 JSON", e) }
        root["error"]?.let { throw WikiQueryFailure("${action}接口错误: $it") }
        if ("query" !in root && "batchcomplete" !in root) throw WikiQueryFailure("${action}响应缺少查询结果")
        return body
    }
}
