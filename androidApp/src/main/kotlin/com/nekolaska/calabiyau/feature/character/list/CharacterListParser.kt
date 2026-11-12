package com.nekolaska.calabiyau.feature.character.list

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.parseWikiListCards
import com.nekolaska.calabiyau.core.wiki.wikiListHtml

internal object CharacterListParser {
    private const val CARDS = ".hvr-bounce-out"
    fun parse(html: String): List<CharacterListApi.CharacterInfo> {
        val parsed = parseWikiListCards(html, CARDS)
        if (parsed.malformed > 0) WikiParseLogger.warnMalformed("CharacterList", "${parsed.malformed} cards lack unique article links", html)
        return WikiParseLogger.finishList("CharacterListApi.parseCharactersFromHtml",
            parsed.cards.map { CharacterListApi.CharacterInfo(it.name, it.wikiUrl, it.imageUrl) }, html)
    }
    fun isValidPayload(payload: String): Boolean =
        wikiListHtml(payload)?.let { parseWikiListCards(it, CARDS).complete } == true
}
