package com.nekolaska.calabiyau

import java.io.File
import java.util.IdentityHashMap
import java.util.UUID

internal const val MAX_CRASH_CHARS = 48 * 1024

internal fun boundedStackTrace(failure: Throwable): String = buildString {
    var cause: Throwable? = failure
    val visited = IdentityHashMap<Throwable, Boolean>()
    while (cause != null && visited.size < 24 && length < MAX_CRASH_CHARS) {
        if (visited.put(cause, true) != null) { appendLine("[circular cause]"); break }
        if (visited.size > 1) append("Caused by: ")
        append(cause.javaClass.name); append(": "); appendLine(cause.message.orEmpty().take(2048))
        for (frame in cause.stackTrace.take(96)) {
            append("  at "); appendLine(frame.toString().take(512))
            if (length >= MAX_CRASH_CHARS) break
        }
        for (s in cause.suppressed.take(4)) appendLine("  Suppressed: ${s.javaClass.name}: ${s.message.orEmpty().take(256)}")
        cause = cause.cause
    }
}.take(MAX_CRASH_CHARS)

/** Pending records survive process death; viewed records survive report Activity recreation. */
internal class CrashReportStore(private val directory: File) {
    private fun valid(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    private fun record(id: String, suffix: String) = File(directory, "$id.$suffix")
    fun newestPending(): String? = directory.listFiles()?.filter { it.extension == "pending" && valid(it.nameWithoutExtension) }
        ?.maxByOrNull { it.lastModified() }?.nameWithoutExtension

    fun save(text: String): String {
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val tmp = record(id, "tmp")
        try {
            tmp.writeText(text.take(MAX_CRASH_CHARS))
            check(tmp.renameTo(record(id, "pending"))) { "Cannot publish crash log" }
        } finally { tmp.delete() }
        // Bound storage, but never remove a record just opened by a concurrent Activity.
        directory.listFiles()?.filter { it.extension in setOf("pending", "viewed", "tmp") && valid(it.nameWithoutExtension) }
            ?.sortedByDescending { it.lastModified() }?.drop(4)?.filter { it.nameWithoutExtension != id }?.forEach { it.delete() }
        return id
    }

    fun open(id: String): String? {
        if (!valid(id)) return null
        val seen = record(id, "viewed")
        val pending = record(id, "pending")
        if (!seen.exists() && !pending.renameTo(seen) && !seen.exists()) return null
        return runCatching { seen.bufferedReader().use { it.readText() }.take(MAX_CRASH_CHARS) }.getOrNull()
    }
    fun finish(id: String) { if (valid(id)) record(id, "viewed").delete() }
}
