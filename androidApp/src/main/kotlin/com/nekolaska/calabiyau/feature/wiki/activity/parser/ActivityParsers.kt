package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.activity.model.ACTIVITY_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder

data class ParsedActivity(val entry: ActivityEntry) {
    val detailPageTitle: String? get() = entry.detailPageTitle
}

object ActivityParsers {

    private val pageBase = URI("https://wiki.biligame.com/klbq/")
    private val startHeaders = setOf("开始", "开始时间", "活动开始时间")
    private val endHeaders = setOf("结束", "结束时间", "活动结束时间")
    private val nonArticleNamespaces = setOf(
        "文件", "file", "图像", "image", "媒体", "media", "特殊", "special", "分类", "category", "模板", "template"
    )

    fun parseActivities(html: String): List<ParsedActivity> {
        val document = Jsoup.parse(html)
        // 现网结构：div.klbq-event-card 卡片网格（klbq-event-card__title/time-item/desc/imagebox）
        val cards = document.select("div.klbq-event-card")
        if (cards.isNotEmpty()) {
            val cardParsed = cards.mapNotNull { card -> parseEventCard(card) }
            if (cardParsed.isNotEmpty()) {
                return WikiParseLogger.finishList("ActivityApi.parseActivities", cardParsed, html, "cards=${cards.size}")
            }
        }
        return parseLegacyActivityTable(document, html)
    }

    private fun parseEventCard(card: Element): ParsedActivity? {
        val titleBox = card.selectFirst("[class*=title]") ?: return null
        val title = titleBox.text().trim()
        if (title.isBlank()) return null

        var startTime = ""
        var endTime = ""
        card.select("[class*=time-item]").forEach { item ->
            val label = item.selectFirst("[class*=label]")?.text()?.trim().orEmpty()
            val value = item.selectFirst("[class*=value]")?.text()?.trim().orEmpty()
            when {
                label.contains("开始") -> startTime = value
                label.contains("结束") -> endTime = value
            }
        }

        val destination = activityDestination(titleBox.select("a[href]") + card.select("[class*=imagebox] a[href]"))

        return ParsedActivity(
            entry = ActivityEntry(
                title = title,
                startTime = startTime,
                endTime = endTime,
                description = card.selectFirst("[class*=desc]")?.text()?.trim().orEmpty(),
                imageUrl = card.selectFirst("[class*=imagebox] img")?.attr("src"),
                wikiUrl = destination.url,
                detailPageTitle = destination.pageTitle
            )
        )
    }

    private fun parseLegacyActivityTable(document: Document, html: String): List<ParsedActivity> {
        val rows = document.select("table.klbqtable tr")

        val parsed = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 4 || row.parent()?.normalName() == "thead" || cells.all { it.normalName() == "th" }) {
                return@mapNotNull null
            }

            val titleCell = cells[0]
            val titleCellText = HtmlText.clean(titleCell.html())
            val title = titleCellText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val startTime = HtmlText.clean(cells[1].html())
            val endTime = HtmlText.clean(cells[2].html())
            val description = HtmlText.clean(cells[3].html())

            val hasColumnLabels = startTime.trimEnd(':', '：') in startHeaders && endTime.trimEnd(':', '：') in endHeaders
            if (title.isBlank() || hasColumnLabels) return@mapNotNull null
            val destination = activityDestination(titleCell.select("a[href]"))

            ParsedActivity(
                entry = ActivityEntry(
                    title = title,
                    startTime = startTime,
                    endTime = endTime,
                    description = description,
                    imageUrl = titleCell.selectFirst("img")?.attr("src"),
                    wikiUrl = destination.url,
                    detailPageTitle = destination.pageTitle
                )
            )
        }

        return WikiParseLogger.finishList("ActivityApi.parseActivities", parsed, html, "rows=${rows.size}")
    }

    fun extractFirstFileTitle(detailHtml: String): String? {
        val document = Jsoup.parse(detailHtml)
        return document.select("a[href]").firstNotNullOfOrNull { link ->
            webUri(link)?.let(::wikiPageTitle)?.takeIf { it.startsWith("文件:") || it.startsWith("File:", ignoreCase = true) }
        }
    }

    private data class Destination(val url: String, val pageTitle: String? = null)

    /** Cover images may link to either a File page or the article. A caption/tooltip is not a page identity. */
    private fun activityDestination(links: List<Element>): Destination {
        var coverArticle: Destination? = null
        var external: Destination? = null
        for (link in links) {
            if (link.hasClass("new")) continue
            val uri = webUri(link) ?: continue
            if (!uri.host.equals(pageBase.host, ignoreCase = true)) {
                if (external == null && link.hasText()) external = Destination(uri.toString())
                continue
            }
            val title = wikiPageTitle(uri) ?: continue
            if (title == "活动" || title.substringBefore(':', "").lowercase() in nonArticleNamespaces) continue
            // A link to a particular section belongs on the original page; opening the native
            // article at the top silently loses the selected reward track.
            val article = Destination(uri.toString(), title.takeIf { uri.rawFragment.isNullOrEmpty() })
            if (link.hasText()) return article
            if (coverArticle == null) coverArticle = article
        }
        return external ?: coverArticle ?: Destination(ACTIVITY_PAGE_URL)
    }

    private fun webUri(link: Element): URI? = runCatching { pageBase.resolve(link.attr("href").trim()) }.getOrNull()?.takeIf {
        !it.host.isNullOrEmpty() && it.userInfo == null && (it.scheme.equals("https", true) || it.scheme.equals("http", true))
    }

    private fun wikiPageTitle(uri: URI): String? {
        val defaultPort = if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
        if (!uri.host.equals(pageBase.host, ignoreCase = true) || (uri.port != -1 && uri.port != defaultPort)) return null
        val path = uri.path
        if (!path.startsWith("/klbq/")) return null
        val query = uri.rawQuery?.split('&').orEmpty().filterNot { it == "action=view" }
        val title = if (path == "/klbq/index.php") {
            val param = query.singleOrNull()?.takeIf { it.startsWith("title=") } ?: return null
            URLDecoder.decode(param.removePrefix("title="), "UTF-8")
        } else {
            if (query.isNotEmpty()) return null
            path.removePrefix("/klbq/")
        }
        return title.replace('_', ' ').trim().takeIf { it.isNotEmpty() && !it.endsWith(".php", ignoreCase = true) }
    }
}
