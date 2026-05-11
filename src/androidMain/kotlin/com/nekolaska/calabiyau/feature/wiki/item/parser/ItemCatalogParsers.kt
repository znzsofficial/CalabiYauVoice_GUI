package com.nekolaska.calabiyau.feature.wiki.item.parser

import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import org.jsoup.Jsoup

object ItemCatalogParsers {

    fun parseItems(html: String): List<ItemInfo> {
        val document = Jsoup.parse(html)
        return document.select("table#CardSelectTr tr.divsort").mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 3) return@mapNotNull null

            val nameCell = cells[0]
            val qualityCell = cells[1]
            val descriptionCell = cells[2]
            val name = nameCell.selectFirst("b")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val badge = qualityCell.selectFirst("span.quality-badge")
            badge?.select("span[style*=display: none]")?.remove()
            val qualityCode = badge?.attr("data-quality").orEmpty().ifBlank { row.attr("data-param2") }
            val qualityName = badge?.text()?.trim().orEmpty()

            ItemInfo(
                name = name,
                category = row.attr("data-param1").trim().ifBlank { "其他" },
                quality = Quality.fromLevel(qualityCode),
                qualityName = qualityName,
                description = descriptionCell.text().trim(),
                iconUrl = nameCell.selectFirst("a.image img")?.attr("src")?.takeIf { it.isNotBlank() }
            )
        }
    }
}
