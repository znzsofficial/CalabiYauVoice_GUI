package com.nekolaska.calabiyau.core.wiki

import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import util.wikiPathEncode

internal data class WikiListCard(val name: String, val wikiUrl: String, val imageUrl: String)
internal data class WikiListCards(val cards: List<WikiListCard>, val malformed: Int) {
    val complete: Boolean get() = cards.isNotEmpty() && malformed == 0
}

/** Keep article identity even without artwork; only recognized card containers are inspected. */
internal fun parseWikiListCards(html: String, selector: String): WikiListCards {
    val document = Jsoup.parse(html)
    val cards = linkedMapOf<String, WikiListCard>()
    var malformed = 0
    document.select(selector).forEach { card ->
        val titles = card.select("a[href]").mapNotNull { wikiArticleTitle(it.attr("href")) }.distinct()
        val name = titles.singleOrNull()
        if (name == null) {
            malformed++
        } else {
            val image = card.selectFirst("img")
            val imageUrl = image?.let {
                listOf(it.attr("data-src"), it.attr("src"),
                    it.attr("data-srcset").substringBefore(',').trim().substringBefore(' '),
                    it.attr("srcset").substringBefore(',').trim().substringBefore(' '))
                    .firstNotNullOfOrNull { raw -> WikiImageUrls.originalFromThumbnail(raw)
                        ?.takeIf { url -> url.startsWith("https://") || url.startsWith("http://") } }
            }.orEmpty()
            val previous = cards[name]
            if (previous == null || (previous.imageUrl.isBlank() && imageUrl.isNotBlank())) {
                cards[name] = WikiListCard(name, "https://wiki.biligame.com/klbq/${name.wikiPathEncode()}", imageUrl)
            }
        }
    }
    return WikiListCards(cards.values.toList(), malformed)
}

/** Path identity, not link captions/tooltips; exclude files, special pages and external links. */
internal fun wikiArticleTitle(href: String): String? = runCatching {
    val uri = URI("https://wiki.biligame.com/klbq/").resolve(href)
    if (uri.scheme !in setOf("https", "http") || uri.host != "wiki.biligame.com" || uri.userInfo != null)
        return@runCatching null
    val rawPath = uri.rawPath.orEmpty()
    if (!rawPath.startsWith("/klbq/") || !uri.rawQuery.isNullOrEmpty()) return@runCatching null
    val title = URLDecoder.decode(rawPath.removePrefix("/klbq/").replace("+", "%2B"), "UTF-8")
        .replace('_', ' ').trim()
    title.takeIf { it.isNotBlank() && it != "index.php" && ':' !in it && !it.contains('/') }
}.getOrNull()

internal fun wikiListHtml(payload: String): String? = runCatching {
    val root = SharedJson.parseToJsonElement(payload) as? JsonObject ?: return@runCatching null
    if (root["error"] != null) return@runCatching null
    val parse = root["parse"] as? JsonObject ?: return@runCatching null
    val value = parse["text"]
    ((value as? JsonObject)?.get("*") as? JsonPrimitive ?: value as? JsonPrimitive)
        ?.takeIf { it.isString }?.content
}.getOrNull()
