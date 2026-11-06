package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** November 2026's paired overview/colspan panel table, independent of the historical merged tables. */
internal object ExtractionContainerParser {
    private val required = setOf("名称", "来源类别", "子类", "档次", "件数概率", "专属池", "掉落池")
    private val togglePattern = Regex("mw-customtoggle-sec-c-(\\d+)")
    private val panelPattern = Regex("mw-customcollapsible-sec-c-(\\d+)")

    fun parseResult(html: String): ExtractionParseResult {
        val root = Jsoup.parse(html)
        val issues = mutableListOf<String>()
        val entries = mutableListOf<ExtractionEntry>()
        val usedIds = mutableSetOf<String>()
        val usedPanels = mutableSetOf<Element>()
        root.select("table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") === table }
            val header = rows.firstOrNull { row ->
                required.all { key -> row.children().any { headerKey(content(it)) == key } }
            } ?: return@forEach
            val headers = header.children().map { headerKey(content(it)) }
            if (headers.size != required.size || headers.toSet() != required) {
                issues += "container-table: ambiguous headers"
                return@forEach
            }
            rows.dropWhile { it !== header }.drop(1).forEach rowLoop@{ row ->
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                if (cells.map { headerKey(content(it)) } == headers) return@rowLoop
                if (row.select("[id^=mw-customcollapsible-sec-c-]").isNotEmpty()) {
                    if (cells.size != 1 || cells.single().attr("colspan") != headers.size.toString())
                        issues += "container-table: malformed panel row"
                    return@rowLoop
                }
                if (cells.size != headers.size || cells.any { it.hasAttr("rowspan") || it.hasAttr("colspan") }) {
                    issues += "container-table: incomplete metadata row"
                    return@rowLoop
                }
                fun field(key: String) = content(cells[headers.indexOf(key)])
                val name = field("名称")
                val poolCell = cells[headers.indexOf("掉落池")]
                val toggles = poolCell.getAllElements().flatMap { element ->
                    element.classNames().mapNotNull { togglePattern.matchEntire(it)?.groupValues?.get(1) }
                }
                val id = toggles.singleOrNull()
                if (name.isBlank() || field("来源类别").isBlank()) issues += "container-table: missing identity"
                val groups = if (id == null) {
                    // The live airdrop row explicitly has no published pool. Never infer ID 80001 here.
                    if (toggles.isNotEmpty() || field("掉落池") != "—") issues += "container-table: missing toggle"
                    emptyList()
                } else {
                    if (!usedIds.add(id)) issues += "container-table: duplicate container $id"
                    val panels = table.select("[id=mw-customcollapsible-sec-c-$id]")
                        .filter { it.closest("table") === table }
                    val panel = panels.singleOrNull()
                    if (panel == null) {
                        issues += "container-table: missing/duplicate panel $id"
                        emptyList()
                    } else {
                        usedPanels.add(panel)
                        val expected = Regex("""展开\s+(\d+)\s*项""").matchEntire(field("掉落池"))
                            ?.groupValues?.get(1)?.toIntOrNull()
                        if (expected == null) issues += "container-table: missing total $id"
                        parseGroups(panel, issues).also { groups ->
                            if (expected != groups.sumOf { it.items.size }) issues += "container-table: total mismatch $id"
                        }
                    }
                }
                entries += ExtractionEntry(
                    id = "container:${id ?: "unpublished:$name:${field("来源类别")}"}", name = name,
                    category = field("来源类别"), sourceCategory = field("来源类别"),
                    tags = listOf(field("子类"), field("档次")).filter { it.isNotBlank() },
                    fields = headers.filter { it !in setOf("名称", "掉落池", "来源类别") }
                        .map { ExtractionField(it, field(it)) },
                    type = ExtractionEntryType.DROP_POOL, containerId = id, dropPoolGroups = groups,
                    dropCountState = dropCountState(field("件数概率"))
                )
            }
        }
        root.select("[id^=mw-customcollapsible-sec-c-]").forEach {
            if (panelPattern.matches(it.id()) && it !in usedPanels) issues += "container-table: orphan panel"
        }
        val declared = root.select("h2").mapNotNull {
            Regex("""容器掉落全表[（(]\s*(\d+)\s*个容器[）)]""").find(content(it))?.groupValues?.get(1)?.toIntOrNull()
        }.singleOrNull()
        if (declared == null) issues += "container-table: missing/ambiguous declared container count"
        else if (declared != entries.size) issues += "container-table: declared container count mismatch"
        if (entries.map { it.id }.distinct().size != entries.size) issues += "container-table: ambiguous records"
        return ExtractionParseResult(entries, issues)
    }

    private fun parseGroups(panel: Element, issues: MutableList<String>): List<ExtractionDropPoolGroup> {
        val groups = mutableListOf<ExtractionDropPoolGroup>()
        var label: String? = null
        var count: Int? = null
        panel.select(".sec-drop-pool__q, .sec-drop-pool__chips").forEach { element ->
            if (element.hasClass("sec-drop-pool__q")) {
                if (label != null) issues += "container-pool: missing chips"
                label = content(element.selectFirst(".sec-drop-pool__tag")).takeIf { it.isNotBlank() }
                count = Regex("""(\d+)\s*项""").matchEntire(content(element.selectFirst(".sec-drop-pool__cnt")))
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (label == null || count == null) issues += "container-pool: missing group metadata"
            } else {
                val items = element.select(".sec-drop-chip").map { content(it) }
                if (label == null || items.any { it.isBlank() } || count != items.size) issues += "container-pool: group mismatch"
                label?.let { groups += ExtractionDropPoolGroup(it, items, qualityCode(it), "$it:${groups.size}") }
                label = null
                count = null
            }
        }
        if (label != null) issues += "container-pool: unfinished group"
        if (panel.select(".sec-drop-chip").size != groups.sumOf { it.items.size }) issues += "container-pool: orphan chips"
        return groups
    }
}
