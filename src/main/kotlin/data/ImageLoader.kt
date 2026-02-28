package data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {

    private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    private const val MAX_IMAGE_CACHE_ENTRIES = 512

    // LRU 图片缓存，Key: URL，Value: ImageBitmap
    private val imageCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_IMAGE_CACHE_ENTRIES
        }
    )

    // 进行中的请求去重表
    private val imageInflight = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()

    // 头像 URL 缓存，Key: 角色名，Value: 真实 URL
    private val avatarCache = ConcurrentHashMap<String, String>()

    /**
     * 下载图片并转换为 ImageBitmap。
     * - LRU 缓存最多 256 张
     * - 进行中的同 URL 请求只发起一次
     * - 校验 content-type 与大小上限（8 MB）
     */
    suspend fun loadNetworkImage(client: OkHttpClient, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        imageCache[url]?.let { return@withContext it }

        val existing = imageInflight[url]
        if (existing != null) return@withContext existing.await()

        val deferred = CompletableDeferred<ImageBitmap?>()
        val raced = imageInflight.putIfAbsent(url, deferred)
        if (raced != null) return@withContext raced.await()

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    deferred.complete(null)
                    return@withContext null
                }

                val body = response.body

                val contentType = body.contentType()?.toString()?.lowercase()
                if (contentType != null && !contentType.startsWith("image/")) {
                    deferred.complete(null)
                    return@withContext null
                }

                if (body.contentLength() > MAX_IMAGE_BYTES) {
                    deferred.complete(null)
                    return@withContext null
                }

                val bytes = body.byteStream().use { readBytesWithLimit(it, MAX_IMAGE_BYTES) }
                if (bytes.isEmpty()) {
                    deferred.complete(null)
                    return@withContext null
                }

                val bitmap = bytes.decodeToImageBitmap()
                imageCache[url] = bitmap
                deferred.complete(bitmap)
                return@withContext bitmap
            }
        } catch (_: Exception) {
            deferred.complete(null)
            return@withContext null
        } finally {
            imageInflight.remove(url)
        }
    }

    /**
     * 获取角色头像的真实 URL。
     * 规则：角色名 + "头像.png" -> 查询 MediaWiki File 页 -> 返回 CDN URL
     */
    suspend fun getCharacterAvatarUrl(
        client: OkHttpClient,
        apiBaseUrl: String,
        jsonParser: kotlinx.serialization.json.Json,
        characterName: String
    ): String? = withContext(Dispatchers.IO) {
        avatarCache[characterName]?.let { return@withContext it }

        val fileName = "File:${characterName}头像.png"
        val encodedTitle = URLEncoder.encode(fileName, "UTF-8")
        val url = "$apiBaseUrl?action=query&titles=$encodedTitle&prop=imageinfo&iiprop=url&format=json"

        val jsonStr = try {
            client.newCall(Request.Builder().url(url).build()).execute()
                .use { if (it.isSuccessful) it.body.string() else null }
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        try {
            val response = jsonParser.decodeFromString<WikiEngine.WikiResponse>(jsonStr)
            val realUrl = response.query?.pages?.values?.firstOrNull()
                ?.imageinfo?.firstOrNull()?.url
            if (realUrl != null) avatarCache[characterName] = realUrl
            realUrl
        } catch (_: Exception) {
            null
        }
    }

    /** 读取字节流，超过 limitBytes 则返回空数组（保护内存）*/
    private fun readBytesWithLimit(input: java.io.InputStream, limitBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        var total = 0
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limitBytes) return ByteArray(0)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

