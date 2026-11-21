package com.nekolaska.calabiyau.feature.wiki.voting.source

import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import com.nekolaska.calabiyau.core.wiki.CookieSessionGuard
import com.nekolaska.calabiyau.core.wiki.WikiUserApi
import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteSession
import data.SharedJson
import util.buildWikiUrl
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import util.awaitResponseText
import util.formBodyOf

data class VoteSubmitOperation(val pollId: String, val answer: String)
data class VoteSubmitResult(val success: Boolean, val statusCode: Int, val message: String? = null)

object VotingRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val VOTE_PAGE_URL = "https://wiki.biligame.com/klbq/%E8%A7%92%E8%89%B2%E6%97%B6%E8%A3%85%E6%8A%95%E7%A5%A8"

    internal suspend fun verifiedUser(ticket: CookieSessionGuard.Ticket): WikiUserApi.UserInfo {
        val url = buildWikiUrl(API, "action" to "query", "meta" to "userinfo", "format" to "json")
        val body = WikiAuthHelper.httpGetWithTicket(url, ticket) ?: error("无法读取 Wiki 登录信息，请刷新重试")
        return WikiUserApi.parseUserInfoJson(body)
    }

    private fun VoteSession.currentRequest() = request() ?: error("账号已切换或退出，请刷新投票")

    internal suspend fun fetchCsrfToken(session: VoteSession): String? {
        val url = buildWikiUrl(API, "action" to "query", "meta" to "tokens", "type" to "csrf",
            "assert" to "user", "assertuser" to session.name, "format" to "json")
        val body = WikiAuthHelper.httpGetWithTicket(url, session.currentRequest()) ?: return null
        val root = checkedWikiResponse(body)
        return root["query"]?.jsonObject?.get("tokens")?.jsonObject?.get("csrftoken")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() && it != "+\\" }
    }

    private fun checkedWikiResponse(body: String) = SharedJson.parseToJsonElement(body).jsonObject.also { root ->
        when (root["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content) {
            "assertuserfailed", "assertnameduserfailed" -> error("Wiki 登录身份不一致，请刷新投票")
        }
    }

    suspend fun fetchVotePageHtml(votePage: String): String? {
        val url = buildWikiUrl(API,
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

    internal suspend fun fetchVoteDataHtml(fullText: String, session: VoteSession): String? {
        val url = buildWikiUrl(API,
            "action" to "parse",
            "text" to fullText,
            "prop" to "text",
            "contentmodel" to "wikitext",
            "assert" to "user", "assertuser" to session.name,
            "disablelimitreport" to "true",
            "format" to "json"
        )
        val body = WikiAuthHelper.httpGetWithTicket(url, session.currentRequest()) ?: return null
        val json = checkedWikiResponse(body)
        return json.jsonObject["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
    }

    internal suspend fun submitVoteOperation(session: VoteSession, token: String, operation: VoteSubmitOperation): VoteSubmitResult {
        val ticket = session.currentRequest()
        val request = Request.Builder().url(API).apply {
            post(formBodyOf(
                "action" to "pollsubmitvote",
                "poll" to operation.pollId,
                "answer" to operation.answer,
                "assert" to "user", "assertuser" to session.name,
                "token" to token,
                "format" to "json"
            ))
            header("Cookie", ticket.cookies)
            header("User-Agent", "CalabiYauVoice/2.0 (Android)")
            header("Accept", "application/json, text/javascript, */*; q=0.01")
            header("Origin", "https://wiki.biligame.com")
            header("Referer", VOTE_PAGE_URL)
            header("X-Requested-With", "XMLHttpRequest")
        }.build()
        return WikiEngine.client.newCall(request).awaitResponseText().let { resp ->
            WikiAuthHelper.syncResponseCookies(resp.headers.values("Set-Cookie"), ticket)
            val body = resp.body
            val success = resp.isSuccessful && confirmsVote(body, operation)
            VoteSubmitResult(
                success = success,
                statusCode = resp.code,
                message = if (success) null else "未确认投票结果，请刷新核对"
            )
        }
    }

    internal fun confirmsVote(body: String, operation: VoteSubmitOperation): Boolean {
        return runCatching {
            val root = SharedJson.parseToJsonElement(body).jsonObject
            if ("error" in root) return false
            val result = root["pollsubmitvote"]?.jsonObject?.get("result")?.jsonPrimitive?.content ?: return false
            // AJAXPoll returns the updated poll HTML even for some failed operations. Check identity and actual selection.
            if (result.equals("Success", ignoreCase = true)) return true
            val doc = org.jsoup.Jsoup.parseBodyFragment(result)
            val poll = doc.getElementById("ajaxpoll-id-${operation.pollId}") ?: doc.getElementById("ajaxpoll-container-${operation.pollId}") ?: return false
            val answer = poll.selectFirst("div[answer=1]") ?: return false
            val selected = answer.selectFirst(".ajaxpoll-our-vote, input[checked]") != null
            selected == (operation.answer == "1")
        }.getOrDefault(false)
    }

}
