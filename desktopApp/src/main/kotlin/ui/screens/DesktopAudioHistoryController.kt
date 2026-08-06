package ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.core.media.audio.PcmWavData
import java.awt.image.BufferedImage
import java.io.File
import util.DesktopWavMeta
import util.readPcmWav

private const val DESKTOP_AUDIO_HISTORY_MAX_STEPS = 24
private const val DESKTOP_AUDIO_HISTORY_MAX_SPECTROGRAM_PIXELS = 16_000_000L

internal data class AudioToolInput(
    val source: File,
    val wavFile: File,
    val isTemporary: Boolean,
    val wavData: PcmWavData
)

internal data class AudioHistoryStep(
    val label: String,
    val sourceName: String,
    val wavFile: File,
    val meta: DesktopWavMeta,
    val spectrogram: BufferedImage?
)

internal class DesktopAudioHistoryController(
    private val workDir: File,
    private val maxSteps: Int = DESKTOP_AUDIO_HISTORY_MAX_STEPS
) {
    val steps = mutableStateListOf<AudioHistoryStep>()
    var currentIndex by mutableIntStateOf(-1)
        private set

    fun push(label: String, input: AudioToolInput, meta: DesktopWavMeta, spectrogram: BufferedImage?): AudioHistoryStep {
        while (steps.lastIndex > currentIndex) removeStepAt(steps.lastIndex)
        val historyFile = uniqueFile(workDir, input.source.nameWithoutExtension.ifBlank { "audio_preview" }, "wav")
        input.wavFile.copyTo(historyFile, overwrite = false)
        steps.add(AudioHistoryStep(label, input.source.nameWithoutExtension, historyFile, meta, spectrogram))
        if (steps.size > maxSteps) removeStepAt(0)
        while (steps.size > 1 && retainedSpectrogramPixels() > DESKTOP_AUDIO_HISTORY_MAX_SPECTROGRAM_PIXELS) {
            removeStepAt(0)
        }
        currentIndex = steps.lastIndex
        input.takeIf { it.isTemporary }?.wavFile?.delete()
        return materializeCurrent()
    }

    fun select(index: Int): AudioHistoryStep? {
        if (index == currentIndex || index !in steps.indices) return null
        currentIndex = index
        return materializeCurrent()
    }

    fun nextIndex(delta: Int): Int = (currentIndex + delta).coerceIn(0, steps.lastIndex)

    fun clear() {
        steps.forEach { it.wavFile.delete() }
        steps.clear()
        currentIndex = -1
        materializedInput = null
    }

    fun cleanup(currentInput: AudioToolInput?) {
        currentInput?.takeIf { it.isTemporary }?.wavFile?.delete()
        runCatching { workDir.takeIf { it.isDirectory }?.deleteRecursively() }
    }

    private fun materializeCurrent(): AudioHistoryStep {
        val step = steps[currentIndex]
        return step.also {
            val wavData = readPcmWav(step.wavFile) ?: error("历史音频文件已损坏")
            materializedInput = AudioToolInput(step.wavFile, step.wavFile, false, wavData)
        }
    }

    var materializedInput: AudioToolInput? = null
        private set

    private fun removeStepAt(index: Int) {
        steps.removeAt(index).wavFile.delete()
    }

    private fun retainedSpectrogramPixels(): Long = steps.sumOf { step ->
        step.spectrogram?.let { it.width.toLong() * it.height } ?: 0L
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        dir.mkdirs()
        var candidate = File(dir, "$baseName.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($index).$extension")
            index++
        }
        return candidate
    }
}
