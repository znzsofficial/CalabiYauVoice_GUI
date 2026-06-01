package ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.core.media.audio.PcmWavData
import java.awt.image.BufferedImage
import java.io.File
import util.DesktopWavMeta

private const val DESKTOP_AUDIO_HISTORY_MAX_STEPS = 24

internal data class AudioToolInput(
    val source: File,
    val wavFile: File,
    val isTemporary: Boolean,
    val wavData: PcmWavData
)

internal data class AudioHistoryStep(
    val label: String,
    val sourceName: String,
    val wavBytes: ByteArray,
    val wavData: PcmWavData,
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
        while (steps.lastIndex > currentIndex) steps.removeAt(steps.lastIndex)
        steps.add(AudioHistoryStep(label, input.source.nameWithoutExtension, input.wavFile.readBytes(), input.wavData, meta, spectrogram))
        if (steps.size > maxSteps) steps.removeAt(0)
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
        steps.clear()
        currentIndex = -1
    }

    fun cleanup(currentInput: AudioToolInput?) {
        currentInput?.takeIf { it.isTemporary }?.wavFile?.delete()
        runCatching { workDir.takeIf { it.isDirectory }?.deleteRecursively() }
    }

    private fun materializeCurrent(): AudioHistoryStep {
        val step = steps[currentIndex]
        workDir.mkdirs()
        val workFile = uniqueFile(workDir, step.sourceName.ifBlank { "audio_preview" }, "wav")
        workFile.writeBytes(step.wavBytes)
        return step.copy(
            wavData = step.wavData,
            spectrogram = step.spectrogram
        ).also {
            materializedInput = AudioToolInput(workFile, workFile, true, step.wavData)
        }
    }

    var materializedInput: AudioToolInput? = null
        private set

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
