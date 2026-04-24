package com.nekolaska.calabiyau.feature.wiki.voting.source

import android.webkit.CookieManager
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLEncoder

data class VoteSubmitOperation(val pollId: String, val answer: String)

object VotingRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    fun buildUrl(vararg params: Pair<String, String>): String {
        val query = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8") }=${URLEncoder.encode(v, "UTF-8") }"
        }
        return "$API?$query"
    }

    fun getWikiCookies(): String? {
        return try {
            val cm = CookieManager.getInstance()
            val rootCookies = cm.getCookie("https://wiki.biligame.com") ?: ""
            val klbqCookies = cm.getCookie("https://wiki.biligame.com/klbq/") ?: ""
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

    fun fetchCsrfToken(cookies: String): String? {
        val url = buildUrl(
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

    fun fetchVotePageHtml(votePage: String): String? {
        val url = buildUrl(
            "action" to "parse",
            "page" to votePage,
            "prop" to "text",
            "format" to "json"
        )
        val body = httpGet(url) ?: return null
        val json = SharedJson.parseToJsonElement(body)
        return json.jsonObject["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
    }

    fun fetchVoteDataHtml(fullText: String, cookies: String): String? {
        val url = buildUrl(
            "action" to "parse",
            "text" to fullText,
            "prop" to "text",
            "contentmodel" to "wikitext",
            "disablelimitreport" to "true",
            "format" to "json"
        )
        val body = httpGetWithCookies(url, cookies) ?: return null
        val json = SharedJson.parseToJsonElement(body)
        return json.jsonObject["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
    }

    fun submitVoteOperation(cookies: String, token: String, operation: VoteSubmitOperation): Int {
        val formBody = FormBody.Builder()
            .add("action", "pollsubmitvote")
            .add("poll", operation.pollId)
            .add("answer", operation.answer)
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(API)
            .post(formBody)
            .header("Cookie", cookies)
            .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
            .build()

        return WikiEngine.client.newCall(request).execute().use { resp ->
            resp.code
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string()
                if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetWithCookies(url: String, cookies: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookies)
                .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string()
                if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }
}
