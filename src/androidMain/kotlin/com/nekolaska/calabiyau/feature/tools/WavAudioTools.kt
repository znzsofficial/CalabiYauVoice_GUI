package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max

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
