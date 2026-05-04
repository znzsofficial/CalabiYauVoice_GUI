package com.nekolaska.calabiyau.feature.wiki.history.parser

import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistoryEntry
import com.nekolaska.calabiyau.feature.wiki.history.model.GameHistorySection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object GameHistoryParsers {

    private const val SITE_BASE = "https://wiki.biligame.com"
    private const val PAGE_BASE = "$SITE_BASE/klbq/"

    fun parseSections(html: String): List<GameHistorySection> {
        val document = Jsoup.parse(html)
        val rootChildren = document.selectFirst(".mw-parser-output")?.children().orEmpty()
        if (rootChildren.isEmpty()) return emptyList()

        val sections = mutableListOf<GameHistorySection>()
        var currentTopTitle: String? = null
        var currentTitle: String? = null
        var currentDescription: String? = null
        var currentEntries = mutableListOf<GameHistoryEntry>()

        fun flush() {
            val title = currentTitle?.trim().orEmpty()
            val description = currentDescription?.trim()?.takeIf { it.isNotBlank() }
            if (title.isBlank() && description.isNullOrBlank() && currentEntries.isEmpty()) {
                currentDescription = null
                currentEntries = mutableListOf()
                return
            }
            sections += GameHistorySection(
                title = title.ifBlank { currentTopTitle.orEmpty() },
                description = description,
                entries = currentEntries.distinctBy { it.url }
            )
            currentDescription = null
            currentEntries = mutableListOf()
        }

        rootChildren.forEach { element ->
            when {
                element.tagName().matches(Regex("h[1-6]")) -> {
                    flush()
                    val title = element.selectFirst("span.mw-headline")?.text()?.trim().orEmpty()
                    val level = element.tagName().drop(1).toIntOrNull() ?: 2
                    currentTitle = if (level >= 3 && !currentTopTitle.isNullOrBlank()) {
                        "$currentTopTitle / $title"
                    } else {
                        title
                    }
                    if (level <= 2) {
                        currentTopTitle = title
                    }
                }

                element.hasClass("alert") && currentDescription.isNullOrBlank() -> {
                    val alert = element.clone()
                    alert.select(".close").remove()
                    val text = alert.text().trim()
                    if (text.isNotBlank()) {
                        currentDescription = text
                    }
                }

                element.hasClass("nav-chara") -> {
                    currentEntries += extractNavCharaEntries(element)
                }

                element.select(".wiki-jump-btn a[href]").isNotEmpty() -> {
                    currentEntries += extractJumpEntries(element)
                }
            }
        }

        flush()
        return sections.filter { it.description != null || it.entries.isNotEmpty() }
    }

    private fun extractNavCharaEntries(container: Element): List<GameHistoryEntry> {
        return container.children()
            .filter { it.hasClass("game-story-box") }
            .mapNotNull { box ->
                val links = box.select("a[href]")
                val link = links.lastOrNull { it.text().trim().isNotBlank() } ?: links.firstOrNull()
                    ?: return@mapNotNull null
                val href = link.attr("href").trim()
                if (href.isBlank()) return@mapNotNull null

                val title = link.text().trim()
                    .ifBlank { link.attr("title").trim() }
                    .ifBlank { box.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
                    .ifBlank { href.substringAfterLast('/').ifBlank { href } }

                val image = box.selectFirst("img")
                val imageFileName = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                val imageUrl = image?.attr("src")?.trim()?.takeIf { it.isNotBlank() }

                GameHistoryEntry(
                    title = title,
                    url = toAbsoluteWikiUrl(href),
                    imageFileName = imageFileName,
                    imageUrl = imageUrl
                )
            }
    }

    private fun extractJumpEntries(container: Element): List<GameHistoryEntry> {
        return container.select(".wiki-jump-btn a[href]")
            .mapNotNull { link ->
                val href = link.attr("href").trim()
                if (href.isBlank()) return@mapNotNull null
                val title = link.text().trim()
                    .ifBlank { link.attr("title").trim() }
                    .ifBlank { href.substringAfterLast('/').ifBlank { href } }
                GameHistoryEntry(
                    title = title,
                    url = toAbsoluteWikiUrl(href)
                )
            }
    }

    private fun toAbsoluteWikiUrl(href: String): String = when {
        href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true) -> href
        href.startsWith("/") -> "$SITE_BASE$href"
        else -> "$PAGE_BASE${href.trimStart('/')}"
    }
}