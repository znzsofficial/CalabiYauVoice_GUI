package util

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import data.WikiEngineCore

class ResourceDownloadStagingTest {
    @Test fun restartRecoversOnlyJournaledFilesForThisDirectory() = runBlocking {
        val root = Files.createTempDirectory("download-staging").toFile()
        try {
            val dir = File(root, "media").apply { mkdir() }
            val ledger = File(root, "private")
            val unfinished = ResourceDownloadStaging(ledger, dir).allocate().apply { writeText("partial") }
            val userFile = File(dir, "wiki-download-${java.util.UUID.randomUUID()}.tmp").apply { writeText("keep") }
            val other = File(root, "other").apply { mkdir() }
            val otherTask = ResourceDownloadStaging(ledger, other).allocate().apply { writeText("other") }
            val restored = ResourceDownloadStaging(ledger, dir)
            WikiEngineCore.downloadSpecificFiles(listOf("media" to "url"), dir, 1, {}, { _, _, _ -> }, { url, target ->
                assertFalse(unfinished.exists())
                downloadResourceAtomically(url, target, { _, f -> f.writeText("complete"); true }, staging = restored)
            }, File(root, "manifest"), restored)
            assertEquals("complete", File(dir, "media").readText())
            assertEquals("keep", userFile.readText()); assertEquals("other", otherTask.readText())
            assertEquals(2, dir.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
