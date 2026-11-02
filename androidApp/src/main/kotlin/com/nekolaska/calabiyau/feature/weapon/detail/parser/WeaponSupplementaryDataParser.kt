package com.nekolaska.calabiyau.feature.weapon.detail.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDataSection
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDataValue
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Reads bounded gameplay sections only; keeps reference-data provenance separate from base stats. */
internal object WeaponSupplementaryDataParser {
    private val detailHeadings = setOf("武器详细数据", "武器伤害")
    private val basicHeadings = setOf("武器", "武器数据", "道具数据")
    private val performanceTerms = listOf("间隔", "换弹", "拉栓", "散布", "后坐力", "切枪", "射速", "弹匣", "射程")

    fun performance(html: String, detailParams: Map<String, String>): List<WeaponDataSection> {
        val document = Jsoup.parse(html)
        val sections = mutableListOf<WeaponDataSection>()
        var sourceNotice = ""
        for (heading in document.select("h2, h3")) {
            if (headingTitle(heading) !in detailHeadings) continue
            val elements = sectionElements(heading)
            val notice = elements.firstOrNull {
                it.hasClass("alert") && it.text().contains("仅供参考") && it.text().contains("分析")
            }?.let(::clean).orEmpty()
            if (notice.isNotBlank()) sourceNotice = notice
            val tables = elements.flatMap { element ->
                if (element.tagName() == "table") listOf(element) else element.select("table.klbqtable")
            }.filter { it.hasClass("klbqtable") }
            for (table in tables) {
                if (table.text().contains("武器部位伤害系数")) continue
                val rows = table.select("tr").mapNotNull { row ->
                    val cells = row.select("> th, > td")
                    if (cells.size != 2) return@mapNotNull null
                    val label = clean(cells[0]).trimEnd('：', ':')
                    val value = clean(cells[1])
                    if (value.isBlank() || performanceTerms.none { label.contains(it) }) return@mapNotNull null
                    WeaponDataValue(label, value)
                }.distinct()
                if (rows.isNotEmpty()) sections += WeaponDataSection(
                    table.selectFirst("caption")?.text()?.takeIf { it.isNotBlank() }
                        ?: if (rows.any { it.label.contains("后坐力") }) "后坐力" else "射击、换弹与散布", rows, notice
                )
            }
        }
        // A wikitext-only cache still has exact source numbers, but not the rendered units.
        // Do not infer units from a stat name (some values are coefficients, not seconds).
        val represented = sections.flatMap { it.values }.map { canonical(it.label) }.toSet()
        val fallback = detailParams.mapNotNull { (key, value) ->
            if (performanceTerms.none { key.contains(it) } || canonical(key) in represented) return@mapNotNull null
            val text = TacticalItemEffectParser.parse("", value)
            if (text.isBlank() || text.contains("{{")) null else WeaponDataValue(key, text)
        }
        if (fallback.isNotEmpty()) sections += WeaponDataSection("详细参数（Wiki 原值）", fallback,
            sourceNotice.ifBlank { if (html.isBlank()) "Wiki 模板原值，单位及来源说明请以原页面为准。" else "" })
        return sections.distinct()
    }

    fun mechanics(html: String): List<WeaponDataSection> {
        val document = Jsoup.parse(html)
        val groups = linkedMapOf<String, MutableList<WeaponDataValue>>()
        for (heading in document.select("h2, h3")) {
            if (headingTitle(heading) !in basicHeadings) continue
            var group = "武器机制"
            // Direct siblings only. Do not descend into the hero template, contents nav,
            // augmentation cards, or a transcluded site-wide list.
            for (element in sectionElements(heading)) {
                if (element.tagName() == "dl") {
                    element.selectFirst("> dt")?.let { group = clean(it).trimEnd('：', ':') }
                } else if (element.tagName() == "ul") {
                    for (item in element.select("> li")) {
                        val text = clean(item)
                        val parts = text.split('：', ':', limit = 2)
                        if (parts.size != 2 || parts.any { it.isBlank() }) continue
                        groups.getOrPut(group) { mutableListOf() } += WeaponDataValue(parts[0].trim(), parts[1].trim())
                    }
                }
            }
        }
        return groups.map { (title, values) -> WeaponDataSection(title, values.distinct()) }
    }

    private fun canonical(label: String) = label.removeSuffix("时间").replace(" ", "")
        .replace("移动时散布增量", "移动散布增量").replace("静止时散布衰减", "静止散布衰减")

    private fun headingTitle(element: Element) = (element.selectFirst(".mw-headline")?.text() ?: element.ownText()).trim()

    private fun sectionElements(heading: Element): List<Element> {
        val level = heading.tagName().drop(1).toInt()
        return generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { sibling ->
                val otherLevel = sibling.tagName().takeIf { it.matches(Regex("h[1-6]")) }?.drop(1)?.toInt()
                otherLevel == null || otherLevel > level
            }.toList()
    }

    private fun clean(element: Element): String {
        val copy = element.clone()
        copy.select(".smwttcontent, .smwtticon, .mw-editsection, script, style").remove()
        copy.ownerDocument()?.outputSettings()?.prettyPrint(false)
        return HtmlText.clean(copy.html())
    }
}
