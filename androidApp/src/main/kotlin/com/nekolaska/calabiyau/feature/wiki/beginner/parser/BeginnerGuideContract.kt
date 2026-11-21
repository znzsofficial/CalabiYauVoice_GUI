package com.nekolaska.calabiyau.feature.wiki.beginner.parser

import com.nekolaska.calabiyau.feature.wiki.beginner.model.GuideBlock
import com.nekolaska.calabiyau.feature.wiki.beginner.model.GuideSection
import java.net.URI
import java.net.URLDecoder

private const val OVERVIEW = "这是一款怎样的游戏"
private const val SUITABILITY = "我适合入坑吗"
private const val VERSIONS = "我该下哪个版本"
internal const val DOWNLOAD_SECTION = "下载与安装"
private const val REQUIREMENTS = "配置需求"
private const val SYSTEMS = "开始游戏前需要了解什么"
private const val ADVICE = "游戏设置建议"
private const val FAQ = "常见问题"
private val legacy = listOf(SUITABILITY, VERSIONS, DOWNLOAD_SECTION, REQUIREMENTS)
private val expanded = listOf(OVERVIEW) + legacy + listOf(SYSTEMS, ADVICE, FAQ)

/** The first column named in the contract identifies the record, regardless of DOM position or td/th. */
internal enum class GuideTable(val section: String, val subsection: String, val columns: List<String>, val minRows: Int) {
    Version(VERSIONS, "", listOf("版本", "游玩平台", "登录方式", "说明"), 4),
    Pc(REQUIREMENTS, "PC 端", listOf("项目", "最低配置", "推荐配置"), 8),
    Mobile(REQUIREMENTS, "移动端", listOf("项目", "最低配置", "推荐配置"), 5),
    Equipment(SYSTEMS, "武器装备", listOf("装备类型", "主要作用", "新手需要了解的内容"), 4),
    Advice(ADVICE, "", listOf("设置项目", "建议检查的内容"), 6);

    companion object {
        fun forSection(section: String, subsection: String) = entries.find { it.section == section && it.subsection == subsection }
    }
}

private fun List<GuideBlock>.paragraphs() = filterIsInstance<GuideBlock.Paragraph>()

/** Content before the first heading belongs to the section itself. */
private fun List<GuideBlock>.under(heading: String): List<GuideBlock> {
    val start = if (heading.isEmpty()) 0 else indexOfFirst { it is GuideBlock.Heading && it.title == heading }
        .takeIf { it >= 0 }?.plus(1) ?: return emptyList()
    return drop(start).takeWhile { it !is GuideBlock.Heading }
}

private fun List<String>.coversOnce(required: Collection<String>) = toSet().let { it.size == size && it.containsAll(required) }

private fun List<GuideBlock>.hasHeadings(vararg expected: String) =
    filterIsInstance<GuideBlock.Heading>().map { it.title }.coversOnce(expected.asList())

internal fun checkGuideContent(
    sections: List<GuideSection>, tables: List<GuideTable>, revision: Long?, tocTitles: List<String>?, downloadPanes: List<String>
): List<String> = buildList {
    fun blocks(name: String) = sections.find { it.title == name }?.blocks.orEmpty()
    val titles = sections.map { it.title }
    val notices = blocks(VERSIONS).paragraphs().filter { it.notice }
    // 120474 removed the callout title before 120494 added four chapters. Known revision metadata
    // or (for pre-revid disk payloads) an independently verified four-chapter TOC identifies the old layout.
    val legacyDirectory = tocTitles?.let { it.size == legacy.size && it.coversOnce(legacy) } == true
    val isLegacy = titles.none { it in expanded && it !in legacy } && when (revision) {
        null -> legacyDirectory
        120197L, 120474L -> tocTitles == null || legacyDirectory
        else -> false
    }
    val required = if (isLegacy) legacy else expanded
    if (!titles.coversOnce(required) || sections.any { it.blocks.isEmpty() }) {
        add("章节缺失、重复或无正文")
    }
    GuideTable.entries.filter { it.section in required }.forEach { schema ->
        if (tables.count { it == schema } != 1) add("${schema.section}/${schema.subsection}：表格缺失或重复")
    }
    val suitability = blocks(SUITABILITY)
    if (!suitability.hasHeadings("作为射击游戏", "作为 PvP") || suitability.paragraphs().size < 22 ||
        suitability.paragraphs().count { it.depth == 2 } < 8) add("入坑说明不完整")
    if (notices.isEmpty() ||
        blocks(VERSIONS).paragraphs().none { !it.notice }) add("版本互通说明不完整")
    val requirements = blocks(REQUIREMENTS)
    if (!requirements.hasHeadings("PC 端", "移动端") || requirements.under("PC 端").paragraphs().isEmpty()) add("配置来源说明缺失")
    val downloads = blocks(DOWNLOAD_SECTION)
    val platforms = mapOf("PC国服" to (3 to 1), "PC国际服" to (5 to 2), "移动端" to (5 to 1))
    if (!downloadPanes.coversOnce(platforms.keys) || platforms.any { (platform, minimum) ->
            val paragraphs = downloads.under(platform).paragraphs()
            paragraphs.size < minimum.first || paragraphs.flatMap { it.content.spans }.mapNotNull { it.url }.distinct().size < minimum.second
        }) add("下载平台说明或链接不完整")
    if (isLegacy) return@buildList

    val overview = blocks(OVERVIEW).paragraphs()
    if (overview.count { it.marker != null } < 5 || overview.count { it.marker == null } < 5) add("游戏介绍不完整")
    val systems = blocks(SYSTEMS)
    if (systems.under("").paragraphs().isEmpty() || !systems.hasHeadings("弦化", "超弦体", "武器装备")) add("核心系统说明不完整")
    if (systems.under("弦化").none { it is GuideBlock.Term }) add("弦化要点标题缺失")
    // Each system has an introduction, four or five list entries, follow-up prose and its own Wiki link.
    listOf(Triple("弦化", 4, "弦化"), Triple("超弦体", 5, "角色筛选"), Triple("武器装备", 5, "武器筛选")).forEach { (heading, bullets, target) ->
        val paragraphs = systems.under(heading).paragraphs()
        if (paragraphs.count { it.marker != null } < bullets || paragraphs.count { it.marker == null } < 4 ||
            paragraphs.none { p -> p.content.spans.any { it.url != null && articleTitle(it.url) == target } }) add("$heading：正文或站内链接不完整")
    }
    val advice = blocks(ADVICE)
    if (advice.takeWhile { it !is GuideBlock.Record }.paragraphs().isEmpty() ||
        advice.takeLastWhile { it !is GuideBlock.Record }.paragraphs().isEmpty()) add("设置建议或附注不完整")

    val faq = blocks(FAQ)
    val questions = faq.filterIsInstance<GuideBlock.Term>()
    // Wiki emits dl/dt then a sibling p (or dd in a definition list), not dt/dd pairs exclusively.
    if (questions.size < 8 || questions.map { it.content.text }.distinct().size != questions.size || faq.indices.any { i ->
            faq[i] is GuideBlock.Term && faq.getOrNull(i + 1) !is GuideBlock.Paragraph
        }) add("常见问题或回答不完整")
}

/** A current article read, never a caption, edit/history URL, ambiguous query or another Wiki. */
private fun articleTitle(url: String): String? = runCatching {
    val uri = URI(url)
    val defaultPort = when (uri.scheme.lowercase()) { "https" -> 443; "http" -> 80; else -> return null }
    if (!uri.host.equals("wiki.biligame.com", ignoreCase = true) || uri.userInfo != null ||
        uri.port != -1 && uri.port != defaultPort || uri.rawFragment != null) return null
    fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
    val params = mutableMapOf<String, String>()
    uri.rawQuery?.split('&')?.forEach { part ->
        val key = decode(part.substringBefore('='))
        if ('=' !in part || params.put(key, decode(part.substringAfter('='))) != null) return null
    }
    if (params.remove("action")?.let { it != "view" } == true) return null
    val path = uri.rawPath
    val title = when {
        path == "/klbq/index.php" -> params.remove("title") ?: return null
        path.startsWith("/klbq/") -> decode(path.removePrefix("/klbq/").replace("+", "%2B"))
        else -> return null
    }
    title.replace('_', ' ').takeIf { params.isEmpty() }
}.getOrNull()
