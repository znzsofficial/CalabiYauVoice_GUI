package com.nekolaska.calabiyau.feature.wiki.gamesettings.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcGameSettingsPage
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingField
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsSection
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class PcSettingsParseResult(
    val page: PcGameSettingsPage,
    val tableCount: Int,
    val issues: List<String>
) {
    val canCache: Boolean get() = issues.isEmpty() && !page.isEmpty()
}

object PcGameSettingsParser {
    // Revision 120159's independently named table contexts and minimum complete row counts.
    // A source deletion/rename should be reviewed, rather than silently publishing a truncated page.
    internal val expectedTables: Map<String, Map<String, Int>> = linkedMapOf(
        "推荐画质设置速查" to linkedMapOf("" to 31),
        "基础设置" to linkedMapOf("地图" to 4, "隐私" to 13, "语言与语音设置" to 4,
            "对局界面" to 13, "系统界面" to 3, "鼠标" to 2),
        "操作设置" to linkedMapOf("移动与身法" to 16, "其他操作" to 6, "武器" to 9,
            "装备" to 9, "技能" to 3, "语音聊天" to 2, "标点轮盘" to 5,
            "界面与对局操作" to 12, "观战" to 5),
        "灵敏度" to linkedMapOf("" to 7),
        "画面" to linkedMapOf("显示" to 6, "特效质量" to 2, "图像质量" to 20, "数据" to 4),
        "声音" to linkedMapOf("音量与音频" to 12, "语音聊天" to 13),
        "准星" to linkedMapOf("准星通用选项" to 9, "内侧十字准星" to 7,
            "外侧十字准星" to 7, "开镜准星颜色" to 3),
        "手柄" to linkedMapOf("瞄准设置" to 10, "行动设置" to 11, "常规设置" to 8,
            "高级视角" to 26, "陀螺仪" to 21, "手柄键位" to 20)
    )
    private val bindingHeaders = setOf("设置项", "默认", "可选值或说明")
    private val standardHeaders = setOf("设置项", "说明", "可选值", "默认")
    private val graphicsHeaders = setOf("设置项", "作用", "帧数影响", "可选值", "默认")
    private val audioHeaders = standardHeaders + "推荐"
    private val recommendationHeaders = setOf("分类", "设置项", "推荐", "说明")
    private val supportedHeaders = setOf(bindingHeaders, standardHeaders, graphicsHeaders, audioHeaders, recommendationHeaders)
    private fun isUpscalingSection(title: String): Boolean =
        title.contains("DLSS", ignoreCase = true) && title.contains("XeSS", ignoreCase = true)

    private fun expectedHeaders(section: String): Set<String>? = when (section) {
        "操作设置", "手柄" -> bindingHeaders
        "基础设置", "灵敏度", "准星" -> standardHeaders
        "画面" -> graphicsHeaders
        "声音" -> audioHeaders
        "推荐画质设置速查" -> recommendationHeaders
        else -> null
    }

    fun parse(html: String): PcSettingsParseResult {
        val document = Jsoup.parse(html)
        val root = document.selectFirst(".mw-parser-output") ?: document.body()
        root.select("script, style, #toc, .toc, .mw-editsection, .klbq-breadcrumb-bar, .printfooter, .navbox").remove()
        val sections = mutableListOf<SectionBuilder>()
        val issues = mutableListOf<String>()
        val identities = mutableMapOf<String, Int>()
        val tableCounts = mutableMapOf<Pair<String, String>, MutableList<Int>>()
        var current: SectionBuilder? = null
        var subsection = ""
        var tableCount = 0
        fun identity(vararg parts: String): String {
            val base = parts.joinToString("") { "${it.length}:$it" }
            val occurrence = identities.getOrDefault(base, 0)
            identities[base] = occurrence + 1
            return "$base#$occurrence"
        }
        fun addText(element: Element, depth: Int = 0) {
            val section = current ?: return
            val clone = element.clone()
            if (element.tagName() == "li") clone.select("ul, ol").remove()
            val text = readableText(clone).removePrefix("• ").trim()
            if (text.isNotBlank()) section.blocks += PcSettingsBlock.Text(
                identity(section.id, subsection, "text", text), subsection, text, depth
            )
        }
        fun walk(element: Element, depth: Int = 0) {
            when (element.tagName()) {
                "h2" -> {
                    val title = element.selectFirst(".mw-headline")?.text() ?: element.text()
                    if (title.isNotBlank()) {
                        val view = when {
                            title == "推荐画质设置速查" -> PcSettingsView.RECOMMENDATIONS
                            title == "启动模式" || isUpscalingSection(title) -> PcSettingsView.KNOWLEDGE
                            else -> PcSettingsView.SETTINGS
                        }
                        current = SectionBuilder(identity("section", title), title, view).also(sections::add)
                        subsection = ""
                    }
                }
                "h3", "h4" -> subsection = element.selectFirst(".mw-headline")?.text() ?: element.text()
                "table" -> {
                    if (!element.hasClass("klbqtable")) {
                        if (current != null && element.text().contains("设置项")) issues += "无法识别的设置表格"
                        return
                    }
                    tableCount++
                    val section = current
                    if (section == null) { issues += "设置表格缺少所属章节"; return }
                    val reference = section.view == PcSettingsView.KNOWLEDGE
                    val table = parseTable(element, reference)
                    issues += table.issues.map { "${section.title}/$subsection：$it" }
                    if (reference) {
                        table.rows.forEach { row ->
                            val nameField = row.entries.first()
                            section.blocks += PcSettingsBlock.Reference(
                                identity(section.id, subsection, "reference", nameField.value), subsection,
                                nameField.key, nameField.value,
                                row.entries.drop(1).map { PcSettingField(it.key, it.value) }
                            )
                        }
                        return
                    }
                    if (expectedHeaders(section.title)?.let { it != table.headers } == true) {
                        issues += "${section.title}/$subsection：章节表头字段不完整或发生变化"
                    }
                    tableCounts.getOrPut(section.title to subsection) { mutableListOf() }.add(table.rows.size)
                    table.rows.forEach { row ->
                        val category = row["分类"].orEmpty()
                        val name = row.getValue("设置项")
                        section.blocks += PcSettingsBlock.Setting(
                            id = identity(section.id, subsection, category, "setting", name),
                            subsection = subsection,
                            name = name,
                            fields = row.filterKeys { it != "设置项" && it != "分类" }
                                .map { PcSettingField(it.key, it.value) },
                            recommendationCategory = category,
                            isQuickReference = table.isQuickReference
                        )
                    }
                    if (table.isQuickReference != (section.view == PcSettingsView.RECOMMENDATIONS)) {
                        issues += "${section.title}/$subsection：推荐表与所属章节不符"
                    }
                }
                "li" -> {
                    addText(element, depth.coerceAtLeast(1))
                    element.children().filter { it.tagName() in setOf("ul", "ol") }.forEach { walk(it, depth) }
                }
                "ul", "ol" -> element.children().forEach { walk(it, depth + 1) }
                "p", "dt", "dd" -> addText(element, depth)
                else -> element.children().forEach { walk(it, depth) }
            }
        }
        walk(root)
        expectedTables.forEach { (section, tables) ->
            tables.forEach { (subsection, minimumRows) ->
                val counts = tableCounts[section to subsection].orEmpty()
                if (counts.size != 1 || counts.singleOrNull()?.let { it < minimumRows } != false) {
                    issues += "$section/$subsection：预期一张完整表格（至少 $minimumRows 行），实际 $counts"
                }
            }
        }
        for ((title, minimumBlocks) in mapOf("启动模式" to 8, "DLSS / XeSS 科普" to 5)) {
            val matching = sections.filter { if (title == "启动模式") it.title == title else isUpscalingSection(it.title) }
            if (matching.size != 1 || (matching.singleOrNull()?.blocks?.filterIsInstance<PcSettingsBlock.Text>()?.size ?: 0) < minimumBlocks) {
                issues += "缺少完整科普章节：$title"
            }
        }
        if (sections.groupingBy { it.title }.eachCount().any { it.value > 1 }) issues += "重复的顶级章节"
        val page = PcGameSettingsPage(sections.map { PcSettingsSection(it.id, it.title, it.view, it.blocks.toList()) })
        if (issues.isNotEmpty()) WikiParseLogger.warnMalformed("PcGameSettings", issues.take(5).joinToString("; "), html)
        return PcSettingsParseResult(page, tableCount, issues.distinct())
    }

    private class SectionBuilder(val id: String, val title: String, val view: PcSettingsView) {
        val blocks = mutableListOf<PcSettingsBlock>()
    }

    private data class TableResult(
        val rows: List<Map<String, String>>,
        val isQuickReference: Boolean,
        val headers: Set<String>,
        val issues: List<String>
    )
    private data class Span(val text: String, val remaining: Int)

    /** Expands bounded row/column spans first; fields are then mapped by semantic header, never a page-wide index. */
    private fun parseTable(table: Element, reference: Boolean = false): TableResult {
        val issues = mutableListOf<String>()
        val rows = mutableListOf<Map<String, String>>()
        val carry = mutableMapOf<Int, Span>()
        var headers = emptyList<String>()
        val sourceRows = table.select("tr").filter { it.closest("table") === table }
        if (sourceRows.size > 600) return TableResult(emptyList(), false, emptySet(), listOf("表格行数异常"))
        sourceRows.forEachIndexed { rowIndex, tr ->
            val grid = mutableMapOf<Int, String>()
            carry.toMap().forEach { (column, span) ->
                grid[column] = span.text
                if (span.remaining <= 1) carry.remove(column) else carry[column] = span.copy(remaining = span.remaining - 1)
            }
            var column = 0
            tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }.forEach { cell ->
                while (grid.containsKey(column)) column++
                fun span(name: String): Int = if (!cell.hasAttr(name)) 1 else cell.attr(name).toIntOrNull() ?: -1
                val colspan = span("colspan")
                val rowspan = span("rowspan")
                if (colspan !in 1..12 || rowspan !in 1..600 || column + colspan > 12) {
                    issues += "第 ${rowIndex + 1} 行合并单元格范围异常"
                    return@forEach
                }
                if (rowIndex > 0 && colspan > 1 && headers.indexOf("设置项") in column until (column + colspan)) {
                    issues += "第 ${rowIndex + 1} 行设置名被跨列单元格覆盖"
                }
                val text = readableText(cell)
                repeat(colspan) {
                    if (grid.containsKey(column)) issues += "第 ${rowIndex + 1} 行合并单元格重叠"
                    grid[column] = text
                    if (rowspan > 1) carry[column] = Span(text, rowspan - 1)
                    column++
                }
            }
            if (rowIndex == 0) {
                headers = grid.toSortedMap().values.map { header ->
                    when (header.replace(Regex("\\s+"), "")) {
                        "可选值/说明", "可选值／说明", "可选值或说明" -> "可选值或说明"
                        else -> header
                    }
                }
                val recognized = if (reference) headers.size in 2..12 && headers.all { it.isNotBlank() }
                    else headers.toSet() in supportedHeaders
                if (headers.size != headers.distinct().size || !recognized ||
                    carry.isNotEmpty()) issues += "无法识别的语义表头：$headers"
            } else {
                if (grid.size != headers.size || headers.indices.any { !grid.containsKey(it) }) {
                    issues += "第 ${rowIndex + 1} 行字段缺失或多出"
                } else {
                    val values = headers.mapIndexed { index, header -> header to grid.getValue(index) }.toMap()
                    if (reference) {
                        if (values.values.firstOrNull().isNullOrBlank()) issues += "第 ${rowIndex + 1} 行没有参考条目名称"
                        else rows += values
                    } else if (values["设置项"].isNullOrBlank()) issues += "第 ${rowIndex + 1} 行没有设置名"
                    else {
                        if ("分类" in headers && values["分类"].isNullOrBlank()) issues += "第 ${rowIndex + 1} 行缺少推荐分类"
                        rows += values
                    }
                }
            }
        }
        if (carry.isNotEmpty()) issues += "表格在跨行单元格结束前截断"
        if (rows.isEmpty()) issues += "表格没有资料行"
        return TableResult(rows, headers.toSet() == recommendationHeaders, headers.toSet(), issues)
    }

    /** Preserve paragraph/BR/list boundaries without carrying HTML indentation into native text. */
    private fun readableText(element: Element): String = buildString {
        fun visit(node: Node) {
            when (node) {
                is TextNode -> append(node.text().replace(Regex("[\\s\\u00a0]+"), " "))
                is Element -> {
                    val tag = node.tagName()
                    if (tag == "br") { append('\n'); return }
                    if (tag in setOf("p", "div", "ul", "ol", "li")) append('\n')
                    if (tag == "li") append("• ")
                    node.childNodes().forEach(::visit)
                    if (tag in setOf("p", "div", "li")) append('\n')
                }
            }
        }
        visit(element)
    }.lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
}
