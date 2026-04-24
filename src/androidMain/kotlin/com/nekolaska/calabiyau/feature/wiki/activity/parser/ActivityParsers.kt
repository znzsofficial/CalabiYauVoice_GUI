package com.nekolaska.calabiyau.feature.wiki.activity.parser

import android.text.Html
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.activity.model.ACTIVITY_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import org.jsoup.Jsoup
import java.net.URLDecoder

data class ParsedActivity(
    val entry: ActivityEntry,
    val detailPageTitle: String?
)

object ActivityParsers {

    private const val SITE_BASE = "https://wiki.biligame.com"
    private const val PAGE_BASE = "$SITE_BASE/klbq/"

    fun parseActivities(html: String): List<ParsedActivity> {
        val document = Jsoup.parse(html)
        val rows = document.select("table.klbqtable tr")

        val parsed = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 4) return@mapNotNull null

            val titleCell = cells[0]
            val titleCellText = cleanHtml(titleCell.html())
            val title = titleCellText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val startTime = cleanHtml(cells[1].html())
            val endTime = cleanHtml(cells[2].html())
            val description = cleanHtml(cells[3].html())

            val detailLink = titleCell.selectFirst("a[title], a[href]")
            val detailPageTitle = detailLink?.attr("title")?.takeIf { it.isNotBlank() }
                ?: detailLink?.attr("href")?.let(::extractPageTitleFromHref)
            val wikiUrl = detailLink?.attr("href")
                ?.let(::toAbsoluteWikiUrl)
                ?: ACTIVITY_PAGE_URL

            val isHeaderRow = title in setOf("活动", "活动名称", "名称") ||
                startTime.contains("开始", ignoreCase = true) ||
                endTime.contains("结束", ignoreCase = true) ||
                description.contains("简介", ignoreCase = true) ||
                description.contains("说明", ignoreCase = true)

            if (title.isBlank() || isHeaderRow) return@mapNotNull null

            ParsedActivity(
                entry = ActivityEntry(
                    title = title,
                    startTime = startTime,
                    endTime = endTime,
                    description = description,
                    imageUrl = titleCell.selectFirst("img")?.attr("src"),
                    wikiUrl = wikiUrl
                ),
                detailPageTitle = detailPageTitle
            )
        }

        return WikiParseLogger.finishList("ActivityApi.parseActivities", parsed, html, "rows=${rows.size}")
    }

    fun extractFirstFileTitle(detailHtml: String): String? {
        val document = Jsoup.parse(detailHtml)
        return document.select("a[href]").firstNotNullOfOrNull { link ->
            val pageTitle = extractPageTitleFromHref(link.attr("href")) ?: return@firstNotNullOfOrNull null
            if (pageTitle.startsWith("文件:") || pageTitle.startsWith("File:")) pageTitle else null
        }
    }

    private fun extractPageTitleFromHref(href: String): String? {
        val encodedPart = when {
            "/klbq/" in href -> href.substringAfter("/klbq/")
            href.startsWith("/") -> href.removePrefix("/")
            else -> href
        }
            .substringBefore('#')
            .substringBefore('?')
            .takeIf { it.isNotBlank() }
            ?: return null

        return runCatching { URLDecoder.decode(encodedPart, "UTF-8") }.getOrNull()
    }

    private fun toAbsoluteWikiUrl(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "$SITE_BASE$href"
        else -> "$PAGE_BASE${href.trimStart('/')}"
    }

    fun cleanHtml(raw: String): String {
        val normalized = raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("￼", "")
            .replace("\uFFFC", "")
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }
}
