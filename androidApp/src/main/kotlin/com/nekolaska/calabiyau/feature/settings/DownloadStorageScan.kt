package com.nekolaska.calabiyau.feature.settings

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

internal data class DirSizeInfo(val name: String, val size: Long, val incomplete: Boolean = false)

internal enum class ScanCoverage { Complete, Partial, RootUnavailable }

internal data class DownloadStorageInfo(
    val totalSize: Long, val fileCount: Int, val subDirSizes: List<DirSizeInfo>, val coverage: ScanCoverage
)

internal sealed interface StorageEntry {
    data object Directory : StorageEntry
    data class RegularFile(val bytes: Long) : StorageEntry
    data object Skipped : StorageEntry // Links and special files are deliberately outside the count.
    data object Unreadable : StorageEntry
}

/** The chosen root may be an alias (e.g. /sdcard). Below it, stat must not follow links. */
internal suspend fun scanDownloadStorage(
    root: File, listChildren: (File) -> Array<File>? = { it.listFiles() }, stat: (File) -> StorageEntry
): DownloadStorageInfo {
    val context = currentCoroutineContext()
    val pending = ArrayDeque<File>()
    val sizes = mutableListOf<DirSizeInfo>()
    var total = 0L
    var files = 0
    var failures = 0

    fun visit(file: File) {
        context.ensureActive()
        when (val entry = try { stat(file) } catch (_: SecurityException) { StorageEntry.Unreadable }) {
            StorageEntry.Directory -> pending.addLast(file)
            is StorageEntry.RegularFile -> { total += entry.bytes; files++ }
            StorageEntry.Skipped -> Unit
            StorageEntry.Unreadable -> failures++
        }
    }

    fun children(dir: File): Array<File>? {
        context.ensureActive()
        return try { listChildren(dir) } catch (_: SecurityException) { null }
    }

    val rootChildren = children(root) ?: return DownloadStorageInfo(0, 0, emptyList(), ScanCoverage.RootUnavailable)
    for (child in rootChildren) {
        val before = total
        val failuresBefore = failures
        visit(child)
        if (pending.isEmpty()) continue
        while (pending.isNotEmpty()) {
            val entries = children(pending.removeLast())
            if (entries == null) failures++ else entries.forEach(::visit)
        }
        val incomplete = failures != failuresBefore
        if (total > before || incomplete) sizes += DirSizeInfo(child.name, total - before, incomplete)
    }
    context.ensureActive()
    return DownloadStorageInfo(total, files, sizes.sortedByDescending { it.size }.take(8),
        if (failures == 0) ScanCoverage.Complete else ScanCoverage.Partial)
}
