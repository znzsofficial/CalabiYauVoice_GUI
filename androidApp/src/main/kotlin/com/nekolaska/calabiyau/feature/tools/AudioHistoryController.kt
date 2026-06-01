package com.nekolaska.calabiyau.feature.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AUDIO_HISTORY_MAX_STEPS = 24
private const val AUDIO_HISTORY_MAX_BYTES = 256L * 1024L * 1024L

internal data class AudioPreviewState(
    val asset: LoadedAudioAsset,
    val canPreviewWaveform: Boolean
)

internal data class AudioHistoryStep(
    val label: String,
    val wavFile: File,
    val meta: AudioMeta
)

internal class AudioHistoryController(
    private val historyDir: File,
    private val maxSteps: Int = AUDIO_HISTORY_MAX_STEPS,
    private val maxBytes: Long = AUDIO_HISTORY_MAX_BYTES
) {
    val steps = mutableStateListOf<AudioHistoryStep>()
    var currentIndex by mutableIntStateOf(-1)
        private set

    suspend fun push(label: String, state: AudioPreviewState): AudioPreviewState? {
        val wav = state.asset.wav ?: return null
        while (steps.lastIndex > currentIndex) {
            val removed = steps.removeAt(steps.lastIndex)
            runCatching { removed.wavFile.delete() }
        }
        val stepNumber = steps.size + 1
        val historyFile = withContext(Dispatchers.IO) {
            historyDir.mkdirs()
            val target = buildUniqueFile(historyDir, "step_${stepNumber}_${wav.file.nameWithoutExtension.ifBlank { "audio" }}", "wav")
            wav.file.copyTo(target, overwrite = false)
        }
        steps.add(AudioHistoryStep(label, historyFile, state.asset.meta))
        currentIndex = steps.lastIndex
        trimCache()
        return loadCurrent()
    }

    suspend fun select(index: Int): Pair<AudioHistoryStep, AudioPreviewState>? {
        if (index == currentIndex || index !in steps.indices) return null
        val step = steps[index]
        val state = loadStep(step)
        currentIndex = steps.indexOf(step).takeIf { it >= 0 } ?: index
        return step to state
    }

    fun nextIndex(delta: Int): Int = (currentIndex + delta).coerceIn(0, steps.lastIndex)

    fun clear() {
        steps.forEach { runCatching { it.wavFile.delete() } }
        steps.clear()
        currentIndex = -1
    }

    fun cleanup() {
        clear()
        runCatching { historyDir.takeIf { it.isDirectory }?.deleteRecursively() }
    }

    private suspend fun loadCurrent(): AudioPreviewState? {
        val step = steps.getOrNull(currentIndex) ?: return null
        return loadStep(step)
    }

    private suspend fun loadStep(step: AudioHistoryStep): AudioPreviewState = withContext(Dispatchers.IO) {
        val wav = loadWavAudioFileForPreview(step.wavFile, includeSpectrogram = false) ?: error("历史节点音频不可用")
        AudioPreviewState(
            asset = LoadedAudioAsset(
                input = wav.input,
                file = wav.file,
                meta = step.meta,
                wav = wav
            ),
            canPreviewWaveform = true
        )
    }

    private fun trimCache() {
        while (steps.size > maxSteps) {
            val removed = steps.removeAt(0)
            runCatching { removed.wavFile.delete() }
            currentIndex--
        }
        while (steps.sumOf { it.wavFile.length() } > maxBytes && steps.size > 1) {
            val removeIndex = if (currentIndex == 0) 1 else 0
            val removed = steps.removeAt(removeIndex)
            runCatching { removed.wavFile.delete() }
            if (removeIndex < currentIndex) currentIndex--
        }
        currentIndex = currentIndex.coerceIn(-1, steps.lastIndex)
    }
}
