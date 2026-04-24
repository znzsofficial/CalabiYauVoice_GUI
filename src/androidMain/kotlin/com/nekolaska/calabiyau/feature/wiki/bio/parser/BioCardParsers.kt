package com.nekolaska.calabiyau.feature.wiki.bio.parser

import android.text.Html
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
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
        val rows = document.select("table#CardSelectTr tr.divsort")

        val cards = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 7) {
                WikiParseLogger.warnMalformed("BioCardApi.parsePcCards", "expected >=7 cells, actual=${cells.size}", row.outerHtml())
                return@mapNotNull null
            }

            val nameCell = cells[0]
            val name = nameCell.selectFirst("big b")?.text()?.trim()
                .orEmpty()
                .ifBlank { nameCell.textNodes().joinToString("") { it.text() }.trim() }
                .ifBlank { "未知卡牌" }

            val roles = cleanHtml(cells[6].html())
                .split(Regex("[、,/\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "无" }

            PcCard(
                name = name,
                faction = row.attr("data-param1"),
                rarity = row.attr("data-param2").toIntOrNull() ?: 0,
                category = row.attr("data-param3"),
                defaultTag = row.attr("data-param4"),
                acquireType = row.attr("data-param5"),
                releaseDate = row.attr("data-param6"),
                maxLevel = cleanHtml(cells[4].html()),
                effect = cleanHtml(cells[5].html()),
                roles = roles,
                imageUrl = nameCell.selectFirst("img")?.attr("src"),
                refreshProbability = refreshProbabilityMap[name]
            )
        }

        return WikiParseLogger.finishList("BioCardApi.parsePcCards", cards, html, "rows=${rows.size}")
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
        val items = document.select(".mobile-card.gallerygrid-item").map { item ->
            val values = item.select(".mobile-card-item, .mobile-card-item-odd, .mobile-card-desc")
                .mapNotNull { row ->
                    val key = row.selectFirst(".card-item-key")?.text()?.removeSuffix("：")?.trim().orEmpty()
                    val value = row.selectFirst(".card-item-value")?.text()?.trim().orEmpty()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap()

            MobileCard(
                name = item.selectFirst(".mobile-card-name")?.text()?.trim().orEmpty().ifBlank { "未知卡牌" },
                faction = item.attr("data-param1").ifBlank { values["适用阵营"] ?: "" },
                category = item.attr("data-param2").ifBlank { values["分类"] ?: "" },
                rarity = item.attr("data-param3").toIntOrNull() ?: 0,
                maxLevel = values["最大等级"] ?: "",
                effect = values["等级效果描述"] ?: "",
                imageUrl = item.selectFirst("img")?.attr("src")
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
        val fieldMap = rows.drop(1)
            .mapNotNull { row ->
                val key = row.selectFirst("th")?.text()?.trim().orEmpty()
                val valueCell = row.selectFirst("td") ?: return@mapNotNull null
                if (key.isBlank()) null else key to valueCell
            }
            .toMap()

        val author = fieldMap["分享作者"]?.text()?.trim().orEmpty()
        val shareIdCell = fieldMap["分享ID"]
        val shareIdText = shareIdCell?.text().orEmpty()
        val shareId = extractShareId(shareIdText).ifBlank {
            val normalizedFaction = normalizeDeckFaction(faction)
            val cardIndexMap = cardIndexMapByFaction[normalizedFaction].orEmpty()
            extractShareIdFromDynamicScript(shareIdCell?.html().orEmpty(), normalizedFaction, cardIndexMap)
        }
        val intro = fieldMap["介绍"]?.text()?.trim().orEmpty()
        val cardNames = fieldMap["卡牌列表"]
            ?.select(".zombie-card-name, .zombie-card-item-name, .card-name")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

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
