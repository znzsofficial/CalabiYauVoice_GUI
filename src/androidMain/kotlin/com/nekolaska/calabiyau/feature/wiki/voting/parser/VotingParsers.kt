package com.nekolaska.calabiyau.feature.wiki.voting.parser

import com.nekolaska.calabiyau.feature.wiki.voting.model.PollCandidate
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollConfig
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollData

object VotingParsers {

    fun parsePollConfigFromHtml(html: String): PollConfig? {
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

    fun parseAjaxPollElements(html: String): List<PollData> {
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
}
