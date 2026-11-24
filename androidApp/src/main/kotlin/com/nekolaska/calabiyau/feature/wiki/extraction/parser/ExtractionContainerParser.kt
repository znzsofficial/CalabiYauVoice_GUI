package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** November 2026's paired overview/colspan panel table, independent of the historical merged tables. */
internal object ExtractionContainerParser {
    private val columns = setOf("名称", "来源类别", "子类", "件数概率", "掉落池")
    private val legacyColumns = columns + setOf("档次", "专属池")
    private val togglePattern = Regex("mw-customtoggle-sec-c-(\\d+)")
    private const val panelSelector = "[id^=mw-customcollapsible-sec-c-]"
    private val panelPattern = Regex("mw-customcollapsible-sec-c-(\\d+)")
    private val expandPoolCount = Regex("""展开\s+(\d+)\s*项""")
    private val containerTableCount = Regex("""容器掉落全表[（(]\s*(\d+)\s*个容器[）)]""")

    fun parseResult(html: String): ExtractionParseResult {
        val root = Jsoup.parse(html)
        val issues = mutableListOf<String>()
        val entries = mutableListOf<ExtractionEntry>()
        val usedIds = mutableSetOf<String>()
        val usedPanels = mutableSetOf<Element>()
        root.select("table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") === table }
            val header = rows.firstOrNull { row ->
                row.childrenSize() >= columns.size && row.children().map { headerKey(content(it)) }.containsAll(columns)
            } ?: return@forEach
            val headers = header.children().map { headerKey(content(it)) }
            val keys = headers.toSet()
            if ((keys != columns && keys != legacyColumns) || headers.size != keys.size ||
                header.children().any { it.hasAttr("rowspan") || it.hasAttr("colspan") }) {
                issues += "container-table: unsupported/ambiguous headers"
                return@forEach
            }
            // Index panels only from standalone, full-width rows in this table. Order is immaterial.
            val panelsById = mutableMapOf<String, Element>()
            val metadataRows = rows.dropWhile { it !== header }.drop(1).filter { row ->
                val panels = row.select(panelSelector).filter { it.closest("table") === table }
                if (panels.isEmpty()) return@filter true
                val panel = panels.singleOrNull()
                val id = panel?.let { panelPattern.matchEntire(it.id())?.groupValues?.get(1) }
                val cell = row.children().singleOrNull()
                if (cell == null || cell.tagName() != "td" || cell.hasAttr("rowspan") ||
                    cell.attr("colspan") != headers.size.toString() || panel == null || id == null) {
                    issues += "container-table: malformed panel row"
                } else if (panelsById.put(id, panel) != null) issues += "container-table: duplicate panel $id"
                false
            }
            metadataRows.forEach rowLoop@{ row ->
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                if (cells.size != headers.size || cells.any { it.hasAttr("rowspan") || it.hasAttr("colspan") }) {
                    issues += "container-table: incomplete metadata row"
                    return@rowLoop
                }
                val values = cells.map { content(it) }
                if (values.map(::headerKey) == headers) return@rowLoop
                val fields = headers.zip(values).toMap()
                val name = fields.getValue("名称")
                val category = fields.getValue("来源类别")
                val pool = fields.getValue("掉落池")
                val poolCell = cells[headers.indexOf("掉落池")]
                val toggles = poolCell.getAllElements().flatMap { element ->
                    element.classNames().filter { it.startsWith("mw-customtoggle-sec-c-") }
                }
                val id = toggles.singleOrNull()?.let { togglePattern.matchEntire(it)?.groupValues?.get(1) }
                // Blank cells are lost metadata. An explicit dash or partial distribution is data
                // and remains visible as reported, without being classified as a zero-count draw.
                fields.filterValues { it.isBlank() }.keys.forEach { issues += "container-table: missing $it ($name)" }
                val groups = if (id == null) {
                    // An explicit dash (as in the old airdrop row) publishes neither an ID nor a pool.
                    if (toggles.isNotEmpty() || pool != "—") issues += "container-table: missing toggle"
                    emptyList()
                } else {
                    if (!usedIds.add(id)) issues += "container-table: duplicate container $id"
                    val panel = panelsById[id]
                    if (panel == null) {
                        issues += "container-table: missing panel $id"
                        emptyList()
                    } else {
                        usedPanels.add(panel)
                        val expected = expandPoolCount.matchEntire(pool)
                            ?.groupValues?.get(1)?.toIntOrNull()
                        if (expected == null) issues += "container-table: missing total $id"
                        parseGroups(panel, issues).also { groups ->
                            if (expected != groups.sumOf { it.items.size }) issues += "container-table: total mismatch $id"
                        }
                    }
                }
                entries += ExtractionEntry(
                    id = "container:${id ?: "unpublished:$name:$category"}", name = name,
                    category = category, sourceCategory = category,
                    tags = listOfNotNull(fields["子类"], fields["档次"]).filter { it.isNotBlank() },
                    fields = fields.filterKeys { it !in setOf("名称", "掉落池", "来源类别") }
                        .map { (key, value) -> ExtractionField(key, value) },
                    type = ExtractionEntryType.DROP_POOL, containerId = id, dropPoolGroups = groups,
                    dropCountState = dropCountState(fields.getValue("件数概率"))
                )
            }
            if (table.select(".sec-drop-chip").any { it.closest("table") === table && it.closest(panelSelector) == null }) {
                issues += "container-table: chip outside panel"
            }
        }
        root.select(panelSelector).forEach {
            if (it !in usedPanels) issues += "container-table: orphan panel"
        }
        val declared = root.select("h2").mapNotNull {
            containerTableCount.find(content(it))?.groupValues?.get(1)?.toIntOrNull()
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
                label = content(element.select(".sec-drop-pool__tag").singleOrNull()).takeIf { it.isNotBlank() }
                count = declaredPoolItemCount(content(element.select(".sec-drop-pool__cnt").singleOrNull()))
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
