package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import util.wikiPathEncode

object GameModeParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"
    private val GALLERY_REGEX = Regex("""<gallery[^>]*>[\s\S]*?</gallery>""", RegexOption.IGNORE_CASE)
    private val KEY_TEMPLATE_REGEX = Regex("""\{\{按键\|([^}|]+)\}\}""")
    private val FILE_LINK_REGEX = Regex("""\[\[(?:文件|File):[^\]]*]]""", RegexOption.IGNORE_CASE)
    private val CATEGORY_LINK_REGEX = Regex("""\[\[(?:分类|Category):[^\]]*]]""", RegexOption.IGNORE_CASE)
    private val WIKI_LINK_ALIAS_REGEX = Regex("""\[\[([^\]|]*)\|([^\]]*)]\]""")
    private val WIKI_LINK_REGEX = Regex("""\[\[([^\]]*)]\]""")
    private val STRIKE_REGEX = Regex("""<s>[\s\S]*?</s>""")
    private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val BULLET_REGEX = Regex("""^\*+\s*""")
    private val EMPTY_MAP_LINE_REGEX = Regex("""^[•\s]*模式地图：\s*$""")
    private val MARKUP_PREFIXES = charArrayOf('=', '_', '<', '*', ':', ';', '|')

    fun parseModeMapMapping(wikitext: String): Map<String, List<String>> {
        val mapping = mutableMapOf<String, List<String>>()
        val groups = mutableMapOf<String, String>()
        val lists = mutableMapOf<String, String>()
        val groupRegex = Regex("""\|group(\d+)\s*=""")
        val listRegex = Regex("""\|list(\d+)\s*=""")

        for (line in wikitext.lineSequence()) {
            groupRegex.find(line)?.let { match ->
                groups[match.groupValues[1]] = line.substring(match.range.last + 1).trim()
            }
            listRegex.find(line)?.let { match ->
                lists[match.groupValues[1]] = line.substring(match.range.last + 1).trim()
            }
        }

        for ((num, groupRaw) in groups) {
            val listRaw = lists[num] ?: continue
            val maps = Regex("""\[\[([^\]|]+)]]""").findAll(listRaw)
                .map { it.groupValues[1] }
                .toList()
            val modeNames = Regex("""\[\[[^\]]*\|([^\]]+)]]""").findAll(groupRaw)
                .map { it.groupValues[1].trim() }
                .toList()
            for (modeName in modeNames) {
                mapping[modeName] = maps
            }
        }

        return mapping
    }

    fun parseModeWikitext(mode: ModeEntry, wikitext: String, maps: List<String>): GameModeDetail {
        val body = stripWikiTemplates(
            KEY_TEMPLATE_REGEX.replace(GALLERY_REGEX.replace(wikitext, ""), "$1")
        )
        val enc = mode.pageName.wikiPathEncode()

        return GameModeDetail(
            name = mode.displayName,
            summary = firstProseLine(body.substringBefore("\n==")),
            winCondition = cleanSection(extractSection(body, "获胜条件")),
            settings = cleanSection(extractSection(body, "模式设定")),
            maps = maps,
            wikiUrl = "$WIKI_BASE$enc"
        )
    }

    private fun firstProseLine(text: String): String =
        text.lineSequence()
            .map { line ->
                line.trim()
                    .replace(FILE_LINK_REGEX, "")
                    .replace(BR_REGEX, "")
                    .trim()
            }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    line.first() !in MARKUP_PREFIXES &&
                    !line.startsWith("[[分类:") &&
                    !line.startsWith("[[Category:")
            }
            .orEmpty()

    private fun stripWikiTemplates(text: String): String {
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (index < text.length - 1 && text[index] == '{' && text[index + 1] == '{') {
                var depth = 0
                var cursor = index
                while (cursor < text.length - 1) {
                    when {
                        text[cursor] == '{' && text[cursor + 1] == '{' -> {
                            depth++
                            cursor += 2
                        }
                        text[cursor] == '}' && text[cursor + 1] == '}' -> {
                            depth--
                            cursor += 2
                            if (depth == 0) break
                        }
                        else -> cursor++
                    }
                }
                index = if (depth == 0) cursor else text.length
            } else {
                result.append(text[index])
                index++
            }
        }
        return result.toString()
    }

    private fun extractSection(wikitext: String, sectionName: String): String {
        val pattern = Regex("""^==\s*${Regex.escape(sectionName)}\s*==\s*$""", RegexOption.MULTILINE)
        val match = pattern.find(wikitext) ?: return ""
        val start = match.range.last + 1
        val nextSection = Regex("""^==[^=]""", RegexOption.MULTILINE).find(wikitext, start)
        val end = nextSection?.range?.first ?: wikitext.length
        return wikitext.substring(start, end)
    }

    private fun cleanSection(text: String): String {
        val stripped = text
            .replace(FILE_LINK_REGEX, "")
            .replace(CATEGORY_LINK_REGEX, "")
            .replace(WIKI_LINK_ALIAS_REGEX, "$2")
            .replace(WIKI_LINK_REGEX, "$1")
            .replace(STRIKE_REGEX, "")
            .replace(HTML_TAG_REGEX, "")

        val lines = stripped.lineSequence().mapNotNull { raw ->
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> ""
                trimmed.startsWith(":") -> null
                else -> trimmed.replace(BULLET_REGEX, "• ").takeUnless(EMPTY_MAP_LINE_REGEX::matches)
            }
        }

        return buildString {
            var pendingBlank = false
            for (line in lines) {
                if (line.isEmpty()) {
                    pendingBlank = isNotEmpty()
                    continue
                }
                if (pendingBlank) append('\n')
                if (isNotEmpty()) append('\n')
                append(line)
                pendingBlank = false
            }
        }.trim()
    }
}
