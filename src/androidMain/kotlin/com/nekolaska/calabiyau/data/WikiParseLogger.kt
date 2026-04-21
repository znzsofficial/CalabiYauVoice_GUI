package com.nekolaska.calabiyau.data

import com.nekolaska.calabiyau.CalabiYauApplication
import com.nekolaska.calabiyau.CrashContextStore

internal object WikiParseLogger {
    private const val SAMPLE_LIMIT = 320
    private val STYLE_REGEX = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val SCRIPT_REGEX = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun warnEmpty(source: String, html: String, detail: String = "") {
        recordWarning(
            source = source,
            summary = "parsed 0 items",
            detail = detail,
            html = html
        )
    }

    fun warnMalformed(source: String, detail: String, html: String) {
        recordWarning(
            source = source,
            summary = "unexpected wiki HTML structure",
            detail = detail,
            html = html
        )
    }

    fun <T> finishList(source: String, items: List<T>, html: String, detail: String = ""): List<T> {
        if (items.isEmpty()) warnEmpty(source, html, detail)
        return items
    }

    fun <K, V> finishMap(source: String, items: Map<K, V>, html: String, detail: String = ""): Map<K, V> {
        if (items.isEmpty()) warnEmpty(source, html, detail)
        return items
    }

    private fun recordWarning(source: String, summary: String, detail: String, html: String) {
        val message = buildString {
            append(summary)
            detail.takeIf { it.isNotBlank() }?.let {
                append(" | ")
                append(it)
            }
        }
        CalabiYauApplication.instanceOrNull?.let {
            CrashContextStore.record(it, source, message, sample(html))
        }
    }

    private fun sample(html: String): String {
        return html
            .replace(STYLE_REGEX, " ")
            .replace(SCRIPT_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(SAMPLE_LIMIT)
    }
}
