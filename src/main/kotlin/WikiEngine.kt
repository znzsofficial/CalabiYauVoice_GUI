import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object WikiEngine {
    private const val API_BASE_URL = "https://wiki.biligame.com/klbq/api.php"
    private val client = OkHttpClient.Builder().build()
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
        @SerialName("continue") val continuation: Map<String, String>? = null
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
     * 仅获取指定分类下的文件列表，不下载
     */
    suspend fun fetchFilesInCategory(category: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        // 直接调用内部已有的逻辑
        getCategoryFilesDetail(category)
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
     */
    suspend fun searchAndGroupCharacters(keyword: String): List<CharacterGroup> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$API_BASE_URL?action=query&list=search&srsearch=$encoded&srnamespace=14&format=json&srlimit=500"

        val jsonStr = fetchString(url) ?: return@withContext emptyList()
        val rawList = try {
            jsonParser.decodeFromString<WikiResponse>(jsonStr).query?.search?.map { it.title } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val validList = rawList.filter { it.endsWith("语音") }
        return@withContext groupCategories(validList)
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

    /**
     * 3. 下载逻辑
     */
//    suspend fun downloadFiles(
//        categories: List<String>,
//        saveDir: File,
//        maxConcurrency: Int,
//        onLog: (String) -> Unit,
//        onProgress: (Int, Int, String) -> Unit
//    ) = withContext(Dispatchers.IO) {
//        onLog("正在扫描音频文件链接...")
//        val allFiles = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
//
//        // 并发获取文件列表
//        categories.map { cat ->
//            async { allFiles.addAll(getCategoryFilesDetail(cat)) }
//        }.awaitAll()
//
//        val uniqueFiles = allFiles.distinctBy { it.second } // URL去重
//        val total = uniqueFiles.size
//        onLog("共找到 $total 个唯一音频文件。")
//
//        if (total == 0) return@withContext
//
//        if (!saveDir.exists()) saveDir.mkdirs()
//
//        val semaphore = Semaphore(maxConcurrency)
//        val counter = AtomicInteger(0)
//
//        uniqueFiles.map { (name, url) ->
//            launch(Dispatchers.IO) {
//                semaphore.acquire()
//                try {
//                    var safeName = sanitizeFileName(name)
//                    if (!safeName.contains(".")) {
//                        if (url.endsWith(".ogg")) safeName += ".ogg"
//                        else if (url.endsWith(".mp3")) safeName += ".mp3"
//                    }
//                    val targetFile = File(saveDir, safeName)
//
//                    downloadFile(url, targetFile)
//
//                    val current = counter.incrementAndGet()
//                    onProgress(current, total, safeName)
//                } catch (e: Exception) {
//                    onLog("[错误] ${e.message}")
//                } finally {
//                    semaphore.release()
//                }
//            }
//        }.joinAll()
//        onLog("下载任务完成。")
//    }

    // === 内部工具函数 ===

    private fun groupCategories(rawList: List<String>): List<CharacterGroup> {
        val cleanMap = rawList.associateWith { it.replace(Regex("^(Category:|分类:)"), "") }
        val sortedItems = cleanMap.entries.sortedBy { it.value.length }
        val groups = mutableListOf<CharacterGroup>()
        val assigned = mutableSetOf<String>()

        for ((originalName, cleanName) in sortedItems) {
            if (assigned.contains(originalName)) continue
            val coreName = cleanName.removeSuffix("语音")
            if (coreName.isBlank()) continue
            val familyMembers = rawList.filter { raw ->
                val cl = cleanMap[raw]!!
                cl.startsWith(coreName) && cl.endsWith("语音")
            }
            groups.add(CharacterGroup(coreName, originalName, familyMembers))
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
                token = res.continuation?.get("cmcontinue")
            } catch (_: Exception) {
                break
            }
        } while (token != null)
        return list
    }

    private suspend fun getCategoryFilesDetail(category: String): List<Pair<String, String>> {
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
                    if (i?.url != null && (i.mime?.startsWith("audio/") == true || i.url.endsWith(".ogg") || i.url.endsWith(
                            ".mp3"
                        ))
                    ) {
                        list.add(p.title.replace(Regex("^(File:|文件:)"), "") to i.url)
                    }
                }
                token = res.continuation?.get("gcmcontinue")
            } catch (_: Exception) {
                break
            }
        } while (token != null)
        return list
    }

    // 头像缓存
    // Key: 角色名, Value: 真实URL
    private val avatarCache = ConcurrentHashMap<String, String>()

    /**
     * 获取角色头像的真实 URL
     * 输入: "香奈美" -> 内部查询 "File:香奈美头像.png" -> 返回 "https://.../a/a1/xxxx.png"
     */
    suspend fun getCharacterAvatarUrl(characterName: String): String? {
        // 1. 查缓存
        if (avatarCache.containsKey(characterName)) {
            return avatarCache[characterName]
        }

        // 2. 构造文件名 (根据你的描述，规则是 名字+头像.png)
        // 注意：MediaWiki 查询文件必须带 "File:" 前缀
        val fileName = "File:${characterName}头像.png"
        val encodedTitle = URLEncoder.encode(fileName, "UTF-8")

        // 3. API 请求: prop=imageinfo & iiprop=url
        val url = "$API_BASE_URL?action=query&titles=$encodedTitle&prop=imageinfo&iiprop=url&format=json"

        val jsonStr = fetchString(url) ?: return null

        try {
            val response = jsonParser.decodeFromString<WikiResponse>(jsonStr)
            val pages = response.query?.pages?.values
            val page = pages?.firstOrNull()

            // page.imageinfo 可能为空（如果文件不存在）
            val realUrl = page?.imageinfo?.firstOrNull()?.url

            if (realUrl != null) {
                avatarCache[characterName] = realUrl
                return realUrl
            }
        } catch (_: Exception) {
            // 解析失败忽略
        }
        return null
    }

    // === 图片缓存
    // Key: 图片URL, Value: Compose ImageBitmap
    private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * 下载图片并转换为 Bitmap
     * 会自动使用 client 中的 User-Agent，防止 403
     */
    suspend fun loadNetworkImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        // 1. 查缓存
        if (imageCache.containsKey(url)) {
            return@withContext imageCache[url]
        }

        // 2. 下载
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val bytes = response.body.bytes()

                val bitmap = bytes.decodeToImageBitmap()

                // 4. 存缓存
                imageCache[url] = bitmap
                return@withContext bitmap
            }
        } catch (_: Exception) {
            // e.printStackTrace()
            return@withContext null
        }
    }

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