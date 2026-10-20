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
    private val cookieLock = Any()
    private val sessionGuard = CookieSessionGuard()
    private var clearingCookies = false

    fun getWikiCookies(): String? = synchronized(cookieLock) {
        if (clearingCookies) return@synchronized null
        val now = android.os.SystemClock.elapsedRealtime()
        cookieMemo?.let { (ts, value) -> if (now - ts < 500) return@synchronized value }
        readWikiCookies().also { cookieMemo = now to it }
    }

    private fun readWikiCookies(): String? = try {
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
        cookieMap.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { "${it.key}=${it.value}" }
    } catch (_: Exception) { null }

    fun beginCookieRequest(cookies: String): CookieSessionGuard.Ticket = synchronized(cookieLock) {
        sessionGuard.capture(cookies, if (clearingCookies) null else readWikiCookies())
    }

    fun isCurrentRequest(ticket: CookieSessionGuard.Ticket): Boolean = synchronized(cookieLock) {
        !clearingCookies && sessionGuard.isCurrent(ticket, readWikiCookies())
    }

    fun clearLoginCookies() = synchronized(cookieLock) {
        sessionGuard.invalidate()
        csrfCache.clear(); cookieMemo = null; clearingCookies = true
        try {
            CookieManager.getInstance().removeAllCookies {
                synchronized(cookieLock) {
                    cookieMemo = null
                    sessionGuard.invalidate()
                    clearingCookies = false
                    CookieManager.getInstance().flush()
                }
            }
        } catch (e: Exception) { clearingCookies = false; throw e }
    }

    // CSRF token 会话级有效，按「API 地址 + Cookie 摘要」缓存 30 分钟，避免每次投票/分享都现取。
    // key 必须包含 Cookie：token 与登录会话绑定，账号切换后旧 token 会被服务端拒绝且重试无效。
    private val csrfCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    fun fetchCsrfToken(apiUrl: String, cookies: String): String? {
        val ticket = beginCookieRequest(cookies)
        if (!isCurrentRequest(ticket)) return null
        val now = android.os.SystemClock.elapsedRealtime()
        val cacheKey = csrfSessionCacheKey(apiUrl, cookies)
        csrfCache[cacheKey]?.let { (ts, token) -> if (now - ts < 30 * 60 * 1000L) return token }
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
        return synchronized(cookieLock) {
            if (clearingCookies || !sessionGuard.isCurrent(ticket, readWikiCookies())) null
            else token?.also { csrfCache[cacheKey] = now to it }
        }
    }

    fun httpGet(url: String, expectJson: Boolean = false): String? {
        return try {
            WikiEngine.client.executeRequest(url) {
                header("User-Agent", DEFAULT_USER_AGENT)
            }.use { resp ->
                if (!resp.isSuccessful) return null
                // Public requests carry no session: they must not mutate the browser's login cookies.
                val body = resp.body.string()
                if (expectJson && !body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    fun httpGetWithCookies(url: String, cookies: String, expectJson: Boolean = false): String? {
        val ticket = beginCookieRequest(cookies)
        if (!isCurrentRequest(ticket)) return null
        return try {
            WikiEngine.client.executeRequest(url) {
                header("Cookie", cookies)
                header("User-Agent", DEFAULT_USER_AGENT)
            }.use { resp ->
                if (!resp.isSuccessful) return null
                syncResponseCookies(resp.headers("Set-Cookie"), ticket)
                val body = resp.body.string()
                if (expectJson && !body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    fun syncResponseCookies(setCookieHeaders: List<String>, ticket: CookieSessionGuard.Ticket) = synchronized(cookieLock) {
        if (setCookieHeaders.isEmpty()) return@synchronized
        if (clearingCookies) return@synchronized
        runCatching {
            sessionGuard.applyIfCurrent(ticket, ::readWikiCookies) {
                val cm = CookieManager.getInstance()
                setCookieHeaders.forEach { setCookie ->
                    cm.setCookie(WIKI_ROOT_URL, setCookie)
                    cm.setCookie(WIKI_SUB_PATH, setCookie)
                }
                cm.flush()
            }
        }
        cookieMemo = null
    }

}
