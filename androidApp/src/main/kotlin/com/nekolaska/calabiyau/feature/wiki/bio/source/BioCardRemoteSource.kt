package com.nekolaska.calabiyau.feature.wiki.bio.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioCardParsers
import org.jsoup.Jsoup
import util.wikiPathEncode

data class BioCardPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object BioCardRemoteSource {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    suspend fun fetchPageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): BioCardPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = pageName,
            cacheType = OfflineCache.Type.BIO_CARDS,
            cacheKey = cacheKey,
            forceRefresh = forceRefresh,
            acceptHtml = when (cacheKey) {
                "bio_cards_pc" -> { html -> hasNamedCards(html, mobile = false) }
                "bio_cards_mobile" -> { html -> hasNamedCards(html, mobile = true) }
                "bio_cards_mode" -> { html -> BioCardParsers.parseRefreshProbabilities(html).isNotEmpty() }
                else -> null
            }
        ) ?: return null
        val html = result.html ?: return null
        return BioCardPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    fun pageUrl(pageName: String): String {
        return WIKI_BASE + pageName.wikiPathEncode()
    }

    internal fun hasNamedCards(html: String, mobile: Boolean): Boolean {
        val cardClass = if (mobile) "zombie-card-mobile" else "zombie-card"
        return Jsoup.parse(html).select(".gallerygrid-item.$cardClass .${cardClass}__name").any { it.hasText() }
    }
}
