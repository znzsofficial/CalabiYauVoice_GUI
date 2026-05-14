package com.nekolaska.calabiyau.feature.wiki.achievement.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.achievement.model.ACHIEVEMENT_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementItem
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementPage
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object AchievementParsers {

    fun parseHtml(html: String): AchievementPage {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, style, script, sup.reference, .references, .mw-references-wrap").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val sections = content.select("h2:has(.mw-headline)").mapNotNull { heading ->
            val category = heading.selectFirst(".mw-headline")?.cleanText().orEmpty()
            if (category.isBlank() || category == "目录") return@mapNotNull null
            val sectionRoot = Element("section")
            generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
                .takeWhile { it.tagName() != "h2" }
                .forEach { sectionRoot.appendChild(it.clone()) }
            val achievements = parseSection(category, sectionRoot)
            if (achievements.isEmpty()) null else AchievementSection(category, achievements)
        }
        return AchievementPage(
            title = "成就",
            wikiUrl = ACHIEVEMENT_PAGE_URL,
            sections = sections
        )
    }

    private fun parseSection(category: String, sectionRoot: Element): List<AchievementItem> {
        val tabs = sectionRoot.selectFirst("div.resp-tabs")
        if (tabs != null) {
            val levels = tabs.select("ul.resp-tabs-list > li.bili-list-style span.tab-panel")
                .map { it.cleanText() }
            return tabs.select("div.resp-tabs-container > div.resp-tab-content")
                .flatMapIndexed { index, content ->
                    content.select("li.gallerybox").mapNotNull { parseItem(it, category, levels.getOrNull(index)) }
                }
        }

        return sectionRoot.select("ul.gallery > li.gallerybox")
            .mapNotNull { parseItem(it, category, null) }
    }

    private fun parseItem(item: Element, category: String, level: String?): AchievementItem? {
        val name = item.selectFirst(".gallerytext big")?.cleanText().orEmpty()
        if (name.isBlank()) return null
        val text = item.selectFirst(".gallerytext p")?.wholeText()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        val condition = text.substringAfter("获得方式：", "").trim()
        val image = item.selectFirst(".thumb a.image img")
        val imageUrl = image?.attr("src")?.takeIf { it.isNotBlank() }?.let(WikiImageUrls::originalFromThumbnail)
        val fileHref = item.selectFirst(".thumb a.image")?.attr("href").orEmpty()
        val fileName = fileHref.substringAfterLast(':', "").takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

        return AchievementItem(
            category = category,
            level = level,
            name = name,
            flavorText = item.selectFirst(".gallerytext small")?.cleanText().orEmpty(),
            condition = condition,
            imageUrl = imageUrl,
            fileName = fileName
        )
    }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }
}
