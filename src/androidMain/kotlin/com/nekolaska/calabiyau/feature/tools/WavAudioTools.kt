package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

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

internal data class PcmWavData(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val blockAlign: Int,
    val pcmData: ByteArray
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
    val highColorArgb: Int = 0xFF38BDF8.toInt()
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
        peakPercent = calculatePeakPercent(wav),
        waveform = buildWaveformPreview(wav),
        spectrogramBitmap = if (includeSpectrogram) buildSpectrogramBitmap(wav, spectrogramConfig) else null
    )
}

internal fun loadAudioAssetForPreview(context: Context, input: PickedInput): LoadedAudioAsset? {
    val meta = inspectAudioMeta(context, input) ?: return null
    val wav = when {
        input.file?.extension.equals("wav", ignoreCase = true) || meta.mimeType.contains("wav", ignoreCase = true) -> {
            val preview = loadWavAudioForPreview(context, input, includeSpectrogram = false)
            preview
        }
        isMp3Audio(meta, input) -> {
            decodeAudioInputToTempWav(context, input, meta.name)?.let { loadWavAudioFileForPreview(it, includeSpectrogram = false) }
        }
        else -> null
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
        peakPercent = calculatePeakPercent(wav),
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
        trimPcmWavSilence(
            inputFile = tempInput,
            outputDir = outputDir,
            thresholdRatio = thresholdRatio,
            minSilenceMs = minSilenceMs,
            trimMode = trimMode
        )
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
        convertPcmWavChannels(tempInput, outputDir, mode)
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
        adjustPcmWavVolume(tempInput, outputDir, mode, gainPercent, targetPeakPercent)
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
}

internal fun inspectWavMeta(context: Context, input: PickedInput): WavMeta? {
    val tempInput = materializeInputToFile(context, input) ?: return null
    val shouldDeleteTemp = input.file == null
    return try {
        readPcmWav(tempInput)?.let { wav ->
            WavMeta(
                channels = wav.channels,
                sampleRate = wav.sampleRate,
                bitsPerSample = wav.bitsPerSample,
                peakPercent = calculatePeakPercent(wav)
            )
        }
    } finally {
        if (shouldDeleteTemp) runCatching { tempInput.delete() }
    }
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

private fun isMp3Audio(meta: AudioMeta, input: PickedInput): Boolean {
    val mime = meta.mimeType.lowercase()
    return mime.contains("mpeg") || mime.contains("mp3") ||
        input.file?.extension.equals("mp3", ignoreCase = true) ||
        meta.name.substringAfterLast('.', "").equals("mp3", ignoreCase = true)
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
                        decoder.getOutputBuffer(outputBufferIndex)?.writeBufferRangeTo(pcmOutput, bufferInfo.offset, bufferInfo.size)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }

        val pcmData = pcmOutput.toByteArray()
        if (pcmData.isEmpty() || outputChannels !in 1..2 || outputSampleRate <= 0) return null
        val outputFile = buildUniqueFile(context.cacheDir, sanitizeFileName(sourceName.substringBeforeLast('.') + "_decoded"), "wav")
        writePcmWav(outputFile, pcmData, outputChannels, outputSampleRate, 16)
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
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) return null

    val threshold = maxAmplitude(wav.bitsPerSample) * thresholdRatio
    val minSilenceFrames = max(1, (wav.sampleRate * minSilenceMs) / 1000)

    var startFrame = 0
    while (startFrame < frameCount && framePeak(wav.pcmData, startFrame, wav.blockAlign, wav.bitsPerSample, wav.channels) <= threshold) {
        startFrame++
    }
    var endFrame = frameCount - 1
    while (endFrame >= startFrame && framePeak(wav.pcmData, endFrame, wav.blockAlign, wav.bitsPerSample, wav.channels) <= threshold) {
        endFrame--
    }

    val leadingSilentFrames = startFrame
    val trailingSilentFrames = frameCount - 1 - endFrame
    if (leadingSilentFrames < minSilenceFrames) startFrame = 0
    if (trailingSilentFrames < minSilenceFrames) endFrame = frameCount - 1

    when (trimMode) {
        WavTrimMode.START_ONLY -> endFrame = frameCount - 1
        WavTrimMode.END_ONLY -> startFrame = 0
        WavTrimMode.BOTH -> Unit
    }

    if (startFrame == 0 && endFrame == frameCount - 1) return null
    if (endFrame < startFrame) return null

    val startByte = startFrame * wav.blockAlign
    val endExclusiveByte = (endFrame + 1) * wav.blockAlign
    val trimmedData = wav.pcmData.copyOfRange(startByte, endExclusiveByte)
    val modeSuffix = when (trimMode) {
        WavTrimMode.BOTH -> "trimmed"
        WavTrimMode.START_ONLY -> "trim_start"
        WavTrimMode.END_ONLY -> "trim_end"
    }
    val outputFile =
        buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + modeSuffix), "wav")
    writePcmWav(outputFile, trimmedData, wav.channels, wav.sampleRate, wav.bitsPerSample)

    val totalDurationMs = frameCount * 1000L / wav.sampleRate
    val keptDurationMs = (endFrame - startFrame + 1L) * 1000L / wav.sampleRate
    val trimmedStartMs = startFrame * 1000L / wav.sampleRate
    val trimmedEndMs = max(0L, totalDurationMs - keptDurationMs - trimmedStartMs)

    return WavTrimResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        trimmedStartMs = trimmedStartMs,
        trimmedEndMs = trimmedEndMs,
        keptDurationMs = keptDurationMs
    )
}

private fun convertPcmWavChannels(
    inputFile: File,
    outputDir: File,
    mode: WavChannelMode
): WavChannelConvertResult? {
    if (!inputFile.extension.equals("wav", ignoreCase = true)) return null
    val wav = readPcmWav(inputFile) ?: return null
    val bytesPerSample = wav.bitsPerSample / 8
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) return null

    val targetChannels = when (mode) {
        WavChannelMode.MONO_TO_STEREO -> 2
        WavChannelMode.STEREO_TO_MONO -> 1
    }
    if (mode == WavChannelMode.MONO_TO_STEREO && wav.channels != 1) return null
    if (mode == WavChannelMode.STEREO_TO_MONO && wav.channels != 2) return null

    val output = ByteArrayOutputStream()
    repeat(frameCount) { frameIndex ->
        val frameStart = frameIndex * wav.blockAlign
        when (mode) {
            WavChannelMode.MONO_TO_STEREO -> {
                val sample = wav.pcmData.copyOfRange(frameStart, frameStart + bytesPerSample)
                output.write(sample)
                output.write(sample)
            }
            WavChannelMode.STEREO_TO_MONO -> {
                val left = wav.pcmData.copyOfRange(frameStart, frameStart + bytesPerSample)
                val right = wav.pcmData.copyOfRange(frameStart + bytesPerSample, frameStart + bytesPerSample * 2)
                output.write(averageSamples(left, right, wav.bitsPerSample))
            }
        }
    }

    val suffix = when (mode) {
        WavChannelMode.MONO_TO_STEREO -> "stereo"
        WavChannelMode.STEREO_TO_MONO -> "mono"
    }
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + suffix), "wav")
    writePcmWav(outputFile, output.toByteArray(), targetChannels, wav.sampleRate, wav.bitsPerSample)

    return WavChannelConvertResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        sourceChannels = wav.channels,
        targetChannels = targetChannels,
        durationMs = frameCount * 1000L / wav.sampleRate
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
    val sourcePeak = calculatePeakPercent(wav)
    val multiplier = when (mode) {
        WavVolumeMode.GAIN -> gainPercent / 100.0
        WavVolumeMode.NORMALIZE -> {
            val rawPeak = peakAmplitude(wav)
            if (rawPeak <= 0.0) return null
            (maxAmplitude(wav.bitsPerSample) * (targetPeakPercent / 100.0)) / rawPeak
        }
    }
    if (multiplier <= 0.0) return null

    val adjustedData = scalePcmData(wav, multiplier)
    val suffix = when (mode) {
        WavVolumeMode.GAIN -> "gain_${gainPercent}pct"
        WavVolumeMode.NORMALIZE -> "norm_${targetPeakPercent}pct"
    }
    val outputFile = buildUniqueFile(outputDir, sanitizeFileName(inputFile.nameWithoutExtension + "_" + suffix), "wav")
    writePcmWav(outputFile, adjustedData, wav.channels, wav.sampleRate, wav.bitsPerSample)

    val adjustedWav = wav.copy(pcmData = adjustedData)
    return WavVolumeAdjustResult(
        sourceName = inputFile.name,
        outputFile = outputFile,
        sourcePeakPercent = sourcePeak,
        targetPeakPercent = calculatePeakPercent(adjustedWav),
        durationMs = (adjustedData.size / wav.blockAlign) * 1000L / wav.sampleRate
    )
}

private fun readPcmWav(inputFile: File): PcmWavData? {
    RandomAccessFile(inputFile, "r").use { raf ->
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
            val chunkSize = raf.readLittleInt().toLong() and 0xffffffffL
            val chunkStart = raf.filePointer
            when (chunkId) {
                "fmt " -> {
                    audioFormat = raf.readLittleShort()
                    channels = raf.readLittleShort()
                    sampleRate = raf.readLittleInt()
                    raf.skipBytes(4)
                    blockAlign = raf.readLittleShort()
                    bitsPerSample = raf.readLittleShort()
                }
                "data" -> {
                    dataOffset = raf.filePointer
                    dataSize = chunkSize
                    raf.seek(chunkStart + chunkSize)
                }
                else -> raf.seek(chunkStart + chunkSize)
            }
            if (chunkSize % 2L == 1L && raf.filePointer < raf.length()) raf.skipBytes(1)
        }

        if (audioFormat != 1 || channels <= 0 || sampleRate <= 0 || bitsPerSample !in setOf(8, 16, 24, 32) || blockAlign <= 0 || dataOffset < 0 || dataSize <= 0) {
            return null
        }

        raf.seek(dataOffset)
        val data = ByteArray(dataSize.toInt())
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

private fun framePeak(data: ByteArray, frameIndex: Int, blockAlign: Int, bitsPerSample: Int, channels: Int): Double {
    val frameStart = frameIndex * blockAlign
    val bytesPerSample = bitsPerSample / 8
    var peak = 0.0
    repeat(channels) { channel ->
        val sampleOffset = frameStart + channel * bytesPerSample
        val amplitude = sampleAmplitude(data, sampleOffset, bitsPerSample)
        if (amplitude > peak) peak = amplitude
    }
    return peak
}

private fun sampleAmplitude(data: ByteArray, offset: Int, bitsPerSample: Int): Double {
    return when (bitsPerSample) {
        8 -> abs((data[offset].toInt() and 0xff) - 128).toDouble()
        16 -> abs(((data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)).toShort().toInt()).toDouble()
        24 -> {
            var value = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                (data[offset + 2].toInt() shl 16)
            if (value and 0x800000 != 0) value = value or -0x1000000
            abs(value).toDouble()
        }
        32 -> abs(
            (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                (data[offset + 3].toInt() shl 24)
        ).toDouble()
        else -> 0.0
    }
}

private fun maxAmplitude(bitsPerSample: Int): Double = when (bitsPerSample) {
    8 -> 127.0
    16 -> 32767.0
    24 -> 8388607.0
    32 -> Int.MAX_VALUE.toDouble()
    else -> 1.0
}

private fun averageSamples(left: ByteArray, right: ByteArray, bitsPerSample: Int): ByteArray {
    return when (bitsPerSample) {
        8 -> byteArrayOf((((left[0].toInt() and 0xff) + (right[0].toInt() and 0xff)) / 2).toByte())
        16 -> {
            val l = ((left[0].toInt() and 0xff) or (left[1].toInt() shl 8)).toShort().toInt()
            val r = ((right[0].toInt() and 0xff) or (right[1].toInt() shl 8)).toShort().toInt()
            val avg = ((l + r) / 2).toShort().toInt()
            byteArrayOf((avg and 0xff).toByte(), ((avg shr 8) and 0xff).toByte())
        }
        24 -> {
            fun decode(bytes: ByteArray): Int {
                var value = (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8) or (bytes[2].toInt() shl 16)
                if (value and 0x800000 != 0) value = value or -0x1000000
                return value
            }
            val avg = (decode(left) + decode(right)) / 2
            byteArrayOf((avg and 0xff).toByte(), ((avg shr 8) and 0xff).toByte(), ((avg shr 16) and 0xff).toByte())
        }
        32 -> {
            fun decode(bytes: ByteArray): Int {
                return (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8) or ((bytes[2].toInt() and 0xff) shl 16) or (bytes[3].toInt() shl 24)
            }
            val avg = (decode(left).toLong() + decode(right).toLong()) / 2L
            byteArrayOf((avg and 0xff).toByte(), ((avg shr 8) and 0xff).toByte(), ((avg shr 16) and 0xff).toByte(), ((avg shr 24) and 0xff).toByte())
        }
        else -> left
    }
}

private fun peakAmplitude(wav: PcmWavData): Double {
    val frameCount = wav.pcmData.size / wav.blockAlign
    var peak = 0.0
    for (frameIndex in 0 until frameCount) {
        peak = max(peak, framePeak(wav.pcmData, frameIndex, wav.blockAlign, wav.bitsPerSample, wav.channels))
    }
    return peak
}

private fun calculatePeakPercent(wav: PcmWavData): Double {
    return (peakAmplitude(wav) / maxAmplitude(wav.bitsPerSample) * 100.0).coerceIn(0.0, 100.0)
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
                peak = max(peak, abs(sampleValue(wav, frame, channel)))
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
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) return createBitmap(1, 1)

    val windowSize = normalizeSpectrogramWindowSize(config.windowSize)
    val hopRatio = config.hopRatio.coerceIn(0.05f, 1.0f)
    val maxTimeBins = config.maxTimeBins.coerceAtLeast(1)
    val maxFrequencyBins = config.maxFrequencyBins.coerceAtLeast(1)
    val nyquist = (wav.sampleRate / 2.0).coerceAtLeast(1.0)
    val displayMaxHz = config.cutoffFrequencyHz
        .takeIf { it > 0 }
        ?.toDouble()
        ?.coerceIn(1.0, nyquist)
        ?: nyquist
    val rawHop = max(1, (windowSize * hopRatio).toInt())
    val totalWindows = max(1, 1 + max(0, frameCount - windowSize) / rawHop)
    val hop = max(1, rawHop * max(1, totalWindows / maxTimeBins))
    val timeBins = min(maxTimeBins, totalWindows)
    val fftFrequencyBins = (windowSize / 2).coerceAtLeast(1)
    val frequencyScaleDenominator = (maxFrequencyBins - 1).coerceAtLeast(1)
    val window = DoubleArray(windowSize) { index ->
        0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))
    }
    val maxAmp = maxAmplitude(wav.bitsPerSample).coerceAtLeast(1.0)

    val bitmapWidth = timeBins
    val bitmapHeight = maxFrequencyBins * wav.channels
    val pixels = IntArray(bitmapWidth * bitmapHeight)
    val real = DoubleArray(windowSize)
    val imag = DoubleArray(windowSize)

    for (channel in 0 until wav.channels) {
        val channelTop = channel * maxFrequencyBins
        for (timeIndex in 0 until timeBins) {
            real.fill(0.0)
            imag.fill(0.0)
            val startFrame = timeIndex * hop
            for (i in 0 until windowSize) {
                real[i] = (sampleValue(wav, startFrame + i, channel) / maxAmp) * window[i]
            }
            fft(real, imag)
            for (pixelRow in 0 until maxFrequencyBins) {
                val fromBottom = maxFrequencyBins - 1 - pixelRow
                val freqHz = displayMaxHz * (fromBottom.toDouble() / frequencyScaleDenominator.toDouble())
                val fftBin = ((freqHz / nyquist) * (fftFrequencyBins - 1)).roundToInt().coerceIn(0, fftFrequencyBins - 1)
                val magnitude = sqrt(real[fftBin] * real[fftBin] + imag[fftBin] * imag[fftBin])
                val intensity = (ln(1.0 + magnitude * 32.0) / ln(33.0)).coerceIn(0.0, 1.0).toFloat()
                val y = channelTop + pixelRow
                pixels[y * bitmapWidth + timeIndex] = spectrogramColorArgb(intensity, config)
            }
        }
    }

    return Bitmap.createBitmap(pixels, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
}

private fun normalizeSpectrogramWindowSize(windowSize: Int): Int {
    val clamped = windowSize.coerceIn(256, 8192)
    val lowerPower = Integer.highestOneBit(clamped)
    return if (lowerPower == clamped) {
        clamped
    } else {
        val upperPower = lowerPower shl 1
        if (upperPower <= 8192 && upperPower - clamped < clamped - lowerPower) upperPower else lowerPower
    }
}

private fun sampleValue(wav: PcmWavData, frameIndex: Int, channel: Int): Double {
    if (frameIndex < 0 || channel !in 0 until wav.channels) return 0.0
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameIndex >= frameCount) return 0.0
    val bytesPerSample = wav.bitsPerSample / 8
    val offset = frameIndex * wav.blockAlign + channel * bytesPerSample
    return when (wav.bitsPerSample) {
        8 -> ((wav.pcmData[offset].toInt() and 0xff) - 128).toDouble()
        16 -> ((wav.pcmData[offset].toInt() and 0xff) or (wav.pcmData[offset + 1].toInt() shl 8)).toShort().toDouble()
        24 -> {
            var value = (wav.pcmData[offset].toInt() and 0xff) or
                ((wav.pcmData[offset + 1].toInt() and 0xff) shl 8) or
                (wav.pcmData[offset + 2].toInt() shl 16)
            if (value and 0x800000 != 0) value = value or -0x1000000
            value.toDouble()
        }
        32 -> ((wav.pcmData[offset].toInt() and 0xff) or
            ((wav.pcmData[offset + 1].toInt() and 0xff) shl 8) or
            ((wav.pcmData[offset + 2].toInt() and 0xff) shl 16) or
            (wav.pcmData[offset + 3].toInt() shl 24)).toDouble()
        else -> 0.0
    }
}

private fun spectrogramColorArgb(value: Float, config: SpectrogramRenderConfig): Int {
    val v = value.coerceIn(0f, 1f)
    val low = config.lowColorArgb
    val high = config.highColorArgb
    val lowA = (low ushr 24) and 0xff
    val lowR = (low ushr 16) and 0xff
    val lowG = (low ushr 8) and 0xff
    val lowB = low and 0xff
    val highA = (high ushr 24) and 0xff
    val highR = (high ushr 16) and 0xff
    val highG = (high ushr 8) and 0xff
    val highB = high and 0xff
    val red = (lowR + (highR - lowR) * v).toInt().coerceIn(0, 255)
    val green = (lowG + (highG - lowG) * v).toInt().coerceIn(0, 255)
    val blue = (lowB + (highB - lowB) * v).toInt().coerceIn(0, 255)
    val alpha = (lowA + (highA - lowA) * v).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun fft(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            val tempReal = real[i]
            real[i] = real[j]
            real[j] = tempReal
            val tempImag = imag[i]
            imag[i] = imag[j]
            imag[j] = tempImag
        }
    }

    var length = 2
    while (length <= n) {
        val angle = -2.0 * PI / length
        val wLengthReal = cos(angle)
        val wLengthImag = sin(angle)
        var i = 0
        while (i < n) {
            var wReal = 1.0
            var wImag = 0.0
            for (k in 0 until length / 2) {
                val even = i + k
                val odd = even + length / 2
                val oddReal = real[odd] * wReal - imag[odd] * wImag
                val oddImag = real[odd] * wImag + imag[odd] * wReal
                real[odd] = real[even] - oddReal
                imag[odd] = imag[even] - oddImag
                real[even] += oddReal
                imag[even] += oddImag
                val nextReal = wReal * wLengthReal - wImag * wLengthImag
                wImag = wReal * wLengthImag + wImag * wLengthReal
                wReal = nextReal
            }
            i += length
        }
        length = length shl 1
    }
}

private fun scalePcmData(wav: PcmWavData, multiplier: Double): ByteArray {
    val output = wav.pcmData.copyOf()
    when (wav.bitsPerSample) {
        8 -> {
            output.indices.forEach { index ->
                val centered = (output[index].toInt() and 0xff) - 128
                val scaled = (centered * multiplier).toInt().coerceIn(-128, 127)
                output[index] = (scaled + 128).toByte()
            }
        }
        16 -> {
            var i = 0
            while (i + 1 < output.size) {
                val sample = ((output[i].toInt() and 0xff) or (output[i + 1].toInt() shl 8)).toShort().toInt()
                val scaled = (sample * multiplier).toInt().coerceIn(-32768, 32767)
                output[i] = (scaled and 0xff).toByte()
                output[i + 1] = ((scaled shr 8) and 0xff).toByte()
                i += 2
            }
        }
        24 -> {
            var i = 0
            while (i + 2 < output.size) {
                var sample = (output[i].toInt() and 0xff) or ((output[i + 1].toInt() and 0xff) shl 8) or (output[i + 2].toInt() shl 16)
                if (sample and 0x800000 != 0) sample = sample or -0x1000000
                val scaled = (sample * multiplier).toInt().coerceIn(-8388608, 8388607)
                output[i] = (scaled and 0xff).toByte()
                output[i + 1] = ((scaled shr 8) and 0xff).toByte()
                output[i + 2] = ((scaled shr 16) and 0xff).toByte()
                i += 3
            }
        }
        32 -> {
            var i = 0
            while (i + 3 < output.size) {
                val sample = (output[i].toInt() and 0xff) or
                    ((output[i + 1].toInt() and 0xff) shl 8) or
                    ((output[i + 2].toInt() and 0xff) shl 16) or
                    (output[i + 3].toInt() shl 24)
                val scaled = (sample * multiplier).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                output[i] = (scaled and 0xff).toByte()
                output[i + 1] = ((scaled shr 8) and 0xff).toByte()
                output[i + 2] = ((scaled shr 16) and 0xff).toByte()
                output[i + 3] = ((scaled shr 24) and 0xff).toByte()
                i += 4
            }
        }
    }
    return output
}

private fun writePcmWav(file: File, pcmData: ByteArray, channels: Int, sampleRate: Int, bitsPerSample: Int) {
    val bytesPerSample = bitsPerSample / 8
    val byteRate = sampleRate * channels * bytesPerSample
    val blockAlign = channels * bytesPerSample
    file.outputStream().use { out ->
        val header = ByteArrayOutputStream()
        header.writeAscii("RIFF")
        header.writeLittleInt(36 + pcmData.size)
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

private fun RandomAccessFile.readLittleShort(): Int {
    val b0 = read()
    val b1 = read()
    return (b0 and 0xff) or ((b1 and 0xff) shl 8)
}

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
