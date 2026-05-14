package com.nekolaska.calabiyau.feature.wiki.playerlevel.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PLAYER_LEVEL_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelEntry
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelPage
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelReward
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelRewardItem
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelWeapon
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

object PlayerLevelParsers {

    fun parseHtml(html: String, wikitext: String? = null): PlayerLevelPage {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, style, script, sup.reference, .references, .mw-references-wrap").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val levelHeading = content.selectFirst("#等级经验值")?.parent()
        val rewardHeading = content.selectFirst("#等级奖励")?.parent()
        val rewardResult = parseHtmlRewards(rewardHeading)
        val levels = wikitext?.let(::parseWikitextLevels).orEmpty().ifEmpty { parseLevels(levelHeading) }

        return PlayerLevelPage(
            title = "玩家等级",
            wikiUrl = PLAYER_LEVEL_PAGE_URL,
            intro = parseIntro(rewardHeading),
            levels = levels,
            rewards = rewardResult.first,
            note = rewardResult.second
        )
    }

    private fun parseWikitextLevels(wikitext: String): List<PlayerLevelEntry> {
        val section = extractSection(wikitext, "等级经验值", "等级奖励")
        if (section.isBlank()) return emptyList()

        var currentFrameName: String? = null
        val entries = mutableListOf<PlayerLevelEntry>()
        val lines = section.lineSequence().map { it.trim() }.toList()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val level = Regex("^!\\s*(\\d+)\\s*$").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (level != null && level in 1..500) {
                val exp = lines.getOrNull(index + 1)
                    ?.let { Regex("^\\|\\s*([0-9,]+)\\s*$").find(it)?.groupValues?.getOrNull(1) }
                    ?.replace(",", "")
                    ?.toIntOrNull()
                val imageLine = lines.getOrNull(index + 2).orEmpty()
                extractFileName(imageLine)?.let { currentFrameName = it }
                entries += PlayerLevelEntry(
                    level = level,
                    requiredExp = exp,
                    frameImageUrl = currentFrameName?.toWikiFileRedirectUrl(),
                    frameName = null
                )
            }
            index++
        }
        return entries.distinctBy { it.level }.sortedBy { it.level }
    }

    private fun extractFileName(line: String): String? {
        return Regex("\\[\\[(?:文件|File):([^|\\]]+)").find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String.toWikiFileRedirectUrl(): String {
        val encoded = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
        return "https://wiki.biligame.com/klbq/Special:Redirect/file/$encoded"
    }

    private fun extractSection(wikitext: String, title: String, nextTitle: String?): String {
        val startMatch = Regex("(?m)^==\\s*${Regex.escape(title)}\\s*==\\s*$").find(wikitext) ?: return ""
        val afterStart = startMatch.range.last + 1
        val end = nextTitle?.let { next ->
            Regex("(?m)^==\\s*${Regex.escape(next)}\\s*==\\s*$").find(wikitext, afterStart)?.range?.first
        } ?: Regex("(?m)^\\[\\[分类:").find(wikitext, afterStart)?.range?.first
        return wikitext.substring(afterStart, end ?: wikitext.length)
    }

    private fun parseLevels(heading: Element?): List<PlayerLevelEntry> {
        if (heading == null) return emptyList()
        val sectionElements = generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() != "h2" }
            .toList()
        return sectionElements.flatMap { element ->
            element.select("table.klbqtable").flatMap { table -> parseLevelTable(table) }
        }
    }

    private fun parseLevelTable(table: Element): List<PlayerLevelEntry> {
        var currentFrameUrl: String? = null
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("th,td")
            val level = cells.getOrNull(0)?.cleanText()?.toIntOrNull() ?: return@mapNotNull null
            if (level !in 1..500) return@mapNotNull null
            val exp = cells.getOrNull(1)?.cleanText()?.replace(",", "")?.toIntOrNull()
            row.selectFirst("td img")?.let { image ->
                currentFrameUrl = WikiImageUrls.originalFromThumbnail(image.attr("src"))
            }
            PlayerLevelEntry(
                level = level,
                requiredExp = exp,
                frameImageUrl = currentFrameUrl,
                frameName = null
            )
        }
    }

    private fun parseIntro(rewardHeading: Element?): String {
        return rewardHeading?.nextElementSibling()
            ?.takeIf { it.tagName() == "p" }
            ?.cleanText()
            .orEmpty()
    }

    private fun parseHtmlRewards(rewardHeading: Element?): Pair<List<PlayerLevelReward>, String?> {
        if (rewardHeading == null) return emptyList<PlayerLevelReward>() to null
        val table = generateSequence(rewardHeading.nextElementSibling()) { it.nextElementSibling() }
            .firstOrNull { it.tagName() == "table" && it.hasClass("klbqtable") }
            ?: return emptyList<PlayerLevelReward>() to null

        var note: String? = null
        val rewards = table.select("tr").drop(1).mapNotNull { row ->
            row.selectFirst("td[colspan]")?.let { noteCell ->
                note = noteCell.cleanText().takeIf { it.isNotBlank() }
                return@mapNotNull null
            }
            val cells = row.select("th,td")
            if (cells.size < 3) return@mapNotNull null
            val level = cells[0].cleanText().replace("级", "").trim().toIntOrNull() ?: return@mapNotNull null
            PlayerLevelReward(
                level = level,
                items = parseItems(cells[1]),
                weapons = parseWeapons(cells[2])
            )
        }
        return rewards to note
    }

    private fun parseItems(container: Element): List<PlayerLevelRewardItem> {
        return container.select("span.items-icon").mapNotNull { icon ->
            val name = icon.selectFirst("span.items-icon-text")?.cleanText().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val imgBox = icon.selectFirst("span.items-icon-img")
            val image = imgBox?.selectFirst("img")
            val quality = icon.classNames().firstOrNull { it.startsWith("items-quality-") }
                ?.removePrefix("items-quality-")
                ?.toIntOrNull()
            PlayerLevelRewardItem(
                name = name,
                count = imgBox?.attr("data-count")?.toIntOrNull(),
                quality = quality,
                iconUrl = WikiImageUrls.originalFromThumbnail(image?.attr("src"))
            )
        }
    }

    private fun parseWeapons(container: Element): List<PlayerLevelWeapon> {
        return container.select("a[href]").mapNotNull { link ->
            val name = link.cleanText()
            if (name.isBlank()) return@mapNotNull null
            PlayerLevelWeapon(
                name = name,
                wikiUrl = link.attr("href").takeIf { it.isNotBlank() }?.let { href ->
                    if (href.startsWith("http")) href else "https://wiki.biligame.com$href"
                }
            )
        }
    }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }
}
