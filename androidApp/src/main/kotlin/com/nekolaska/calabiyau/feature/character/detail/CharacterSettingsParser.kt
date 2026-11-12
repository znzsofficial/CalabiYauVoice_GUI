package com.nekolaska.calabiyau.feature.character.detail

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.wikiArticleTitle
import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailApi.InfoPair
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class CharacterSettingInfo(
    val settingSummary: String = "",
    val settingObserverQuote: String = "",
    val lifeInfo: List<InfoPair> = emptyList(),
    val relations: List<InfoPair> = emptyList()
)

internal data class CharacterSettingsResult(val entries: Map<String, CharacterSettingInfo>, val issues: List<String>) {
    val canCache: Boolean get() = entries.isNotEmpty() && issues.isEmpty()
}

internal object CharacterSettingsParser {
    // Verified full names used by the setting page vs short article names. Never prefix-match names.
    private val aliases = mapOf("奥黛丽" to "奥黛丽格罗夫", "玛德蕾娜" to "玛德蕾娜利里", "加拉蒂亚" to "加拉蒂亚利里")
    fun identity(name: String): String {
        val normalized = name.replace(Regex("[·・\\s\\u00a0]"), "").trim()
        return aliases[normalized] ?: normalized
    }

    fun parse(html: String): CharacterSettingsResult {
        val root = Jsoup.parse(html)
        val issues = mutableListOf<String>()
        val entries = linkedMapOf<String, CharacterSettingInfo>()
        val conflicts = mutableSetOf<String>()
        val blocks = root.select(".klbq-role-info")
        if (blocks.isEmpty()) issues += "settings: no role blocks"
        blocks.forEachIndexed { index, block ->
            fun own(selector: String) = block.select(selector).filter { it.closest(".klbq-role-info") === block }
            val nameElement = own(".name").singleOrNull()
            val rawName = nameElement?.let { element ->
                val linkedNames = element.select("a[href]").mapNotNull { wikiArticleTitle(it.attr("href")) }.distinct()
                when {
                    linkedNames.size == 1 -> linkedNames.single()
                    linkedNames.isNotEmpty() -> ""
                    element.ownText().isNotBlank() -> element.ownText().trim()
                    else -> element.clone().apply {
                        select("small, [lang], .en, .jp, .english, .japanese").remove()
                    }.text().trim().takeIf { !it.contains(Regex("[A-Za-z]")) }.orEmpty()
                }
            }.orEmpty()
            val name = identity(rawName)
            if (name.isBlank()) {
                issues += "settings block $index: missing/ambiguous name"
                return@forEachIndexed
            }

            val fields = mutableListOf<InfoPair>()
            val usedValues = mutableSetOf<Element>()
            own("dl dt").forEach { dt ->
                val label = clean(dt).trim().trimEnd('：', ':').trim()
                val values = generateSequence(dt.nextElementSibling()) { it.nextElementSibling() }
                    .takeWhile { it.tagName() != "dt" }
                    .filter { it.tagName() == "dd" && it.closest("dl") === dt.closest("dl") }.toList()
                if (label.isBlank() || values.isEmpty()) issues += "settings $name: incomplete definition"
                values.forEach { dd ->
                    usedValues.add(dd)
                    clean(dd).takeIf { it.isNotBlank() }?.let { fields += InfoPair(label, it) }
                }
            }
            if (own("dl dd").any { it !in usedValues }) issues += "settings $name: orphan value"
            if (fields.isEmpty()) issues += "settings $name: no fields"
            // Current template always declares both narrative slots; empty values are allowed.
            val labels = own("dl dt").map { clean(it).trimEnd('：', ':').trim() }
            if ("简介" !in labels || "观测语录" !in labels) issues += "settings $name: missing narrative slots"

            val relations = own(".data").flatMap { data ->
                // Read each container once, not every nested span (which duplicated linked names).
                clean(data).lines().mapNotNull { line ->
                    val separator = line.indexOfFirst { it == '：' || it == ':' }
                    if (separator <= 0) return@mapNotNull null
                    val label = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim().trim('、', '，', ',', ' ')
                    if (label.contains("CV", true) || label.contains("声优") || value.isBlank()) null
                    else InfoPair(label, value)
                }
            }.distinct()
            fun narrative(label: String) = fields.filter { it.label == label }.map { it.value }.distinct().joinToString("\n")
            val info = CharacterSettingInfo(narrative("简介"), narrative("观测语录"),
                fields.filterNot { it.label in setOf("简介", "观测语录") }.distinct(), relations)
            val previous = entries[name]
            if (previous != null && previous != info) {
                conflicts += name
                issues += "settings $name: conflicting blocks"
            } else entries[name] = info
        }
        conflicts.forEach(entries::remove)
        if (issues.isNotEmpty()) WikiParseLogger.warnMalformed("CharacterSettings", issues.take(8).joinToString("; "), html)
        return CharacterSettingsResult(WikiParseLogger.finishMap("CharacterSettings", entries, html), issues)
    }

    private fun clean(element: Element): String = HtmlText.clean(element.clone().apply {
        select("script, style, .mw-editsection, .reference, .smwttcontent, .smwtticon").remove()
    }.html())
}
