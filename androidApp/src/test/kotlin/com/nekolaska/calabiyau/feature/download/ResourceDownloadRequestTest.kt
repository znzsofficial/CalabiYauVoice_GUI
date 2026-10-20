package com.nekolaska.calabiyau.feature.download

import data.CharacterGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourceDownloadRequestTest {
    @Test
    fun retryAndProcessRestoreKeepOriginalSelectionDirectoryAndOptions() {
        val files = mutableListOf("a.wav" to "https://example.test/a")
        val cats = mutableListOf("Category:A")
        val manual = mutableMapOf<String, List<Pair<String, String>>>("Category:A" to files)
        val request = ResourceDownloadRequest.snapshot(DownloadTask.Category(
            CharacterGroup("A", "Category:A", cats), cats, manual, true), "/original", 3)
        files.clear(); cats.clear(); manual.clear()
        val restored = ResourceDownloadRequest.restore(request.encode())!!
        assertEquals(request, restored)
        assertEquals("/original", restored.savePath); assertEquals(3, restored.maxConcurrency)
        val task = restored.task as DownloadTask.Category
        assertEquals(listOf("Category:A"), task.checkedCategories)
        assertEquals(listOf("a.wav" to "https://example.test/a"), task.manualSelectionMap["Category:A"])
        // A later UI task is independent; the old error event retains its exact retry request.
        val event = ResourceDownloadFailure("failed", request)
        ResourceDownloadRequest.snapshot(DownloadTask.FileSearch(listOf("b.png" to "https://example.test/b")), "/new", 8)
        assertEquals(request, event.request)
    }

    @Test
    fun missingOrMalformedJournalDoesNotInventATask() {
        assertNull(ResourceDownloadRequest.restore(null))
        assertNull(ResourceDownloadRequest.restore("broken"))
    }
}
