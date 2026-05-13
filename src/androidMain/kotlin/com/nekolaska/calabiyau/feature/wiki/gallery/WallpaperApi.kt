package com.nekolaska.calabiyau.feature.wiki.gallery

import com.nekolaska.calabiyau.CalabiYauApplication
import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 从 Wiki 壁纸页面随机抽取一张壁纸 URL。
 * 使用 MediaWiki API 获取壁纸分类下的文件列表，随机选取一张。
 */
object WallpaperApi {

    init {
        MemoryCacheRegistry.register("WallpaperApi", ::clearMemoryCache)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val CACHE_DIR = "offline_cache/gallery"
    private const val WALLPAPER_FILE_LIST_KEY = "wallpaper_file_names"

    /** 缓存壁纸文件名列表，避免每次启动都请求 */
    @Volatile
    private var cachedFileNames: List<String>? = null

    fun clearMemoryCache() { cachedFileNames = null }

    /** 本次进程是否已自动刷新过壁纸（冷启动后第一次为 false） */
    @Volatile
    var hasRefreshedThisSession: Boolean = false
        private set

    /**
     * 获取一张随机壁纸的 URL。
     * 优先使用 AppPrefs 中持久化的 URL，仅在无缓存或强制刷新时重新获取。
     */
    fun fetchRandomWallpaperUrl(forceRefresh: Boolean = false): String? {
        return ensureWallpaperUrl(forceRefresh)
    }

    /**
     * 确保存在可用的壁纸 URL。
     * 非强制刷新时优先返回持久化缓存；请求失败时不清空旧缓存。
     */
    fun ensureWallpaperUrl(forceRefresh: Boolean = false): String? {
        // 非强制刷新时，优先返回持久化缓存
        if (!forceRefresh) {
            val cached = AppPrefs.wallpaperUrl
            if (!cached.isNullOrBlank()) return cached
        }
        return try {
            val fileNames = cachedFileNames ?: fetchWallpaperFileNames().also {
                cachedFileNames = it
            }
            if (fileNames.isEmpty()) return null
            val randomFile = fileNames.random()
            val url = fetchImageUrl(randomFile, forceRefresh)
            // 持久化到 AppPrefs
            if (url != null) {
                AppPrefs.wallpaperUrl = url
                hasRefreshedThisSession = true
            }
            url ?: AppPrefs.wallpaperUrl
        } catch (_: Exception) {
            AppPrefs.wallpaperUrl
        }
    }

    /**
     * 从壁纸页面的 wikitext 中提取所有壁纸文件名。
     * 匹配 [[文件:壁纸-xxx.jpg]] 和 <gallery> 中的 文件:壁纸-xxx.jpg 格式。
     */
    private fun fetchWallpaperFileNames(): List<String> {
        val cacheFile = cacheFile(WALLPAPER_FILE_LIST_KEY)
        val url = "$API?action=parse&page=${URLEncoder.encode("壁纸", "UTF-8")}&prop=wikitext&format=json"
        val body = WikiEngine.safeGet(url)?.also { cacheFile?.writeTextSafely(it) }
            ?: cacheFile?.readTextSafely()
            ?: return emptyList()
        val json = SharedJson.parseToJsonElement(body).jsonObject
        val wikitext = json["parse"]?.jsonObject
            ?.get("wikitext")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content ?: return emptyList()

        val fileNames = mutableSetOf<String>()

        // 匹配 [[文件:壁纸-xxx.ext|...]] 格式
        val linkRegex = Regex("""\[\[文件:(壁纸-[^|\]]+)""")
        linkRegex.findAll(wikitext).forEach { match ->
            fileNames.add(match.groupValues[1])
        }

        // 匹配 <gallery> 中的 文件:壁纸-xxx.ext 格式
        val galleryRegex = Regex("""文件:(壁纸-[^\n|]+)""")
        galleryRegex.findAll(wikitext).forEach { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) fileNames.add(name)
        }

        // 也匹配不带 "文件:" 前缀的 gallery 条目（如 壁纸-xxx.ext）
        val bareRegex = Regex("""(?:^|\n)(壁纸-[^\n|]+\.\w+)""")
        bareRegex.findAll(wikitext).forEach { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) fileNames.add(name)
        }

        return fileNames.toList()
    }

    private fun fetchImageUrl(fileName: String, forceRefresh: Boolean): String? {
        return try {
            val cacheFile = cacheFile("wallpaper_image_url_$fileName")
            val fileTitle = URLEncoder.encode("文件:$fileName", "UTF-8")
            val url = "$API?action=query&titles=$fileTitle&prop=imageinfo&iiprop=url&format=json"
            val body = if (forceRefresh) {
                WikiEngine.safeGet(url)?.also { cacheFile?.writeTextSafely(it) }
            } else {
                cacheFile?.readTextSafely()
                    ?: WikiEngine.safeGet(url)?.also { cacheFile?.writeTextSafely(it) }
            } ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }

    private fun cacheFile(key: String): File? {
        val context = CalabiYauApplication.instanceOrNull ?: return null
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val hash = MessageDigest.getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private fun File.readTextSafely(): String? {
        return try {
            if (!exists()) return null
            readText()
        } catch (_: Exception) {
            null
        }
    }

    private fun File.writeTextSafely(value: String) {
        try {
            parentFile?.mkdirs()
            writeText(value)
        } catch (_: Exception) {
        }
    }

}
