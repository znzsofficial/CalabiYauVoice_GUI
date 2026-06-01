package com.nekolaska.calabiyau.core.media.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectrogramConfig(
    val windowSize: Int = 1024,
    val hopRatio: Float = 0.25f,
    val maxTimeBins: Int = 720,
    val maxFrequencyBins: Int = 256,
    val cutoffFrequencyHz: Int = 0,
    val lowColorArgb: Int = 0xFF0F172A.toInt(),
    val highColorArgb: Int = 0xFF38BDF8.toInt(),
    val gainDb: Double = 0.0,
    val gamma: Double = 1.0,
    val floorDb: Double = -60.0
)

data class SpectrogramPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray
)

fun buildSpectrogramPixels(wav: PcmWavData, config: SpectrogramConfig = SpectrogramConfig()): SpectrogramPixels {
    val frameCount = pcmFrameCount(wav)
    if (frameCount <= 0) return SpectrogramPixels(1, 1, IntArray(1))

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
    val windowMagnitudeScale = (window.sum() / 2.0).coerceAtLeast(1.0)
    val maxAmp = maxAmplitude(wav.bitsPerSample).coerceAtLeast(1.0)

    val width = timeBins
    val height = maxFrequencyBins * wav.channels
    val pixels = IntArray(width * height)
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
                val magnitude = sqrt(real[fftBin] * real[fftBin] + imag[fftBin] * imag[fftBin]) / windowMagnitudeScale
                val floorDb = config.floorDb.coerceIn(-120.0, -24.0)
                val db = 20.0 * log10(magnitude.coerceAtLeast(1.0e-9)) + config.gainDb.coerceIn(-24.0, 36.0)
                val normalized = ((db - floorDb) / -floorDb).coerceIn(0.0, 1.0)
                val intensity = normalized.pow(1.0 / config.gamma.coerceIn(0.35, 3.0)).coerceIn(0.0, 1.0).toFloat()
                val y = channelTop + pixelRow
                pixels[y * width + timeIndex] = spectrogramColorArgb(intensity, config)
            }
        }
    }

    return SpectrogramPixels(width, height, pixels)
}

fun normalizeSpectrogramWindowSize(windowSize: Int): Int {
    val clamped = windowSize.coerceIn(256, 8192)
    val lowerPower = highestOneBit(clamped)
    return if (lowerPower == clamped) {
        clamped
    } else {
        val upperPower = lowerPower shl 1
        if (upperPower <= 8192 && upperPower - clamped < clamped - lowerPower) upperPower else lowerPower
    }
}

private fun sampleValue(wav: PcmWavData, frameIndex: Int, channel: Int): Double {
    if (frameIndex < 0 || channel !in 0 until wav.channels) return 0.0
    val frameCount = pcmFrameCount(wav)
    if (frameIndex >= frameCount) return 0.0
    val bytesPerSample = wav.bitsPerSample / 8
    val offset = frameIndex * wav.blockAlign + channel * bytesPerSample
    return readSignedSample(wav.pcmData, offset, wav.bitsPerSample).toDouble()
}

private fun spectrogramColorArgb(value: Float, config: SpectrogramConfig): Int {
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

private fun highestOneBit(value: Int): Int {
    var result = 1
    while (result <= value / 2) result = result shl 1
    return result
}
