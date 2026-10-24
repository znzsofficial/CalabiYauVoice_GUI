package com.nekolaska.calabiyau.core.wiki

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DownloadManifestStorageTest {
    @Test fun recordIsPrivateStablePerCanonicalDirectoryAndNotSharedAcrossDirectories() {
        val root = Files.createTempDirectory("private-manifest-path").toFile()
        try {
            val appFiles = File(root, "app-files")
            val media = File(root, "media").apply { mkdirs() }
            val record = privateDownloadManifestFile(appFiles, media)
            assertEquals(File(appFiles, "download_manifests"), record.parentFile)
            assertTrue(record.name.matches(Regex("[a-f0-9]{64}\\.tsv")))
            assertEquals(record, privateDownloadManifestFile(appFiles, File(media, "child/..")))
            assertNotEquals(record, privateDownloadManifestFile(appFiles, File(root, "other-media")))
            assertTrue(media.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
}
