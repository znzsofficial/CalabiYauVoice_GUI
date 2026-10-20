package com.nekolaska.calabiyau.feature.download

import data.CharacterGroup
import data.SharedJson
import kotlinx.serialization.Serializable
import portrait.PortraitCostume
import java.util.UUID

@Serializable
sealed class DownloadTask {
    @Serializable
    data class Portrait(val characterName: String, val costume: PortraitCostume) : DownloadTask()
    @Serializable
    data class FileSearch(val files: List<Pair<String, String>>) : DownloadTask()
    @Serializable
    data class Category(
        val group: CharacterGroup,
        val checkedCategories: List<String>,
        val manualSelectionMap: Map<String, List<Pair<String, String>>>,
        val voiceOnly: Boolean
    ) : DownloadTask()
}

/** Persist task, directory and options together. Retries never read the current UI selection. */
@Serializable
data class ResourceDownloadRequest(
    val task: DownloadTask,
    val savePath: String,
    val maxConcurrency: Int,
    val id: String = UUID.randomUUID().toString()
) {
    fun encode(): String = SharedJson.encodeToString(serializer(), this)

    companion object {
        fun restore(json: String?): ResourceDownloadRequest? = json?.let {
            runCatching { SharedJson.decodeFromString(serializer(), it) }.getOrNull()
        }

        /** Deep-copy all caller-owned collections before starting asynchronous work. */
        fun snapshot(task: DownloadTask, path: String, concurrency: Int): ResourceDownloadRequest =
            checkNotNull(restore(ResourceDownloadRequest(task, path, concurrency).encode()))
    }
}

data class ResourceDownloadFailure(val message: String, val request: ResourceDownloadRequest)
