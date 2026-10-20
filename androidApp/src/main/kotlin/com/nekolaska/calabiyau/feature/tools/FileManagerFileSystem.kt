package com.nekolaska.calabiyau.feature.tools

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal data class ManagedFileEntry(
    val file: File,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int?
)

internal fun File.isWithinManagedRoot(root: File): Boolean {
    val base = root.canonicalFile
    val target = canonicalFile
    return target == base || target.path.startsWith(base.path.trimEnd(File.separatorChar) + File.separator)
}

internal fun validManagedFileName(name: String): Boolean = name.isNotBlank() && name != "." && name != ".." &&
    name.none { it == '/' || it == '\\' || it.isISOControl() }

/** Called on IO; a failed read must never masquerade as an empty folder. */
internal fun readManagedDirectory(root: File, directory: File): List<ManagedFileEntry> {
    require(directory.isWithinManagedRoot(root)) { "目录不在允许的范围内" }
    if (!directory.isDirectory) throw IOException("目录不存在或已被移走")
    val children = directory.listFiles() ?: throw IOException("无法读取目录，请检查存储权限")
    return children.filter { runCatching { it.isWithinManagedRoot(root) }.getOrDefault(false) }
        .map { file ->
            val isDirectory = file.isDirectory
            ManagedFileEntry(file, isDirectory, file.length(), file.lastModified(),
                if (isDirectory) file.list()?.size else null)
        }.sortedWith(compareByDescending<ManagedFileEntry> { it.directory }.thenBy { it.file.name.lowercase() })
}

internal fun renameManagedFile(root: File, source: File, name: String): File {
    require(validManagedFileName(name)) { "名称不能为 .、..，也不能包含斜线或控制字符" }
    val parent = source.parentFile ?: throw IOException("无法重命名根目录")
    require(source.canonicalFile != root.canonicalFile && source.isWithinManagedRoot(root) && parent.isWithinManagedRoot(root)) {
        "文件不在允许的范围内"
    }
    val target = File(parent, name)
    require(target.canonicalFile.parentFile == parent.canonicalFile) { "目标必须在原目录内" }
    if (target.exists()) throw IOException("同名文件已存在")
    if (!source.renameTo(target)) throw IOException("重命名失败，请检查文件状态和存储权限")
    return target
}

/** No chmod or shell bypass. Never follow directory symlinks, including during permission changes. */
internal suspend fun deleteManagedFile(root: File, source: File, isLink: (File) -> Boolean): Boolean {
    val parent = source.parentFile ?: return false
    require(parent.isWithinManagedRoot(root) && source.absoluteFile.normalize() != root.absoluteFile.normalize()) {
        "不能删除管理根目录或范围外文件"
    }
    suspend fun remove(file: File): Boolean {
        currentCoroutineContext().ensureActive()
        require(file.parentFile?.isWithinManagedRoot(root) == true) { "文件不在允许的范围内" }
        val link = isLink(file)
        if (!link && file.isDirectory) {
            require(file.isWithinManagedRoot(root)) { "目录不在允许的范围内" }
            val children = file.listFiles() ?: return false
            var complete = true
            for (child in children) if (!remove(child)) complete = false
            if (!complete) return false
        }
        return file.delete() || (!link && !file.exists())
    }
    return remove(source)
}

internal fun selectableManagedFiles(entries: List<ManagedFileEntry>, picker: Boolean): List<File> =
    entries.filter { !picker || !it.directory }.map { it.file }

internal fun validateManagedFileSelection(root: File, files: List<File>, multiple: Boolean): List<File> {
    val selected = files.distinctBy { it.canonicalPath }
    require(selected.isNotEmpty() && (multiple || selected.size == 1)) { "请选择${if (multiple) "至少一个" else "一个"}文件" }
    require(selected.all { it.isFile && it.isWithinManagedRoot(root) }) { "所选文件已不存在或不在允许的范围内" }
    return selected
}

internal fun managedGalleryFiles(entries: List<ManagedFileEntry>): List<File> = entries
    .filter { !it.directory && it.file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp") }
    .map { it.file }
