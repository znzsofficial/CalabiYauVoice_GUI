package com.nekolaska.calabiyau.feature.wiki.voting.parser

import com.nekolaska.calabiyau.feature.wiki.voting.model.PollCandidate
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollConfig
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollData
import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object VotingParsers {

    fun parsePollConfigFromHtml(html: String): PollConfig? {
        val document = Jsoup.parse(html)
        if (document.selectFirst(".kqp-poll-container") == null) return null

        // Lines like 名称：… / 限制票数：… / 结束时间：… separated by <br>.
        val configMap = mutableMapOf<String, String>()
        document.selectFirst(".poll-info")?.let { info ->
            textLines(info).forEach { line ->
                val separator = when {
                    "：" in line -> "："
                    ":" in line -> ":"
                    else -> null
                }
                if (separator != null) {
                    val parts = line.split(separator, limit = 2)
                    if (parts.size == 2) configMap[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        val candidates = document.select("input[type=checkbox][value]").mapNotNull { checkbox ->
            val name = checkbox.attr("value").trim().ifBlank { return@mapNotNull null }
            val card = checkbox.closest(".card-content")
                ?: checkbox.nextElementSibling()?.takeIf { it.hasClass("card-content") }
            val imageUrl = card?.selectFirst("img[src]")?.absUrl("src").takeUnless { it.isNullOrBlank() }
                ?: card?.selectFirst("img[src]")?.attr("src").orEmpty()
            PollCandidate(name, imageUrl)
        }
        if (candidates.isEmpty()) return null

        return PollConfig(
            name = configMap["名称"] ?: "未命名投票",
            voteLimit = configMap["限制票数"]?.toIntOrNull() ?: 1,
            endTime = configMap["结束时间"] ?: "",
            candidates = candidates
        )
    }

    fun parseAjaxPollElements(html: String): List<PollData> {
        val containers = Jsoup.parse(html).select("[id^=ajaxpoll-container-], [class*=ajaxpoll-container]")
        if (containers.isEmpty()) return emptyList()

        // Never compress missing slots: the response order mirrors the generated <poll> tags.
        return containers.map { container ->
            val ids = container.select(".poll-id").flatMap { it.classNames() }.filter { it.matches(hexId) }.toMutableList()
            if (container.id().startsWith("ajaxpoll-container-")) {
                val id = container.id().removePrefix("ajaxpoll-container-")
                if (!id.matches(hexId)) return emptyList()
                ids += id
            }
            val pollId = ids.distinctBy { it.lowercase() }.singleOrNull() ?: return emptyList()

            val answerBlock = container.selectFirst("div[answer=1]")
            var userVoted = false
            var votes: Int? = null

            if (answerBlock != null) {
                userVoted = answerBlock.selectFirst(".ajaxpoll-our-vote") != null ||
                    answerBlock.select("input[checked]").isNotEmpty()
                votes = answerBlock.selectFirst(".ajaxpoll-answer-vote span")?.text()?.trim()?.toIntOrNull()
            }

            if (votes == null) {
                votes = Regex("""共有(\d+)\s*人投票""")
                    .find(container.text())
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            PollData(pollId = pollId, votes = votes, userVoted = userVoted,
                question = container.selectFirst(".ajaxpoll-question")?.text()?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    private val hexId = Regex("[A-Fa-f0-9]+")

    private fun textLines(element: Element): List<String> =
        element.textWithLineBreaks()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun Element.textWithLineBreaks(): String =
        HtmlText.textWithLineBreaks(this)
}
