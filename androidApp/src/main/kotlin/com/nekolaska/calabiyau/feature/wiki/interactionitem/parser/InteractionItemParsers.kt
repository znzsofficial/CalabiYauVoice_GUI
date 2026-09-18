package com.nekolaska.calabiyau.feature.wiki.interactionitem.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.interactionitem.model.InteractionItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object InteractionItemParsers {
    fun parseItems(html: String): List<InteractionItemInfo> {
        val document = Jsoup.parse(html)
        val table = document.selectFirst("span.mw-headline#互动道具列表")
            ?.parent()
            ?.let(::findNextKlbqTable)
            ?: document.select("table.klbqtable").firstOrNull { table ->
                table.selectFirst("th")?.text()?.trim() == "名称" &&
                    table.select("th").any { it.text().trim() == "获得方式" }
            }
        val rows = table?.select("tr").orEmpty()
        val items = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 4) return@mapNotNull null

            val name = HtmlText.clean(cells[0].html()).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            if (name.isBlank() || name == "名称") return@mapNotNull null

            val quality = qualityFromCell(cells[1])
            InteractionItemInfo(
                name = name,
                quality = quality,
                qualityName = quality?.displayName ?: HtmlText.clean(cells[1].html()),
                description = HtmlText.clean(cells[2].html()),
                obtainMethod = HtmlText.clean(cells[3].html()),
                iconUrl = WikiImageUrls.originalFromThumbnail(
                    cells[0].selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
                )
            )
        }

        return WikiParseLogger.finishList("InteractionItemParsers.parseItems", items, html, "rows=${rows.size}")
    }

    private fun findNextKlbqTable(heading: Element): Element? {
        return generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() !in setOf("h2", "h3") }
            .flatMap { element ->
                if (element.tagName() == "table" && element.hasClass("klbqtable")) {
                    sequenceOf(element)
                } else {
                    element.select("table.klbqtable").asSequence()
                }
            }
            .firstOrNull()
    }

    private fun qualityFromCell(cell: Element): Quality? {
        val badge = cell.selectFirst(".quality-badge")
        val qualityValue = badge?.attr("data-quality").orEmpty()
        val qualityText = badge?.ownText().orEmpty().ifBlank { HtmlText.clean(cell.html()) }
        return Quality.entries.firstOrNull { quality ->
            qualityValue == quality.level.toString() ||
                qualityValue == quality.displayName ||
                qualityText.contains(quality.displayName)
        }
    }

}
