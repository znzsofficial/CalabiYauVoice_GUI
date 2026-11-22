package com.nekolaska.calabiyau.feature.settings

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadSettingsPathTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun acceptsFutureDirectoriesButNotFilesOrRelativePaths() {
        val existing = temp.newFolder("已有目录")
        val future = existing.resolve("包含 空格/新子目录")
        assertTrue(isDownloadDirectoryPath(existing.path))
        assertTrue(isDownloadDirectoryPath(future.path))
        assertFalse(future.exists())
        val file = temp.newFile("file.txt")
        assertFalse(isDownloadDirectoryPath(file.path))
        assertFalse(isDownloadDirectoryPath(file.resolve("child").path))
        assertFalse(isDownloadDirectoryPath("relative/path"))
        assertFalse(isDownloadDirectoryPath(""))
    }
}
