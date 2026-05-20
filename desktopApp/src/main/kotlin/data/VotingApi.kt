package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request

/**
 * Desktop 端 Wiki 投票 API。
 * 通过解析 Wiki 投票页面获取候选项列表和图片，
 * 再通过 AJAXPoll 扩展 API 获取投票数据并提交投票。
 *
 * 所有请求使用 [WikiEngine.client]，自动携带 [WikiCookieManager] 注入的 Cookie。
 */
object VotingApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val VOTE_PAGE = "角色时装投票"

    // ───── 数据模型 ──────────────────────────────────────────────────

    data class PollConfig(
        val name: String,
        val voteLimit: Int,
        val endTime: String,
        val candidates: List<PollCandidate>
    )

    data class PollCandidate(
        val name: String,
        val imageUrl: String
    )

    data class PollData(
        val pollId: String,
        val votes: Int,
        val userVoted: Boolean
    )

    data class VoteState(
        val config: PollConfig,
        val totalParticipants: Int,
        val totalPollId: String,
        val userVotedTotal: Boolean,
        val pollDataMap: Map<String, PollData>
    )

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

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
     *    需要已登录（Cookie 通过 WikiEngine CookieJar 自动携带）。
     */
    suspend fun fetchVoteData(config: PollConfig): ApiResult<VoteState> = withContext(Dispatchers.IO) {
        try {
            val pollTexts = config.candidates.map { candidate ->
                "<poll show-results-before-voting=1>\n${config.name}-${candidate.name}\n投给TA\n</poll>"
            }
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
            val body = httpGet(url)
                ?: return@withContext ApiResult.Error("请求投票数据失败")

            val json = SharedJson.parseToJsonElement(body)
            val html = json.jsonObject["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return@withContext ApiResult.Error("无法解析投票数据")

            val pollDataList = parseAjaxPollElements(html)
            if (pollDataList.isEmpty()) {
                return@withContext ApiResult.Error("服务器未返回投票数据，可能未登录或被拦截")
            }

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
     */
    suspend fun submitVotes(
        voteState: VoteState,
        selectedNames: Set<String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = fetchCsrfToken()
                ?: return@withContext ApiResult.Error("获取 CSRF Token 失败，可能未登录")

            val operations = mutableListOf<Pair<String, String>>()

            for (candidate in voteState.config.candidates) {
                val data = voteState.pollDataMap[candidate.name] ?: continue
                val isSelected = candidate.name in selectedNames
                if (isSelected && !data.userVoted) {
                    operations.add(data.pollId to "1")
                } else if (!isSelected && data.userVoted) {
                    operations.add(data.pollId to "0")
                }
            }

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
                return@withContext ApiResult.Success(Unit)
            }

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

    private fun parsePollConfigFromHtml(html: String): PollConfig? {
        if (!html.contains("kqp-poll-container")) return null

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

        val candidates = mutableListOf<PollCandidate>()
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
     * 从 parse API 返回的 HTML 中解析所有 ajaxpoll 元素。
     */
    private fun parseAjaxPollElements(html: String): List<PollData> {
        val results = mutableListOf<PollData>()

        val containerStarts = mutableListOf<Int>()
        val containerPrefix = "ajaxpoll-container-"
        var searchFrom = 0
        while (true) {
            val idx = html.indexOf(containerPrefix, searchFrom)
            if (idx == -1) break
            val divStart = html.lastIndexOf("<div", idx)
            if (divStart != -1) containerStarts.add(divStart)
            searchFrom = idx + containerPrefix.length
        }

        if (containerStarts.isEmpty()) return results

        for (i in containerStarts.indices) {
            val start = containerStarts[i]
            val end = if (i < containerStarts.size - 1) containerStarts[i + 1] else html.length
            val block = html.substring(start, end)

            val pollIdMatch = Regex("""poll-id\s+([A-Fa-f0-9]+)""").find(block)
                ?: continue
            val pollId = pollIdMatch.groupValues[1]

            var votes = 0
            var userVoted = false

            val answerBlockMatch = Regex(
                """<div[^>]*answer="1"[^>]*>(.*?)(?=<div[^>]*class="ajaxpoll-info")""",
                RegexOption.DOT_MATCHES_ALL
            ).find(block)

            if (answerBlockMatch != null) {
                val answerContent = answerBlockMatch.groupValues[1]

                userVoted = answerContent.contains("ajaxpoll-our-vote")

                if (!userVoted) {
                    userVoted = Regex("""checked=""")
                        .containsMatchIn(answerContent)
                }

                val voteSpanMatch = Regex(
                    """ajaxpoll-answer-vote[^>]*>\s*<span[^>]*>(\d+)</span>""",
                    RegexOption.DOT_MATCHES_ALL
                ).find(answerContent)
                if (voteSpanMatch != null) {
                    votes = voteSpanMatch.groupValues[1].toIntOrNull() ?: 0
                }
            }

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

    private fun fetchCsrfToken(): String? {
        val url = buildUrl(
            "action" to "query",
            "meta" to "tokens",
            "type" to "csrf",
            "format" to "json"
        )
        val body = httpGet(url) ?: return null
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
        val request = Request.Builder()
            .url(url)
            .build()
        return WikiEngine.client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) resp.body.string() else null
        }
    }
}
