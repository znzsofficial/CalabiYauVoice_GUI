package com.nekolaska.calabiyau.feature.wiki.map.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import org.jsoup.Jsoup

object MapListParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com"

    fun parseMapsFromHtml(html: String): List<MapInfo> {
        val results = mutableListOf<MapInfo>()
        val seen = mutableSetOf<String>()
        val document = Jsoup.parse(html)

        document.select("div.hvr-bounce-out").forEach { card ->
            val imageLink = card.selectFirst("a[href^=/klbq/]:has(img)") ?: return@forEach
            val path = imageLink.attr("href")
            val name = imageLink.attr("title").trim()
            val image = imageLink.selectFirst("img") ?: return@forEach
            val defaultSrc = image.attr("src")
            val srcset = image.attr("srcset")

            if (name !in seen) {
                seen += name
                val imageUrl = extract600pxUrl(srcset) ?: defaultSrc
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
