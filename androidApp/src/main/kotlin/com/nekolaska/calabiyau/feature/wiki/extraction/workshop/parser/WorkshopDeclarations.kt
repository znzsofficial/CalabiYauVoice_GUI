package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser

import com.nekolaska.calabiyau.core.wiki.withoutWikiComments
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*

/** The API returns rendered HTML and its own source in ONE revision. The generated calls provide
 * an independent inventory (including the middle tiers), without pinning last week's item counts. */
private data class Declaration(
    val type: String, val room: String, val category: String, val name: String,
    val tiers: List<String> = emptyList(), val recipeCosts: List<RecipeCost> = emptyList(), val upgradeCostSize: Int? = null,
    val badge: Boolean = false, val subtitle: Boolean = false, val detail: Boolean = false
)

private val heading = Regex("""^(={2,4})\s*(.+?)\s*\1$""")
private val tierName = Regex("""档(\d+)名""")
private val unpublished = Regex("""(\d+)\s*件\s*(?:尚未|暂未|未)公布""")
private val materialClass = Regex("""\bsec-ws-lv__mat\b""")

internal fun checkWorkshopDeclarations(wikitext: String, page: WorkshopPage) {
    val expected = mutableListOf<Declaration>()
    var h2 = ""; var h3 = ""; var h4 = ""
    var template: String? = null
    val params = mutableMapOf<String, String>()
    fun asDeclaration(): Declaration {
        fun field(key: String) = params[key]?.takeIf(String::isNotBlank) ?: broken("工坊源条目缺少$key")
        if (template == "升级行" && h2 == "舱室升级") return Declaration("升级", h3, "", field("等级"),
            upgradeCostSize = materialClass.findAll(field("消耗")).count(), subtitle = !params["数值"].isNullOrBlank())
        val type = field("类型"); val name = field("名称")
        val declaredType = when (h2) {
            "舱室总览" -> "舱室"; "建造配方" -> "配方"; "派驻角色" -> "角色"
            "装修与收藏" -> if (h3 == "赛季珍藏品") "收藏" else h3
            else -> broken("工坊源条目位于未知章节")
        }
        verifyWorkshop(type == declaredType, "$name 的条目类型与章节不符")
        if (type == "配方") {
            val tiers = params.keys.mapNotNull { tierName.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.sorted()
            return Declaration(type, h3, h4, name, tiers.map { field("档${it}名") },
                tiers.map { recipeCost(field("档${it}成本")) })
        }
        return Declaration(type, "", "", name, badge = !params["徽章"].isNullOrBlank(),
            subtitle = !params["信息行"].isNullOrBlank(), detail = !params["详情"].isNullOrBlank())
    }
    val published = wikitext.withoutWikiComments()
    for (raw in published.lineSequence()) {
        val line = raw.trim()
        if (template != null) {
            if (line == "}}") { expected += asDeclaration(); params.clear(); template = null }
            else if (line.startsWith('|') && '=' in line) {
                val key = line.substring(1).substringBefore('=').trim()
                verifyWorkshop(params.put(key, line.substringAfter('=').trim()) == null, "工坊源字段重复：$key")
            }
        } else when (line) {
            "{{弦溯战线工坊卡" -> template = "卡"
            "{{弦溯战线工坊升级行" -> template = "升级行"
            else -> heading.matchEntire(line)?.destructured?.let { (marks, title) ->
                when (marks.length) { 2 -> { h2 = title; h3 = ""; h4 = "" }; 3 -> { h3 = title; h4 = "" }; 4 -> h4 = title }
            }
        }
    }
    verifyWorkshop(template == null, "工坊源条目不完整")

    val actual = buildList {
        fun card(type: String, card: WorkshopCard) = add(Declaration(type, "", "", card.name,
            badge = card.badge.isNotBlank(), subtitle = card.subtitle.isNotBlank(), detail = card.detail.isNotBlank()))
        page.rooms.forEach { room ->
            card("舱室", room.overview)
            room.upgrades.forEach { add(Declaration("升级", room.overview.name, "", it.level,
                upgradeCostSize = it.materials.size, subtitle = it.stats.isNotBlank())) }
        }
        page.recipes.forEach { recipe -> add(Declaration("配方", recipe.room, recipe.category, recipe.name,
            recipe.tiers.map { it.quality }, recipe.tiers.map { it.cost })) }
        page.staff.forEach { card("角色", it) }
        page.decorations.forEach { group -> group.cards.forEach { card(if (group.title == "赛季珍藏品") "收藏" else group.title, it) } }
    }
    verifyWorkshop(expected.size == actual.size && expected.toSet() == actual.toSet(), "工坊条目与本页公布的配方、档位或舱室资料不符")
    fun pendingCounts(s: String) = unpublished.findAll(s).map { it.groupValues[1] }.toList()
    verifyWorkshop(pendingCounts(published) == pendingCounts(page.decorations.joinToString { it.notes }), "珍藏品公布状态不符")
}
