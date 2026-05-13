package com.nekolaska.calabiyau.feature.wiki.collaboration.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationEvent
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationPage
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationTimelineItem
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationTimelineYear
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object CollaborationParsers {

    fun parsePage(html: String): CollaborationPage {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, style, script, sup.reference, .references, .mw-references-wrap").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        return CollaborationPage(
            timelineYears = parseTimeline(content),
            events = parseEvents(content)
        )
    }

    private fun parseTimeline(content: Element): List<CollaborationTimelineYear> {
        val heading = content.select("h2").firstOrNull { it.headingText() == "时间轴" } ?: return emptyList()
        val years = mutableListOf<CollaborationTimelineYear>()
        var currentYear: String? = null
        var currentItems = mutableListOf<CollaborationTimelineItem>()

        fun flush() {
            val year = currentYear.orEmpty()
            if (year.isNotBlank() && currentItems.isNotEmpty()) {
                years += CollaborationTimelineYear(year, currentItems.toList())
            }
            currentItems = mutableListOf()
        }

        elementsUntilNextH2(heading).flatMap { it.select("li.year, li.date") }.forEach { item ->
            if (item.hasClass("year")) {
                flush()
                currentYear = item.selectFirst(".year1")?.cleanText().orEmpty().ifBlank { item.cleanText() }
            } else if (item.hasClass("date")) {
                val date = item.selectFirst(".time")?.cleanText().orEmpty()
                val title = item.selectFirst(".content")?.cleanText().orEmpty()
                if (date.isNotBlank() || title.isNotBlank()) {
                    currentItems += CollaborationTimelineItem(date, title)
                }
            }
        }
        flush()
        return years
    }

    private fun parseEvents(content: Element): List<CollaborationEvent> {
        val detailHeading = content.select("h2").firstOrNull { it.headingText() == "联动具体信息" } ?: return emptyList()
        val elements = elementsUntilNextH2(detailHeading)
        val events = mutableListOf<CollaborationEvent>()
        var title: String? = null
        var sectionTitle: String? = null
        var parts = mutableListOf<Element>()

        fun flush() {
            val eventTitle = title.orEmpty()
            if (eventTitle.isBlank() || parts.isEmpty()) {
                parts.clear()
                return
            }
            parseEvent(eventTitle, sectionTitle, parts)?.let { events += it }
            parts.clear()
        }

        elements.forEach { element ->
            when (element.tagName()) {
                "h3" -> {
                    flush()
                    title = element.headingText()
                    sectionTitle = null
                }
                "h4" -> {
                    flush()
                    sectionTitle = element.headingText()
                }
                else -> if (title != null) parts.add(element)
            }
        }
        flush()
        return events
    }

    private fun parseEvent(title: String, sectionTitle: String?, elements: List<Element>): CollaborationEvent? {
        val textLines = elements.flatMap(::extractUsefulLines)
            .map { it.cleanLine() }
            .filter { it.isNotBlank() && it != "×" && it != "放大" }
            .distinct()
        val imageUrls = elements.flatMap { element ->
            element.select("img").mapNotNull { img -> img.imageUrl() }
        }.filterNot { it.contains("60000038") }.distinct()
        val publishInfo = textLines.firstOrNull { it.startsWith("本内容在") }
        val date = textLines.firstOrNull { it.startsWith("活动时间：") }?.substringAfter("活动时间：")?.trim()
        val theme = textLines.firstOrNull { it.startsWith("主题：") }?.substringAfter("主题：")?.trim()
        val content = textLines
            .filterNot { line ->
                line == publishInfo || line.startsWith("活动时间：") || line.startsWith("主题：") || line == "活动店铺："
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (content.isBlank() && imageUrls.isEmpty() && date.isNullOrBlank() && theme.isNullOrBlank()) return null
        return CollaborationEvent(
            title = title,
            sectionTitle = sectionTitle,
            publishInfo = publishInfo,
            date = date,
            theme = theme,
            content = content,
            imageUrls = imageUrls
        )
    }

    private fun extractUsefulLines(element: Element): List<String> {
        return when (element.tagName()) {
            "p" -> element.html().split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).map { Jsoup.parse(it).text() }
            "ul", "ol" -> element.select("li").map { it.cleanText() }
            "dl" -> element.select("dt,dd").map { it.cleanText() }
            "div" -> {
                val className = element.className()
                when {
                    className.contains("thumb") || className.contains("gallery") -> emptyList()
                    element.select("p, ul, ol, dl").isNotEmpty() -> element.children().flatMap(::extractUsefulLines)
                    else -> listOf(element.cleanText())
                }
            }
            else -> emptyList()
        }
    }

    private fun elementsUntilNextH2(heading: Element): List<Element> {
        return generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() != "h2" }
            .toList()
    }

    private fun Element.headingText(): String = selectFirst(".mw-headline")?.cleanText().orEmpty().ifBlank { cleanText() }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }

    private fun String.cleanLine(): String = replace(Regex("\\s+"), " ").trim()

    private fun Element.imageUrl(): String? {
        val src = attr("src").takeIf { it.isNotBlank() } ?: return null
        return WikiImageUrls.originalFromThumbnail(src)
    }
}
