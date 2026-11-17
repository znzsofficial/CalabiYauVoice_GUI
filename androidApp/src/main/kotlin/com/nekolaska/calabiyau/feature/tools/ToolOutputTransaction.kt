package com.nekolaska.calabiyau.feature.tools

import java.io.File
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

internal val toolTaskJob = ThreadLocal<Job?>()

internal fun checkToolCancellation() {
    toolTaskJob.get()?.ensureActive()
    if (Thread.currentThread().isInterrupted) throw CancellationException("工具任务已取消")
}

/** Reserves a new name atomically; a failed export never truncates an existing user's file. */
internal fun publishToolOutput(requested: File,
    rename: (File, File) -> Unit = { from, to -> Os.rename(from.absolutePath, to.absolutePath) },
    write: (File) -> Unit): File {
    requested.parentFile?.mkdirs()
    checkToolCancellation()
    val temporary = File.createTempFile(".tool-", ".tmp", requested.parentFile)
    var reserved: File? = null
    try {
        write(temporary)
        checkToolCancellation()
        var candidate = requested
        var suffix = 1
        while (!candidate.createNewFile()) {
            candidate = File(requested.parentFile, "${requested.nameWithoutExtension}_${suffix++}.${requested.extension}")
            checkToolCancellation()
        }
        reserved = candidate
        rename(temporary, candidate)
        reserved = null
        return candidate
    } finally {
        temporary.delete()
        reserved?.delete()
    }
}

internal fun requireImageBudget(width: Int, height: Int, bytesPerPixel: Int = 32) {
    val budget = (Runtime.getRuntime().maxMemory() / 3).coerceAtMost(128L * 1024 * 1024)
    require(width > 0 && height > 0 && width.toLong() * height <= budget / bytesPerPixel) {
        "图片尺寸过大，请缩小尺寸或减少输入图片"
    }
}
