package com.nekolaska.calabiyau.feature.tools

import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
internal val toolRename: (File, File) -> Unit = { from, to -> Os.rename(from.absolutePath, to.absolutePath) }

internal fun publishToolOutput(requested: File, rename: (File, File) -> Unit = toolRename,
    write: (File) -> Unit): File = ToolOutputBatch(rename).use { batch ->
    batch.stage(requested, write)
    batch.commit().single()
}

/** All encoders finish before the first final name appears. Roll back only our own files if
 * publication of a subsequent member fails. use{} cleans uncommitted staging files. */
internal class ToolOutputBatch(private val rename: (File, File) -> Unit = toolRename) : AutoCloseable {
    private val staged = mutableListOf<Pair<File, File>>()
    fun stage(requested: File, write: (File) -> Unit) {
        requested.parentFile?.mkdirs()
        checkToolCancellation()
        val temp = File.createTempFile(".tool-", ".tmp", requested.parentFile)
        try {
            write(temp)
            check(temp.length() > 0) { "输出文件为空" }
            staged += requested to temp
        } catch (e: Throwable) { temp.delete(); throw e }
    }

    fun commit(): List<File> {
        val published = mutableListOf<File>()
        try {
            for ((requested, temporary) in staged) {
                checkToolCancellation()
                published += publishStagedOutput(requested, temporary, rename)
            }
            return published
        } catch (e: Throwable) {
            published.forEach { it.delete() }
            throw e
        } finally { close() }
    }

    override fun close() { staged.forEach { it.second.delete() }; staged.clear() }
}

private fun publishStagedOutput(requested: File, temporary: File, rename: (File, File) -> Unit): File {
    var reserved: File? = null
    try {
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
        reserved?.delete()
    }
}

internal fun InputStream.copyToolBytes(out: OutputStream, limit: Long = Long.MAX_VALUE) {
    val buf = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        checkToolCancellation()
        val count = read(buf)
        if (count < 0) break
        checkToolCancellation()
        total += count
        require(total <= limit) { "输入文件过大" }
        out.write(buf, 0, count)
    }
}

internal fun File.copyToolFile(target: File) = inputStream().use { input ->
    target.outputStream().use { output -> input.copyToolBytes(output) }
}

internal fun publishEncodedImage(requested: File, rename: (File, File) -> Unit = toolRename,
    encode: (OutputStream) -> Boolean): File = publishToolOutput(requested, rename) { temporary ->
    check(temporary.outputStream().use(encode)) { "图片写出失败：${requested.name}" }
}

internal fun publishSanitizedCopy(privateDir: File, requested: File, copy: (File) -> Unit,
    clean: (File) -> Boolean, rename: (File, File) -> Unit = toolRename): File {
    val scratch = File.createTempFile("exif-", ".tmp", privateDir)
    try {
        copy(scratch)
        checkToolCancellation()
        check(clean(scratch)) { "隐私字段清理失败" }
        return publishToolOutput(requested, rename) { scratch.copyToolFile(it) }
    } finally { scratch.delete() }
}

internal fun requireImageBudget(width: Int, height: Int, bytesPerPixel: Int = 32) {
    val budget = (Runtime.getRuntime().maxMemory() / 3).coerceAtMost(128L * 1024 * 1024)
    require(width > 0 && height > 0 && width.toLong() * height <= budget / bytesPerPixel) {
        "图片尺寸过大，请缩小尺寸或减少输入图片"
    }
}
