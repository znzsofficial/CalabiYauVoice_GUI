package com.nekolaska.calabiyau.feature.wiki.submission.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.submission.model.SubmissionEntry
import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup

object SubmissionParsers {
    private const val SITE_BASE = "https://wiki.biligame.com"
    private const val PAGE_BASE = "$SITE_BASE/klbq/"

    fun parseEntries(html: String): List<SubmissionEntry> {
        val document = Jsoup.parse(html)
        val rows = document.select("table tr")
        val entries = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 5) return@mapNotNull null

            val titleLink = cells[0].selectFirst("a[href]") ?: return@mapNotNull null
            val title = HtmlText.clean(cells[0].html()).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            val date = HtmlText.clean(cells[1].html())
            val author = HtmlText.clean(cells[2].html())
            val type = HtmlText.clean(cells[3].html())
            val topic = HtmlText.clean(cells[4].html())

            if (title.isBlank() || title == "标题" || date == "时间") return@mapNotNull null

            SubmissionEntry(
                title = title,
                date = date,
                author = author,
                type = type,
                topic = topic,
                wikiUrl = toAbsoluteWikiUrl(titleLink.attr("href"))
            )
        }

        return WikiParseLogger.finishList("SubmissionParsers.parseEntries", entries, html, "rows=${rows.size}")
    }

    private fun toAbsoluteWikiUrl(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "$SITE_BASE$href"
        else -> "$PAGE_BASE${href.trimStart('/')}"
    }

}
