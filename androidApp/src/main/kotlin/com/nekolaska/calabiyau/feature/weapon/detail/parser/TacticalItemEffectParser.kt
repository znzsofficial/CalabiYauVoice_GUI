package com.nekolaska.calabiyau.feature.weapon.detail.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup

/** ITEM EFFECT is separate from the tactical item's short introduction. No network access. */
internal object TacticalItemEffectParser {
    fun parse(renderedHtml: String, templateEffect: String): String {
        val rendered = Jsoup.parse(renderedHtml).select(".klbq-tac-effect__body")
            .map { cleanHtml(it.html()) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n\n")
        if (rendered.isNotBlank()) return rendered

        // Older cached responses can contain wikitext only. Preserve prose, breaks and links,
        // but do not leak #info tooltips or list/template punctuation into the native card.
        val fallback = expandTemplates(templateEffect)
            .replace(Regex("""\[\[([^\[\]]+)]]""")) { match ->
                match.groupValues[1].substringAfterLast('|')
            }
            .replace(Regex("""\[(https?://\S+)\s+([^]]+)]"""), "$2")
            .replace(Regex("'{2,5}"), "")
            .replace(Regex("(?m)^\\s*[*#]+\\s*"), "• ")
        return cleanHtml(fallback)
    }

    private fun cleanHtml(raw: String): String {
        val document = Jsoup.parseBodyFragment(raw)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        body.select(".smwttcontent, .smwtticon, .mw-editsection, sup.reference, script, style").remove()
        body.select("li").forEach { it.prependText("• ") }
        return HtmlText.clean(body.html())
    }

    private fun expandTemplates(raw: String, nesting: Int = 0): String {
        if (nesting >= 16) return ""
        val result = StringBuilder()
        var cursor = 0
        while (cursor < raw.length) {
            val start = raw.indexOf("{{", cursor).takeIf { it >= 0 } ?: break
            result.append(raw, cursor, start)
            var end = start
            var depth = 0
            while (end < raw.length) {
                when {
                    raw.startsWith("{{", end) -> { depth++; end += 2 }
                    raw.startsWith("}}", end) -> { depth--; end += 2; if (depth == 0) break }
                    else -> end++
                }
            }
            if (depth != 0) return result.append(raw, start, raw.length).toString()
            val parts = splitArguments(raw.substring(start + 2, end - 2))
            val name = parts.first().trim().lowercase()
            val args = parts.drop(1).map { expandTemplates(it, nesting + 1).trim() }
            when {
                name.startsWith("#info:") -> Unit // Hidden tooltip, not the item's body text.
                name == "ul" || name == "ol" -> result.append(args.filter { it.isNotBlank() }
                    .joinToString("\n", prefix = "\n", postfix = "\n") { "• $it" })
                name == "黑幕" -> result.append(args.firstOrNull().orEmpty())
                name == "color" -> result.append(args.getOrNull(1).orEmpty())
                // Unknown templates are not executable here. Keep their visible argument
                // instead of displaying raw braces; rendered HTML remains the preferred path.
                else -> result.append(args.lastOrNull().orEmpty())
            }
            cursor = end
        }
        return result.append(raw, cursor, raw.length).toString()
    }

    private fun splitArguments(raw: String): List<String> {
        val parts = mutableListOf<String>()
        var templateDepth = 0
        var linkDepth = 0
        var start = 0
        var index = 0
        while (index < raw.length) {
            when {
                raw.startsWith("{{", index) -> { templateDepth++; index += 2 }
                raw.startsWith("}}", index) -> { templateDepth--; index += 2 }
                raw.startsWith("[[", index) -> { linkDepth++; index += 2 }
                raw.startsWith("]]", index) -> { linkDepth--; index += 2 }
                raw[index] == '|' && templateDepth == 0 && linkDepth == 0 -> {
                    parts += raw.substring(start, index); start = ++index
                }
                else -> index++
            }
        }
        return parts + raw.substring(start)
    }
}
