package com.nekolaska.calabiyau.feature.settings

import android.app.DownloadManager
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class UpdateDownloadOwnerTest {
    @Test fun toleratesDelayedRegistrationButStopsWhenTheRowIsGone() = kotlinx.coroutines.runBlocking {
        var queries = 0
        assertEquals("ready", awaitDownloadRow({ if (++queries == 3) "ready" else null }, {}))
        assertEquals(3, queries); queries = 0
        assertNull(awaitDownloadRow<String>({ queries++; null }, {}))
        assertEquals(4, queries)
    }
    @Test fun reopenAdoptsPausedOrFinishedRowForThisArtifactOnly() {
        val root = createTempDirectory().toFile()
        try {
            val target = File(root, "CalabiYauVoice-3.0.apk")
            val job = UpdateDownloadOwner.Row(4L, "url?build=x", "v3", target, DownloadManager.STATUS_PAUSED)
            fun pick(vararg rows: UpdateDownloadOwner.Row) = UpdateDownloadOwner.reusable(rows.toList(), job.url!!, setOf(job.version!!), root)
            assertEquals(job, pick(job, job.copy(id = 5, url = "url?build=y"), job.copy(id = 6, target = File(root.parentFile, "other.apk"))))
            val finished = job.copy(status = DownloadManager.STATUS_SUCCESSFUL)
            assertNull(pick(finished))
            target.writeText("apk"); assertEquals(finished, pick(finished))
            assertNull(pick(job.copy(status = DownloadManager.STATUS_FAILED)))
        } finally { root.deleteRecursively() }
    }
}
