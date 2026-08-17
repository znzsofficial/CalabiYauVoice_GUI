package util

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioConverterTest {

    @Test
    fun failedConversionPreservesExistingOutput() = withTempDir("audio-convert-failure") { dir ->
        val source = File(dir, "broken.mp3").apply { writeText("not audio") }
        val existingOutput = File(
            dir,
            convertedWavFileName(source, null, BitDepthTarget.ORIGINAL, false)
        ).apply { writeText("existing") }

        assertFailsWith<IOException> {
            runBlocking {
                batchConvertAudioToWav(
                    dir = dir,
                    sourceFiles = listOf(source),
                    deleteOriginal = true
                )
            }
        }

        assertEquals("existing", existingOutput.readText())
        assertTrue(source.isFile)
    }

    @Test
    fun explicitMergeSourcesPreserveCallerOrder() = withTempDir("audio-merge-order") { dir ->
        val first = writeMonoWav(File(dir, "first.wav"), byteArrayOf(1, 0, 2, 0))
        val second = writeMonoWav(File(dir, "second.wav"), byteArrayOf(3, 0, 4, 0))

        val outputs = runBlocking {
            mergeWavFiles(dir = dir, sourceFiles = listOf(second, first))
        }

        assertEquals(1, outputs.size)
        AudioSystem.getAudioInputStream(outputs.single()).use { merged ->
            assertContentEquals(byteArrayOf(3, 0, 4, 0, 1, 0, 2, 0), merged.readAllBytes())
        }
    }

    @Test
    fun failedMergePreservesExistingOutputAndSources() = withTempDir("audio-merge-failure") { dir ->
        val source = writeMonoWav(File(dir, "source.wav"), byteArrayOf(1, 0))
        val broken = File(dir, "broken.wav").apply { writeText("not audio") }
        val existingOutput = File(dir, "${dir.name}_merged.wav").apply { writeText("existing") }

        assertFailsWith<IOException> {
            runBlocking {
                mergeWavFiles(
                    dir = dir,
                    sourceFiles = listOf(source, broken),
                    deleteOriginal = true
                )
            }
        }

        assertEquals("existing", existingOutput.readText())
        assertTrue(source.isFile)
        assertTrue(broken.isFile)
        assertFalse(File(dir, "${dir.name}_merged (2).wav").exists())
    }

    @Test
    fun partialChunkFailureRollsBackOutputsAndKeepsAllSources() = withTempDir("audio-merge-rollback") { dir ->
        val valid = writeMonoWav(File(dir, "valid.wav"), byteArrayOf(1, 0))
        val broken = File(dir, "broken.wav").apply { writeText("not audio") }

        assertFailsWith<IOException> {
            runBlocking {
                mergeWavFiles(
                    dir = dir,
                    sourceFiles = listOf(valid, broken),
                    maxPerFile = 1,
                    deleteOriginal = true
                )
            }
        }

        assertTrue(valid.isFile)
        assertTrue(broken.isFile)
        assertFalse(File(dir, "${dir.name}_merged_1.wav").exists())
        assertFalse(File(dir, "${dir.name}_merged_2.wav").exists())
    }

    @Test
    fun converterTempDirectoryUsesParentFolderName() {
        val tempDir = File("converted/_mp3conv_tmp_123_卡拉彼丘资源")
        assertEquals("卡拉彼丘资源", mergeOutputBaseName(tempDir))
        assertEquals("normal", mergeOutputBaseName(File("normal")))
    }

    @Test
    fun successfulRerunDoesNotOverwriteExistingMergedOutput() = withTempDir("audio-merge-collision") { dir ->
        val source = writeMonoWav(File(dir, "source.wav"), byteArrayOf(1, 0))
        val existingOutput = File(dir, "${dir.name}_merged.wav").apply { writeText("existing") }

        val outputs = runBlocking {
            mergeWavFiles(dir = dir, sourceFiles = listOf(source))
        }

        assertEquals("existing", existingOutput.readText())
        assertEquals("${dir.name}_merged (2).wav", outputs.single().name)
        assertTrue(outputs.single().isFile)
    }

    private fun writeMonoWav(file: File, pcm: ByteArray): File {
        val format = AudioFormat(8_000f, 16, 1, true, false)
        AudioInputStream(
            ByteArrayInputStream(pcm),
            format,
            (pcm.size / format.frameSize).toLong()
        ).use { stream ->
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }

    private fun withTempDir(prefix: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory(prefix).toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
