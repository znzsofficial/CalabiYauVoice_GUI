package ui.screens

import com.nekolaska.calabiyau.core.media.audio.PcmWavData
import util.DesktopWavMeta
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAudioHistoryControllerTest {

    @Test
    fun prunedAndClearedStepsDeleteDiskSnapshots() = withTempDir("audio-history") { root ->
        val workDir = File(root, "history")
        val source = writeMonoWav(File(root, "source.wav"), byteArrayOf(1, 0))
        val wavData = PcmWavData(
            channels = 1,
            sampleRate = 8_000,
            bitsPerSample = 16,
            blockAlign = 2,
            pcmData = byteArrayOf(1, 0)
        )
        val meta = DesktopWavMeta(1, 8_000, 16, 1, 0.1)
        val controller = DesktopAudioHistoryController(workDir, maxSteps = 2)

        repeat(3) { index ->
            controller.push(
                label = "step $index",
                input = AudioToolInput(source, source, false, wavData),
                meta = meta,
                spectrogram = null
            )
        }

        assertEquals(2, controller.steps.size)
        assertEquals(2, workDir.listFiles()?.size)
        controller.clear()
        assertTrue(controller.steps.isEmpty())
        assertTrue(workDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun spectrogramBudgetPrunesOldestSteps() = withTempDir("audio-history-images") { root ->
        val source = writeMonoWav(File(root, "source.wav"), byteArrayOf(1, 0))
        val wavData = PcmWavData(
            channels = 1,
            sampleRate = 8_000,
            bitsPerSample = 16,
            blockAlign = 2,
            pcmData = byteArrayOf(1, 0)
        )
        val meta = DesktopWavMeta(1, 8_000, 16, 1, 0.1)
        val controller = DesktopAudioHistoryController(File(root, "history"))
        val image = BufferedImage(4_000, 2_000, BufferedImage.TYPE_INT_ARGB)

        repeat(3) { index ->
            controller.push("step $index", AudioToolInput(source, source, false, wavData), meta, image)
        }

        assertEquals(2, controller.steps.size)
        assertEquals("step 1", controller.steps.first().label)
        assertFalse(controller.steps.any { it.label == "step 0" })
    }

    private fun writeMonoWav(file: File, pcm: ByteArray): File {
        val format = AudioFormat(8_000f, 16, 1, true, false)
        AudioInputStream(ByteArrayInputStream(pcm), format, (pcm.size / 2).toLong()).use { stream ->
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
