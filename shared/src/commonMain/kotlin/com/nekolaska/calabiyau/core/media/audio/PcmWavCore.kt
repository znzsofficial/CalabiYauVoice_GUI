package com.nekolaska.calabiyau.core.media.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class PcmWavData(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val blockAlign: Int,
    val pcmData: ByteArray
)

enum class PcmWavTrimMode { BOTH, START_ONLY, END_ONLY }
enum class PcmWavChannelMode { MONO_TO_STEREO, STEREO_TO_MONO }
enum class PcmWavVolumeMode { GAIN, NORMALIZE }
enum class PcmWavPhaseMode { INVERT_ALL, INVERT_LEFT, INVERT_RIGHT, SWAP_STEREO }

data class PcmWavTrimOutput(
    val wav: PcmWavData,
    val trimmedStartMs: Long,
    val trimmedEndMs: Long,
    val keptDurationMs: Long
)

data class PcmWavVolumeOutput(
    val wav: PcmWavData,
    val sourcePeakPercent: Double,
    val targetPeakPercent: Double
)

fun expectedPcmBlockAlign(channels: Int, bitsPerSample: Int): Int? {
    if (channels !in 1..2 || bitsPerSample !in listOf(8, 16, 24, 32)) return null
    return channels * (bitsPerSample / 8)
}

fun pcmFrameCount(wav: PcmWavData): Int = wav.pcmData.size / wav.blockAlign

fun pcmDurationMs(wav: PcmWavData): Long = pcmFrameCount(wav) * 1000L / wav.sampleRate

fun calculatePcmPeakPercent(wav: PcmWavData): Double =
    (peakAmplitude(wav) / maxAmplitude(wav.bitsPerSample) * 100.0).coerceIn(0.0, 100.0)

fun trimPcmWavSilence(
    wav: PcmWavData,
    thresholdRatio: Double,
    minSilenceMs: Int,
    mode: PcmWavTrimMode
): PcmWavTrimOutput? {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    val minSilenceFrames = max(1, (wav.sampleRate * minSilenceMs / 1000.0).roundToInt())

    var startFrame = 0
    var silentRun = 0
    for (frame in 0 until frameCount) {
        if (framePeakRatio(wav, frame) <= thresholdRatio) {
            silentRun++
            if (silentRun >= minSilenceFrames) startFrame = frame + 1
        } else {
            break
        }
    }

    var endFrame = frameCount
    silentRun = 0
    for (frame in frameCount - 1 downTo 0) {
        if (framePeakRatio(wav, frame) <= thresholdRatio) {
            silentRun++
            if (silentRun >= minSilenceFrames) endFrame = frame
        } else {
            break
        }
    }

    if (mode == PcmWavTrimMode.END_ONLY) startFrame = 0
    if (mode == PcmWavTrimMode.START_ONLY) endFrame = frameCount
    if (endFrame <= startFrame) return null

    val from = startFrame * wav.blockAlign
    val to = endFrame * wav.blockAlign
    val trimmedData = wav.pcmData.copyOfRange(from, to)
    val output = wav.copy(pcmData = trimmedData)
    val keptDurationMs = pcmDurationMs(output)
    val trimmedStartMs = startFrame * 1000L / wav.sampleRate
    val trimmedEndMs = max(0L, pcmDurationMs(wav) - keptDurationMs - trimmedStartMs)
    return PcmWavTrimOutput(output, trimmedStartMs, trimmedEndMs, keptDurationMs)
}

fun convertPcmWavChannels(wav: PcmWavData, mode: PcmWavChannelMode): PcmWavData? {
    val bytesPerSample = wav.bitsPerSample / 8
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    return when (mode) {
        PcmWavChannelMode.MONO_TO_STEREO -> {
            if (wav.channels != 1) return null
            val out = ByteArray(wav.pcmData.size * 2)
            var sourceOffset = 0
            var targetOffset = 0
            while (sourceOffset < wav.pcmData.size) {
                wav.pcmData.copyInto(out, targetOffset, sourceOffset, sourceOffset + bytesPerSample)
                targetOffset += bytesPerSample
                wav.pcmData.copyInto(out, targetOffset, sourceOffset, sourceOffset + bytesPerSample)
                targetOffset += bytesPerSample
                sourceOffset += bytesPerSample
            }
            wav.copy(channels = 2, blockAlign = bytesPerSample * 2, pcmData = out)
        }
        PcmWavChannelMode.STEREO_TO_MONO -> {
            if (wav.channels != 2) return null
            val out = ByteArray(frameCount * bytesPerSample)
            repeat(frameCount) { frame ->
                val source = frame * wav.blockAlign
                val target = frame * bytesPerSample
                val left = readSignedSample(wav.pcmData, source, wav.bitsPerSample)
                val right = readSignedSample(wav.pcmData, source + bytesPerSample, wav.bitsPerSample)
                writeSignedSample(out, target, ((left.toLong() + right.toLong()) / 2L).toInt(), wav.bitsPerSample)
            }
            wav.copy(channels = 1, blockAlign = bytesPerSample, pcmData = out)
        }
    }
}

fun adjustPcmWavVolume(wav: PcmWavData, mode: PcmWavVolumeMode, gainPercent: Int, targetPeakPercent: Int): PcmWavVolumeOutput? {
    val sourcePeak = calculatePcmPeakPercent(wav)
    val multiplier = when (mode) {
        PcmWavVolumeMode.GAIN -> gainPercent.coerceAtLeast(0) / 100.0
        PcmWavVolumeMode.NORMALIZE -> {
            val rawPeak = peakAmplitude(wav)
            if (rawPeak <= 0.0) return null
            (maxAmplitude(wav.bitsPerSample) * (targetPeakPercent.coerceIn(1, 100) / 100.0)) / rawPeak
        }
    }
    val output = wav.copy(pcmData = scalePcmData(wav, multiplier))
    return PcmWavVolumeOutput(output, sourcePeak, calculatePcmPeakPercent(output))
}

fun applyPcmWavNoiseGate(wav: PcmWavData, thresholdPercent: Double, reductionDb: Double): PcmWavData? {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    val thresholdRatio = thresholdPercent.coerceIn(0.0, 100.0) / 100.0
    val reduction = 10.0.pow(-reductionDb.coerceIn(0.0, 80.0) / 20.0)
    val bytesPerSample = wav.bitsPerSample / 8
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    val closeCoeff = smoothingCoefficient(wav.sampleRate, 12.0)
    val openCoeff = smoothingCoefficient(wav.sampleRate, 45.0)
    var currentMultiplier = 1.0
    repeat(frameCount) { frame ->
        val frameOffset = frame * wav.blockAlign
        val targetMultiplier = if (framePeakRatio(wav, frame) < thresholdRatio) reduction else 1.0
        val coeff = if (targetMultiplier < currentMultiplier) closeCoeff else openCoeff
        currentMultiplier += (targetMultiplier - currentMultiplier) * coeff
        repeat(wav.channels) { channel ->
            val offset = frameOffset + channel * bytesPerSample
            val value = (readSignedSample(wav.pcmData, offset, wav.bitsPerSample) * currentMultiplier).roundToInt().coerceIn(minValue, maxValue)
            writeSignedSample(out, offset, value, wav.bitsPerSample)
        }
    }
    return wav.copy(pcmData = out)
}

fun applyPcmWavFade(wav: PcmWavData, fadeInMs: Int, fadeOutMs: Int): PcmWavData? {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    val fadeInFrames = (wav.sampleRate * fadeInMs.coerceAtLeast(0) / 1000.0).roundToInt().coerceAtMost(frameCount)
    val fadeOutFrames = (wav.sampleRate * fadeOutMs.coerceAtLeast(0) / 1000.0).roundToInt().coerceAtMost(frameCount)
    val bytesPerSample = wav.bitsPerSample / 8
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    repeat(frameCount) { frame ->
        val inGain = if (fadeInFrames > 0 && frame < fadeInFrames) frame.toDouble() / fadeInFrames else 1.0
        val outGain = if (fadeOutFrames > 0 && frame >= frameCount - fadeOutFrames) (frameCount - frame - 1).coerceAtLeast(0).toDouble() / fadeOutFrames else 1.0
        val gain = min(inGain, outGain).coerceIn(0.0, 1.0)
        val frameOffset = frame * wav.blockAlign
        repeat(wav.channels) { channel ->
            val offset = frameOffset + channel * bytesPerSample
            val value = (readSignedSample(wav.pcmData, offset, wav.bitsPerSample) * gain).roundToInt().coerceIn(minValue, maxValue)
            writeSignedSample(out, offset, value, wav.bitsPerSample)
        }
    }
    return wav.copy(pcmData = out)
}

fun removePcmWavDcOffset(wav: PcmWavData): PcmWavData? {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    val bytesPerSample = wav.bitsPerSample / 8
    val offsets = DoubleArray(wav.channels)
    repeat(frameCount) { frame ->
        val frameOffset = frame * wav.blockAlign
        repeat(wav.channels) { channel ->
            offsets[channel] += readSignedSample(wav.pcmData, frameOffset + channel * bytesPerSample, wav.bitsPerSample)
        }
    }
    for (channel in offsets.indices) offsets[channel] /= frameCount
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    repeat(frameCount) { frame ->
        val frameOffset = frame * wav.blockAlign
        repeat(wav.channels) { channel ->
            val offset = frameOffset + channel * bytesPerSample
            val value = (readSignedSample(wav.pcmData, offset, wav.bitsPerSample) - offsets[channel]).roundToInt().coerceIn(minValue, maxValue)
            writeSignedSample(out, offset, value, wav.bitsPerSample)
        }
    }
    return wav.copy(pcmData = out)
}

fun applyPcmWavPhase(wav: PcmWavData, mode: PcmWavPhaseMode): PcmWavData? {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return null
    val bytesPerSample = wav.bitsPerSample / 8
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    repeat(frameCount) { frame ->
        val frameOffset = frame * wav.blockAlign
        repeat(wav.channels) { channel ->
            val sourceChannel = if (mode == PcmWavPhaseMode.SWAP_STEREO && wav.channels == 2) 1 - channel else channel
            val sourceOffset = frameOffset + sourceChannel * bytesPerSample
            val targetOffset = frameOffset + channel * bytesPerSample
            var value = readSignedSample(wav.pcmData, sourceOffset, wav.bitsPerSample)
            val invert = mode == PcmWavPhaseMode.INVERT_ALL ||
                (mode == PcmWavPhaseMode.INVERT_LEFT && channel == 0) ||
                (mode == PcmWavPhaseMode.INVERT_RIGHT && channel == 1)
            if (invert) value = (-value.toLong()).coerceIn(minValue.toLong(), maxValue.toLong()).toInt()
            writeSignedSample(out, targetOffset, value, wav.bitsPerSample)
        }
    }
    return wav.copy(pcmData = out)
}

fun framePeakRatio(wav: PcmWavData, frameIndex: Int): Double =
    framePeakAmplitude(wav, frameIndex) / maxAmplitude(wav.bitsPerSample).coerceAtLeast(1.0)

fun framePeakAmplitude(wav: PcmWavData, frameIndex: Int): Double {
    val bytesPerSample = wav.bitsPerSample / 8
    val base = frameIndex * wav.blockAlign
    var peak = 0.0
    repeat(wav.channels) { channel ->
        peak = max(peak, abs(readSignedSample(wav.pcmData, base + channel * bytesPerSample, wav.bitsPerSample).toDouble()))
    }
    return peak
}

fun peakAmplitude(wav: PcmWavData): Double {
    val bytesPerSample = wav.bitsPerSample / 8
    var peak = 0.0
    var offset = 0
    while (offset + bytesPerSample <= wav.pcmData.size) {
        peak = max(peak, abs(readSignedSample(wav.pcmData, offset, wav.bitsPerSample).toDouble()))
        offset += bytesPerSample
    }
    return peak
}

fun scalePcmData(wav: PcmWavData, multiplier: Double): ByteArray {
    val bytesPerSample = wav.bitsPerSample / 8
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    var offset = 0
    while (offset + bytesPerSample <= wav.pcmData.size) {
        val scaled = (readSignedSample(wav.pcmData, offset, wav.bitsPerSample) * multiplier).roundToInt().coerceIn(minValue, maxValue)
        writeSignedSample(out, offset, scaled, wav.bitsPerSample)
        offset += bytesPerSample
    }
    return out
}

fun maxAmplitude(bits: Int): Double = maxSignedValue(bits).toDouble()
fun maxSignedValue(bits: Int): Int = if (bits == 32) Int.MAX_VALUE else (1 shl (bits - 1)) - 1
fun minSignedValue(bits: Int): Int = if (bits == 32) Int.MIN_VALUE else -(1 shl (bits - 1))

fun readSignedSample(data: ByteArray, offset: Int, bits: Int): Int {
    return when (bits) {
        8 -> (data[offset].toInt() and 0xFF) - 128
        16 -> ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
        24 -> {
            val value = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset + 2].toInt() shl 16)
            if (value and 0x800000 != 0) value or -0x1000000 else value
        }
        32 -> (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8) or ((data[offset + 2].toInt() and 0xFF) shl 16) or (data[offset + 3].toInt() shl 24)
        else -> 0
    }
}

fun writeSignedSample(data: ByteArray, offset: Int, value: Int, bits: Int) {
    when (bits) {
        8 -> data[offset] = (value + 128).coerceIn(0, 255).toByte()
        16 -> {
            data[offset] = value.toByte()
            data[offset + 1] = (value shr 8).toByte()
        }
        24 -> {
            data[offset] = value.toByte()
            data[offset + 1] = (value shr 8).toByte()
            data[offset + 2] = (value shr 16).toByte()
        }
        32 -> {
            data[offset] = value.toByte()
            data[offset + 1] = (value shr 8).toByte()
            data[offset + 2] = (value shr 16).toByte()
            data[offset + 3] = (value shr 24).toByte()
        }
    }
}

private fun smoothingCoefficient(sampleRate: Int, timeMs: Double): Double {
    val samples = (sampleRate * timeMs / 1000.0).coerceAtLeast(1.0)
    return 1.0 - exp(-1.0 / samples)
}
