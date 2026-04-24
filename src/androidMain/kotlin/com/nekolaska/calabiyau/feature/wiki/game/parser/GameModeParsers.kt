package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import java.net.URLEncoder

object GameModeParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    fun parseModeMapMapping(wikitext: String): Map<String, List<String>> {
        val mapping = mutableMapOf<String, List<String>>()
        val groupRegex = Regex("""\|group(\d+)=(.+)""")
        val listRegex = Regex("""\|list(\d+)=(.+)""")

        val groups = mutableMapOf<String, String>()
        val lists = mutableMapOf<String, String>()

        for (line in wikitext.lines()) {
            groupRegex.find(line)?.let { m ->
                groups[m.groupValues[1]] = m.groupValues[2].trim()
            }
            listRegex.find(line)?.let { m ->
                lists[m.groupValues[1]] = m.groupValues[2].trim()
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
        val noGallery = wikitext.replace(Regex("""<gallery[^>]*>[\s\S]*?</gallery>"""), "")
        val lines = noGallery.lines()

        val summary = lines
            .map { line ->
                line.trim()
                    .replace(Regex("""\[\[文件:[^\]]*]]"""), "")
                    .replace(Regex("""\[\[File:[^\]]*]]"""), "")
                    .replace(Regex("""<br\s*/?>"""), "")
                    .trim()
            }.firstOrNull {
                it.isNotBlank() &&
                    !it.startsWith("{{") &&
                    !it.startsWith("==") &&
                    !it.startsWith("__") &&
                    !it.startsWith("<") &&
                    !it.startsWith("*") &&
                    !it.startsWith(":") &&
                    !it.startsWith(";") &&
                    !it.startsWith("文件:") &&
                    !it.startsWith("File:")
            } ?: ""

        val winCondition = extractSection(wikitext, "获胜条件")
        val settings = extractSection(wikitext, "模式设定")
        val enc = URLEncoder.encode(mode.pageName, "UTF-8").replace("+", "%20")

        return GameModeDetail(
            name = mode.displayName,
            summary = summary,
            winCondition = cleanWikitext(winCondition),
            settings = cleanWikitext(settings),
            maps = maps,
            wikiUrl = "$WIKI_BASE$enc"
        )
    }

    private fun extractSection(wikitext: String, sectionName: String): String {
        val pattern = Regex("""==\s*${Regex.escape(sectionName)}\s*==\s*\n""")
        val match = pattern.find(wikitext) ?: return ""
        val start = match.range.last + 1
        val nextSection = Regex("""^==\s*[^=]""", RegexOption.MULTILINE).find(wikitext, start)
        val end = nextSection?.range?.first ?: wikitext.length
        return wikitext.substring(start, end).trim()
    }

    private fun cleanWikitext(text: String): String {
        return text
            .replace(Regex("""\[\[文件:[^\]]*]]"""), "")
            .replace(Regex("""\[\[File:[^\]]*]]"""), "")
            .replace(Regex("""\[\[([^\]|]*)\|([^\]]*)\]\]"""), "$2")
            .replace(Regex("""\[\[([^\]]*)\]\]"""), "$1")
            .replace(Regex("""\{\{#ask:[^}]*\}\}"""), "")
            .replace(Regex("""\{\{#info:[^}]*\}\}"""), "")
            .replace(Regex("""\{\{[^}]*\}\}"""), "")
            .replace(Regex("""<s>.*?</s>"""), "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""^:{1,}\s*$""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\*\s*""", RegexOption.MULTILINE), "• ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
