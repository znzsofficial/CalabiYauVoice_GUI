package com.nekolaska.calabiyau.feature.settings

import java.io.File

// Match the Download directory at an Android volume root, not a nested folder with the same name.
private val publicDownloadDirectory = Regex(
    """/(?:storage/(?:emulated/[0-9]+|self/primary|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})|sdcard|mnt/sdcard)/Download"""
)

internal fun downloadDirectorySummary(path: String, includeParent: Boolean = true): String {
    if (path.isBlank()) return "未设置"
    val directory = File(path).normalize()
    if (directory.name.isEmpty()) return "/"
    if (publicDownloadDirectory.matches(directory.invariantSeparatorsPath)) return "下载"
    if (!includeParent) return directory.name

    val parent = directory.parentFile ?: return directory.name
    val parentName = if (publicDownloadDirectory.matches(parent.invariantSeparatorsPath)) "下载" else parent.name
    return if (parentName.isEmpty()) directory.name else "$parentName › ${directory.name}"
}
