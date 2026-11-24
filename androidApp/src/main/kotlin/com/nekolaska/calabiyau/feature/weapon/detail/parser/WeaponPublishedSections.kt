package com.nekolaska.calabiyau.feature.weapon.detail.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponAugmentGroup
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponAugmentMode
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponAugmentOption
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDiagram
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Standalone artwork and weapon-only upgrade networks authored in the article body. */
internal class WeaponPublishedSections(document: Document) {
    private val article = document.selectFirst(".mw-parser-output") ?: document.body()

    private fun section(title: String): List<Element> {
        val start = article.select("> h2").singleOrNull {
            (it.selectFirst(".mw-headline")?.text() ?: it.ownText()) == title
        } ?: return emptyList()
        return generateSequence(start.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.normalName() !in headings }.toList()
    }

    fun diagrams(): List<WeaponDiagram> = section("武器").filter { it.normalName() == "p" }
        .flatMap { it.select("> a.image[href] > img") }.mapNotNull { image ->
            val file = image.attr("alt").takeIf { it.endsWith(".svg", ignoreCase = true) } ?: return@mapNotNull null
            val href = image.parent()?.attr("href") ?: return@mapNotNull null
            val filePath = runCatching { wikiBase.resolve(href) }.getOrNull()
                ?.takeIf { it.scheme == wikiBase.scheme && it.host == wikiBase.host && it.query == null }?.path
            if (filePath != "/klbq/文件:$file" && filePath != "/klbq/File:$file") return@mapNotNull null
            val preview = runCatching { URI(image.attr("src")) }.getOrNull() ?: return@mapNotNull null
            if (preview.scheme != "https" || preview.host != "patchwiki.biligame.com" ||
                !preview.path.startsWith("/images/klbq/thumb/") || !preview.path.endsWith(".svg.png", ignoreCase = true)
            ) return@mapNotNull null
            val width = image.attr("width").toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val height = image.attr("height").toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            WeaponDiagram(file.dropLast(4), preview.toString(), width.toFloat() / height)
        }.distinct()

    fun augmentations(templates: Sequence<Map<String, String>>): List<WeaponAugmentMode> {
        // The Wiki template uses {{{模式|爆破模式}}}: absent and explicitly empty are different.
        // option() still requires the corresponding mode-qualified target AND its unique panel.
        val modes = templates.groupBy { it.getOrDefault("模式", "爆破模式") }.filterKeys { it.isNotBlank() }
        if (modes.isEmpty()) return emptyList()
        val groups = section(augmentationTitle).filter { it.hasClass("growth3") }
            .flatMap { it.select(".upgrade-group") }
        val rows = groups.flatMap { it.select(rowSelector) }
            .groupBy { it.attr("data-target") }
        val panels = groups.flatMap { it.select("> .upgrade-options > .panel-collapse[id]") }.groupBy { it.id() }

        fun option(row: Element, ids: Set<String>): WeaponAugmentOption? {
            val target = row.attr("data-target")
            if (!target.startsWith('#') || rows[target]?.singleOrNull() !== row) return null
            val id = target.drop(1).takeIf { it in ids } ?: return null
            val panel = panels[id]?.singleOrNull()?.takeIf { it.parent() === row.parent() } ?: return null
            val name = row.select("> .upgrade-text").singleOrNull()?.text().orEmpty()
            val changes = HtmlText.detail(panel.clone().also { it.select(".smwttcontent, .smwtticon, sup.reference").remove() })
            if (name.isBlank() || changes.isBlank()) return null
            val cost = row.select("> .upgrade-cost").singleOrNull()?.takeIf {
                it.selectFirst("img[alt=图标-能量点.png]") != null
            }?.text()?.toIntOrNull()
            return WeaponAugmentOption(name, cost, changes)
        }

        return modes.mapNotNull { (mode, declarations) ->
            val params = declarations.singleOrNull() ?: return@mapNotNull null
            val ids = params.filterValues { it.isNotBlank() }.keys.mapNotNull { key ->
                optionKey.matchEntire(key)?.destructured?.let { (part, index) -> "mc_collapse-${mode}Parts${part}Group${index}Change" }
            }.toSet()
            val parsed = groups.mapNotNull group@{ group ->
                val title = group.select("> .upgrade-top > .upgrade-name").singleOrNull()?.text().orEmpty()
                if (title.isBlank()) return@group null
                val options = group.select(rowSelector).mapNotNull { option(it, ids) }
                options.takeIf { it.isNotEmpty() }?.let { WeaponAugmentGroup(title, it) }
            }
            parsed.takeIf { it.isNotEmpty() }?.let { WeaponAugmentMode(mode, it) }
        }
    }

    companion object {
        private const val augmentationTitle = "弦能增幅网络"
        private const val rowSelector = "> .upgrade-options > .upgrade-row-weapon[data-target]"
        private val wikiBase = URI("https://wiki.biligame.com/klbq/")
        private val headings = (1..6).map { "h$it" }.toSet()
        private val optionKey = Regex("Parts(\\d+)组(\\d+)属性变化")
        private val headingLine = Regex("(?m)^(={1,6})[ \\t]*([^=\\r\\n]+?)[ \\t]*\\1[ \\t]*\\r?$")

        /** Already comment-stripped by WeaponDetailApi. A same-named template in history is not a declaration. */
        fun augmentationSource(visible: String): String {
            val headings = headingLine.findAll(visible).toList()
            val start = headings.singleOrNull {
                it.groupValues[1] == "==" && it.groupValues[2] == augmentationTitle
            } ?: return ""
            val end = headings.firstOrNull { it.range.first > start.range.last }?.range?.first ?: visible.length
            return visible.substring(start.range.last + 1, end)
        }
    }
}
