package com.nekolaska.calabiyau.feature.wiki.stringer.parser

import com.nekolaska.calabiyau.feature.wiki.stringer.model.CardPage
import com.nekolaska.calabiyau.feature.wiki.stringer.model.ModeCard
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentItem
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentPage
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.nekolaska.calabiyau.core.wiki.HtmlText

object StringerPushCardParsers {

    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F/%E8%B6%85%E5%BC%A6%E6%8E%A8%E8%BF%9B"

    fun parseHtml(html: String): CardPage {
        val document = Jsoup.parse(html)
        val content = document.selectFirst(".mw-parser-output") ?: document.body()

        val summary = content.select("p")
            .map { it.text().trim() }
            .firstOrNull { it.isNotBlank() && !it.contains("此页面内容为【移动端】独有内容") }
            .orEmpty()

        val cardsHeading = content.selectFirst("span.mw-headline#卡牌")
        val cardsRoot = cardsHeading?.parent()
        val gallery = generateSequence(cardsRoot?.nextElementSibling()) { it.nextElementSibling() }
            .firstOrNull { it.hasClass("gallerygrid") }

        val cards = gallery?.select(".gallerygrid-item.mobile-card")?.mapNotNull { item ->
            val name = item.selectFirst(".mobile-card-name")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val itemRows = item.select(".mobile-card-item, .mobile-card-role")
            val category = item.attr("data-param1").ifBlank {
                findItemValue(itemRows, "分类")
            }
            val rarity = item.attr("data-param2").toIntOrNull() ?: 0
            val effect = HtmlText.detail(item.selectFirst(".mobile-card-desc .card-item-value"))
            val roles = findItemValue(itemRows, "适用角色")
                .split("、")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            ModeCard(
                name = name,
                category = category,
                rarity = rarity,
                effect = effect,
                roles = roles,
                imageUrl = item.selectFirst("img")?.attr("src")
            )
        }.orEmpty()

        return CardPage(
            title = "超弦推进卡牌",
            summary = summary,
            wikiUrl = WIKI_URL,
            cards = cards
        )
    }

    private fun findItemValue(rows: org.jsoup.select.Elements, key: String): String {
        return rows.firstOrNull { row ->
            row.selectFirst(".card-item-key")?.text()?.replace("：", "")?.trim() == key
        }?.selectFirst(".card-item-value")?.text()?.trim().orEmpty()
    }
}

object StringerTalentParsers {

    private const val PAGE_NAME = "超弦体天赋"
    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E8%B6%85%E5%BC%A6%E4%BD%93%E5%A4%A9%E8%B5%8B"
    private val sectionNames = setOf("机能", "生存", "续航", "输出")
    private val levelBadge = Regex("""Lv\.\s*([1-9]\d*)""")

    fun parseHtml(html: String): TalentPage {
        val document = Jsoup.parse(html)
        return parseContent(document.selectFirst(".mw-parser-output") ?: document.body())
    }

    private fun parseContent(content: Element): TalentPage {
        val sections = mutableListOf<TalentSection>()

        var currentTitle: String? = null
        var currentItems = mutableListOf<TalentItem>()

        fun flushSection() {
            val title = currentTitle ?: return
            if (currentItems.isNotEmpty()) {
                sections += TalentSection(title = title, items = currentItems.toList())
            }
            currentItems = mutableListOf()
        }

        content.children().forEach { element ->
            when (element.tagName()) {
                "h2", "h3" -> {
                    val title = element.selectFirst(".mw-headline")
                        ?.text()
                        ?.replace(" ", "")
                        ?.trim()
                        .orEmpty()
                    if (title in sectionNames) {
                        flushSection()
                        currentTitle = title
                    } else if (element.tagName() == "h2") {
                        flushSection()
                        currentTitle = null
                    }
                }

                "table" -> {
                    if (currentTitle == null) return@forEach
                    if (looksLikeTalentTable(element)) {
                        currentItems += parseTalentRows(element)
                    }
                }

                "div" -> {
                    if (currentTitle == null) return@forEach
                    if (element.hasClass("talent-gallery")) {
                        currentItems += element.children().filter { it.hasClass("talent-card") }.mapNotNull(::parseTalentCard)
                        return@forEach
                    }
                    element.select("table").forEach { table ->
                        if (looksLikeTalentTable(table)) {
                            currentItems += parseTalentRows(table)
                        }
                    }
                }
            }
        }

        flushSection()
        return TalentPage(
            title = PAGE_NAME,
            wikiUrl = WIKI_URL,
            sections = sections
        )
    }

    internal fun isComplete(html: String): Boolean {
        val content = Jsoup.parse(html).selectFirst(".mw-parser-output") ?: return false
        if (content.selectFirst(".error, .scribunto-error, .redirectMsg") != null) return false
        val galleries = content.select(".talent-gallery")
        val sections = parseContent(content).sections
        val hasLegacyTable = content.select("table").any(::looksLikeTalentTable)
        if (galleries.isEmpty()) return hasLegacyTable && sections.isNotEmpty()

        // The four branches each advertise six talents in the current Wiki. Allow additions;
        // a shorter branch needs review before it can replace a full offline catalog.
        val galleryCards = galleries.map { gallery -> gallery.children().filter { it.hasClass("gallerygrid-item") } }
        return !hasLegacyTable && galleries.size == sectionNames.size && sections.size == sectionNames.size &&
            sections.map { it.title }.toSet() == sectionNames &&
            galleryCards.all { cards -> cards.size >= 6 && cards.all { it.hasClass("talent-card") } } &&
            sections.sumOf { it.items.size } == galleryCards.sumOf { it.size } &&
            sections.all { section -> section.items.distinctBy { it.name }.size == section.items.size }
    }

    private fun parseTalentCard(card: Element): TalentItem? {
        val name = card.selectFirst(".talent-card__name")?.text()?.takeIf(String::isNotBlank) ?: return null
        val unlockLevel = card.selectFirst(".talent-card__badge:not(.talent-card__badge--max)")?.text()
            ?.removePrefix("解锁")?.trim()?.takeIf(levelBadge::matches) ?: return null
        val maxLevel = card.selectFirst(".talent-card__badge--max")?.text()?.removePrefix("上限")?.trim() ?: return null
        val count = levelBadge.matchEntire(maxLevel)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val effect = card.selectFirst(".talent-card__effect")?.clone() ?: return null
        effect.select(".talent-card__label, style, script").remove()
        val values = effect.select(".klbq-num")
        if (values.size != count || values.any { it.text().isBlank() }) return null
        val detail = HtmlText.detail(effect).takeIf(String::isNotBlank) ?: return null
        return TalentItem(name, unlockLevel, maxLevel, listOf(detail), card.selectFirst(".talent-card__imagebox img")
            ?.attr("src")?.takeIf(String::isNotBlank))
    }

    private fun parseTalentRows(table: org.jsoup.nodes.Element): List<TalentItem> {
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("th,td")
            if (cells.size < 4) return@mapNotNull null

            val nameCell = cells[0]
            val rawName = nameCell.text().trim()
            val name = rawName.substringAfterLast(".png", rawName).trim().ifBlank { rawName }
            val imageUrl = nameCell.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
            val unlockLevel = cells[1].text().trim()
            val maxLevel = cells[2].text().trim()
            val detailText = HtmlText.detail(cells[3])
            val details = splitTalentDetails(detailText)

            TalentItem(
                name = name,
                unlockLevel = unlockLevel,
                maxLevel = maxLevel,
                details = details.ifEmpty { listOf(detailText) },
                imageUrl = imageUrl
            )
        }
    }

    private fun looksLikeTalentTable(table: org.jsoup.nodes.Element): Boolean {
        val headers = table.select("tr").firstOrNull()?.select("th,td")
            ?.map { it.text().replace(" ", "").trim() }
            .orEmpty()
        return headers.any { it.contains("解锁等级") } &&
            headers.any { it.contains("最大等级") } &&
            headers.any { it.contains("天赋等级详情") }
    }

    private fun splitTalentDetails(text: String): List<String> {
        val normalized = text.replace("：", ":")
        val matches = Regex("(\\d+)级:").findAll(normalized).toList()
        if (matches.isEmpty()) return listOf(text)

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            normalized.substring(start, end).trim()
        }
    }
}
