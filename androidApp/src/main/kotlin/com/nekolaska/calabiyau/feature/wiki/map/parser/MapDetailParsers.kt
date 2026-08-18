package com.nekolaska.calabiyau.feature.wiki.map.parser

import android.text.Html
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.UpdateEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MapDetailParsers {

    fun parseMapWikitext(
        name: String,
        wikitext: String,
        html: String
    ): MapDetail? {
        val mapContent = extractTemplate(wikitext, "地图") ?: return null
        val params = parseTemplateParams(mapContent)

        val document = Jsoup.parse(html)
        val terrainMapUrl = parseTerrainMapUrl(document)
        val galleryUrls = parseGalleryUrls(document)
        val updateHistory = parseUpdateHistoryFromHtml(document)

        return MapDetail(
            name = name,
            description = params["简介"] ?: "",
            supportedModes = params["支持模式"] ?: "",
            platforms = params["上线平台"] ?: "",
            terrainMapUrl = terrainMapUrl,
            galleryUrls = galleryUrls,
            updateHistory = updateHistory
        )
    }

    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        val startIdx = wikitext.indexOf(startMarker)
        if (startIdx == -1) return null

        var depth = 0
        var i = startIdx
        while (i < wikitext.length - 1) {
            if (wikitext[i] == '{' && wikitext[i + 1] == '{') {
                depth++
                i += 2
            } else if (wikitext[i] == '}' && wikitext[i + 1] == '}') {
                depth--
                if (depth == 0) {
                    return wikitext.substring(startIdx + startMarker.length, i).trimStart()
                }
                i += 2
            } else {
                i++
            }
        }
        return null
    }

    private fun parseTemplateParams(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        splitTopLevel(content, '|').forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.isNotEmpty()) params[key] = value
            }
        }
        return params
    }

    private fun splitTopLevel(content: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var braceDepth = 0
        var bracketDepth = 0
        var index = 0
        while (index < content.length) {
            when {
                content.startsWith("{{", index) -> {
                    braceDepth++
                    current.append("{{")
                    index += 2
                }
                content.startsWith("}}", index) -> {
                    braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    current.append("}}")
                    index += 2
                }
                content.startsWith("[[", index) -> {
                    bracketDepth++
                    current.append("[[")
                    index += 2
                }
                content.startsWith("]]", index) -> {
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    current.append("]]")
                    index += 2
                }
                content[index] == delimiter && braceDepth == 0 && bracketDepth == 0 -> {
                    parts += current.toString()
                    current.clear()
                    index++
                }
                else -> {
                    current.append(content[index])
                    index++
                }
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun parseTerrainMapUrl(document: org.jsoup.nodes.Document): String? {
        val sectionNodes = collectSectionNodes(document, "地形图")
        return sectionNodes
            .flatMap { node -> node.select("img[src]") }
            .mapNotNull { image -> WikiImageUrls.originalFromThumbnail(image.absUrl("src").ifBlank { image.attr("src").trim() }) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun parseGalleryUrls(document: org.jsoup.nodes.Document): List<String> {
        val sectionTitles = listOf("地图概览", "旧版地图概览")
        val urls = linkedSetOf<String>()

        sectionTitles.forEach { title ->
            val sectionNodes = collectSectionNodes(document, title)
            sectionNodes
                .flatMap { node -> node.select("img[src]") }
                .mapNotNull { image -> WikiImageUrls.originalFromThumbnail(image.absUrl("src").ifBlank { image.attr("src").trim() }) }
                .filter { it.isNotBlank() }
                .forEach { urls += it }
        }

        return urls.toList()
    }

    private fun parseUpdateHistoryFromHtml(document: org.jsoup.nodes.Document): List<UpdateEntry> {
        val sectionNodes = collectSectionNodes(document, "更新改动历史")
        if (sectionNodes.isEmpty()) return emptyList()

        val wrapper = Jsoup.parse("<div id='map-update-history-wrapper'></div>")
        val container = wrapper.getElementById("map-update-history-wrapper") ?: return emptyList()
        sectionNodes.forEach { container.appendChild(it.clone()) }

        val entries = mutableListOf<UpdateEntry>()
        var currentDate = ""
        var currentChanges = mutableListOf<String>()

        fun flushEntry() {
            if (currentDate.isNotBlank() && currentChanges.isNotEmpty()) {
                entries += UpdateEntry(date = currentDate, changes = currentChanges.toList())
            }
            currentDate = ""
            currentChanges = mutableListOf()
        }

        container.allElements
            .drop(1)
            .forEach { element ->
                when {
                    element.tagName().equals("div", ignoreCase = true) &&
                        element.hasClass("alert") &&
                        element.hasClass("alert-warning") -> {
                        flushEntry()
                        currentDate = cleanHtml(element.html())
                    }

                    element.tagName().equals("li", ignoreCase = true) && currentDate.isNotBlank() -> {
                        cleanHtml(element.html())
                            .takeIf { it.isNotBlank() }
                            ?.let { currentChanges += it }
                    }
                }
            }

        flushEntry()
        if (entries.isNotEmpty()) return entries

        val fallbackParagraphs = sectionNodes
            .filter { it.tagName().equals("p", ignoreCase = true) }
            .mapNotNull { paragraph -> cleanHtml(paragraph.html()).takeIf { it.isNotBlank() } }

        return if (fallbackParagraphs.isNotEmpty()) {
            listOf(UpdateEntry(date = "历史记录", changes = fallbackParagraphs))
        } else {
            emptyList()
        }
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
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

    private fun collectSectionNodes(
        document: org.jsoup.nodes.Document,
        headingText: String
    ): List<Element> {
        val headline = document.select("span.mw-headline")
            .firstOrNull { it.text().trim() == headingText }
            ?: return emptyList()

        val sectionRoot = headline.parent() ?: return emptyList()
        val nodes = mutableListOf<Element>()
        var cursor: Element? = sectionRoot.nextElementSibling()
        while (cursor != null) {
            if (cursor.tagName().matches(Regex("h[1-6]", RegexOption.IGNORE_CASE))) break
            nodes.add(cursor)
            cursor = cursor.nextElementSibling()
        }
        return nodes
    }
}
