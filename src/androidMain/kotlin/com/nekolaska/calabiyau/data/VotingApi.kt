package com.nekolaska.calabiyau.data

import android.webkit.CookieManager
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request

/**
 * Wiki 投票 API。
 * 通过解析 Wiki 投票页面获取候选项列表和图片，
 * 再通过 AJAXPoll 扩展 API 获取投票数据并提交投票。
 */
object VotingApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val VOTE_PAGE = "角色时装投票"

    // ───── 数据模型 ──────────────────────────────────────────────────

    /** 投票配置（从页面 HTML 解析） */
    data class PollConfig(
        val name: String,         // 投票名称（如"传说时装投票"）
        val voteLimit: Int,       // 限制票数
        val endTime: String,      // 结束时间
        val candidates: List<PollCandidate>  // 候选项列表
    )

    /** 单个候选项 */
    data class PollCandidate(
        val name: String,         // 时装名称（如"艾卡【日珥战姬】"）
        val imageUrl: String      // 图片 URL
    )

    /** 投票数据（从 AJAXPoll 解析） */
    data class PollData(
        val pollId: String,       // AJAXPoll 内部 ID
        val votes: Int,           // 得票数
        val userVoted: Boolean    // 当前用户是否已投
    )

    /** 完整投票状态 */
    data class VoteState(
        val config: PollConfig,
        val totalParticipants: Int,
        val totalPollId: String,
        val userVotedTotal: Boolean,
        val pollDataMap: Map<String, PollData>  // candidateName → PollData
    )


    // ───── 公开方法 ──────────────────────────────────────────────────

    /**
     * 1. 解析投票页面 HTML，提取候选人列表和图片。
     */
    suspend fun fetchPollConfig(): ApiResult<PollConfig> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(
                "action" to "parse",
                "page" to VOTE_PAGE,
                "prop" to "text",
                "format" to "json"
            )
            val body = httpGet(url) ?: return@withContext ApiResult.Error("请求失败")
            val json = SharedJson.parseToJsonElement(body)
            val html = json.jsonObject["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return@withContext ApiResult.Error("无法解析页面内容")

            val config = parsePollConfigFromHtml(html)
                ?: return@withContext ApiResult.Error("未找到投票组件")
            ApiResult.Success(config)
        } catch (e: Exception) {
            ApiResult.Error("获取投票页失败: ${e.message}")
        }
    }

    /**
     * 2. 批量获取所有候选人的投票数据 + 总计锚点。
     *    需要已登录 Cookie（用于判断当前用户是否投过）。
     */
    suspend fun fetchVoteData(config: PollConfig): ApiResult<VoteState> = withContext(Dispatchers.IO) {
        val cookies = getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未检测到登录 Cookie，请先在 Wiki 页面登录")
        }

        try {
            // 构建批量 <poll> wikitext
            val pollTexts = config.candidates.map { candidate ->
                "<poll show-results-before-voting=1>\n${config.name}-${candidate.name}\n投给TA\n</poll>"
            }
            // 总计锚点
            val totalPollText = "<poll show-results-before-voting=1>\n${config.name}-总计锚点\n已参加\n</poll>"
            val fullText = (pollTexts + totalPollText).joinToString("\n")

            val url = buildUrl(
                "action" to "parse",
                "text" to fullText,
                "prop" to "text",
                "contentmodel" to "wikitext",
                "disablelimitreport" to "true",
                "format" to "json"
            )
            val body = httpGetWithCookies(url, cookies)
                ?: return@withContext ApiResult.Error("请求投票数据失败")

            val json = SharedJson.parseToJsonElement(body)
            val html = json.jsonObject["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return@withContext ApiResult.Error("无法解析投票数据")

            // 解析所有 ajaxpoll 元素
            val pollDataList = parseAjaxPollElements(html)
            if (pollDataList.isEmpty()) {
                return@withContext ApiResult.Error("服务器未返回投票数据，可能被拦截")
            }

            // 映射：前 N 个对应候选人，最后一个是总计锚点
            val pollDataMap = mutableMapOf<String, PollData>()
            config.candidates.forEachIndexed { index, candidate ->
                if (index < pollDataList.size) {
                    pollDataMap[candidate.name] = pollDataList[index]
                }
            }

            val totalData = if (pollDataList.size > config.candidates.size) {
                pollDataList.last()
            } else null

            ApiResult.Success(
                VoteState(
                    config = config,
                    totalParticipants = totalData?.votes ?: 0,
                    totalPollId = totalData?.pollId ?: "",
                    userVotedTotal = totalData?.userVoted ?: false,
                    pollDataMap = pollDataMap
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("获取投票数据失败: ${e.message}")
        }
    }

    /**
     * 3. 提交投票。
     *    @param voteState 当前完整投票状态
     *    @param selectedNames 用户选中的候选项名称集合
     */
    suspend fun submitVotes(
        voteState: VoteState,
        selectedNames: Set<String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cookies = getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未登录")
        }

        try {
            // 获取 CSRF Token
            val token = fetchCsrfToken(cookies)
                ?: return@withContext ApiResult.Error("获取 CSRF Token 失败")

            // 构建需要提交的投票操作
            val operations = mutableListOf<Pair<String, String>>() // pollId, answer

            for (candidate in voteState.config.candidates) {
                val data = voteState.pollDataMap[candidate.name] ?: continue
                val isSelected = candidate.name in selectedNames
                if (isSelected && !data.userVoted) {
                    operations.add(data.pollId to "1")
                } else if (!isSelected && data.userVoted) {
                    operations.add(data.pollId to "0")
                }
            }

            // 总计锚点处理
            if (selectedNames.isNotEmpty()) {
                if (!voteState.userVotedTotal && voteState.totalPollId.isNotEmpty()) {
                    operations.add(voteState.totalPollId to "1")
                }
            } else {
                if (voteState.userVotedTotal && voteState.totalPollId.isNotEmpty()) {
                    operations.add(voteState.totalPollId to "0")
                }
            }

            if (operations.isEmpty()) {
                return@withContext ApiResult.Success(Unit) // 没有变更
            }

            // 逐个提交投票（AJAXPoll 不支持批量）
            for ((pollId, answer) in operations) {
                val formBody = FormBody.Builder()
                    .add("action", "pollsubmitvote")
                    .add("poll", pollId)
                    .add("answer", answer)
                    .add("token", token)
                    .build()

                val request = Request.Builder()
                    .url(API)
                    .post(formBody)
                    .header("Cookie", cookies)
                    .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                    .build()

                WikiEngine.client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext ApiResult.Error("提交投票失败: HTTP ${resp.code}")
                    }
                }
            }

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error("提交投票失败: ${e.message}")
        }
    }

    // ───── HTML 解析 ─────────────────────────────────────────────────

    /**
     * 从投票页面 HTML 中解析投票配置和候选人列表。
     */
    private fun parsePollConfigFromHtml(html: String): PollConfig? {
        // 检查是否包含投票容器
        if (!html.contains("kqp-poll-container")) return null

        // 解析配置：名称、限制票数、结束时间
        val pollInfoRegex = Regex("""<div\s+class="poll-info"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        val pollInfoMatch = pollInfoRegex.find(html) ?: return null
        val infoText = pollInfoMatch.groupValues[1]
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace(Regex("<[^>]+>"), "")

        val configMap = mutableMapOf<String, String>()
        infoText.split("\n").forEach { line ->
            val separator = if ("：" in line) "：" else if (":" in line) ":" else null
            if (separator != null) {
                val parts = line.split(separator, limit = 2)
                if (parts.size == 2) {
                    configMap[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        val pollName = configMap["名称"] ?: "未命名投票"
        val limit = configMap["限制票数"]?.toIntOrNull() ?: 1
        val endTime = configMap["结束时间"] ?: ""

        // 解析候选人：提取 checkbox value 和 img src
        val candidates = mutableListOf<PollCandidate>()
        // 正则匹配每个 poll-card 的 checkbox value 和 img src
        val cardRegex = Regex(
            """<input\s+type="checkbox"\s+value="([^"]+)"[^/]*/>\s*<div\s+class="card-content">.*?<img\s+[^>]*src="([^"]+)"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL
        )
        cardRegex.findAll(html).forEach { match ->
            val name = decodeHtmlEntities(match.groupValues[1])
            val imageUrl = match.groupValues[2]
            candidates.add(PollCandidate(name, imageUrl))
        }

        if (candidates.isEmpty()) return null

        return PollConfig(
            name = pollName,
            voteLimit = limit,
            endTime = endTime,
            candidates = candidates
        )
    }

    /**
     * 从 parse API 返回的 HTML 中解析所有 ajaxpoll 元素，提取 poll ID、投票数、是否已投。
     *
     * AJAXPoll 扩展为每个 <poll> 生成的结构：
     * ```
     * <div id="ajaxpoll-container-{ID}">
     *   <div id="ajaxpoll-id-{ID}" class="ajaxpoll">
     *     ...
     *     <div class="ajaxpoll-answer" answer="1">
     *       <div class="ajaxpoll-answer-name">...<input checked="true"/>...</div>
     *       <div class="ajaxpoll-answer-vote ajaxpoll-our-vote"> <!-- 仅已登录有权限时 -->
     *         <span title="...">票数</span>
     *       </div>
     *     </div>
     *     <div class="ajaxpoll-info">
     *       自...共有N 人投票。
     *       <div class="ajaxpoll-id-info">poll-id {ID}</div>
     *     </div>
     *   </div>
     * </div>
     * ```
     */
    private fun parseAjaxPollElements(html: String): List<PollData> {
        val results = mutableListOf<PollData>()

        // 用 ajaxpoll-container 容器来分割每个独立的 poll 块
        // 每个块以 <div id="ajaxpoll-container-" 开头
        val containerStarts = mutableListOf<Int>()
        val containerPrefix = "ajaxpoll-container-"
        var searchFrom = 0
        while (true) {
            val idx = html.indexOf(containerPrefix, searchFrom)
            if (idx == -1) break
            // 回退到 <div 的开始位置
            val divStart = html.lastIndexOf("<div", idx)
            if (divStart != -1) containerStarts.add(divStart)
            searchFrom = idx + containerPrefix.length
        }

        if (containerStarts.isEmpty()) return results

        // 将 HTML 分割成各个 poll 块
        for (i in containerStarts.indices) {
            val start = containerStarts[i]
            val end = if (i < containerStarts.size - 1) containerStarts[i + 1] else html.length
            val block = html.substring(start, end)

            // 1. 提取 poll-id
            val pollIdMatch = Regex("""poll-id\s+([A-Fa-f0-9]+)""").find(block)
                ?: continue
            val pollId = pollIdMatch.groupValues[1]

            var votes = 0
            var userVoted = false

            // 2. 提取投票数和用户投票状态
            //    查找 answer="1" 的 div 块（第一个答案选项）
            val answerBlockMatch = Regex(
                """<div[^>]*answer="1"[^>]*>(.*?)(?=<div[^>]*class="ajaxpoll-info")""",
                RegexOption.DOT_MATCHES_ALL
            ).find(block)

            if (answerBlockMatch != null) {
                val answerContent = answerBlockMatch.groupValues[1]

                // 检查 ajaxpoll-our-vote class（已登录时，若用户已投此项）
                userVoted = answerContent.contains("ajaxpoll-our-vote")

                // 如果没有 our-vote class，检查 radio input 是否 checked
                if (!userVoted) {
                    userVoted = Regex("""checked=""")
                        .containsMatchIn(answerContent)
                }

                // 从 ajaxpoll-answer-vote 内的 <span> 提取投票数
                val voteSpanMatch = Regex(
                    """ajaxpoll-answer-vote[^>]*>\s*<span[^>]*>(\d+)</span>""",
                    RegexOption.DOT_MATCHES_ALL
                ).find(answerContent)
                if (voteSpanMatch != null) {
                    votes = voteSpanMatch.groupValues[1].toIntOrNull() ?: 0
                }
            }

            // 3. Fallback: 从 ajaxpoll-info 中提取总票数
            //    格式如 "共有3 人投票" 或 "共有0 人投票"
            if (votes == 0) {
                val infoMatch = Regex("""共有(\d+)\s*人投票""").find(block)
                if (infoMatch != null) {
                    votes = infoMatch.groupValues[1].toIntOrNull() ?: 0
                }
            }

            results.add(PollData(pollId = pollId, votes = votes, userVoted = userVoted))
        }

        return results
    }

    // ───── 工具方法 ──────────────────────────────────────────────────

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&middot;", "·")
            .replace("\\u00b7", "·")
    }

    private fun getWikiCookies(): String? {
        return try {
            val cm = CookieManager.getInstance()
            // 根路径获取 .biligame.com 域的通用 Cookie（SESSDATA, DedeUserID 等）
            val rootCookies = cm.getCookie("https://wiki.biligame.com") ?: ""
            // /klbq/ 路径获取 Wiki session Cookie（gamecenter_wiki__session 等，path=/klbq/）
            val klbqCookies = cm.getCookie("https://wiki.biligame.com/klbq/") ?: ""
            // 合并去重
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

    private fun fetchCsrfToken(cookies: String): String? {
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

    private fun buildUrl(vararg params: Pair<String, String>): String {
        val query = params.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        return "$API?$query"
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
