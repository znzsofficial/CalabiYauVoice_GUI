package com.nekolaska.calabiyau.feature.wiki.bio.parser

import android.text.Html
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.bio.model.CardRefreshProbability
import com.nekolaska.calabiyau.feature.wiki.bio.model.MobileCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.SharedDeck
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object BioCardParsers {

    fun parsePcCards(
        html: String,
        refreshProbabilityMap: Map<String, CardRefreshProbability>
    ): List<PcCard> {
        val document = Jsoup.parse(html)
        val items = document.select(".gallerygrid-item.zombie-card")

        val cards = items.mapNotNull { item ->
            val name = item.selectFirst(".zombie-card__name")?.text()?.trim()
                .orEmpty()
                .ifBlank { "未知卡牌" }

            val roles = cleanHtml(item.selectFirst(".zombie-card__roles")?.html().orEmpty())
                .split(Regex("[、,/\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "无" }

            PcCard(
                name = name,
                faction = item.attr("data-param3"),
                rarity = item.attr("data-param1").toIntOrNull() ?: 0,
                category = item.attr("data-param2"),
                defaultTag = item.attr("data-param4"),
                acquireType = item.attr("data-param5"),
                releaseDate = item.attr("data-param6"),
                maxLevel = cleanHtml(item.selectFirst(".zombie-card__stat .zombie-card__value")?.html().orEmpty()),
                effect = cleanHtml(item.selectFirst(".zombie-card__desc .zombie-card__value")?.html().orEmpty()),
                roles = roles,
                imageUrl = item.selectFirst(".zombie-card__imagebox img")?.let { img ->
                    img.attr("data-src").ifBlank { img.attr("src") }
                }.let { WikiImageUrls.originalFromThumbnail(it) },
                refreshProbability = refreshProbabilityMap[name]
            )
        }

        return WikiParseLogger.finishList("BioCardApi.parsePcCards", cards, html, "items=${items.size}")
    }

    fun parseRefreshProbabilities(html: String): Map<String, CardRefreshProbability> {
        val document = Jsoup.parse(html)
        val heading = document.selectFirst("span#卡牌刷新概率表")
        val table = generateSequence(heading?.parent()) { it.nextElementSibling() }
            .flatMap { element -> sequenceOf(element).plus(element.select("table.klbqtable")) }
            .firstOrNull { it.tagName() == "table" && it.hasClass("klbqtable") }

        if (table == null) {
            WikiParseLogger.warnMalformed("BioCardApi.parseRefreshProbabilities", "missing refresh probability table", html)
            return emptyMap()
        }

        val items = table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("> th, > td").map { cleanHtml(it.html()) }
            if (cells.size < 5) return@mapNotNull null
            val name = cells[0]
            if (name.isBlank()) return@mapNotNull null
            name to CardRefreshProbability(
                stage1 = cells[1],
                stage2 = cells[2],
                stage3 = cells[3],
                stage4 = cells[4]
            )
        }.toMap()

        return WikiParseLogger.finishMap("BioCardApi.parseRefreshProbabilities", items, html)
    }

    fun parseMobileCards(html: String): List<MobileCard> {
        val document = Jsoup.parse(html)
        val items = document.select(".gallerygrid-item.zombie-card-mobile").map { item ->
            MobileCard(
                name = item.selectFirst(".zombie-card-mobile__name")?.text()?.trim().orEmpty().ifBlank { "未知卡牌" },
                faction = item.attr("data-param1"),
                category = item.attr("data-param2"),
                rarity = item.attr("data-param3").toIntOrNull() ?: 0,
                maxLevel = cleanHtml(item.selectFirst(".zombie-card-mobile__stat .zombie-card-mobile__value")?.html().orEmpty()),
                effect = cleanHtml(item.selectFirst(".zombie-card-mobile__desc .zombie-card-mobile__value")?.html().orEmpty()),
                imageUrl = item.selectFirst(".zombie-card-mobile__imagebox img")?.let { img ->
                    img.attr("data-src").ifBlank { img.attr("src") }
                }.let { WikiImageUrls.originalFromThumbnail(it) }
            )
        }.toList()

        return WikiParseLogger.finishList("BioCardApi.parseMobileCards", items, html)
    }

    fun parseDecks(
        html: String,
        cardIndexMapByFaction: Map<String, Map<String, Int>>
    ): List<SharedDeck> {
        val document = Jsoup.parse(html)
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()
        var currentFaction = "卡组"
        val decks = mutableListOf<SharedDeck>()

        rootChildren.forEach { element ->
            if (element.tagName().matches(Regex("h[1-6]"))) {
                val title = element.selectFirst("span.mw-headline")?.text()?.trim().orEmpty()
                if (title == "超弦体卡组" || title == "晶源体卡组") {
                    currentFaction = title
                }
                return@forEach
            }

            val tables = when {
                element.tagName() == "table" && element.hasClass("klbqtable") -> listOf(element)
                else -> element.select("table.klbqtable")
            }

            tables.forEach { table ->
                parseDeckTable(table, currentFaction, cardIndexMapByFaction)?.let { decks += it }
            }
        }

        return WikiParseLogger.finishList("BioCardApi.parseDecks", decks, html)
    }

    private fun parseDeckTable(
        table: Element,
        faction: String,
        cardIndexMapByFaction: Map<String, Map<String, Int>>
    ): SharedDeck? {
        val rows = table.select("tr")
        if (rows.isEmpty()) return null

        val title = rows.firstOrNull()?.selectFirst("th[colspan]")?.text()?.trim().orEmpty()
        
        val author = table.selectFirst("tr:has(th:contains(分享作者)) td")?.text()?.trim().orEmpty()
        val shareIdCell = table.selectFirst("tr:has(th:contains(分享ID)) td")
        val shareIdText = shareIdCell?.text().orEmpty()
        val shareId = extractShareId(shareIdText).ifBlank {
            val normalizedFaction = normalizeDeckFaction(faction)
            val cardIndexMap = cardIndexMapByFaction[normalizedFaction].orEmpty()
            extractShareIdFromDynamicScript(shareIdCell?.html().orEmpty(), normalizedFaction, cardIndexMap)
        }
        val intro = table.selectFirst("tr:has(th:contains(介绍)) td")?.text()?.trim().orEmpty()
        val cardNames = table.select(".zombie-card-name, .zombie-card-item-name, .card-name")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (title.isBlank() && shareId.isBlank() && cardNames.isEmpty()) return null
        return SharedDeck(
            faction = faction,
            title = title.ifBlank { "未命名卡组" },
            author = author,
            shareId = shareId,
            intro = intro,
            cardNames = cardNames
        )
    }

    private fun cleanHtml(raw: String): String {
        val normalized = raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("￼", "")
            .replace("\uFFFC", "")
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun extractShareId(raw: String): String {
        val cleaned = raw
            .substringAfterLast('｜', raw)
            .substringBefore("**")
            .substringAfterLast(' ', "")
            .trim()
        if (cleaned.isNotBlank()) return cleaned

        return Regex("""[A-Za-z0-9+/=_-]{12,}""")
            .find(raw)
            ?.value
            .orEmpty()
    }

    private fun normalizeDeckFaction(raw: String): String {
        return raw.removeSuffix("卡组").trim()
    }

    private fun extractShareIdFromDynamicScript(
        html: String,
        fallbackFaction: String,
        cardIndexMap: Map<String, Int>
    ): String {
        if (html.isBlank() || cardIndexMap.isEmpty()) return ""

        val cardIdsParam = Regex("""var\s+cardIdsParam\s*=\s*\"([^\"]*)\"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (cardIdsParam.isBlank()) return ""

        val factionFromScript = Regex("""var\s+factionParam\s*=\s*\"([^\"]*)\"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .trim()
        val faction = factionFromScript.ifBlank { fallbackFaction }
        if (faction.isBlank()) return ""

        val cardIndexes = cardIdsParam
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { cardId -> cardIndexMap[cardId] }

        if (cardIndexes.isEmpty()) return ""

        return runCatching {
            BioDeckShareCodecs.encodeShareCode(cardIndexes, faction)
        }.getOrDefault("")
    }
}
