package com.nekolaska.calabiyau.core.wiki

import android.webkit.CookieManager
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

internal object WikiAuthHelper {

    private const val DEFAULT_USER_AGENT = "CalabiYauVoice/2.0 (Android)"
    private const val WIKI_ROOT_URL = "https://wiki.biligame.com"
    private const val WIKI_SUB_PATH = "$WIKI_ROOT_URL/klbq/"

    fun buildUrl(baseUrl: String, vararg params: Pair<String, String>): String {
        val query = params.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return if (query.isBlank()) baseUrl else "$baseUrl?$query"
    }

    fun getWikiCookies(): String? {
        return try {
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
            if (cookieMap.isEmpty()) return null
            cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } catch (_: Exception) {
            null
        }
    }

    fun fetchCsrfToken(apiUrl: String, cookies: String): String? {
        val url = buildUrl(
            apiUrl,
            "action" to "query",
            "meta" to "tokens",
            "type" to "csrf",
            "format" to "json"
        )
        val body = httpGetWithCookies(url, cookies) ?: return null
        return try {
            val json = SharedJson.parseToJsonElement(body)
            json.jsonObject["query"]
                ?.jsonObject?.get("tokens")
                ?.jsonObject?.get("csrftoken")
                ?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    fun httpGet(url: String, expectJson: Boolean = false): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
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
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookies)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
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
        }
    }

}