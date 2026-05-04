package com.nekolaska.calabiyau.core.cache

import android.content.Context
import com.nekolaska.calabiyau.CrashContextStore
import com.nekolaska.calabiyau.core.network.NetworkMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 离线缓存管理器。
 *
 * 将 API 响应的原始内容缓存到磁盘，离线时可读取。
 * 缓存按类型分目录存储，文件名为 key 的 MD5 哈希。
 *
 * 缓存策略：
 * - 有网络时：先请求网络，成功后写入缓存；失败则回退到任意磁盘缓存（即便已过期）
 * - 无网络时：读取磁盘缓存（即便已过期），返回数据 + 离线标记
 * - 缓存有效期：默认 7 天，可按类型自定义。过期条目不会在读取时自动删除，
 *   由 [pruneExpired] 定期清理（建议挂在应用启动流程中异步执行一次）
 *
 * 请求去重：
 * - 并发调用相同 key 的 `fetchWithCache` 会共享同一次网络请求，
 *   避免切换 Tab 时重复打网
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
        ACTIVITIES("activities", 2 * HOUR),
        GAME_MODES("game_modes", 3 * DAY),
        COSTUMES("costumes", 3 * DAY),
        WEAPON_SKINS("weapon_skins", 3 * DAY),
        BIO_CARDS("bio_cards", 3 * DAY),
        GALLERY("gallery", 3 * DAY),
        GAME_HISTORY("game_history", 3 * DAY),
        DECORATIONS("decorations", 3 * DAY),
        NAVIGATION("navigation", 3 * DAY),
        BALANCE_DATA("balance_data", 1 * DAY),
    }

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    /** 磁盘缓存读取结果（含年龄与过期标记）。 */
    data class CacheEntry(
        val content: String,
        val ageMs: Long,
        val expired: Boolean
    )

    /**
     * [fetchWithCache] 返回结果。
     *
     * 这里的 [payload] 始终表示缓存中存放的原始网络响应内容。
     * 页面解析后的 HTML / wikitext 等内容应由各自的 source / api 数据类单独承载，
     * 不要再复用这个字段表达不同语义。
     *
     * @param payload 原始 JSON 响应（来自网络或磁盘）
     * @param isFromCache 是否来自磁盘缓存
     * @param ageMs 缓存年龄（毫秒），仅在 [isFromCache] == true 时有意义
     */
    data class CacheResult(
        val payload: String,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    // ── 请求去重 ──
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<CacheResult?>>()

    fun init(context: Context) {
        appContext = context.applicationContext
        cacheDir = File(context.cacheDir, "offline_cache")
        cacheDir.mkdirs()
    }

    /**
     * 读取缓存条目（不删除过期文件）。
     * 调用方据 [CacheEntry.expired] 判断是否可用。
     */
    suspend fun getEntry(type: Type, key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val file = cacheFile(type, key)
        if (!file.exists()) return@withContext null
        val age = System.currentTimeMillis() - file.lastModified()
        try {
            CacheEntry(
                content = file.readText(),
                ageMs = age,
                expired = age > type.maxAgeMs
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取未过期的缓存。
     * 过期条目不会删除，仅作为 [fetchWithCache] 的离线回退。
     */
    suspend fun get(type: Type, key: String): String? =
        getEntry(type, key)?.takeIf { !it.expired }?.content

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
     * 使单个缓存条目失效（用于 forceRefresh）。
     */
    suspend fun invalidate(type: Type, key: String) = withContext(Dispatchers.IO) {
        val f = cacheFile(type, key)
        if (f.exists()) f.delete()
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
        clearMemoryCaches()
    }

    /**
     * 清除所有 API 的内存缓存，与 [clearAll] 配合使用。
     */
    fun clearMemoryCaches() {
        MemoryCacheRegistry.clearAll()
    }

    /**
     * 获取缓存总大小（字节）。
     */
    fun totalSize(): Long {
        if (!::cacheDir.isInitialized) return 0L
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 清理所有过期缓存条目。
     * 建议在 App 启动时异步调用一次。
     */
    suspend fun pruneExpired() = withContext(Dispatchers.IO) {
        if (!::cacheDir.isInitialized) return@withContext
        val now = System.currentTimeMillis()
        cacheDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val dirName = file.parentFile?.name ?: return@forEach
            val type = Type.entries.firstOrNull { it.dir == dirName } ?: return@forEach
            if (now - file.lastModified() > type.maxAgeMs) {
                file.delete()
            }
        }
    }

    /**
     * 带缓存的网络请求封装。
     *
     * 行为：
     * 1. `forceRefresh` → 先 [invalidate]，再跳过内存 inflight
     * 2. 并发相同 key 的请求共享同一个 [CompletableDeferred]（请求去重）
     * 3. 有网络：网络请求成功则写入并返回；失败则回退到任何磁盘缓存（即便已过期）
     * 4. 无网络：直接读磁盘缓存（即便已过期）
     *
     * @return [CacheResult]；全部失败（无网络且无缓存）返回 null
     */
    suspend fun fetchWithCache(
        type: Type,
        key: String,
        forceRefresh: Boolean = false,
        networkFetch: suspend () -> String?
    ): CacheResult? {
        if (forceRefresh) {
            invalidate(type, key)
        }

        // 强制刷新走独立命名空间，避免搭便车拿到旧数据；
        // 也避免强制刷新的 Deferred 覆盖非强制请求者正在等待的结果
        val dedupKey = if (forceRefresh) "force:${type.dir}:$key" else "${type.dir}:$key"

        val deferred = CompletableDeferred<CacheResult?>()
        val prev = inflight.putIfAbsent(dedupKey, deferred)
        if (prev != null) {
            return runCatching { prev.await() }.getOrNull()
        }

        return try {
            val result = doFetch(type, key, networkFetch)
            if (result == null && ::appContext.isInitialized) {
                CrashContextStore.record(
                    appContext,
                    "OfflineCache.fetchWithCache",
                    "null result | type=${type.dir} | key=$key | forceRefresh=$forceRefresh"
                )
            } else if (result?.isFromCache == true && ::appContext.isInitialized) {
                CrashContextStore.record(
                    appContext,
                    "OfflineCache.fetchWithCache",
                    "cache fallback | type=${type.dir} | key=$key | ageMs=${result.ageMs}"
                )
            }
            deferred.complete(result)
            result
        } catch (e: Throwable) {
            if (::appContext.isInitialized) {
                CrashContextStore.record(
                    appContext,
                    "OfflineCache.fetchWithCache",
                    "exception | type=${type.dir} | key=$key | ${e::class.java.simpleName}: ${e.message.orEmpty().take(120)}"
                )
            }
            deferred.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(dedupKey, deferred)
        }
    }

    private suspend fun doFetch(
        type: Type,
        key: String,
        networkFetch: suspend () -> String?
    ): CacheResult? {
        val isOnline = try {
            NetworkMonitor.isNetworkAvailable(appContext)
        } catch (_: Exception) {
            true
        }

        if (isOnline) {
            // 有网络：先尝试网络请求
            val fresh = try {
                networkFetch()
            } catch (e: Throwable) {
                if (::appContext.isInitialized) {
                    CrashContextStore.record(
                        appContext,
                        "OfflineCache.doFetch",
                        "networkFetch exception | type=${type.dir} | key=$key | ${e::class.java.simpleName}: ${e.message.orEmpty().take(120)}"
                    )
                }
                null
            }
            if (fresh != null) {
                put(type, key, fresh)
                return CacheResult(payload = fresh, isFromCache = false, ageMs = 0L)
            }
            if (::appContext.isInitialized) {
                CrashContextStore.record(
                    appContext,
                    "OfflineCache.doFetch",
                        "networkFetch returned null | type=${type.dir} | key=$key | fallback=cache"
                )
            }
            // 网络失败，回退到任意磁盘缓存（包含过期条目）
            val entry = getEntry(type, key) ?: return null
                    return CacheResult(payload = entry.content, isFromCache = true, ageMs = entry.ageMs)
        }

        // 无网络：读任意磁盘缓存（包含过期条目）
        val entry = getEntry(type, key) ?: return null
                return CacheResult(payload = entry.content, isFromCache = true, ageMs = entry.ageMs)
    }

    private fun cacheFile(type: Type, key: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(cacheDir, type.dir).also { it.mkdirs() }, "$hash.json")
    }
}
