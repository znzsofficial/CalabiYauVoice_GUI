package com.nekolaska.calabiyau.core.wiki

import android.webkit.CookieManager
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import util.buildWikiUrl
import util.executeRequest

internal object WikiAuthHelper {

    private const val DEFAULT_USER_AGENT = "CalabiYauVoice/2.0 (Android)"
    private const val WIKI_ROOT_URL = "https://wiki.biligame.com"
    private const val WIKI_SUB_PATH = "$WIKI_ROOT_URL/klbq/"

    // Cookie 拼接结果 500ms memo：组合期/状态对比处高频调用，CookieManager 解析非零成本
    @Volatile
    private var cookieMemo: Pair<Long, String?>? = null

    fun getWikiCookies(): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        cookieMemo?.let { (ts, value) -> if (now - ts < 500) return value }
        val value = try {
            val cm = CookieManager.getInstance()
            val rootCookies = cm.getCookie(WIKI_ROOT_URL) ?: ""
            val klbqCookies = cm.getCookie(WIKI_SUB_PATH) ?: ""
            val cookieMap = mutableMapOf<String, String>()
            ("$rootCookies; $klbqCookies").split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    cookieMap[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
            if (cookieMap.isEmpty()) return null.also { cookieMemo = now to null }
            cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } catch (_: Exception) {
            null
        }
        cookieMemo = now to value
        return value
    }

    // CSRF token 会话级有效，按 API 地址缓存 30 分钟，避免每次投票/分享都现取
    private val csrfCache = mutableMapOf<String, Pair<Long, String>>()

    fun fetchCsrfToken(apiUrl: String, cookies: String): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        csrfCache[apiUrl]?.let { (ts, token) -> if (now - ts < 30 * 60 * 1000L) return token }
        val url = buildWikiUrl(
            apiUrl,
            "action" to "query",
            "meta" to "tokens",
            "type" to "csrf",
            "format" to "json"
        )
        val body = httpGetWithCookies(url, cookies) ?: return null
        val token = try {
            val json = SharedJson.parseToJsonElement(body)
            json.jsonObject["query"]
                ?.jsonObject?.get("tokens")
                ?.jsonObject?.get("csrftoken")
                ?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        return token?.also { csrfCache[apiUrl] = now to it }
    }

    fun httpGet(url: String, expectJson: Boolean = false): String? {
        return try {
            WikiEngine.client.executeRequest(url) {
                header("User-Agent", DEFAULT_USER_AGENT)
            }.use { resp ->
                if (!resp.isSuccessful) return null
                syncResponseCookies(resp.headers("Set-Cookie"))
                val body = resp.body.string()
                if (expectJson && !body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    fun httpGetWithCookies(url: String, cookies: String, expectJson: Boolean = false): String? {
        return try {
            WikiEngine.client.executeRequest(url) {
                header("Cookie", cookies)
                header("User-Agent", DEFAULT_USER_AGENT)
            }.use { resp ->
                if (!resp.isSuccessful) return null
                syncResponseCookies(resp.headers("Set-Cookie"))
                val body = resp.body.string()
                if (expectJson && !body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    fun syncResponseCookies(setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        runCatching {
            val cm = CookieManager.getInstance()
            setCookieHeaders.forEach { setCookie ->
                cm.setCookie(WIKI_ROOT_URL, setCookie)
                cm.setCookie(WIKI_SUB_PATH, setCookie)
            }
            cm.flush()
        cookieMemo = null
        }
    }

}
