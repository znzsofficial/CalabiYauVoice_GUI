package com.nekolaska.calabiyau.feature.wiki.voting.source

import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request

data class VoteSubmitOperation(val pollId: String, val answer: String)
data class VoteSubmitResult(val success: Boolean, val statusCode: Int, val message: String? = null)

object VotingRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val VOTE_PAGE_URL = "https://wiki.biligame.com/klbq/%E8%A7%92%E8%89%B2%E6%97%B6%E8%A3%85%E6%8A%95%E7%A5%A8"
    private const val WIKI_ROOT_URL = "https://wiki.biligame.com"

    fun buildUrl(vararg params: Pair<String, String>): String {
        return WikiAuthHelper.buildUrl(API, *params)
    }

    fun getWikiCookies(): String? {
        return WikiAuthHelper.getWikiCookies()
    }

    fun fetchCsrfToken(cookies: String): String? {
        return WikiAuthHelper.fetchCsrfToken(API, cookies)
    }

    fun fetchVotePageHtml(votePage: String): String? {
        val url = buildUrl(
            "action" to "parse",
            "page" to votePage,
            "prop" to "text",
            "format" to "json"
        )
        val body = WikiAuthHelper.httpGet(url) ?: return null
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
        val body = WikiAuthHelper.httpGetWithCookies(url, cookies) ?: return null
        val json = SharedJson.parseToJsonElement(body)
        return json.jsonObject["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
    }

    fun submitVoteOperation(cookies: String, token: String, operation: VoteSubmitOperation): VoteSubmitResult {
        val formBody = FormBody.Builder()
            .add("action", "pollsubmitvote")
            .add("poll", operation.pollId)
            .add("answer", operation.answer)
            .add("token", token)
            .add("format", "json")
            .build()

        val request = Request.Builder()
            .url(API)
            .post(formBody)
            .header("Cookie", cookies)
            .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", "https://wiki.biligame.com")
            .header("Referer", VOTE_PAGE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        return WikiEngine.client.newCall(request).execute().use { resp ->
            WikiAuthHelper.syncResponseCookies(resp.headers("Set-Cookie"))
            val body = resp.body.string()
            val success = resp.isSuccessful && isSubmitBodySuccessful(body)
            VoteSubmitResult(
                success = success,
                statusCode = resp.code,
                message = if (success) null else body.take(300).ifBlank { resp.message }
            )
        }
    }

    private fun isSubmitBodySuccessful(body: String): Boolean {
        val text = body.trim()
        if (text.isBlank()) return true
        if (text.contains("error", ignoreCase = true)) return false
        if (text.contains("badtoken", ignoreCase = true)) return false
        if (text.contains("notloggedin", ignoreCase = true)) return false
        if (text.contains("permissiondenied", ignoreCase = true)) return false
        if (text.contains("<html", ignoreCase = true)) return false
        if (text.contains("pollsubmitvote", ignoreCase = true)) return true
        if (text.contains("success", ignoreCase = true)) return true
        return text.startsWith("{") || text.startsWith("[")
    }

}
