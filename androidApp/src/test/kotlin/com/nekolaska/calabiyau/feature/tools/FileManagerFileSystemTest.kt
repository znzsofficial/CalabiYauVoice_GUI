package com.nekolaska.calabiyau.feature.tools

import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*
import org.junit.Assume.assumeNoException
import com.nekolaska.calabiyau.feature.tools.deleteManagedFile as deleteWithPolicy

class FileManagerFileSystemTest {
    private suspend fun deleteManagedFile(root: File, source: File): Boolean =
        deleteWithPolicy(root, source) { Files.isSymbolicLink(it.toPath()) }
    private fun withDirectory(test: (File) -> Unit) {
        val scratch = Files.createTempDirectory("managed-files-test").toFile()
        try { test(scratch) } finally { scratch.deleteRecursively() }
    }

    @Test fun rootBoundaryRejectsTraversalAndSiblingPrefixButAcceptsNormalizedChildren() = withDirectory { scratch ->
        val root = File(scratch, "root").apply { mkdirs() }
        File(scratch, "outside").mkdirs()
        assertFalse(File(root, "../outside").isWithinManagedRoot(root))
        assertFalse(File(scratch, "root-other").isWithinManagedRoot(root))
        assertTrue(File(root, "sub/../inside").isWithinManagedRoot(root))
    }

    @Test fun renameRejectsTraversalControlsAndExistingTargetWithoutMovingOrOverwriting() = withDirectory { root ->
        val source = File(root, "voice.wav").apply { writeText("source") }
        val existing = File(root, "existing.wav").apply { writeText("keep") }
        for (name in listOf("../escape.wav", "sub/file.wav", "sub\\file.wav", ".", "..", "", " ", "bad\u0000.wav")) {
            assertFalse(validManagedFileName(name))
            assertFailsWith<IllegalArgumentException> { renameManagedFile(root, source, name) }
        }
        assertFailsWith<IOException> { renameManagedFile(root, source, existing.name) }
        assertEquals("source", source.readText()); assertEquals("keep", existing.readText())
        val renamed = renameManagedFile(root, source, "语音.wav")
        assertEquals("source", renamed.readText()); assertFalse(source.exists())
    }

    @Test fun emptyDirectoryIsAValidSnapshotButUnreadableAndMissingDirectoriesAreErrors() = withDirectory { root ->
        assertTrue(readManagedDirectory(root, root).isEmpty())
        assertFailsWith<IOException> { readManagedDirectory(root, File(root, "missing")) }
        val denied = object : File(root, "denied") {
            override fun isDirectory() = true
            override fun listFiles(): Array<File>? = null
        }
        assertFailsWith<IOException> { readManagedDirectory(root, denied) }
    }

    @Test fun filePickerAndGalleryExcludeDirectoriesIncludingImageNamedOnes() = withDirectory { root ->
        File(root, "album.jpg").mkdirs()
        File(root, "image.png").writeText("image")
        File(root, "audio.wav").writeText("audio")
        val rows = readManagedDirectory(root, root)
        assertTrue(rows.first().directory)
        assertEquals(setOf("image.png", "audio.wav"), selectableManagedFiles(rows, true).map { it.name }.toSet())
        assertEquals(3, selectableManagedFiles(rows, false).size)
        assertEquals(listOf("image.png"), managedGalleryFiles(rows).map { it.name })
    }

    @Test fun selectionConfirmationEnforcesSingleOrMultipleAndRechecksExistence() = withDirectory { root ->
        val a = File(root, "a.wav").apply { writeText("a") }
        val b = File(root, "b.wav").apply { writeText("b") }
        val dir = File(root, "dir").apply { mkdirs() }
        assertFailsWith<IllegalArgumentException> { validateManagedFileSelection(root, emptyList(), true) }
        assertFailsWith<IllegalArgumentException> { validateManagedFileSelection(root, listOf(a, b), false) }
        assertFailsWith<IllegalArgumentException> { validateManagedFileSelection(root, listOf(dir), true) }
        assertEquals(listOf(a, b), validateManagedFileSelection(root, listOf(a, b), true))
        b.delete()
        assertFailsWith<IllegalArgumentException> { validateManagedFileSelection(root, listOf(b), true) }
    }

    @Test fun refreshedSnapshotReflectsExternalAddsRenamesAndDeletesAndKeepsMetadata() = withDirectory { root ->
        assertTrue(readManagedDirectory(root, root).isEmpty())
        val file = File(root, "new.wav").apply { writeText("1234") }
        assertEquals(4L, readManagedDirectory(root, root).single().size)
        val moved = File(root, "renamed.wav")
        file.renameTo(moved)
        assertEquals("renamed.wav", readManagedDirectory(root, root).single().file.name)
        moved.delete()
        assertTrue(readManagedDirectory(root, root).isEmpty())
    }

    @Test fun deletionIsBoundedToRootAndCanDeleteNestedDirectoriesWithoutShell() = withDirectory { scratch ->
        runBlocking {
            val root = File(scratch, "root").apply { mkdirs() }
            val dir = File(root, "sub").apply { mkdirs() }
            File(dir, "file.wav").writeText("data")
            assertFailsWith<IllegalArgumentException> { deleteManagedFile(root, root) }
            assertFailsWith<IllegalArgumentException> { deleteManagedFile(root, File(scratch, "outside")) }
            assertTrue(deleteManagedFile(root, dir)); assertFalse(dir.exists()); assertTrue(root.exists())
        }
    }

    @Test fun deletionDoesNotFollowDirectoryLinksOrTouchTheirTargets() = withDirectory { scratch ->
        val root = File(scratch, "root").apply { mkdirs() }
        val outside = File(scratch, "outside").apply { mkdirs() }
        val keep = File(outside, "keep").apply { writeText("safe") }
        val link = File(root, "link")
        try {
            try { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            catch (e: Exception) { assumeNoException("Symbolic links require OS privileges", e) }
            runBlocking { assertTrue(deleteManagedFile(root, link)) }
            assertEquals("safe", keep.readText())
        } finally { link.delete() }
    }

    @Test fun deniedDeletionIsReportedWithoutPermissionChangesOrShellFallback() = withDirectory { root ->
        val denied = object : File(root, "denied") {
            override fun exists() = true
            override fun isDirectory() = false
            override fun delete() = false
            override fun setWritable(writable: Boolean, ownerOnly: Boolean): Boolean = error("must not chmod")
        }
        runBlocking { assertFalse(deleteManagedFile(root, denied)) }
        assertTrue(root.exists())
    }
}
