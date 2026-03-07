package util

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AudioConverterTest {
    @Test
    fun `supported audio sources include mp3 and flac only`() {
        val dir = Files.createTempDirectory("audio-source-ext").toFile()

        try {
            val mp3 = File(dir, "voice.mp3").also { it.writeText("mp3") }
            val flac = File(dir, "voice.flac").also { it.writeText("flac") }
            val wav = File(dir, "voice.wav").also { it.writeText("wav") }
            val folder = File(dir, "folder").also { it.mkdirs() }

            assertTrue(isSupportedAudioSource(mp3))
            assertTrue(isSupportedAudioSource(flac))
            assertFalse(isSupportedAudioSource(wav))
            assertFalse(isSupportedAudioSource(folder))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `mergeWavFiles skips previously generated merged wav inputs`() {
        runBlocking<Unit> {
            val dir = Files.createTempDirectory("audio-converter-merge").toFile()
            val logs = mutableListOf<String>()
            val format = pcm16Mono()

            try {
                val first = File(dir, "001.wav").also { writeSilentWav(it, format, 120) }
                val second = File(dir, "002.wav").also { writeSilentWav(it, format, 80) }
                val existingMerged = File(dir, "${dir.name}_merged.wav").also { writeSilentWav(it, format, 999) }

                mergeWavFiles(dir, onLog = logs::add)

                val merged = File(dir, "${dir.name}_merged.wav")
                assertTrue("merged output should exist", merged.isFile)
                assertEquals("existing merged wav should be overwritten, not re-merged as input", 200L, readFrameLength(merged))
                assertTrue("source files should remain when deleteOriginal=false", first.isFile)
                assertTrue("source files should remain when deleteOriginal=false", second.isFile)
                assertTrue("merged output file should still exist after overwrite", existingMerged.exists())
                assertTrue("skip log should mention ignored merged wav", logs.any { it.contains("已跳过 1 个已生成的合并 WAV 文件") })
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun `batchConvertAudioToWav rejects unsupported target format before scanning`() {
        runBlocking<Unit> {
            val dir = Files.createTempDirectory("audio-converter-validate").toFile()

            try {
                expectIllegalArgument {
                    batchConvertAudioToWav(dir, targetBitDepth = 12)
                }
                expectIllegalArgument {
                    batchConvertAudioToWav(dir, targetSampleRate = -1f)
                }
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun `convertAudioToWav rejects unsupported file extension early`() {
        val dir = Files.createTempDirectory("audio-converter-unsupported").toFile()

        try {
            val txt = File(dir, "voice.txt").also { it.writeText("not audio") }
            expectIllegalArgument {
                convertAudioToWav(txt, File(dir, "voice.wav"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun expectIllegalArgument(block: suspend () -> Unit) {
        try {
            runBlocking<Unit> { block() }
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun pcm16Mono(): AudioFormat =
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 44100f, false)

    private fun writeSilentWav(file: File, format: AudioFormat, frames: Int) {
        val bytes = ByteArray(frames * format.frameSize)
        AudioInputStream(ByteArrayInputStream(bytes), format, frames.toLong()).use { stream ->
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file)
        }
    }

    private fun readFrameLength(file: File): Long =
        AudioSystem.getAudioInputStream(file).use { it.frameLength }
}
