package com.nekolaska.calabiyau.feature.wiki.tips.parser

import com.nekolaska.calabiyau.feature.wiki.tips.model.GameTipsSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object GameTipsParsers {

    fun parseSections(html: String): List<GameTipsSection> {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, sup.reference, .references, .mw-references-wrap, .alert, style, script").remove()
        val root = document.selectFirst(".mw-parser-output") ?: return emptyList()
        val children = root.children()
        if (children.isEmpty()) return emptyList()

        val sections = mutableListOf<GameTipsSection>()
        var title = "游戏Tips"
        val tips = mutableListOf<String>()

        fun flush() {
            val cleanTips = tips.map { it.normalizeTip() }
                .filter { it.isNotBlank() }
                .distinct()
            if (cleanTips.isNotEmpty()) {
                sections += GameTipsSection(title = title, tips = cleanTips)
            }
            tips.clear()
        }

        children.forEach { element ->
            when (element.tagName()) {
                "h2", "h3" -> {
                    flush()
                    title = element.selectFirst(".mw-headline")?.text()?.normalizeTip()
                        ?: element.text().normalizeTip()
                }

                "p" -> tips += parseParagraph(element)
                "ul", "ol" -> tips += parseList(element)
                "table" -> tips += parseTableTips(element)
            }
        }

        flush()
        return sections.filter { it.tips.isNotEmpty() }
    }

    private fun parseParagraph(element: Element): List<String> =
        listOfNotNull(element.text().normalizeTip().takeIf { it.looksLikeTip() })

    private fun parseList(element: Element): List<String> =
        element.select("li").mapNotNull { li ->
            li.text().normalizeTip().takeIf { it.looksLikeTip() }
        }

    private fun parseTableTips(table: Element): List<String> {
        val lines = mutableListOf<String>()
        table.select("tr").forEach { row ->
            val cells = row.select("td, th")
            if (cells.isEmpty()) return@forEach
            val candidates = cells.map { it.text().normalizeTip() }
                .filter { it.looksLikeTip() }
            if (candidates.isNotEmpty()) {
                lines += candidates.maxBy { it.length }
            }
        }
        return lines
    }

    private fun String.normalizeTip(): String = trim()
        .replace(Regex("\\s+"), " ")
        .trim(' ', '：')

    private fun String.looksLikeTip(): Boolean {
        if (isBlank() || length < 6) return false
        if (contains("文件:") || contains("File:") || contains("Template:")) return false
        return any { it in '\u4e00'..'\u9fff' }
    }
}
