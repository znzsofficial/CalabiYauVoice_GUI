package com.nekolaska.calabiyau.feature.wiki.item.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import org.jsoup.Jsoup

object ItemCatalogParsers {
    fun parseItems(html: String): List<ItemInfo> {
        val document = Jsoup.parse(html)
        // PC / 移动端均使用此结构；旧表格页面已下线，不再解析 CardSelectTr。
        return document.select("div.klbq-item-card[data-param1]").mapNotNull { card ->
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
        }.distinct()
    }
}
