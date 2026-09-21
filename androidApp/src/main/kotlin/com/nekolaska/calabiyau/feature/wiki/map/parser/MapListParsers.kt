package com.nekolaska.calabiyau.feature.wiki.map.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import org.jsoup.Jsoup

object MapListParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com"

    fun parseMapsFromHtml(html: String): List<MapInfo> {
        val document = Jsoup.parse(html)

        // 2026-09 改版：{{游戏地图}} 的 format 子模板输出 div.klbq-map-card 卡片
        // （覆盖链接 a[title] + __image 直链原图 img，无 srcset）；
        // 旧结构 div.hvr-bounce-out 作为兜底保留。
        val newCards = document.select("div.klbq-map-card")
        if (newCards.isNotEmpty()) {
            val results = mutableListOf<MapInfo>()
            val seen = mutableSetOf<String>()
            newCards.forEach { card ->
                val nameLink = card.selectFirst("a[href^=/klbq/][title]") ?: return@forEach
                val path = nameLink.attr("href")
                val name = nameLink.attr("title").trim()
                val image = card.selectFirst("img") ?: return@forEach
                if (name.isNotEmpty() && name !in seen) {
                    seen += name
                    // 新结构 img 无 srcset，src 即 patchwiki 原图直链
                    results += MapInfo(
                        name = name,
                        wikiUrl = "$WIKI_BASE$path",
                        imageUrl = WikiImageUrls.originalFromThumbnail(image.attr("src")).orEmpty()
                    )
                }
            }
            return WikiParseLogger.finishList("MapListApi.parseMapsFromHtml", results, html)
        }

        val results = mutableListOf<MapInfo>()
        val seen = mutableSetOf<String>()
        document.select("div.hvr-bounce-out").forEach { card ->
            val imageLink = card.selectFirst("a[href^=/klbq/]:has(img)") ?: return@forEach
            val path = imageLink.attr("href")
            val name = imageLink.attr("title").trim()
            val image = imageLink.selectFirst("img") ?: return@forEach
            val defaultSrc = image.attr("src")
            val srcset = image.attr("srcset")

            if (name !in seen) {
                seen += name
                val imageUrl = WikiImageUrls.originalFromThumbnail(extract600pxUrl(srcset) ?: defaultSrc).orEmpty()
                results += MapInfo(
                    name = name,
                    wikiUrl = "$WIKI_BASE$path",
                    imageUrl = imageUrl
                )
            }
        }
        return WikiParseLogger.finishList("MapListApi.parseMapsFromHtml", results, html)
    }

    private fun extract600pxUrl(srcset: String): String? {
        return srcset.split(",")
            .map { it.trim() }
            .firstOrNull { it.contains("600px") || it.endsWith("2x") }
            ?.split(" ")
            ?.firstOrNull()
    }
}
