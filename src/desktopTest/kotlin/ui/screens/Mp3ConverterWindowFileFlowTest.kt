package ui.screens

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3ConverterWindowFileFlowTest {
    @Test
    fun `exportConvertedFiles skips staged source audio files and keeps duplicate outputs`() {
        val tempDir = Files.createTempDirectory("mp3conv-temp").toFile()
        val outDir = Files.createTempDirectory("mp3conv-out").toFile()

        try {
            File(tempDir, "source.mp3").writeText("mp3-temp")
            File(tempDir, "source.flac").writeText("flac-temp")
            File(tempDir, ".source.wav.tmp").writeText("tmp")
            File(tempDir, "source.wav").writeText("wav-1")
            File(tempDir, "nested").mkdirs()
            File(tempDir, "nested/source.wav").writeText("wav-2")
            File(tempDir, "nested/merged.wav").writeText("wav-3")

            val exported = exportConvertedFiles(tempDir, outDir)

            assertEquals(3, exported.size)
            assertTrue(File(outDir, "source.wav").isFile)
            assertTrue(File(outDir, "source (2).wav").isFile)
            assertTrue(File(outDir, "merged.wav").isFile)
            assertFalse(File(outDir, "source.mp3").exists())
            assertFalse(File(outDir, "source.flac").exists())
            assertFalse(File(outDir, ".source.wav.tmp").exists())
        } finally {
            tempDir.deleteRecursively()
            outDir.deleteRecursively()
        }
    }

    @Test
    fun `stageDraggedAudioFiles isolates duplicate source names across formats`() {
        val inputRoot = Files.createTempDirectory("mp3conv-input").toFile()
        val tempDir = Files.createTempDirectory("mp3conv-stage").toFile()

        try {
            val firstDir = File(inputRoot, "a").also { it.mkdirs() }
            val secondDir = File(inputRoot, "b").also { it.mkdirs() }
            val first = File(firstDir, "voice.mp3").also { it.writeText("first") }
            val second = File(secondDir, "voice.flac").also { it.writeText("second") }

            val staged = stageDraggedAudioFiles(listOf(first, second), tempDir)

            assertEquals(2, staged.size)
            assertNotEquals(staged[0].stagedDirectory.absolutePath, staged[1].stagedDirectory.absolutePath)
            assertTrue(staged.all { it.stagedSourceFile.isFile })
            assertEquals("first", staged[0].stagedSourceFile.readText())
            assertEquals("second", staged[1].stagedSourceFile.readText())
        } finally {
            inputRoot.deleteRecursively()
            tempDir.deleteRecursively()
        }
    }
}
