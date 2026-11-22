package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import org.jsoup.nodes.Element

internal class WorkshopFormat(message: String) : IllegalArgumentException(message)
internal fun broken(reason: String): Nothing = throw WorkshopFormat(reason)
internal fun verifyWorkshop(condition: Boolean, reason: String) { if (!condition) broken(reason) }

/** Only count/render cards owned by this heading, never by a TOC, navbox or adjacent grid. */
internal class WorkshopSection(val title: String, val nodes: List<Element>) {
    fun split(tag: String): Pair<WorkshopSection, List<WorkshopSection>> {
        val groups = mutableListOf<WorkshopSection>()
        var heading = title
        var body = mutableListOf<Element>()
        for (node in nodes) {
            if (node.normalName() == tag) {
                groups += WorkshopSection(heading, body)
                heading = (node.selectFirst(".mw-headline") ?: node).text().trim()
                verifyWorkshop(heading.isNotBlank(), "缺少章节标题")
                body = mutableListOf()
            } else body.add(node)
        }
        groups += WorkshopSection(heading, body)
        val sections = groups.drop(1)
        verifyWorkshop(sections.distinctBy { it.title }.size == sections.size, "$title 的章节重复")
        return groups.first() to sections
    }

    fun notes() = nodes.filter { it.normalName() == "p" }.map { HtmlText.detail(it) }
        .filter(String::isNotBlank).joinToString("\n\n")

    fun rows(container: String, row: String): List<Element> {
        val groups = nodes.filter { it.hasClass(container) }
        if (groups.isEmpty()) broken("$title 缺少列表")
        return groups.flatMap { group ->
            val items = group.children().filter { it.hasClass(row) }
            verifyWorkshop(items.isNotEmpty() && items.size == group.select(".$row").size, "$title 列表不完整")
            items
        }
    }

    fun cards() = rows("sec-ws-grid", "sec-ws-card")
}

private fun Element.field(selector: String, required: Boolean = true): String {
    val fields = select(selector)
    if (fields.isEmpty() && !required) return ""
    val field = fields.singleOrNull() ?: broken("缺少或重复字段 $selector")
    return HtmlText.detail(field).ifBlank { broken("字段为空 $selector") }
}

internal fun Element.card(requiredDetails: Boolean = false) = WorkshopCard(
    field(".sec-ws-card__title"), field(".sec-ws-card__badge", requiredDetails),
    field(".sec-ws-card__sub", requiredDetails), field(".sec-ws-card__desc", requiredDetails)
)

private val materialPattern = Regex("""([^×]+)×\s*([0-9]+)""")
private fun material(value: String): WorkshopMaterial {
    val (name, count) = materialPattern.matchEntire(value.trim())?.destructured ?: broken("材料数量缺失：$value")
    val quantity = count.toIntOrNull()?.takeIf { it > 0 } ?: broken("材料数量异常：$value")
    val materialName = name.trim()
    verifyWorkshop(materialName.isNotEmpty() && materialName != "#0", "材料名称缺失：$value")
    return WorkshopMaterial(materialName, quantity)
}

/** #0×0 is a complete recipe cost field, not a free upgrade or a material with zero quantity. */
internal fun recipeCost(value: String): RecipeCost = if (value.trim() == RecipeCost.Unspecified.marker) RecipeCost.Unspecified
    else RecipeCost.Listed(value.split('、').map(::material))

private val timeAndEnergy = Regex("""(.+?)\s*[·・]\s*加速\s*([0-9]+)""")
private val levelPattern = Regex("""Lv\.\s*([1-9][0-9]*)""", RegexOption.IGNORE_CASE)
internal fun levelNumber(level: String) = levelPattern.matchEntire(level)?.groupValues?.get(1)?.toIntOrNull()
    ?: broken("舱室等级异常：$level")

internal fun Element.recipe(room: String, category: String): WorkshopRecipe {
    val name = field(".sec-ws-card__title")
    val range = field(".sec-ws-card__tags")
    val group = select(".sec-ws-card__recipe").singleOrNull() ?: broken("$name 缺少配方")
    val tiers = group.children().filter { it.hasClass("sec-ws-card__tier") }.map { tier ->
        val quality = tier.field(".sec-ws-card__tiername")
        val level = tier.field(".sec-ws-card__lv").also(::levelNumber)
        val costs = recipeCost(tier.field(".sec-ws-card__cost"))
        val (time, energy) = timeAndEnergy.matchEntire(tier.field(".sec-ws-card__time"))?.destructured
            ?: broken("$name 的$quality 档缺少耗时或加速能量")
        RecipeTier(quality, level, costs, time.trim(), energy.toIntOrNull() ?: broken("$name 加速能量异常"))
    }
    val labels = tiers.map { it.quality }
    verifyWorkshop(labels.isNotEmpty() && labels.distinct() == labels, "$name 缺少品质档或品质重复")
    val ends = range.split('→').map(String::trim)
    verifyWorkshop(ends == (if (tiers.size == 1) listOf(labels.first()) else listOf(labels.first(), labels.last())), "$name 品质范围不符")
    val icon = selectFirst(".sec-ws-card__icon img")
    val image = icon?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }?.takeIf { it.startsWith("https://") }
    return WorkshopRecipe(room, category, name, range, image, tiers)
}

internal fun Element.upgrade(): RoomUpgrade {
    val level = field(".sec-ws-lv__lv").also(::levelNumber)
    val cost = select(".sec-ws-lv__cost").singleOrNull()?.clone() ?: broken("$level 缺少升级消耗")
    cost.select(".sec-ws-lv__label").remove()
    val chips = cost.select(".sec-ws-lv__mat")
    val materials = chips.map { material(it.text()) }
    chips.remove()
    val note = cost.text().trim()
    verifyWorkshop(if (materials.isEmpty()) note == "无需消耗" else note.isEmpty(), "$level 升级消耗不完整")
    return RoomUpgrade(level, field(".sec-ws-lv__effect"), field(".sec-ws-lv__meta", false), materials, note)
}
