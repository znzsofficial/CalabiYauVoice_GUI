package com.nekolaska.calabiyau.feature.wiki.oath.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathBirthdayGift
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathBondItem
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathBondSection
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathFavorGift
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathLevel
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathPage
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object OathParsers {

    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E8%AA%93%E7%BA%A6"
    private val bondCharacters = listOf("米雪儿·李", "奥黛丽", "星绘", "拉薇", "心夏", "玛德蕾娜", "伊薇特", "香奈美", "绯莎")

    fun parseHtml(html: String): OathPage {
        val document = Jsoup.parse(html)
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val tables = content.select("table")
        val summary = parseSummary(content)

        return OathPage(
            title = "誓约",
            summary = summary,
            wikiUrl = WIKI_URL,
            levels = tables.getOrNull(0)?.let(::parseLevels).orEmpty(),
            birthdayGifts = tables.getOrNull(1)?.let(::parseBirthdayGifts).orEmpty(),
            favorGifts = listOfNotNull(
                tables.getOrNull(2)?.let { parseFavorGifts(it, "常驻礼物") },
                tables.getOrNull(3)?.let { parseFavorGifts(it, "活动礼物") }
            ).flatten(),
            bondSections = parseBondSections(tables.drop(4))
        )
    }

    private fun parseSummary(content: Element): String {
        val introHeading = content.select("span.mw-headline#简介").firstOrNull()?.parent()
        val paragraphs = generateSequence(introHeading?.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() !in setOf("h2", "h3") }
            .filter { it.tagName() == "p" }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .toList()
        return paragraphs.joinToString("\n")
    }

    private fun parseLevels(table: Element): List<OathLevel> {
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("th,td").map { it.cleanText() }
            if (cells.size < 4 || cells[0].isBlank()) return@mapNotNull null
            OathLevel(
                level = cells[0],
                name = cells[1],
                requiredFavor = cells[2],
                totalFavor = cells[3]
            )
        }
    }

    private fun parseBirthdayGifts(table: Element): List<OathBirthdayGift> {
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("th,td").map { it.cleanText() }
            if (cells.size < 4 || cells[0].isBlank()) return@mapNotNull null
            OathBirthdayGift(
                name = cells[0],
                character = cells[1],
                description = cells[2],
                effect = cells[3],
                imageUrl = row.select("th,td").firstOrNull()?.firstImageUrl()
            )
        }
    }

    private fun parseFavorGifts(table: Element, source: String): List<OathFavorGift> {
        val rows = table.select("tr")
        val headers = rows.firstOrNull()?.select("th,td")?.map { it.cleanText() }.orEmpty()
        val characters = headers.drop(3).filter { it.isNotBlank() }
        return rows.drop(1).mapNotNull { row ->
            val cells = row.select("th,td").map { it.cleanText() }
            if (cells.size < 4 || cells[0].isBlank()) return@mapNotNull null
            OathFavorGift(
                name = cells[0],
                description = cells[1],
                rarity = cleanRarity(cells[2]),
                source = source,
                favorByCharacter = characters.mapIndexedNotNull { index, character ->
                    val value = cells.getOrNull(index + 3)?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    character to value
                }.toMap(),
                imageUrl = row.select("th,td").firstOrNull()?.firstImageUrl()
            )
        }
    }

    private fun parseBondSections(tables: List<Element>): List<OathBondSection> {
        return tables.mapIndexedNotNull { index, table ->
            val items = table.select("tr").drop(1).flatMap { row ->
                val cells = row.select("th,td").filter { it.cleanText().isNotBlank() }
                cells.chunked(2).mapNotNull { pair ->
                    val nameCell = pair.getOrNull(0)
                    val descriptionCell = pair.getOrNull(1)
                    val name = nameCell?.cleanText().orEmpty()
                    val description = descriptionCell?.cleanText().orEmpty()
                    if (name.isBlank() || description.isBlank()) {
                        null
                    } else {
                        OathBondItem(name, description, nameCell?.firstImageUrl())
                    }
                }
            }
            val character = bondCharacters.getOrNull(index) ?: "角色 ${index + 1}"
            if (items.isEmpty()) null else OathBondSection(character, items)
        }
    }

    private fun Element.cleanText(): String {
        select("style,script,.mw-editsection").remove()
        return text().replace(Regex("\\s+"), " ").trim()
    }

    private fun Element.firstImageUrl(): String? {
        val src = selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() } ?: return null
        return WikiImageUrls.originalFromThumbnail(src)
    }

    private fun cleanRarity(raw: String): String {
        return raw.replace(Regex("^\\d+\\s*"), "").trim()
    }
}
