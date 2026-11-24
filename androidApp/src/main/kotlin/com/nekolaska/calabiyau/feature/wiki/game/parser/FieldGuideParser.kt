package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.core.wiki.reading.GuideSpan
import com.nekolaska.calabiyau.core.wiki.reading.GuideText
import com.nekolaska.calabiyau.core.wiki.reading.WikiRichTextParser
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock.Kind
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideChapter
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Read article siblings, never the TOC, navigation templates, widgets or edit links. */
internal fun parseFieldGuide(document: Document, titles: Set<String>, pageUrl: String): List<FieldGuideChapter> {
    val body = document.selectFirst(".mw-parser-output") ?: return emptyList()
    return body.children().filter { it.tagName() == "h2" }.mapNotNull { h2 ->
        val title = h2.selectFirst(".mw-headline")?.text() ?: return@mapNotNull null
        if (title !in titles) return@mapNotNull null
        val blocks = buildList {
            fun emit(node: Element, kind: Kind = Kind.TEXT, prefix: String = "", headingLevel: Int = 0, captionedBlockCount: Int = 0) {
                val text = WikiRichTextParser.parse(node, pageUrl)
                if (text.text.isNotBlank()) add(FieldGuideBlock(kind,
                    if (prefix.isEmpty()) text else GuideText(listOf(GuideSpan(prefix)) + text.spans), headingLevel, captionedBlockCount))
            }
            fun readList(list: Element) {
                list.children().filter { it.tagName() == "li" }.forEachIndexed { index, li ->
                    val copy = li.clone()
                    copy.select("ul, ol").remove()
                    emit(copy, prefix = if (list.tagName() == "ol") "${index + 1}. " else "• ")
                    li.children().filter { it.tagName() in setOf("ul", "ol") }.forEach(::readList)
                }
            }
            for (node in generateSequence(h2.nextElementSibling()) { it.nextElementSibling() }.takeWhile { it.tagName() != "h2" }) {
                when (node.tagName()) {
                    "h3", "h4", "h5", "h6" -> node.selectFirst(".mw-headline")?.let {
                        emit(it, Kind.HEADING, headingLevel = node.tagName().last().digitToInt())
                    }
                    "p", "blockquote" -> emit(node)
                    "ul", "ol" -> readList(node)
                    "dl" -> node.children().forEach { emit(it, if (it.tagName() == "dt") Kind.HEADING else Kind.TEXT) }
                    "table" -> if (node.hasClass("klbqtable") || node.hasClass("wikitable")) {
                        val rows = fieldGuideRows(node, pageUrl)
                        node.children().firstOrNull { it.tagName() == "caption" }?.let {
                            val readable = rows != null && rows.any { row -> row.kind == Kind.RECORD }
                            emit(it, Kind.HEADING, captionedBlockCount = if (readable) rows.size else 0)
                        }
                        addAll(rows ?: listOf(FieldGuideBlock(Kind.TEXT,
                            GuideText(listOf(GuideSpan("在 Wiki 查看此表", url = "$pageUrl#$title"))))))
                    }
                }
            }
        }
        blocks.takeIf { it.isNotEmpty() }?.let { FieldGuideChapter(title, it) }
    }
}

/** These articles publish rectangular tables. Never shift a value under another field after a missing/merged cell. */
private fun fieldGuideRows(table: Element, pageUrl: String): List<FieldGuideBlock>? {
    val rows = table.select("tr").filter { it.closest("table") == table }
    val headers = rows.firstOrNull()?.children() ?: return null
    if (headers.isEmpty() || headers.any { it.tagName() != "th" } ||
        table.select("[colspan], [rowspan]").any { cell -> listOf("colspan", "rowspan").any { cell.hasAttr(it) && cell.attr(it) != "1" } }) return null
    val labels = headers.map { header -> header.clone().also { it.select(".smw-highlighter").remove() }.text() }
    if (labels.any(String::isBlank)) return null
    return buildList {
        headers.forEach { cell ->
            if (cell.selectFirst(".smw-highlighter") != null) add(FieldGuideBlock(Kind.TEXT, WikiRichTextParser.parse(cell, pageUrl)))
        }
        for (row in rows.drop(1)) {
            val cells = row.children()
            if (cells.size != labels.size || cells.any { it.tagName() !in setOf("td", "th") }) return null
            val record = buildList {
                cells.forEachIndexed { index, cell ->
                    if (index > 0) add(GuideSpan("\n"))
                    add(GuideSpan("${labels[index]}：", bold = true))
                    addAll(WikiRichTextParser.parse(cell, pageUrl).spans)
                }
            }
            add(FieldGuideBlock(Kind.RECORD, GuideText(record)))
        }
    }
}
