package com.nekolaska.calabiyau.feature.tools

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** This directory owns only materializations and decoded/processed copies, never borrowed inputs. */
internal suspend fun <T> withAudioWorkspace(cacheDir: File, block: suspend (File) -> T): T {
    val directory = File(cacheDir, "audio_work_${UUID.randomUUID()}")
    try {
        withContext(Dispatchers.IO) { check(directory.mkdir()) { "无法创建音频暂存目录" } }
        return block(directory)
    } finally { withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() } }
}
