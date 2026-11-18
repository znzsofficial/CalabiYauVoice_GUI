package com.nekolaska.calabiyau.feature.wiki.gallery

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import util.buildParseUrl
import util.buildWikiUrl

internal enum class WallpaperRefresh { UPDATED, RETAINED, UNAVAILABLE }

/** Requests remain in their caller's scope; a manual choice supersedes any earlier automatic one. */
internal class WallpaperSelection(private val current: () -> String?, private val publish: (String) -> Unit) {
    private var version = 0L
    private var active: Long? = null
    private var automaticDone = false

    @Synchronized fun clear() { ++version; active = null; automaticDone = false }
    private fun retained() = if (current().isNullOrBlank()) WallpaperRefresh.UNAVAILABLE else WallpaperRefresh.RETAINED

    suspend fun select(manual: Boolean, rotate: Boolean, choose: suspend () -> String?): WallpaperRefresh {
        val ticket = synchronized(this) {
            if (!manual && (active != null || (!current().isNullOrBlank() && (!rotate || automaticDone)))) return retained()
            (++version).also { active = it }
        }
        return try {
            val next = choose()
            currentCoroutineContext().ensureActive()
            synchronized(this) {
                if (ticket != version || next.isNullOrBlank()) retained()
                else {
                    automaticDone = true
                    if (next == current()) WallpaperRefresh.RETAINED else { publish(next); WallpaperRefresh.UPDATED }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { synchronized(this) { retained() } }
        finally { synchronized(this) { if (active == ticket) active = null } }
    }
}

internal object WallpaperApi {
    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private val type = OfflineCache.Type.GALLERY
    private val selection = WallpaperSelection({ AppPrefs.wallpaperUrl }) { url ->
        AppPrefs.wallpaperSeedColorCache = 0
        AppPrefs.wallpaperSeedColorUrl = url
        AppPrefs.wallpaperUrl = url
    }
    init { MemoryCacheRegistry.register("WallpaperSelection", selection::clear) }

    private val filenames = object : CachedWikiApi<List<String>>("WallpaperFiles") {
        override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<String>> {
            val raw = OfflineCache.fetchWithCache(type, "wallpaper_file_names", forceRefresh,
                validatePayload = { parseFiles(it).isNotEmpty() }) {
                WikiEngine.safeGet(buildParseUrl(API, "壁纸", "wikitext"))
            } ?: return ApiResult.Error("壁纸名单暂不可用")
            return ApiResult.Success(parseFiles(raw.payload), raw.isFromCache, raw.ageMs)
        }
    }

    suspend fun refresh(manual: Boolean = false, rotate: Boolean = false): WallpaperRefresh = withContext(Dispatchers.IO) {
        selection.select(manual, rotate) {
            val files = (filenames.fetch() as? ApiResult.Success)?.value.orEmpty()
            val name = files.randomOrNull() ?: return@select null
            val url = buildWikiUrl(API, "action" to "query", "titles" to "文件:$name", "prop" to "imageinfo",
                "iiprop" to "url", "redirects" to "1", "format" to "json")
            val raw = OfflineCache.fetchWithCache(type, "wallpaper_image_url_$name", forceRefresh = manual || rotate,
                validatePayload = { imageUrl(it) != null }) { WikiEngine.safeGet(url) }
            raw?.let { imageUrl(it.payload) }
        }
    }

    private fun imageUrl(body: String) = runCatching {
        SharedJson.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject?.values
            ?.firstOrNull()?.jsonObject?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")
            ?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()

    private val filePattern = Regex("""(?:文件:|^\h*)(壁纸-[^\n\r|\]<>]+?\.(?:png|jpe?g|webp|gif))(?=[\h|\]\r\n<]|$)""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    internal fun parseFiles(body: String): List<String> = runCatching {
        val text = SharedJson.parseToJsonElement(body).jsonObject.getValue("parse").jsonObject.getValue("wikitext").jsonObject
            .getValue("*").jsonPrimitive.content
        filePattern.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }.getOrDefault(emptyList())
}
