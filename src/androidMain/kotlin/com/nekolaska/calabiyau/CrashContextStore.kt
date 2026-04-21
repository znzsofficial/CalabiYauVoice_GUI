package com.nekolaska.calabiyau

import android.content.Context
import androidx.core.content.edit

/**
 * 记录最近的解析/页面上下文，在崩溃时附加到 Crash 日志中。
 * 仅保留少量最近事件，平时开销很低。
 */
object CrashContextStore {

    private const val PREFS_NAME = "crash_context_prefs"
    private const val KEY_EVENTS = "events"
    private const val SEPARATOR = "\n---\n"
    private const val MAX_EVENTS = 8
    private const val MAX_EVENT_LENGTH = 800
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val trackedQueryKeys = setOf("action", "page", "titles", "srsearch", "query", "text", "aiprefix", "sroffset")

    @Volatile
    private var memoryEvents: ArrayDeque<String>? = null

    @Volatile
    private var lastEventFingerprint: Int = 0

    private val memoryLock = Any()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(context: Context, source: String, detail: String, sample: String = "") {
        val sanitized = buildString {
            append('[')
            append(source)
            append("] ")
            append(detail.trim())
            if (sample.isNotBlank()) {
                append(" | sample=")
                append(sample.trim())
            }
        }
            .replace(WHITESPACE_REGEX, " ")
            .take(MAX_EVENT_LENGTH)

        val fingerprint = sanitized.hashCode()
        if (fingerprint == lastEventFingerprint) return
        lastEventFingerprint = fingerprint

        synchronized(memoryLock) {
            val events = memoryEvents ?: ArrayDeque(readEvents(context)).also { memoryEvents = it }
            events.addLast(sanitized)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
        persistAsync(context)
    }

    fun recordWikiRequest(
        context: Context,
        source: String,
        url: String,
        cacheKey: String? = null,
        outcome: String
    ) {
        val detail = buildString {
            append(outcome)
            cacheKey?.takeIf { it.isNotBlank() }?.let {
                append(" | key=")
                append(it)
            }
            append(" | ")
            append(summarizeUrl(url))
        }
        record(context, source, detail)
    }

    fun dump(context: Context): String {
        val events = synchronized(memoryLock) {
            memoryEvents?.toList()
        } ?: readEvents(context).also { loaded ->
            synchronized(memoryLock) {
                if (memoryEvents == null) memoryEvents = ArrayDeque(loaded)
            }
        }
        return events.joinToString("\n")
    }

    fun flush(context: Context) {
        persistSync(context)
    }

    fun clear(context: Context) {
        synchronized(memoryLock) {
            memoryEvents = ArrayDeque()
            lastEventFingerprint = 0
        }
        prefs(context).edit(commit = true) { remove(KEY_EVENTS) }
    }

    private fun readEvents(context: Context): List<String> {
        return prefs(context)
            .getString(KEY_EVENTS, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun summarizeUrl(url: String): String {
        return url
            .substringAfter('?', url)
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (key in trackedQueryKeys && value.isNotBlank()) "$key=${value.take(80)}" else null
            }
            .joinToString("&")
            .ifBlank { url.take(120) }
    }

    private fun persistAsync(context: Context) {
        val serialized = synchronized(memoryLock) {
            memoryEvents?.joinToString(SEPARATOR).orEmpty()
        }
        prefs(context).edit(commit = false) {
            putString(KEY_EVENTS, serialized)
        }
    }

    private fun persistSync(context: Context) {
        val serialized = synchronized(memoryLock) {
            memoryEvents?.joinToString(SEPARATOR).orEmpty()
        }
        prefs(context).edit(commit = true) {
            putString(KEY_EVENTS, serialized)
        }
    }
}