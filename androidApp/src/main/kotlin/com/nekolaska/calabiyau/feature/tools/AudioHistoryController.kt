package com.nekolaska.calabiyau.feature.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.asContextElement

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

    suspend fun push(label: String, state: AudioPreviewState, replace: Boolean = false): AudioPreviewState? {
        val wav = state.asset.wav ?: return null
        val keep = if (replace) 0 else currentIndex + 1
        var candidate: File? = null
        var committed = false
        try {
            val prepared = withContext(Dispatchers.IO + toolTaskJob.asContextElement(currentCoroutineContext()[Job])) {
                historyDir.mkdirs()
                val target = File.createTempFile("step_${keep + 1}_", ".wav", historyDir)
                candidate = target
                wav.file.copyToolFile(target)
                val step = AudioHistoryStep(label, target, state.asset.meta)
                step to readStep(step)
            }
            // No suspension between replacing the branch and installing the validated candidate.
            val removed = steps.drop(keep)
            while (steps.size > keep) steps.removeAt(steps.lastIndex)
            steps.add(prepared.first)
            currentIndex = steps.lastIndex
            committed = true
            removed.forEach { runCatching { it.wavFile.delete() } }
            trimCache()
            return prepared.second
        } finally { if (!committed) candidate?.delete() }
    }

    suspend fun select(index: Int): Pair<AudioHistoryStep, AudioPreviewState>? {
        if (index == currentIndex || index !in steps.indices) return null
        val step = steps[index]
        val state = loadStep(step)
        currentIndex = steps.indexOf(step).takeIf { it >= 0 } ?: index
        return step to state
    }

    fun nextIndex(delta: Int): Int = if (steps.isEmpty()) -1 else (currentIndex + delta).coerceIn(0, steps.lastIndex)

    fun clear() {
        steps.forEach { runCatching { it.wavFile.delete() } }
        steps.clear()
        currentIndex = -1
    }

    fun cleanup() {
        clear()
        runCatching { historyDir.takeIf { it.isDirectory }?.deleteRecursively() }
    }

    private suspend fun loadStep(step: AudioHistoryStep): AudioPreviewState = withContext(Dispatchers.IO) {
        readStep(step)
    }

    private fun readStep(step: AudioHistoryStep): AudioPreviewState {
        val wav = loadWavAudioFileForPreview(step.wavFile, includeSpectrogram = false)?.copy(name = step.meta.name)
            ?: error("历史节点音频不可用")
        return AudioPreviewState(
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
