package com.nekolaska.calabiyau.feature.wiki.gamesettings.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.network.NetworkMonitor.Route
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PC_GAME_SETTINGS_PAGE_NAME
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import util.HttpTextResponse
import util.awaitResponseText
import util.buildParseUrl
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException

internal class PcSettingsFailure(val error: ApiResult.Error, val transient: Boolean = false) : Exception(error.message)

object PcGameSettingsRemoteSource {
    internal const val CACHE_KEY = "pc_game_settings_v1"
    private val cacheType = OfflineCache.Type.GAME_TIPS
    private val url = buildParseUrl("https://wiki.biligame.com/klbq/api.php", PC_GAME_SETTINGS_PAGE_NAME, "text")
    private class HttpFailure(val status: HttpTextResponse) : ProtocolException("HTTP ${status.code}")
    private val client by lazy {
        WikiEngine.client.newBuilder().addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Capture the status before OkHttp's own 503/Retry-After: 0 follow-up, and before
            // reading an error body can turn an access restriction into a retryable timeout.
            if (response.code >= 400) response.use { throw HttpFailure(HttpTextResponse(it.code, "", it.headers)) }
            response
        }.build()
    }

    internal suspend fun get(target: String = url): HttpTextResponse = try {
        client.newCall(Request.Builder().url(target).build()).awaitResponseText()
    } catch (e: HttpFailure) { e.status }

    suspend fun fetch(forceRefresh: Boolean): WikiHtmlPageSourceResult {
        val result = OfflineCache.fetchWithPolicy(cacheType, CACHE_KEY, forceRefresh, ::isValidPayload) { ticket ->
            loadPayload(forceRefresh, OfflineCache::defaultRoute, ::readDisk, { OfflineCache.put(ticket, it) },
                get = { get() })
        }
        return result?.let(::toSource) ?: throw PcSettingsFailure(ApiResult.Error("PC游戏设置缓存已更新，请重试"))
    }

    private suspend fun readDisk() = OfflineCache.getEntry(cacheType, CACHE_KEY)?.let {
        OfflineCache.CacheResult(it.content, true, it.ageMs)
    }

    suspend fun readCache() = readDisk()?.let(::toSource)

    internal fun toSource(result: OfflineCache.CacheResult) = WikiHtmlPageSourceResult(
        htmlFromPayload(result.payload) ?: throw invalidPayload(), result.isFromCache, result.ageMs)

    /** One recovery budget on ordinary loads: wait for a route OR retry a transient GET on a disk miss. */
    internal suspend fun loadPayload(
        forceRefresh: Boolean, route: () -> Route, read: suspend () -> OfflineCache.CacheResult?,
        publish: suspend (String) -> Unit, pause: suspend (Long) -> Unit = { delay(it) },
        get: suspend () -> HttpTextResponse
    ): OfflineCache.CacheResult {
        val initialRoute = route()
        suspend fun fallback() = read()?.takeIf { isValidPayload(it.payload) }
        if (initialRoute != Route.VALIDATED) {
            fallback()?.let { return it }
            if (initialRoute == Route.NONE) {
                if (!forceRefresh) pause(750)
                if (forceRefresh || route() == Route.NONE) throw PcSettingsFailure(
                    ApiResult.Error("当前没有可用网络，且无PC游戏设置缓存", ErrorKind.NETWORK))
            }
        }
        val payload = try { requestPayload(get) } catch (e: PcSettingsFailure) {
            if (initialRoute == Route.VALIDATED) fallback()?.let { return it }
            if (forceRefresh || initialRoute == Route.NONE || !e.transient) throw e
            pause(6_500) // Leave the Wiki cooling interval between actual GETs.
            if (route() == Route.NONE) throw e
            requestPayload(get)
        }
        publish(payload)
        return OfflineCache.CacheResult(payload, false, 0)
    }

    private suspend fun requestPayload(get: suspend () -> HttpTextResponse): String {
        val response = try { get() } catch (e: IOException) {
            val kind = if (e is InterruptedIOException) ErrorKind.TIMEOUT else ErrorKind.NETWORK
            throw PcSettingsFailure(ApiResult.Error("未能读取PC游戏设置", kind),
                transient = e is InterruptedIOException || e is SocketException || e is UnknownHostException)
        }
        if (!response.isSuccessful) {
            val kind = when (response.code) {
                403, 429, 503, 567 -> ErrorKind.CDN_BLOCKED
                404 -> ErrorKind.NOT_FOUND
                408, 504 -> ErrorKind.TIMEOUT
                else -> ErrorKind.UNKNOWN
            }
            throw PcSettingsFailure(ApiResult.Error("Wiki 暂时无法提供PC游戏设置（HTTP ${response.code}）", kind, response.code),
                transient = response.code in setOf(408, 500, 502, 504) && response.headers["Retry-After"] == null)
        }
        requireValidPayload(response.body)
        return response.body
    }

    internal fun isValidPayload(payload: String): Boolean = try { requireValidPayload(payload); true }
        catch (_: PcSettingsFailure) { false }

    private fun requireValidPayload(payload: String) {
        val html = requireHtml(payload)
        val parsed = try { PcGameSettingsParser.parse(html) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw invalidPayload() }
        if (!parsed.canCache) throw PcSettingsFailure(ApiResult.Error(
            "PC游戏设置页面结构不完整：${parsed.issues.take(2).joinToString("；")}", ErrorKind.PARSE))
    }

    private fun invalidPayload() = PcSettingsFailure(ApiResult.Error("PC游戏设置响应格式异常", ErrorKind.PARSE))

    internal fun htmlFromPayload(payload: String): String? = try { requireHtml(payload) }
        catch (_: PcSettingsFailure) { null }

    private fun requireHtml(payload: String): String {
        val root = try { SharedJson.parseToJsonElement(payload) as? JsonObject }
            catch (_: SerializationException) { null }
        if (root == null) throw invalidPayload()
        root["error"]?.let { error ->
            val code = ((error as? JsonObject)?.get("code") as? JsonPrimitive)?.content ?: throw invalidPayload()
            val kind = when (code) {
                "missingtitle" -> ErrorKind.NOT_FOUND
                "ratelimited" -> ErrorKind.CDN_BLOCKED
                else -> ErrorKind.UNKNOWN
            }
            throw PcSettingsFailure(ApiResult.Error("Wiki 暂时无法提供PC游戏设置（$code）", kind, apiCode = code))
        }
        val parse = root["parse"] as? JsonObject ?: throw invalidPayload()
        val title = (parse["title"] as? JsonPrimitive)?.content
        if (title != null && title != PC_GAME_SETTINGS_PAGE_NAME) throw invalidPayload()
        return ((parse["text"] as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content ?: throw invalidPayload()
    }
}
