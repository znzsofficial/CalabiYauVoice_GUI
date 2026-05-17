package com.nekolaska.calabiyau.feature.wiki.gallery.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import kotlin.math.roundToInt

data class RawGalleryImage(
    val fileName: String,
    val caption: String,
    val directImageUrl: String?,
    val description: String = "",
    val obtainMethod: String = ""
)

object GalleryParsers {

    fun parseHtml(pageName: String, html: String): List<Pair<String, List<RawGalleryImage>>> {
        val document = Jsoup.parse(html)
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()
        if (rootChildren.isEmpty()) return emptyList()

        val sections = mutableListOf<Pair<String, List<RawGalleryImage>>>()
        var currentTitle = pageName
        var currentElements = mutableListOf<Element>()
        var seenHeading = false

        fun flushSection() {
            val images = currentElements.flatMap { extractImagesFromElement(it) }
                .distinctBy { it.fileName }
            if (images.isNotEmpty()) {
                sections += currentTitle to images
            }
            currentElements = mutableListOf()
        }

        rootChildren.forEach { element ->
            if (element.isHeading()) {
                if (seenHeading) flushSection()
                seenHeading = true
                currentTitle = extractHeadingText(element).ifBlank { pageName }
            } else if (seenHeading) {
                currentElements.add(element)
            }
        }
        if (seenHeading) {
            flushSection()
        } else {
            currentElements = rootChildren.toMutableList()
            flushSection()
        }

        return WikiParseLogger.finishList("GalleryApi.parseHtml", sections, html, "page=$pageName")
    }

    private fun extractImagesFromElement(element: Element): List<RawGalleryImage> {
        return when {
            element.hasClass("resp-tabs") -> extractRespTabsImages(element)
            element.hasClass("tab") -> extractTabImages(element)
            else -> extractPlainImages(element)
        }
    }

    private fun extractRespTabsImages(container: Element): List<RawGalleryImage> {
        val labels = container.select(".resp-tabs-list .tab-panel").map { it.text().trim() }
        return container.select(".resp-tabs-container > .resp-tab-content").flatMap { pane ->
            val index = pane.elementSiblingIndex()
            val paneLabel = labels.getOrNull(index).orEmpty()
            extractMixedImages(pane, paneLabel)
        }
    }

    private fun extractTabImages(container: Element): List<RawGalleryImage> {
        val labels = container.select(".tab-nav > li > a").map { it.text().trim() }
        return container.select(".tab-content > .tab-pane").flatMap { pane ->
            val paneIndex = pane.elementSiblingIndex()
            val paneLabel = labels.getOrNull(paneIndex).orEmpty()
            extractMixedImages(pane, paneLabel)
        }
    }

    private fun extractPlainImages(element: Element): List<RawGalleryImage> {
        return extractMixedImages(element, fallbackCaption = null)
    }

    private fun extractMixedImages(container: Element, fallbackCaption: String?): List<RawGalleryImage> {
        return buildList {
            addAll(container.select("li.gallerybox").mapNotNull { box -> extractGalleryBoxImage(box, fallbackCaption) })
            addAll(container.select("a.image").filterNot(::isInsideGalleryBox).mapNotNull { link ->
                extractStandaloneImage(link, fallbackCaption)
            })
        }
    }

    private fun extractGalleryBoxImage(box: Element, fallbackCaption: String?): RawGalleryImage? {
        val link = box.selectFirst("a.image") ?: return null
        val fileName = extractFileName(link) ?: return null
        val text = parseGalleryText(box.selectFirst(".gallerytext"))
        val caption = text.caption
            ?: fallbackCaption.takeIf { !it.isNullOrBlank() }
            ?: imageAlt(link)
            ?: defaultCaption(fileName)
        return RawGalleryImage(
            fileName = fileName,
            caption = caption,
            directImageUrl = extractDirectImageUrl(link),
            description = text.description.orEmpty(),
            obtainMethod = text.obtainMethod.orEmpty()
        )
    }

    private fun extractStandaloneImage(link: Element, fallbackCaption: String?): RawGalleryImage? {
        val fileName = extractFileName(link) ?: return null
        val caption = fallbackCaption.takeIf { !it.isNullOrBlank() }
            ?: imageAlt(link)
            ?: defaultCaption(fileName)
        return RawGalleryImage(fileName, caption, extractDirectImageUrl(link))
    }

    private data class GalleryText(
        val caption: String?,
        val description: String?,
        val obtainMethod: String?
    )

    private fun parseGalleryText(element: Element?): GalleryText {
        if (element == null) return GalleryText(null, null, null)
        val caption = element.selectFirst("big")?.text()?.cleanText().takeUnless { it.isNullOrBlank() }
        val description = element.selectFirst("small")?.text()?.cleanText().takeUnless { it.isNullOrBlank() }
        val fullText = element.text().trim()
        val obtainMethod = extractObtainMethod(fullText)
        return GalleryText(
            caption = caption ?: fullText.takeIf { it.isNotBlank() && obtainMethod == null },
            description = description,
            obtainMethod = obtainMethod
        )
    }

    private fun extractObtainMethod(text: String): String? {
        val markers = listOf("获取方式：", "获取方式:")
        val marker = markers.firstOrNull { text.contains(it) } ?: return null
        return text.substringAfter(marker).trim().takeIf { it.isNotBlank() }
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

    private fun extractDirectImageUrl(link: Element): String? {
        val img = link.selectFirst("img") ?: return null
        return extractOriginalFromSrcSet(img.attr("srcset"))
            ?: normalizeImageUrl(img.attr("data-src")).takeIf { !it.isNullOrBlank() && "/thumb/" !in it }
            ?: normalizeImageUrl(img.attr("src")).takeIf { !it.isNullOrBlank() && "/thumb/" !in it }
    }

    private fun isInsideGalleryBox(link: Element): Boolean {
        return link.parents().any { it.tagName() == "li" && it.hasClass("gallerybox") }
    }

    private fun extractOriginalFromSrcSet(srcSet: String): String? {
        return srcSet.split(',')
            .asSequence()
            .mapNotNull(::parseSrcSetCandidate)
            .filter { "/thumb/" !in it.url }
            .maxByOrNull { it.scale }
            ?.url
    }

    private data class SrcSetCandidate(val url: String, val scale: Int)

    private fun parseSrcSetCandidate(raw: String): SrcSetCandidate? {
        val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val url = normalizeImageUrl(parts.firstOrNull().orEmpty()) ?: return null
        val descriptor = parts.getOrNull(1).orEmpty()
        val scale = when {
            descriptor.endsWith("x") -> (descriptor.removeSuffix("x").toFloatOrNull() ?: 1f).times(100).roundToInt()
            descriptor.endsWith("w") -> descriptor.removeSuffix("w").toIntOrNull() ?: 0
            else -> 1
        }
        return SrcSetCandidate(url, scale)
    }

    private fun normalizeImageUrl(url: String): String? {
        val clean = url.trim().takeIf { it.isNotBlank() } ?: return null
        return when {
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "https://wiki.biligame.com$clean"
            else -> clean
        }
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

    private fun Element.isHeading(): Boolean = tagName().matches(Regex("h[1-6]"))

    private fun extractHeadingText(element: Element): String {
        return element.selectFirst("span.mw-headline")?.text()?.cleanText()
            ?: element.ownText().cleanText().takeIf { it.isNotBlank() }
            ?: element.text().cleanText().substringBefore('[').trim()
    }

    private fun imageAlt(link: Element): String? {
        return link.selectFirst("img")?.attr("alt")?.cleanText()?.takeIf { it.isNotBlank() }
    }

    private fun String.cleanText(): String = replace(Regex("\\s+"), " ").trim()

    private fun defaultCaption(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
}
