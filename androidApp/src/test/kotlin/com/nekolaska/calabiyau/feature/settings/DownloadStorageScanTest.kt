package com.nekolaska.calabiyau.feature.settings

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadStorageScanTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun oneVisitPerEntryCountsRootFilesAndNestedGroupsBeforeTakingTopEight() = runBlocking {
        val root = temp.root
        root.resolve("loose").writeBytes(ByteArray(9))
        root.resolve("zero").createNewFile()
        for (n in 1..10) {
            val dir = root.resolve("group$n/nested").apply { mkdirs() }
            dir.resolve("file").writeBytes(ByteArray(n))
        }
        root.resolve("ignored").apply { mkdir() }.resolve("outside").writeBytes(ByteArray(100))
        val visited = mutableSetOf<String>()
        val result = scanDownloadStorage(root) {
            assertTrue(visited.add(it.path), "visited twice: $it")
            when {
                it.name == "ignored" -> StorageEntry.Skipped // e.g. lstat reported a directory link
                it.isDirectory -> StorageEntry.Directory
                else -> StorageEntry.RegularFile(it.length())
            }
        }
        assertEquals(33, visited.size)
        assertEquals(ScanCoverage.Complete, result.coverage)
        assertEquals(64L, result.totalSize)
        assertEquals(12, result.fileCount)
        assertEquals((10 downTo 3).map { DirSizeInfo("group$it", it.toLong()) }, result.subDirSizes)
    }

    @Test fun emptyRootIsMeasuredButMissingOrDeniedRootIsUnknown() = runBlocking {
        fun unexpected(): Nothing = error("No child should be inspected")
        val empty = scanDownloadStorage(temp.root) { unexpected() }
        assertEquals(DownloadStorageInfo(0, 0, emptyList(), ScanCoverage.Complete), empty)
        val missing = scanDownloadStorage(temp.root.resolve("missing")) { unexpected() }
        val denied = scanDownloadStorage(temp.root, listChildren = { throw SecurityException() }) { unexpected() }
        assertEquals(ScanCoverage.RootUnavailable, missing.coverage)
        assertEquals(missing, denied)
    }

    @Test fun unreadableSubtreeAndFailedStatPreserveOnlyKnownBytesAndMarkAffectedGroups() = runBlocking {
        val group = temp.newFolder("mixed")
        group.resolve("good").writeBytes(ByteArray(12))
        group.resolve("denied").mkdir()
        val unseen = temp.newFolder("unseen")
        unseen.resolve("unknown").createNewFile()
        temp.newFolder("ok").resolve("file").writeBytes(ByteArray(8))
        val partial = scanDownloadStorage(temp.root, listChildren = { if (it.name == "denied") null else it.listFiles() }) {
            when {
                it.name == "unknown" -> StorageEntry.Unreadable
                it.isDirectory -> StorageEntry.Directory
                else -> StorageEntry.RegularFile(it.length())
            }
        }
        assertEquals(DownloadStorageInfo(20, 2, listOf(DirSizeInfo("mixed", 12, true),
            DirSizeInfo("ok", 8), DirSizeInfo("unseen", 0, true)), ScanCoverage.Partial), partial)
        val noKnownFiles = scanDownloadStorage(unseen) { throw SecurityException() }
        assertEquals(ScanCoverage.Partial, noKnownFiles.coverage)
        assertEquals(0, noKnownFiles.fileCount)
    }

    @Test fun leavingStorageStopsBeforeInspectingTheNextEntry() = runBlocking {
        repeat(16) { temp.newFile("file$it") }
        var visits = 0
        var returned = false
        val scan = launch {
            scanDownloadStorage(temp.root) {
                if (++visits == 3) cancel()
                StorageEntry.RegularFile(1L)
            }
            returned = true
        }
        scan.join()
        assertTrue(scan.isCancelled)
        assertFalse(returned)
        assertEquals(3, visits)
    }
}
