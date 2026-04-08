package com.nekolaska.calabiyau.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import data.SharedJson
import java.io.File

/**
 * 离线缓存管理器。
 *
 * 将 API 响应的原始 JSON 缓存到磁盘，离线时可读取。
 * 缓存按类型分目录存储，文件名为 key 的 MD5 哈希。
 *
 * 缓存策略：
 * - 有网络时：先请求网络，成功后写入缓存
 * - 无网络时：读取缓存，返回缓存数据 + 离线标记
 * - 缓存有效期：默认 7 天，可按类型自定义
 */
object OfflineCache {

    private lateinit var cacheDir: File
    private lateinit var appContext: Context

    /** 缓存类型（子目录） */
    enum class Type(val dir: String, val maxAgeMs: Long) {
        CHARACTER_DETAIL("character_detail", 7 * DAY),
        WEAPON_DETAIL("weapon_detail", 7 * DAY),
        MAP_DETAIL("map_detail", 7 * DAY),
        CHARACTER_LIST("character_list", 1 * DAY),
        WEAPON_LIST("weapon_list", 1 * DAY),
        MAP_LIST("map_list", 1 * DAY),
        ANNOUNCEMENTS("announcements", 2 * HOUR),
        GAME_MODES("game_modes", 3 * DAY),
        COSTUMES("costumes", 3 * DAY),
        WEAPON_SKINS("weapon_skins", 3 * DAY),
        GALLERY("gallery", 3 * DAY),
        DECORATIONS("decorations", 3 * DAY),
        NAVIGATION("navigation", 3 * DAY),
        BALANCE_DATA("balance_data", 1 * DAY),
    }

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    fun init(context: Context) {
        appContext = context.applicationContext
        cacheDir = File(context.cacheDir, "offline_cache")
        cacheDir.mkdirs()
    }

    /**
     * 读取缓存。
     * @return 缓存的 JSON 字符串，不存在或已过期返回 null
     */
    suspend fun get(type: Type, key: String): String? = withContext(Dispatchers.IO) {
        val file = cacheFile(type, key)
        if (!file.exists()) return@withContext null
        // 检查过期
        val age = System.currentTimeMillis() - file.lastModified()
        if (age > type.maxAgeMs) {
            file.delete()
            return@withContext null
        }
        try {
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 写入缓存。
     */
    suspend fun put(type: Type, key: String, json: String) = withContext(Dispatchers.IO) {
        try {
            val dir = File(cacheDir, type.dir)
            dir.mkdirs()
            cacheFile(type, key).writeText(json)
        } catch (_: Exception) { }
    }

    /**
     * 清除指定类型的所有缓存。
     */
    suspend fun clear(type: Type) = withContext(Dispatchers.IO) {
        File(cacheDir, type.dir).deleteRecursively()
    }

    /**
     * 清除所有离线缓存。
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    /**
     * 获取缓存总大小（字节）。
     */
    fun totalSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 带缓存的网络请求封装。
     *
     * - 有网络：请求网络 → 成功则缓存并返回；失败则尝试读缓存
     * - 无网络：直接读缓存
     *
     * @return Pair<JSON字符串, 是否来自缓存>，全部失败返回 null
     */
    suspend fun fetchWithCache(
        type: Type,
        key: String,
        networkFetch: suspend () -> String?
    ): Pair<String, Boolean>? {
        val isOnline = try {
            NetworkMonitor.isNetworkAvailable(appContext)
        } catch (_: Exception) { true }

        if (isOnline) {
            // 有网络：先尝试网络请求
            val result = networkFetch()
            if (result != null) {
                put(type, key, result)
                return result to false
            }
            // 网络失败，回退缓存
            val cached = get(type, key)
            if (cached != null) return cached to true
            return null
        } else {
            // 无网络：直接读缓存
            val cached = get(type, key)
            if (cached != null) return cached to true
            return null
        }
    }

    private fun cacheFile(type: Type, key: String): File {
        val hash = key.toByteArray().fold(0L) { acc, b ->
            acc * 31 + b.toLong()
        }.let { java.lang.Long.toHexString(it) }
        return File(File(cacheDir, type.dir).also { it.mkdirs() }, "$hash.json")
    }
}
