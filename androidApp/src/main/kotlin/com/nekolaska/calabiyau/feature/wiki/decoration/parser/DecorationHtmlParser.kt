package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationImageFile
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationItem
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationPreview
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationSection
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class DecorationHtmlParseResult(
    val sections: List<RawDecorationSection>,
    val cardCount: Int,
    val parsedCardCount: Int,
    val issues: List<String>
) {
    val isComplete: Boolean get() = cardCount > 0 && parsedCardCount == cardCount && issues.isEmpty()
}

/** Rendered directory parser for complex catalogs and the simple catalogs' compatibility fallback. */
internal object DecorationHtmlParser {
    private val idFromFile = Regex("""[\s_](\d+)""")
    private const val cards = ".gallerygrid-item, li.gallerybox, .roomgrid, .roomgrid-item"

    // Compatibility entry point; publication callers must check parseResult().isComplete.
    fun parse(html: String): List<RawDecorationSection> = parseResult(html).sections

    fun parseResult(html: String): DecorationHtmlParseResult {
        val root = Jsoup.parse(html).selectFirst(".mw-parser-output")
            ?: return DecorationHtmlParseResult(emptyList(), 0, 0, listOf("缺少 Wiki 正文容器"))
        val sections = mutableListOf<RawDecorationSection>()
        val issues = mutableListOf<String>()
        var cardCount = 0
        var parsedCardCount = 0
        if (root.select(".error, .scribunto-error").isNotEmpty()) issues += "目录含模板或模块错误"
        val headings = sortedMapOf<Int, String>()
        val occurrences = mutableMapOf<String, Int>()
        var title = "默认"
        var sectionPath = "默认"
        val items = mutableListOf<RawDecorationItem>()
        fun flush() {
            if (items.isEmpty()) return
            val occurrence = occurrences[sectionPath] ?: 0
            occurrences[sectionPath] = occurrence + 1
            // Only exact duplicates within this source group collapse; entries in other groups survive.
            sections += RawDecorationSection(title, items.distinct(), "$sectionPath#$occurrence")
            items.clear()
        }
        root.select("h1, h2, h3, h4, h5, h6, $cards").forEach { element ->
            if (element.parents().any(::isCard)) return@forEach
            if (element.tagName().matches(Regex("h[1-6]"))) {
                val heading = element.selectFirst(".mw-headline") ?: return@forEach
                flush()
                val level = element.tagName().last().digitToInt()
                headings.keys.filter { it >= level }.toList().forEach { headings.remove(it) }
                title = DecorationText.read(heading).ifBlank { "默认" }
                headings[level] = title
                sectionPath = headings.values.joinToString(" / ")
            } else {
                cardCount++
                val item = parseCard(element)
                if (item == null) issues += "卡片[$cardCount] 缺少有效主图文件身份或名称"
                else { parsedCardCount++; items += item }
            }
        }
        flush()
        if (cardCount == 0) issues += "未找到装饰卡片"
        if (issues.isNotEmpty()) WikiParseLogger.warnMalformed("DecorationHtmlParser", issues.take(5).joinToString("; "), html)
        return DecorationHtmlParseResult(sections, cardCount, parsedCardCount, issues)
    }

    private fun isCard(element: Element): Boolean = element.hasClass("gallerygrid-item") ||
        (element.tagName() == "li" && element.hasClass("gallerybox")) ||
        element.hasClass("roomgrid") || element.hasClass("roomgrid-item")

    private fun parseCard(item: Element): RawDecorationItem? {
        val room = item.hasClass("roomgrid") || item.hasClass("roomgrid-item")
        val imageBox = when {
            room -> item.selectFirst(".roomgrid__image-bg, .roomgrid-image-bg")
            item.hasClass("gallerybox") -> item.selectFirst(".thumb")
            else -> item.selectFirst(".gallerygrid-imagebox")
        }
        val image = if (imageBox != null) mainImage(imageBox) else {
            // Legacy direct images remain compatible; redlinks require an explicit main-image box.
            item.children().firstNotNullOfOrNull { child -> when (child.tagName()) {
                "img" -> parseImageFile(child)
                "a" -> parseImageFile(child.selectFirst("img"))
                else -> null
            } }
        } ?: return null
        val id = idFromFile.find(image.fileName)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val badge = item.selectFirst("[data-quality]")
        val caption = item.selectFirst(".gallerygrid-player-decoration, .gallerygrid-vehicle-skin, .roomgrid__player-decoration, .roomgrid-player-decoration, .gallerytext")
        val name = DecorationText.read(badge ?: caption).takeIf(String::isNotBlank) ?: return null
        val special = labelledList(item, "外观效果")
        val description = DecorationText.read(item.selectFirst(
            ".roomgrid__desc, .roomgrid-desc, .klbq-decoration-card__subtitle, small"
        ))
        val previews = if (room) item.select(".roomgrid__ui-item, .roomgrid-ui-item").mapNotNull { ui ->
            val label = DecorationText.read(ui.selectFirst(".roomgrid__ui-label, .ui-label"))
            parseImageFile(ui.selectFirst("img"))?.takeIf { label.isNotBlank() }?.let { RawDecorationPreview(label, it) }
        } else {
            val images = item.select("img").mapNotNull { img ->
                parseImageFile(img)?.takeIf { it.fileName.endsWith(".gif", ignoreCase = true) && it.fileName != image.fileName }
                    ?.let { RawDecorationPreview("动作预览", it) }
            }
            // An upload/red link still declares a preview identity, even though no img exists yet.
            val declared = item.select(".wiki-lemma-content-data a[title]").mapNotNull { link ->
                val title = link.attr("title")
                val file = when {
                    title.startsWith("文件:") -> title.removePrefix("文件:")
                    title.startsWith("File:") -> title.removePrefix("File:")
                    else -> return@mapNotNull null
                }
                file.takeIf { it.endsWith(".gif", ignoreCase = true) }
                    ?.let { RawDecorationPreview("动作预览", DecorationImageFile(it)) }
            }
            (images + declared).distinctBy { it.image.fileName.replace('_', ' ') }
        }
        return RawDecorationItem(id, name, badge?.attr("data-quality")?.toIntOrNull() ?: -1,
            description, parseSources(item), image, image, previews, special,
            if ("移动端" in image.fileName || image.fileName.startsWith("载具外观")) "移动端" else "PC")
    }

    private fun parseSources(item: Element): String {
        item.selectFirst(".klbq-decoration-card__obtain-value")?.let { return DecorationText.read(it) }
        val labelled = findLabelledList(item, "获得方式")
        if (labelled != null) return withoutLabel(labelled, "获得方式")
        // Legacy rooms/cards: top-level list only. Never concatenate a parent li and its nested li again.
        return item.select("li").filter { li ->
            li != item && li.parents().takeWhile { it != item }.none { it.tagName() == "li" } &&
                !li.text().trimStart().startsWith("外观效果")
        }.map { DecorationText.read(it) }.filter(String::isNotBlank).joinToString("\n")
    }

    private fun labelledList(item: Element, label: String): String =
        findLabelledList(item, label)?.let { withoutLabel(it, label) }.orEmpty()

    private fun findLabelledList(item: Element, label: String): Element? = item.select("li").firstOrNull {
        it.selectFirst("b, strong")?.text()?.trim()?.startsWith(label) == true
    }

    private fun withoutLabel(element: Element, label: String): String {
        val clone = element.clone()
        clone.selectFirst("b, strong")?.takeIf { it.text().trim().startsWith(label) }?.remove()
        return DecorationText.read(clone).removePrefix("：").removePrefix(":").trim()
    }

    private fun parseImageFile(image: Element?): DecorationImageFile? {
        image ?: return null
        val anchor = image.parents().firstOrNull { it.tagName() == "a" }
        val fileName = validFile(image.attr("alt"))
            ?: anchor?.let(::declaredFile)
            ?: return null
        val candidates = listOf(image.attr("data-src"), image.attr("src")) +
            image.attr("srcset").split(',').map { it.trim().substringBefore(' ') }
        val original = candidates.asSequence().map { it.trim() }.map {
            when { it.startsWith("//") -> "https:$it"; it.startsWith("/") -> "https://wiki.biligame.com$it"; else -> it }
        }.firstOrNull { (it.startsWith("https://") || it.startsWith("http://")) && "/thumb/" !in it }.orEmpty()
        return DecorationImageFile(fileName, original)
    }

    private fun mainImage(container: Element): DecorationImageFile? {
        // If an img is present but malformed, do not replace it with some other link's identity.
        container.selectFirst("img")?.let { return parseImageFile(it) }
        val links = if (container.tagName() == "a") listOf(container) else container.select("a")
        val files = links.mapNotNull(::declaredFile).distinctBy { it.replace('_', ' ') }
        return files.singleOrNull()?.let { DecorationImageFile(it) }
    }

    private fun validFile(value: String): String? = value.trim().takeIf {
        it.matches(Regex("""[^/\\:#?\r\n]+\.(?i:png|jpe?g|gif|webp|svg|avif)"""))
    }

    private fun namespaceFile(title: String): String? = when {
        title.startsWith("文件:") -> validFile(title.removePrefix("文件:"))
        title.startsWith("File:", ignoreCase = true) -> validFile(title.substringAfter(':'))
        else -> null
    }

    private fun declaredFile(link: Element): String? {
        namespaceFile(link.attr("title"))?.let { return it }
        val uri = runCatching { URI(link.attr("href")) }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        if (!path.startsWith("/klbq/")) return null
        fun decode(value: String) = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull().orEmpty()
        namespaceFile(decode(path.removePrefix("/klbq/").replace("+", "%2B")))?.let { return it }
        if (path != "/klbq/index.php") return null
        val query = uri.rawQuery.orEmpty().split('&').associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }
        namespaceFile(query["title"].orEmpty())?.let { return it }
        return if (query["title"] in setOf("特殊:上传文件", "Special:Upload")) validFile(query["wpDestFile"].orEmpty()) else null
    }
}
