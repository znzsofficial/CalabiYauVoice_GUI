package com.nekolaska.calabiyau.feature.wiki.game.parser

/** Read group/list pairs within the same template, including multiline and nested navboxes. */
internal object ModeMapMappingParser {
    private val link = Regex("""\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]""")
    // Consume the first opening marker and its entire region; markers inside it are literal.
    private val ignoredMarkup = Regex(
        """<!--.*?(?:-->|\z)|<nowiki(?:\s+[^>]*?)?(?:/>|>.*?</nowiki\s*>)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val navboxParameter = Regex("""(?:group|list)\d+""")

    fun parse(raw: String): Map<String, List<String>> {
        val text = raw.replace(ignoredMarkup, "")
        val frames = mutableListOf<String>()
        val starts = ArrayDeque<Int>()
        var i = 0
        while (i < text.length - 1) {
            when {
                text.startsWith("{{", i) -> { starts.addLast(i + 2); i += 2 }
                text.startsWith("}}", i) -> {
                    if (starts.isNotEmpty()) frames += text.substring(starts.removeLast(), i)
                    i += 2
                }
                else -> i++
            }
        }
        // Fixtures/older cached fragments can be a raw sequence of navbox parameters.
        if (frames.isEmpty()) frames += text
        val candidates = linkedMapOf<String, MutableList<List<String>>>()
        frames.forEach { frame ->
            val params = splitParameters(frame).mapNotNull { part ->
                val key = part.substringBefore('=').trim()
                if (navboxParameter.matches(key) && '=' in part) key to part.substringAfter('=') else null
            }.groupBy({ it.first }, { it.second })
            params.filterKeys { it.startsWith("group") }.forEach { (key, groups) ->
                val group = groups.singleOrNull() ?: return@forEach
                val list = params["list" + key.removePrefix("group")]?.singleOrNull() ?: return@forEach
                // Nested navboxes own their children; don't mix their links into the outer group.
                if ("{{" in group || "{{" in list) return@forEach
                val modes = link.findAll(group).map { it.groupValues[1].trim() }
                    .filter { it.startsWith("战斗模式/") }.map { it.removePrefix("战斗模式/") }.distinct().toList()
                val maps = link.findAll(list).map { it.groupValues[1].trim().replace('_', ' ') }
                    .filter { it.isNotBlank() && ':' !in it && '/' !in it }.distinct().toList()
                if (maps.isNotEmpty()) modes.forEach { mode -> candidates.getOrPut(mode) { mutableListOf() }.add(maps) }
            }
        }
        return candidates.mapNotNull { (mode, lists) -> lists.distinct().singleOrNull()?.let { mode to it } }.toMap()
    }

    private fun splitParameters(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var templateDepth = 0
        var linkDepth = 0
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("{{", i) -> { templateDepth++; i += 2 }
                text.startsWith("}}", i) -> { templateDepth--; i += 2 }
                text.startsWith("[[", i) -> { linkDepth++; i += 2 }
                text.startsWith("]]", i) -> { linkDepth--; i += 2 }
                else -> {
                    if (text[i] == '|' && templateDepth == 0 && linkDepth == 0) {
                        parts += text.substring(start, i)
                        start = i + 1
                    }
                    i++
                }
            }
        }
        parts += text.substring(start)
        return parts
    }
}
