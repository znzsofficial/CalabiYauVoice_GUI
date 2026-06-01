package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.nekolaska.calabiyau.core.media.audio.PcmWavData
import com.nekolaska.calabiyau.core.media.audio.PcmWavChannelMode
import com.nekolaska.calabiyau.core.media.audio.PcmWavPhaseMode
import com.nekolaska.calabiyau.core.media.audio.PcmWavTrimMode
import com.nekolaska.calabiyau.core.media.audio.PcmWavVolumeMode
import com.nekolaska.calabiyau.core.media.audio.SpectrogramConfig
import com.nekolaska.calabiyau.core.media.audio.adjustPcmWavVolume
import com.nekolaska.calabiyau.core.media.audio.applyPcmWavFade
import com.nekolaska.calabiyau.core.media.audio.applyPcmWavNoiseGate
import com.nekolaska.calabiyau.core.media.audio.applyPcmWavPhase
import com.nekolaska.calabiyau.core.media.audio.buildSpectrogramPixels
import com.nekolaska.calabiyau.core.media.audio.calculatePcmPeakPercent
import com.nekolaska.calabiyau.core.media.audio.convertPcmWavChannels
import com.nekolaska.calabiyau.core.media.audio.expectedPcmBlockAlign
import com.nekolaska.calabiyau.core.media.audio.framePeakAmplitude
import com.nekolaska.calabiyau.core.media.audio.maxAmplitude
import com.nekolaska.calabiyau.core.media.audio.pcmDurationMs
import com.nekolaska.calabiyau.core.media.audio.peakAmplitude
import com.nekolaska.calabiyau.core.media.audio.readSignedSample
import com.nekolaska.calabiyau.core.media.audio.removePcmWavDcOffset
import com.nekolaska.calabiyau.core.media.audio.trimPcmWavSilence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_ANDROID_PCM_BYTES = 256L * 1024L * 1024L

internal enum class WavTrimMode(val label: String) {
    BOTH("裁剪头尾"),
    START_ONLY("只裁开头"),
    END_ONLY("只裁结尾")
}

internal enum class WavChannelMode(val label: String) {
    MONO_TO_STEREO("单转双"),
    STEREO_TO_MONO("双转单")
}

internal enum class WavVolumeMode(val label: String) {
    GAIN("调节音量"),
    NORMALIZE("峰值标准化")
}

internal enum class WavPhaseMode(val label: String) {
    INVERT_ALL("整体反相"),
    INVERT_LEFT("左声道反相"),
    INVERT_RIGHT("右声道反相"),
    SWAP_STEREO("左右互换")
}

internal data class WavTrimResult(
    val sourceName: String,
    val outputFile: File,
    val trimmedStartMs: Long,
    val trimmedEndMs: Long,
    val keptDurationMs: Long
)

internal data class WavChannelConvertResult(
    val sourceName: String,
    val outputFile: File,
    val sourceChannels: Int,
    val targetChannels: Int,
    val durationMs: Long
)

internal data class WavVolumeAdjustResult(
    val sourceName: String,
    val outputFile: File,
    val sourcePeakPercent: Double,
    val targetPeakPercent: Double,
    val durationMs: Long
)

internal data class WavRepairResult(
    val sourceName: String,
    val outputFile: File,
    val durationMs: Long
)

internal data class WavMeta(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val peakPercent: Double
)

internal data class AudioMeta(
    val name: String,
    val mimeType: String,
    val durationMs: Long,
    val artist: String?,
    val sampleRate: String?,
    val bitrate: String?,
    val size: Long
)

internal data class LoadedAudioAsset(
    val input: PickedInput,
    val file: File?,
    val meta: AudioMeta,
    val wav: LoadedWavAudio? = null
)

internal data class LoadedWavAudio(
    val name: String,
    val input: PickedInput,
    val file: File,
    val data: PcmWavData,
    val durationMs: Long,
    val peakPercent: Double,
    val waveform: List<FloatArray>,
    val spectrogramBitmap: Bitmap? = null
)

internal data class SpectrogramRenderConfig(
    val windowSize: Int = 1024,
    val hopRatio: Float = 0.25f,
    val maxTimeBins: Int = 720,
    val maxFrequencyBins: Int = 256,
    val cutoffFrequencyHz: Int = 0,
    val lowColorArgb: Int = 0xFF0F172A.toInt(),
    val highColorArgb: Int = 0xFF38BDF8.toInt(),
    val gainDb: Double = 6.0,
    val gamma: Double = 1.2,
    val floorDb: Double = -72.0
)

private fun SpectrogramRenderConfig.toSharedConfig(): SpectrogramConfig = SpectrogramConfig(
    windowSize = windowSize,
    hopRatio = hopRatio,
    maxTimeBins = maxTimeBins,
    maxFrequencyBins = maxFrequencyBins,
    cutoffFrequencyHz = cutoffFrequencyHz,
    lowColorArgb = lowColorArgb,
    highColorArgb = highColorArgb,
    gainDb = gainDb,
    gamma = gamma,
    floorDb = floorDb
)

internal fun loadWavAudioForPreview(
    context: Context,
    input: PickedInput,
    includeSpectrogram: Boolean = true,
    spectrogramConfig: SpectrogramRenderConfig = SpectrogramRenderConfig()
): LoadedWavAudio? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val wav = readPcmWav(tempInput) ?: return null.also {
        if (input.file == null) runCatching { tempInput.delete() }
    }
    if (wav.channels !in 1..2) {
        if (input.file == null) runCatching { tempInput.delete() }
        return null
    }

    val frameCount = wav.pcmData.size / wav.blockAlign
    val durationMs = frameCount * 1000L / wav.sampleRate
    return LoadedWavAudio(
        name = input.file?.name ?: input.uri?.let(context::queryDisplayName) ?: tempInput.name,
        input = PickedInput(file = tempInput),
        file = tempInput,
        data = wav,
        durationMs = durationMs,
        peakPercent = calculatePcmPeakPercent(wav),
        waveform = buildWaveformPreview(wav),
        spectrogramBitmap = if (includeSpectrogram) buildSpectrogramBitmap(wav, spectrogramConfig) else null
    )
}

internal fun loadAudioAssetForPreview(context: Context, input: PickedInput): LoadedAudioAsset? {
    val meta = inspectAudioMeta(context, input) ?: return null
    val wav = when {
        input.file?.extension.equals("wav", ignoreCase = true) || meta.mimeType.contains("wav", ignoreCase = true) -> {
            loadWavAudioForPreview(context, input, includeSpectrogram = false)
                ?: decodeAudioInputToTempWav(context, input, meta.name)?.let {
                    loadWavAudioFileForPreview(it, includeSpectrogram = false)
                }
        }
        else -> decodeAudioInputToTempWav(context, input, meta.name)?.let {
            loadWavAudioFileForPreview(it, includeSpectrogram = false)
        }
    }
    return LoadedAudioAsset(
        input = wav?.input ?: input,
        file = wav?.file ?: input.file,
        meta = meta,
        wav = wav
    )
}

internal fun loadWavAudioFileForPreview(file: File, includeSpectrogram: Boolean = true): LoadedWavAudio? {
    val input = PickedInput(file = file)
    val wav = readPcmWav(file) ?: return null
    if (wav.channels !in 1..2) return null
    val frameCount = wav.pcmData.size / wav.blockAlign
    return LoadedWavAudio(
        name = file.name,
        input = input,
        file = file,
        data = wav,
        durationMs = frameCount * 1000L / wav.sampleRate,
        peakPercent = calculatePcmPeakPercent(wav),
        waveform = buildWaveformPreview(wav),
        spectrogramBitmap = if (includeSpectrogram) buildSpectrogramBitmap(wav) else null
    )
}

internal fun trimWavInputSilence(
    context: Context,
    input: PickedInput,
    outputDir: File,
    thresholdRatio: Double,
    minSilenceMs: Int,
    trimMode: WavTrimMode
): WavTrimResult? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        resolvePcmWavInputFile(context, tempInput)?.use { resolved ->
            trimPcmWavSilence(
                inputFile = resolved.file,
                outputDir = outputDir,
                thresholdRatio = thresholdRatio,
                minSilenceMs = minSilenceMs,
                trimMode = trimMode
            )
        }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

internal fun convertWavChannels(
    context: Context,
    input: PickedInput,
    outputDir: File,
    mode: WavChannelMode
): WavChannelConvertResult? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        resolvePcmWavInputFile(context, tempInput)?.use { resolved ->
            convertPcmWavChannels(resolved.file, outputDir, mode)
        }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

internal fun adjustWavVolume(
    context: Context,
    input: PickedInput,
    outputDir: File,
    mode: WavVolumeMode,
    gainPercent: Int,
    targetPeakPercent: Int
): WavVolumeAdjustResult? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        resolvePcmWavInputFile(context, tempInput)?.use { resolved ->
            adjustPcmWavVolume(resolved.file, outputDir, mode, gainPercent, targetPeakPercent)
        }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

internal fun inspectWavMeta(context: Context, input: PickedInput): WavMeta? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        resolvePcmWavInputFile(context, tempInput)?.use { resolved ->
            readPcmWav(resolved.file)?.let { wav ->
                WavMeta(
                    channels = wav.channels,
                    sampleRate = wav.sampleRate,
                    bitsPerSample = wav.bitsPerSample,
                    peakPercent = calculatePcmPeakPercent(wav)
                )
            }
        }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

private data class ResolvedPcmWavInput(
    val file: File,
    val shouldDelete: Boolean
) : AutoCloseable {
    override fun close() {
        if (shouldDelete) runCatching { file.delete() }
    }
}

private fun resolvePcmWavInputFile(context: Context, inputFile: File): ResolvedPcmWavInput? {
    readPcmWav(inputFile)?.let { return ResolvedPcmWavInput(inputFile, false) }
    val decoded = decodeAudioInputToTempWav(context, PickedInput(file = inputFile), inputFile.name) ?: return null
    return ResolvedPcmWavInput(decoded, true)
}

private fun materializeInputToFile(context: Context, input: PickedInput): File? {
    input.file?.let { return it.takeIf(File::exists) }
    val uri = input.uri ?: return null
    val displayName = context.queryDisplayName(uri) ?: "audio.wav"
    val name = displayName.substringBeforeLast('.').ifBlank { "audio" }
    val ext = displayName.substringAfterLast('.', "wav")
    val temp = File.createTempFile("audio_wav_", ".${ext}", context.cacheDir)
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            temp.outputStream().use { output -> inputStream.copyTo(output) }
        } ?: return null
        File(temp.parentFile, "${sanitizeFileName(name)}.${ext}").also { renamed ->
            if (temp.renameTo(renamed)) renamed else temp
        }
    }.getOrNull()
}

private fun decodeAudioInputToTempWav(context: Context, input: PickedInput, sourceName: String): File? {
    val sourceFile = materializeInputToFile(context, input) ?: return null
    val shouldDeleteSource = input.file == null
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
        extractor.setDataSource(sourceFile.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
        extractor.selectTrack(trackIndex)

        val decoder = MediaCodec.createDecoderByType(mime)
        codec = decoder
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmOutput = ByteArrayOutputStream()
        var inputDone = false
        var outputDone = false
        var outputChannels = inputFormat.safeInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var outputSampleRate = inputFormat.safeInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outputEncoding = AudioFormat.ENCODING_PCM_16BIT

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    val sampleSize = inputBuffer?.let { buffer ->
                        buffer.clear()
                        extractor.readSampleData(buffer, 0)
                    } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = decoder.outputFormat
                    outputChannels = outputFormat.safeInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    outputSampleRate = outputFormat.safeInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outputEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                    if (outputChannels !in 1..2 || outputSampleRate <= 0 || outputEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                        return null
                    }
                }
                else -> if (outputBufferIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    if (bufferInfo.size > 0) {
                        if (outputChannels !in 1..2 || outputSampleRate <= 0 || outputEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                            return null
                        }
                        if (pcmOutput.size().toLong() + bufferInfo.size > MAX_ANDROID_PCM_BYTES) return null
                        decoder.getOutputBuffer(outputBufferIndex)?.writeBufferRangeTo(pcmOutput, bufferInfo.offset, bufferInfo.size)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }

        val pcmData = pcmOutput.toByteArray()
        val outputBlockAlign = expectedPcmBlockAlign(outputChannels, 16) ?: return null
        val alignedSize = pcmData.size - (pcmData.size % outputBlockAlign)
        if (alignedSize <= 0 || outputSampleRate <= 0) return null
        val alignedPcmData = if (alignedSize == pcmData.size) pcmData else pcmData.copyOf(alignedSize)
        val outputFile = buildUniqueFile(context.cacheDir, sanitizeFileName(sourceName.substringBeforeLast('.') + "_decoded"), "wav")
        writePcmWav(outputFile, alignedPcmData, outputChannels, outputSampleRate, 16)
        outputFile
    } catch (_: Exception) {
        null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
        if (shouldDeleteSource) runCatching { sourceFile.delete() }
    }
}

private fun MediaFormat.safeInteger(key: String): Int {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}

private fun ByteBuffer.writeBufferRangeTo(output: ByteArrayOutputStream, offset: Int, size: Int) {
    val duplicate = duplicate().order(ByteOrder.LITTLE_ENDIAN)
    duplicate.position(offset)
    duplicate.limit(offset + size)
    val bytes = ByteArray(size)
    duplicate.get(bytes)
    output.write(bytes)
}

internal fun inspectAudioMeta(context: Context, input: PickedInput): AudioMeta? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        input.file?.let { file ->
            retriever.setDataSource(file.absolutePath)
        } ?: input.uri?.let { uri ->
            retriever.setDataSource(context, uri)
        } ?: return null

        val name = input.file?.name ?: input.uri?.let(context::queryDisplayName) ?: "audio"
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: getMimeType(input.file ?: File(name))
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
        } else {
            null
        }
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val size = input.file?.length() ?: input.uri?.let { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0L
        } ?: 0L

        AudioMeta(
            name = name,
            mimeType = mime,
            durationMs = duration,
            artist = artist,
            sampleRate = sampleRate,
            bitrate = bitrate,
            size = size
        )
    }.getOrNull().also {
        runCatching { retriever.release() }
    }
}

private fun trimPcmWavSilence(
    inputFile: File,
    outputDir: File,
    thresholdRatio: Double,
    minSilenceMs: Int,
    trimMode: WavTrimMode
): WavTrimResult? {
    if (!inputFile.extension.equals("wav", ignoreCase = true)) return null
    val wav = readPcmWav(inputFile) ?: return null
    val output = trimPcmWavSilence(
        wav,
        thresholdRatio,
        minSilenceMs,
        when (trimMode) {
            WavTrimMode.BOTH -> PcmWavTrimMode.BOTH
            WavTrimMode.START_ONLY -> PcmWavTrimMode.START_ONLY
            WavTrimMode.END_ONLY -> PcmWavTrimMode.END_ONLY
        }
    ) ?: return null
    val modeSuffix = when (trimMode) {
        WavTrimMode.BOTH -> "trimmed"
        WavTrimMode.START_ONLY -> "trim_start"
        WavTrimMode.END_ONLY -> "trim_end"
    }
    val outputFile =
        buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + modeSuffix), "wav")
    writePcmWav(outputFile, output.wav.pcmData, output.wav.channels, output.wav.sampleRate, output.wav.bitsPerSample)

    return WavTrimResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        trimmedStartMs = output.trimmedStartMs,
        trimmedEndMs = output.trimmedEndMs,
        keptDurationMs = output.keptDurationMs
    )
}

private fun convertPcmWavChannels(
    inputFile: File,
    outputDir: File,
    mode: WavChannelMode
): WavChannelConvertResult? {
    if (!inputFile.extension.equals("wav", ignoreCase = true)) return null
    val wav = readPcmWav(inputFile) ?: return null
    val output = convertPcmWavChannels(
        wav,
        when (mode) {
            WavChannelMode.MONO_TO_STEREO -> PcmWavChannelMode.MONO_TO_STEREO
            WavChannelMode.STEREO_TO_MONO -> PcmWavChannelMode.STEREO_TO_MONO
        }
    ) ?: return null

    val suffix = when (mode) {
        WavChannelMode.MONO_TO_STEREO -> "stereo"
        WavChannelMode.STEREO_TO_MONO -> "mono"
    }
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + suffix), "wav")
    writePcmWav(outputFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)

    return WavChannelConvertResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        sourceChannels = wav.channels,
        targetChannels = output.channels,
        durationMs = pcmDurationMs(output)
    )
}

private fun adjustPcmWavVolume(
    inputFile: File,
    outputDir: File,
    mode: WavVolumeMode,
    gainPercent: Int,
    targetPeakPercent: Int
): WavVolumeAdjustResult? {
    if (!inputFile.extension.equals("wav", ignoreCase = true)) return null
    val wav = readPcmWav(inputFile) ?: return null
    val output = adjustPcmWavVolume(
        wav,
        when (mode) {
            WavVolumeMode.GAIN -> PcmWavVolumeMode.GAIN
            WavVolumeMode.NORMALIZE -> PcmWavVolumeMode.NORMALIZE
        },
        gainPercent,
        targetPeakPercent
    ) ?: return null
    val suffix = when (mode) {
        WavVolumeMode.GAIN -> "gain_${gainPercent}pct"
        WavVolumeMode.NORMALIZE -> "norm_${targetPeakPercent}pct"
    }
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + suffix), "wav")
    writePcmWav(outputFile, output.wav.pcmData, output.wav.channels, output.wav.sampleRate, output.wav.bitsPerSample)

    return WavVolumeAdjustResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        sourcePeakPercent = output.sourcePeakPercent,
        targetPeakPercent = output.targetPeakPercent,
        durationMs = pcmDurationMs(output.wav)
    )
}

private fun applyPcmWavNoiseGate(inputFile: File, outputDir: File, thresholdPercent: Double, reductionDb: Double): WavRepairResult? {
    val wav = readPcmWav(inputFile) ?: return null
    val output = applyPcmWavNoiseGate(wav, thresholdPercent, reductionDb) ?: return null
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_gate"), "wav")
    writePcmWav(outputFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return WavRepairResult(inputFile.name, outputFile, pcmDurationMs(output))
}

private fun applyPcmWavFade(inputFile: File, outputDir: File, fadeInMs: Int, fadeOutMs: Int): WavRepairResult? {
    val wav = readPcmWav(inputFile) ?: return null
    val output = applyPcmWavFade(wav, fadeInMs, fadeOutMs) ?: return null
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_fade"), "wav")
    writePcmWav(outputFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return WavRepairResult(inputFile.name, outputFile, pcmDurationMs(output))
}

private fun removePcmWavDcOffset(inputFile: File, outputDir: File): WavRepairResult? {
    val wav = readPcmWav(inputFile) ?: return null
    val output = removePcmWavDcOffset(wav) ?: return null
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_dc"), "wav")
    writePcmWav(outputFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return WavRepairResult(inputFile.name, outputFile, pcmDurationMs(output))
}

private fun applyPcmWavPhase(inputFile: File, outputDir: File, mode: WavPhaseMode): WavRepairResult? {
    val wav = readPcmWav(inputFile) ?: return null
    val output = applyPcmWavPhase(
        wav,
        when (mode) {
            WavPhaseMode.INVERT_ALL -> PcmWavPhaseMode.INVERT_ALL
            WavPhaseMode.INVERT_LEFT -> PcmWavPhaseMode.INVERT_LEFT
            WavPhaseMode.INVERT_RIGHT -> PcmWavPhaseMode.INVERT_RIGHT
            WavPhaseMode.SWAP_STEREO -> PcmWavPhaseMode.SWAP_STEREO
        }
    ) ?: return null
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_phase"), "wav")
    writePcmWav(outputFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return WavRepairResult(inputFile.name, outputFile, pcmDurationMs(output))
}

private fun readPcmWav(inputFile: File): PcmWavData? {
    RandomAccessFile(inputFile, "r").use { raf ->
        if (raf.length() < 44) return null
        if (raf.readAscii(4) != "RIFF") return null
        raf.skipBytes(4)
        if (raf.readAscii(4) != "WAVE") return null

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var dataOffset = -1L
        var dataSize = 0L

        while (raf.filePointer + 8 <= raf.length()) {
            val chunkId = raf.readAscii(4)
            val chunkSize = raf.readLittleUnsignedInt()
            val chunkStart = raf.filePointer
            when (chunkId) {
                "fmt " -> {
                    audioFormat = raf.readLittleShort()
                    channels = raf.readLittleShort()
                    sampleRate = raf.readLittleInt()
                    raf.skipBytes(4)
                    blockAlign = raf.readLittleShort()
                    bitsPerSample = raf.readLittleShort()
                    if (audioFormat == 0xFFFE) {
                        if (chunkSize < 40) return null
                        raf.readLittleShort()
                        raf.skipBytes(6)
                        val subFormat = ByteArray(16).also { raf.readFully(it) }
                        if (!subFormat.isPcmSubFormatGuid()) return null
                    }
                }
                "data" -> {
                    dataOffset = raf.filePointer
                    dataSize = chunkSize
                    raf.seek(chunkStart + chunkSize)
                }
                else -> raf.seek(chunkStart + chunkSize)
            }
            val next = chunkStart + chunkSize + (chunkSize and 1L)
            if (next < chunkStart || next > raf.length()) return null
            if (raf.filePointer < next) raf.seek(next)
        }

        val expectedBlockAlign = expectedPcmBlockAlign(channels, bitsPerSample)
        if ((audioFormat != 1 && audioFormat != 0xFFFE) || sampleRate <= 0 || expectedBlockAlign == null || blockAlign != expectedBlockAlign || dataOffset < 0 || dataSize <= 0) {
            return null
        }
        if (dataSize > Int.MAX_VALUE) return null
        if (dataSize > MAX_ANDROID_PCM_BYTES) return null

        raf.seek(dataOffset)
        val alignedDataSize = dataSize - (dataSize % blockAlign)
        if (alignedDataSize <= 0) return null
        val data = ByteArray(alignedDataSize.toInt())
        raf.readFully(data)
        return PcmWavData(
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            blockAlign = blockAlign,
            pcmData = data
        )
    }
}

internal fun applyWavNoiseGate(
    context: Context,
    input: PickedInput,
    outputDir: File,
    thresholdPercent: Double,
    reductionDb: Double
): WavRepairResult? = processResolvedPcmWav(context, input) { file ->
    applyPcmWavNoiseGate(file, outputDir, thresholdPercent, reductionDb)
}

internal fun applyWavFade(
    context: Context,
    input: PickedInput,
    outputDir: File,
    fadeInMs: Int,
    fadeOutMs: Int
): WavRepairResult? = processResolvedPcmWav(context, input) { file ->
    applyPcmWavFade(file, outputDir, fadeInMs, fadeOutMs)
}

internal fun removeWavDcOffset(context: Context, input: PickedInput, outputDir: File): WavRepairResult? =
    processResolvedPcmWav(context, input) { file -> removePcmWavDcOffset(file, outputDir) }

internal fun applyWavPhase(
    context: Context,
    input: PickedInput,
    outputDir: File,
    mode: WavPhaseMode
): WavRepairResult? = processResolvedPcmWav(context, input) { file -> applyPcmWavPhase(file, outputDir, mode) }

private inline fun processResolvedPcmWav(
    context: Context,
    input: PickedInput,
    block: (File) -> WavRepairResult?
): WavRepairResult? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        resolvePcmWavInputFile(context, tempInput)?.use { resolved -> block(resolved.file) }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

private fun buildWaveformPreview(wav: PcmWavData, maxPoints: Int = 4800): List<FloatArray> {
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) return List(wav.channels) { FloatArray(0) }

    val pointCount = min(maxPoints, frameCount).coerceAtLeast(1)
    val framesPerPoint = max(1, frameCount / pointCount)
    val maxAmp = maxAmplitude(wav.bitsPerSample).coerceAtLeast(1.0)
    return List(wav.channels) { channel ->
        FloatArray(pointCount) { point ->
            val startFrame = point * framesPerPoint
            val endFrame = min(frameCount, if (point == pointCount - 1) frameCount else startFrame + framesPerPoint)
            var peak = 0.0
            var frame = startFrame
            while (frame < endFrame) {
                peak = max(peak, abs(readSignedSample(wav.pcmData, frame * wav.blockAlign + channel * (wav.bitsPerSample / 8), wav.bitsPerSample).toDouble()))
                frame++
            }
            (peak / maxAmp).coerceIn(0.0, 1.0).toFloat()
        }
    }
}

internal fun buildSpectrogramBitmap(
    wav: PcmWavData,
    config: SpectrogramRenderConfig = SpectrogramRenderConfig()
): Bitmap {
    val pixels = buildSpectrogramPixels(wav, config.toSharedConfig())
    return Bitmap.createBitmap(pixels.argb, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
}

private fun writePcmWav(file: File, pcmData: ByteArray, channels: Int, sampleRate: Int, bitsPerSample: Int) {
    val bytesPerSample = bitsPerSample / 8
    val byteRate = sampleRate * channels * bytesPerSample
    val blockAlign = channels * bytesPerSample
    require(expectedPcmBlockAlign(channels, bitsPerSample) == blockAlign && sampleRate > 0) { "无效的 PCM WAV 参数" }
    require(pcmData.isNotEmpty() && pcmData.size % blockAlign == 0) { "PCM 数据不是完整采样帧" }
    val riffSize = 36L + pcmData.size.toLong()
    require(riffSize <= 0xffffffffL) { "WAV 文件过大，RIFF size 超过 4GB" }
    file.outputStream().use { out ->
        val header = ByteArrayOutputStream()
        header.writeAscii("RIFF")
        header.writeLittleInt(riffSize.toInt())
        header.writeAscii("WAVE")
        header.writeAscii("fmt ")
        header.writeLittleInt(16)
        header.writeLittleShort(1)
        header.writeLittleShort(channels)
        header.writeLittleInt(sampleRate)
        header.writeLittleInt(byteRate)
        header.writeLittleShort(blockAlign)
        header.writeLittleShort(bitsPerSample)
        header.writeAscii("data")
        header.writeLittleInt(pcmData.size)
        out.write(header.toByteArray())
        out.write(pcmData)
    }
}

private fun RandomAccessFile.readAscii(length: Int): String {
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes.decodeToString()
}

private fun RandomAccessFile.readLittleInt(): Int {
    val b0 = read()
    val b1 = read()
    val b2 = read()
    val b3 = read()
    return (b0 and 0xff) or ((b1 and 0xff) shl 8) or ((b2 and 0xff) shl 16) or ((b3 and 0xff) shl 24)
}

private fun RandomAccessFile.readLittleUnsignedInt(): Long = readLittleInt().toLong() and 0xffffffffL

private fun RandomAccessFile.readLittleShort(): Int {
    val b0 = read()
    val b1 = read()
    return (b0 and 0xff) or ((b1 and 0xff) shl 8)
}

private fun ByteArray.isPcmSubFormatGuid(): Boolean = contentEquals(
    byteArrayOf(
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00,
        0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )
)

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.encodeToByteArray())
}

private fun ByteArrayOutputStream.writeLittleInt(value: Int) {
    write(byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte()
    ))
}

private fun ByteArrayOutputStream.writeLittleShort(value: Int) {
    write(byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte()
    ))
}
