package com.nekolaska.calabiyau.feature.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadPoliciesTest {
    private fun request() = ResourceDownloadRequest(
        DownloadTask.FileSearch(listOf("a.wav" to "https://example.test/a")),
        "/voice",
        2,
        "fixed"
    )

    @Test
    fun resumeRecordFailureDoesNotBlockOrFailAFinishedDownload() = runBlocking {
        val warnings = mutableListOf<String>()
        var downloads = 0
        var clears = 0
        val cleared = runResourceDownload(request(), persist = { value ->
            if (value == null) clears++
            false
        }, download = { downloads++ }, warn = warnings::add)
        assertEquals(1, downloads)
        assertEquals(1, clears)
        assertFalse(cleared)
        assertEquals(listOf(RESUME_RECORD_SAVE_WARNING, RESUME_RECORD_CLEAR_WARNING), warnings)
        assertEquals(
            "$RESUME_RECORD_SAVE_WARNING\n下载失败: socket closed",
            downloadFailureMessage(listOf(RESUME_RECORD_SAVE_WARNING), "socket closed")
        )
        assertEquals("下载失败: socket closed", downloadFailureMessage(emptyList(), "socket closed"))
    }

    @Test
    fun downloadFailureKeepsTheResumeRecord() = runBlocking {
        val persisted = mutableListOf<String?>()
        assertFailsWith<IOException> {
            runResourceDownload(request(), persist = { persisted += it; true }, download = {
                throw IOException("socket closed")
            }, warn = { error("bookkeeping warning is not a download failure") })
        }
        assertEquals(request().encode(), persisted.single())
    }

    @Test
    fun failedCategoryDoesNotDiscardFilesAlreadyResolved() = runBlocking {
        var fetched = 0
        val resolved = resolveCategoryDownloads(
            listOf("Category:已取到", "Category:失败", "Category:空", "Category:已取到"),
            emptyMap()
        ) { category ->
            fetched++
            when (category) {
                "Category:已取到" -> listOf("a.wav" to "https://example.test/a", "a-again.wav" to "https://example.test/a")
                "Category:空" -> emptyList()
                else -> throw IOException("HTTP 500")
            }
        }
        assertEquals(3, fetched)
        assertEquals(listOf("Category:已取到"), resolved.batches.map { it.category })
        assertEquals(listOf("a.wav" to "https://example.test/a"), resolved.batches.single().files)
        assertEquals(listOf("失败: HTTP 500"), resolved.failures)
        assertEquals("HTTP 500；1 个分类获取失败: 失败: HTTP 500", incompleteDownloadMessage("HTTP 500", resolved.failures))
    }

    @Test
    fun manualSelectionIsNotFetchedAndCancellationIsNotACategoryFailure() = runBlocking {
        val manual = resolveCategoryDownloads(
            listOf("Category:手动"),
            mapOf("Category:手动" to listOf("manual.wav" to "https://example.test/manual"))
        ) { error("manual category must not be fetched") }
        assertEquals(listOf("manual.wav" to "https://example.test/manual"), manual.batches.single().files)
        assertTrue(manual.failures.isEmpty())

        val cancelled = assertFailsWith<CancellationException> {
            resolveCategoryDownloads(listOf("Category:取消"), emptyMap()) { throw CancellationException("stopped") }
        }
        assertEquals("stopped", cancelled.message)
    }

    @Test
    fun allCategoryFailuresStayVisibleInsteadOfLookingEmpty() = runBlocking {
        val resolved = resolveCategoryDownloads(listOf("分类:甲"), emptyMap()) { throw IOException("timeout") }
        assertTrue(resolved.batches.isEmpty())
        assertEquals(listOf("甲: timeout"), resolved.failures)
        assertEquals("1 个分类获取失败: 甲: timeout", incompleteDownloadMessage(null, resolved.failures))
    }
}
