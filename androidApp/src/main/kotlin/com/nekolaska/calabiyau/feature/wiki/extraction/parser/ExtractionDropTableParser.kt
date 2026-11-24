package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.nodes.Element

internal object ExtractionDropTableParser {
    private val poolHeaders = setOf("掉落池条目(完整)", "掉落池条目(按稀有度分行)")
    private val stagePoolHeaders = setOf("候选池", "通用掉落")
    private val qualityShares = listOf("Q5臻藏", "Q4完美", "Q3卓越", "Q2及以下")
    private val distributionHeaders = setOf("阶段", "阈值", "件数概率") + qualityShares
    private val percentage = Regex("""(\d+(?:\.(\d+))?)%""")
    private val legacyPoolItems = Regex("""^\d+\s*项[：:](.*)$""", RegexOption.DOT_MATCHES_ALL)

    fun parse(table: Element, issues: MutableList<String>): List<ExtractionEntry> {
        val ownRows = table.select("tr").filter { it.closest("table") === table }
        val headingCells = ownRows.flatMap { row -> row.children().filter { it.tagName() == "th" } } +
            ownRows.take(4).flatMap { row -> row.children().filter { it.tagName() == "td" } }
        val texts = headingCells.map { headerKey(content(it, "table")) }
        val isPool = texts.any { it.startsWith("掉落池条目") }
        val distribution = !isPool && ("阶段" in texts || "阈值" in texts) &&
            ("Q3卓越" in texts || "Q2及以下" in texts || ("件数概率" in texts && texts.none { it in stagePoolHeaders }))
        val isStage = distribution || listOf("阶段", "阈值").all { it in texts }
        if (!isPool && !isStage) return emptyList()
        val rows = extractionTableRows(table)
        if (distribution) return parseStageShares(rows, issues)
        val nameHeader = if (isPool) "名称" else "阶段"
        val valueHeaders = if (isPool) poolHeaders else stagePoolHeaders
        val headerIndex = rows.indexOfFirst { row ->
            val keys = row.cells.map { headerKey(content(it)) }
            nameHeader in keys && (isPool || "阈值" in keys) && keys.count { it in valueHeaders } == 1 &&
                keys.none { it.isBlank() } && keys.distinct().size == keys.size &&
                row.cells.filterNotNull().distinct().all { it.tagName() == "th" } && !row.malformed
        }.takeIf { it >= 0 } ?: rows.indexOfFirst { row ->
            // Some older Wiki tables use td for their single header row.
            val keys = row.cells.map { headerKey(content(it)) }
            nameHeader in keys && (isPool || "阈值" in keys) && keys.count { it in valueHeaders } == 1 &&
                keys.none { it.isBlank() } && keys.distinct().size == keys.size && !row.malformed
        }
        if (headerIndex < 0) {
            issues += "drop-table: unsupported headers"
            return emptyList()
        }
        val headers = rows[headerIndex].cells.map { content(it) }
        val keys = headers.map(::headerKey)
        val nameIndex = keys.indexOf(nameHeader)
        val poolIndex = keys.indexOfFirst { it in valueHeaders }
        if (keys.toSet().size != keys.size || keys.any { it.isBlank() }) {
            issues += "drop-table: ambiguous columns"
            return emptyList()
        }
        val grouped = mutableListOf<TableGroup>()
        rows.drop(headerIndex + 1).forEach { row ->
            val rowKeys = row.cells.map { headerKey(content(it)) }
            if (rowKeys == keys) return@forEach // Repeated headings are not data.
            if (row.malformed || row.cells.size != headers.size || row.cells.any { it == null } ||
                row.cells.filterNotNull().distinct().size != headers.size) {
                issues += "drop-table: incomplete or merged data row"
                return@forEach
            }
            val nameCell = row.cells[nameIndex]!!
            if (content(nameCell).isBlank()) {
                issues += "drop-table: missing $nameHeader"
                return@forEach
            }
            val values = row.cells.map { content(it) }
            val previous = grouped.lastOrNull()
            if (previous != null && previous.nameCell === nameCell) {
                // Metadata must not change within one name's rowspan.
                if (values.indices.any { it != poolIndex && previous.values[it] != values[it] }) {
                    issues += "drop-table: inconsistent merged record"
                }
                // A vertically merged pool cell is one value, not one copy per row.
                if (previous.poolCells.lastOrNull() !== row.cells[poolIndex]) previous.poolCells.add(row.cells[poolIndex]!!)
            } else grouped += TableGroup(nameCell, values, mutableListOf(row.cells[poolIndex]!!))
        }
        if (grouped.isEmpty()) issues += "drop-table: no records"
        return grouped.map { group ->
            val values = group.values.toMutableList().apply { this[poolIndex] = group.poolCells.joinToString("\n") { content(it) } }
            val fields = headers.indices.filter { it != nameIndex }.mapNotNull { index ->
                values[index].takeIf { it.isNotBlank() }?.let { value ->
                    ExtractionField(if (isPool && index == poolIndex) "掉落池条目（完整）" else canonicalHeader(keys[index], headers[index]), value)
                }
            }
            fun field(key: String) = values.getOrNull(keys.indexOf(key)).orEmpty()
            if (!isPool) ExtractionEntry(
                id = "stage:${values[nameIndex]}:${field("阈值")}", name = "阶段 ${values[nameIndex]} · ${headers[poolIndex]}",
                category = if (keys[poolIndex] == "通用掉落") "首领阶段通用掉落" else "Boss 阶段候选池",
                fields = fields, type = ExtractionEntryType.DROP_STAGE
            ) else ExtractionEntry(
                id = "summary:${values[nameIndex]}:${field("来源类别")}:${field("子类")}:${field("掉落档次")}",
                name = values[nameIndex], category = "总览表 · 完整掉落池", fields = fields,
                tags = listOf(field("来源类别"), field("子类"), field("掉落档次").takeIf { it.isNotBlank() }?.let { "档次 $it" }.orEmpty()).filter { it.isNotBlank() },
                dropPoolGroups = if (keys[poolIndex].contains("按稀有度分行")) group.poolCells.flatMap { parseRarityRows(content(it)) }
                    else parseLegacy(values[poolIndex]),
                type = ExtractionEntryType.DROP_POOL, sourceCategory = field("来源类别")
            )
        }
    }

    /** Revision 120524 reports probabilities, not candidate-pool sizes. Keep the reported precision. */
    private fun parseStageShares(rows: List<ExtractionTableRow>, issues: MutableList<String>): List<ExtractionEntry> {
        val headerIndex = rows.indexOfFirst { row ->
            !row.malformed && row.cells.size == distributionHeaders.size &&
                row.cells.map { headerKey(content(it)) }.toSet() == distributionHeaders
        }
        if (headerIndex < 0) {
            issues += "drop-table: unsupported headers for stage distribution"
            return emptyList()
        }
        val headers = rows[headerIndex].cells.map { content(it) }
        val keys = headers.map(::headerKey)
        val seen = mutableSetOf<Int>()
        return rows.drop(headerIndex + 1).mapNotNull { row ->
            if (row.malformed || row.cells.size != keys.size || row.cells.any { it == null } ||
                row.cells.distinct().size != keys.size) {
                issues += "drop-table: incomplete stage distribution row"
                return@mapNotNull null
            }
            val values = row.cells.map { content(it) }
            if (values.map(::headerKey) == keys) return@mapNotNull null
            fun field(key: String) = values[keys.indexOf(key)]
            val number = field("阶段").toIntOrNull()
            val threshold = field("阈值")
            if (number == null || number < 1 || (threshold.toIntOrNull() ?: -1) < 0 || !seen.add(number) ||
                dropCountState(field("件数概率")) == DropCountState.UNKNOWN || !validShares(qualityShares.map(::field))) {
                issues += "drop-table: invalid stage distribution for ${field("阶段")}"
                return@mapNotNull null
            }
            ExtractionEntry(id = "stage:$number:$threshold", name = "阶段 $number · 品质占比", category = "首领阶段掉落分布",
                fields = keys.indices.filter { keys[it] != "阶段" }.map { ExtractionField(headers[it], values[it]) },
                type = ExtractionEntryType.DROP_STAGE)
        }.also { if (it.isEmpty()) issues += "drop-table: no stage distributions" }
    }

    private fun validShares(values: List<String>): Boolean {
        var sum = 0.0
        var rounding = 0.0
        for (value in values) {
            val match = percentage.matchEntire(value) ?: return false
            val amount = match.groupValues[1].toDoubleOrNull() ?: return false
            if (amount !in 0.0..100.0) return false
            sum += amount
            // Four one-decimal cells can total 99.9% (stage 6) or 100.1%, for example.
            rounding += 0.5 * Math.pow(0.1, match.groupValues[2].length.toDouble())
        }
        return kotlin.math.abs(sum - 100) <= rounding + 1e-9
    }

    private fun canonicalHeader(key: String, original: String): String = when (key) {
        "来源类别", "子类", "掉落档次", "件数概率" -> key
        else -> original
    }

    private fun parseRarityRows(value: String): List<ExtractionDropPoolGroup> = value.lines().mapNotNull { line ->
        val text = line.trim()
        if (text.isBlank() || text in listOf("—", "—：—", "—:—")) return@mapNotNull null
        val separator = text.indexOfFirst { it == '：' || it == ':' }
        val label = text.takeIf { separator > 0 }?.substring(0, separator)?.trim().orEmpty()
        val code = qualityCode(label)
        if (code == null) ExtractionDropPoolGroup("掉落池", listOf(text))
        else ExtractionDropPoolGroup(label, text.substring(separator + 1).split('、').map { it.trim() }.filter { it.isNotBlank() }, code)
    }

    private fun parseLegacy(value: String): List<ExtractionDropPoolGroup> {
        val items = legacyPoolItems.matchEntire(value.trim())
            ?.groupValues?.get(1)?.split('、')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        return if (items.isEmpty()) emptyList() else listOf(ExtractionDropPoolGroup("全部掉落物", items))
    }

    private class TableGroup(val nameCell: Element, val values: List<String>, val poolCells: MutableList<Element>)
}
