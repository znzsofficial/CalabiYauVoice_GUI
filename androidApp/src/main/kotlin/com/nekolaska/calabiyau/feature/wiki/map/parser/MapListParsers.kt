package com.nekolaska.calabiyau.feature.wiki.map.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.parseWikiListCards
import com.nekolaska.calabiyau.core.wiki.wikiListHtml
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo

object MapListParsers {
    private const val CARDS = ".klbq-map-card, .hvr-bounce-out"

    fun parseMapsFromHtml(html: String): List<MapInfo> {
        val parsed = parseWikiListCards(html, CARDS)
        if (parsed.malformed > 0) WikiParseLogger.warnMalformed("MapList", "${parsed.malformed} cards lack unique article links", html)
        return WikiParseLogger.finishList("MapListApi.parseMapsFromHtml",
            parsed.cards.map { MapInfo(it.name, it.wikiUrl, it.imageUrl) }, html)
    }

    internal fun isValidPayload(payload: String): Boolean =
        wikiListHtml(payload)?.let { parseWikiListCards(it, CARDS).complete } == true
}
