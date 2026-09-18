package com.nekolaska.calabiyau.feature.wiki.item.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import org.jsoup.Jsoup

object ItemCatalogParsers {

    fun parseItems(html: String): List<ItemInfo> {
        val document = Jsoup.parse(html)
        // 现网结构：div.klbq-item-card[data-param1=分类, data-param2=品质] 卡片网格。
        // 单选择器避免逗号组合在 Jsoup 中重复收集；页面偶发的完全重复卡按整行去重。
        val cards = document.select("div.klbq-item-card[data-param1]")
        if (cards.isNotEmpty()) {
            return cards.mapNotNull { card ->
                val name = card.selectFirst("[class*=name]")?.text()?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val quality = Quality.fromLevel(card.attr("data-param2"))
                ItemInfo(
                    name = name,
                    category = card.attr("data-param1").trim().ifBlank { "其他" },
                    quality = quality,
                    qualityName = quality?.displayName.orEmpty(),
                    description = card.selectFirst("[class*=desc]")?.text()?.trim().orEmpty(),
                    iconUrl = WikiImageUrls.originalFromThumbnail(
                        card.selectFirst("[class*=imagebox] img[src]")?.attr("src")?.takeIf { it.isNotBlank() }
                    )
                )
            }.distinctBy { it }
        }
        // 旧结构：table#CardSelectTr 的 divsort 表格行
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
                iconUrl = WikiImageUrls.originalFromThumbnail(
                    nameCell.selectFirst("a.image img")?.attr("src")?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
}
