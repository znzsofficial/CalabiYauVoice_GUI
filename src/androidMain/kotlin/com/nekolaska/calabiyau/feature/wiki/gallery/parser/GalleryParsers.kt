package com.nekolaska.calabiyau.feature.wiki.gallery.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder

object GalleryParsers {

    fun parseHtml(pageName: String, html: String): List<Pair<String, List<Pair<String, String>>>> {
        val document = Jsoup.parse(html)
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()
        if (rootChildren.isEmpty()) return emptyList()

        val sections = mutableListOf<Pair<String, List<Pair<String, String>>>>()
        var currentTitle = pageName
        var currentElements = mutableListOf<Element>()

        fun flushSection() {
            val images = currentElements.flatMap { extractImagesFromElement(it) }
                .distinctBy { it.first }
            if (images.isNotEmpty()) {
                sections += currentTitle to images
            }
            currentElements = mutableListOf()
        }

        rootChildren.forEach { element ->
            if (element.tagName().matches(Regex("h[1-6]"))) {
                flushSection()
                currentTitle = element.selectFirst("span.mw-headline")?.text()?.trim().orEmpty().ifBlank { pageName }
            } else {
                currentElements.add(element)
            }
        }
        flushSection()

        return WikiParseLogger.finishList("GalleryApi.parseHtml", sections, html, "page=$pageName")
    }

    private fun extractImagesFromElement(element: Element): List<Pair<String, String>> {
        return when {
            element.hasClass("resp-tabs") -> extractRespTabsImages(element)
            element.hasClass("tab") -> extractTabImages(element)
            else -> extractPlainImages(element)
        }
    }

    private fun extractRespTabsImages(container: Element): List<Pair<String, String>> {
        val labels = container.select(".resp-tabs-list .tab-panel").map { it.text().trim() }
        return container.select(".resp-tabs-container > .resp-tab-content").mapNotNull { pane ->
            val link = pane.selectFirst("a.image") ?: return@mapNotNull null
            val fileName = extractFileName(link) ?: return@mapNotNull null
            val index = pane.elementSiblingIndex()
            val caption = labels.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?: defaultCaption(fileName)
            fileName to caption
        }
    }

    private fun extractTabImages(container: Element): List<Pair<String, String>> {
        val labels = container.select(".tab-nav > li > a").map { it.text().trim() }
        return container.select(".tab-content > .tab-pane").flatMap { pane ->
            val paneIndex = pane.elementSiblingIndex()
            val paneLabel = labels.getOrNull(paneIndex).orEmpty()
            val galleryBoxes = pane.select("li.gallerybox")
            when {
                galleryBoxes.isNotEmpty() -> galleryBoxes.mapNotNull { box ->
                    val link = box.selectFirst("a.image") ?: return@mapNotNull null
                    val fileName = extractFileName(link) ?: return@mapNotNull null
                    val caption = box.selectFirst(".gallerytext")?.text()?.trim().takeUnless { it.isNullOrBlank() }
                        ?: paneLabel.takeIf { it.isNotBlank() }
                        ?: defaultCaption(fileName)
                    fileName to caption
                }
                else -> {
                    val link = pane.selectFirst("a.image")
                    val fileName = link?.let(::extractFileName)
                    if (fileName != null) {
                        listOf(fileName to (paneLabel.takeIf { it.isNotBlank() } ?: defaultCaption(fileName)))
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    private fun extractPlainImages(element: Element): List<Pair<String, String>> {
        val galleryBoxes = element.select("li.gallerybox")
        if (galleryBoxes.isNotEmpty()) {
            return galleryBoxes.mapNotNull { box ->
                val link = box.selectFirst("a.image") ?: return@mapNotNull null
                val fileName = extractFileName(link) ?: return@mapNotNull null
                val caption = box.selectFirst(".gallerytext")?.text()?.trim().takeUnless { it.isNullOrBlank() }
                    ?: defaultCaption(fileName)
                fileName to caption
            }
        }

        return element.select("> a.image, a.image")
            .mapNotNull { link ->
                val fileName = extractFileName(link) ?: return@mapNotNull null
                fileName to defaultCaption(fileName)
            }
    }

    private fun extractFileName(link: Element): String? {
        val href = link.attr("href")
        if (href.isNotBlank()) {
            val pageTitle = extractPageTitleFromHref(href)
            if (!pageTitle.isNullOrBlank()) {
                return pageTitle.removePrefix("文件:").removePrefix("File:")
            }
        }

        return link.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractPageTitleFromHref(href: String): String? {
        val encodedPart = when {
            "/klbq/" in href -> href.substringAfter("/klbq/")
            href.startsWith("/") -> href.removePrefix("/")
            else -> href
        }
            .substringBefore('#')
            .substringBefore('?')
            .takeIf { it.isNotBlank() }
            ?: return null

        return runCatching { URLDecoder.decode(encodedPart, "UTF-8") }.getOrNull()
    }

    private fun defaultCaption(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
}
