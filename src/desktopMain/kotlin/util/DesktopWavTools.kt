package util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.awt.image.BufferedImage
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

enum class DesktopWavTrimMode(val label: String) {
    BOTH("裁剪头尾"),
    START_ONLY("只裁开头"),
    END_ONLY("只裁结尾")
}

enum class DesktopWavChannelMode(val label: String) {
    MONO_TO_STEREO("单转双"),
    STEREO_TO_MONO("双转单")
}

enum class DesktopWavVolumeMode(val label: String) {
    GAIN("调节音量"),
    NORMALIZE("峰值标准化")
}

private const val MAX_IN_MEMORY_PCM_BYTES = 768L * 1024L * 1024L

data class DesktopWavMeta(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val durationMs: Long,
    val peakPercent: Double
)

data class DesktopSpectrogramConfig(
    val windowSize: Int = 1024,
    val hopRatio: Float = 0.25f,
    val maxTimeBins: Int = 720,
    val maxFrequencyBins: Int = 256,
    val cutoffFrequencyHz: Int = 0,
    val lowColorArgb: Int = 0xFF0F172A.toInt(),
    val highColorArgb: Int = 0xFF38BDF8.toInt()
)

internal data class PcmWavData(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val blockAlign: Int,
    val pcmData: ByteArray
)

fun inspectDesktopWav(file: File): DesktopWavMeta {
    val wav = readPcmWav(file) ?: error("仅支持 PCM WAV")
    return inspectDesktopWav(wav)
}

internal fun inspectDesktopWav(wav: PcmWavData): DesktopWavMeta {
    val frames = wav.pcmData.size / wav.blockAlign
    return DesktopWavMeta(
        channels = wav.channels,
        sampleRate = wav.sampleRate,
        bitsPerSample = wav.bitsPerSample,
        durationMs = frames * 1000L / wav.sampleRate,
        peakPercent = calculatePeakPercent(wav)
    )
}

fun trimDesktopWavSilence(
    inputFile: File,
    outputDir: File,
    thresholdRatio: Double,
    minSilenceMs: Int,
    mode: DesktopWavTrimMode
): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) error("WAV 没有可处理的采样")
    val silenceFrames = (wav.sampleRate * minSilenceMs / 1000.0).roundToInt().coerceAtLeast(1)
    var startFrame = 0
    var silentRun = 0
    for (frame in 0 until frameCount) {
        if (framePeak(wav, frame) <= thresholdRatio) {
            silentRun++
            if (silentRun >= silenceFrames) startFrame = frame + 1
        } else {
            break
        }
    }
    var endFrame = frameCount
    silentRun = 0
    for (frame in frameCount - 1 downTo 0) {
        if (framePeak(wav, frame) <= thresholdRatio) {
            silentRun++
            if (silentRun >= silenceFrames) endFrame = frame
        } else {
            break
        }
    }
    if (mode == DesktopWavTrimMode.END_ONLY) startFrame = 0
    if (mode == DesktopWavTrimMode.START_ONLY) endFrame = frameCount
    if (endFrame <= startFrame) error("裁剪参数过强，未保留有效音频")
    val out = uniqueFile(outputDir, inputFile.nameWithoutExtension + "_trimmed", "wav")
    val from = startFrame * wav.blockAlign
    val to = endFrame * wav.blockAlign
    writePcmWav(out, wav.pcmData.copyOfRange(from, to), wav.channels, wav.sampleRate, wav.bitsPerSample)
    return out
}

fun convertDesktopWavChannels(inputFile: File, outputDir: File, mode: DesktopWavChannelMode): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = when (mode) {
        DesktopWavChannelMode.MONO_TO_STEREO -> {
            require(wav.channels == 1) { "源文件不是单声道" }
            val bytesPerSample = wav.bitsPerSample / 8
            ByteArrayOutputStream(wav.pcmData.size * 2).use { out ->
                var offset = 0
                while (offset < wav.pcmData.size) {
                    out.write(wav.pcmData, offset, bytesPerSample)
                    out.write(wav.pcmData, offset, bytesPerSample)
                    offset += bytesPerSample
                }
                out.toByteArray() to 2
            }
        }
        DesktopWavChannelMode.STEREO_TO_MONO -> {
            require(wav.channels == 2) { "源文件不是双声道" }
            val bytesPerSample = wav.bitsPerSample / 8
            ByteArrayOutputStream(wav.pcmData.size / 2).use { out ->
                var offset = 0
                while (offset + bytesPerSample * 2 <= wav.pcmData.size) {
                    val left = readSignedLE(wav.pcmData, offset, wav.bitsPerSample)
                    val right = readSignedLE(wav.pcmData, offset + bytesPerSample, wav.bitsPerSample)
                    writeSignedLE(out, ((left.toLong() + right.toLong()) / 2).toInt(), wav.bitsPerSample)
                    offset += bytesPerSample * 2
                }
                out.toByteArray() to 1
            }
        }
    }
    val out = uniqueFile(outputDir, inputFile.nameWithoutExtension + "_channels", "wav")
    writePcmWav(out, output.first, output.second, wav.sampleRate, wav.bitsPerSample)
    return out
}

fun adjustDesktopWavVolume(
    inputFile: File,
    outputDir: File,
    mode: DesktopWavVolumeMode,
    gainPercent: Int,
    targetPeakPercent: Int
): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val peak = peakAmplitude(wav)
    val maxAmp = maxAmplitude(wav.bitsPerSample)
    val multiplier = when (mode) {
        DesktopWavVolumeMode.GAIN -> gainPercent.coerceAtLeast(0) / 100.0
        DesktopWavVolumeMode.NORMALIZE -> if (peak <= 0.0) 1.0 else (targetPeakPercent.coerceIn(1, 100) / 100.0 * maxAmp) / peak
    }
    val out = uniqueFile(outputDir, inputFile.nameWithoutExtension + "_volume", "wav")
    writePcmWav(out, scalePcmData(wav, multiplier), wav.channels, wav.sampleRate, wav.bitsPerSample)
    return out
}

suspend fun buildDesktopSpectrogramImage(
    inputFile: File,
    config: DesktopSpectrogramConfig = DesktopSpectrogramConfig()
): BufferedImage {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    return buildDesktopSpectrogramImage(wav, config)
}

internal suspend fun buildDesktopSpectrogramImage(
    wav: PcmWavData,
    config: DesktopSpectrogramConfig = DesktopSpectrogramConfig()
): BufferedImage {
    val frameCount = wav.pcmData.size / wav.blockAlign
    if (frameCount <= 0) return BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

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

    val image = BufferedImage(timeBins, maxFrequencyBins * wav.channels, BufferedImage.TYPE_INT_ARGB)
    val real = DoubleArray(windowSize)
    val imag = DoubleArray(windowSize)

    for (channel in 0 until wav.channels) {
        coroutineContext.ensureActive()
        val channelTop = channel * maxFrequencyBins
        for (timeIndex in 0 until timeBins) {
            coroutineContext.ensureActive()
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
                image.setRGB(timeIndex, channelTop + pixelRow, spectrogramColorArgb(intensity, config))
            }
        }
    }
    return image
}

internal fun readPcmWav(inputFile: File): PcmWavData? {
    RandomAccessFile(inputFile, "r").use { raf ->
        if (raf.length() < 44) return null
        if (raf.readAscii(4) != "RIFF") return null
        raf.skipBytes(4)
        if (raf.readAscii(4) != "WAVE") return null
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var data: ByteArray? = null
        while (raf.filePointer + 8 <= raf.length()) {
            val chunkId = raf.readAscii(4)
            val chunkSize = raf.readLittleUnsignedInt()
            val chunkStart = raf.filePointer
            when (chunkId) {
                "fmt " -> {
                    val audioFormat = raf.readLittleShort()
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
                    } else if (audioFormat != 1) {
                        return null
                    }
                }
                "data" -> {
                    if (chunkSize > Int.MAX_VALUE) error("WAV data chunk 过大，暂不支持超过 2GB 的音频")
                    if (chunkSize > MAX_IN_MEMORY_PCM_BYTES) error("WAV data chunk 过大，当前工具一次性处理上限为 ${MAX_IN_MEMORY_PCM_BYTES / 1024 / 1024}MB")
                    data = ByteArray(chunkSize.toInt()).also { raf.readFully(it) }
                }
            }
            val next = chunkStart + chunkSize + (chunkSize and 1)
            if (next < chunkStart || next > raf.length()) return null
            if (next > raf.filePointer) raf.seek(next)
        }
        val pcm = data ?: return null
        if (channels !in 1..2 || sampleRate <= 0 || bitsPerSample !in listOf(8, 16, 24, 32) || blockAlign <= 0) return null
        return PcmWavData(channels, sampleRate, bitsPerSample, blockAlign, pcm)
    }
}

private fun framePeak(wav: PcmWavData, frameIndex: Int): Double {
    val bytesPerSample = wav.bitsPerSample / 8
    val base = frameIndex * wav.blockAlign
    var peak = 0.0
    for (channel in 0 until wav.channels) {
        val value = abs(readSignedLE(wav.pcmData, base + channel * bytesPerSample, wav.bitsPerSample).toDouble()) / maxAmplitude(wav.bitsPerSample)
        if (value > peak) peak = value
    }
    return peak
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
    return readSignedLE(wav.pcmData, frameIndex * wav.blockAlign + channel * bytesPerSample, wav.bitsPerSample).toDouble()
}

private fun spectrogramColorArgb(value: Float, config: DesktopSpectrogramConfig): Int {
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

private fun calculatePeakPercent(wav: PcmWavData): Double = peakAmplitude(wav) / maxAmplitude(wav.bitsPerSample) * 100.0

private fun peakAmplitude(wav: PcmWavData): Double {
    val bytesPerSample = wav.bitsPerSample / 8
    var peak = 0.0
    var offset = 0
    while (offset + bytesPerSample <= wav.pcmData.size) {
        peak = max(peak, abs(readSignedLE(wav.pcmData, offset, wav.bitsPerSample).toDouble()))
        offset += bytesPerSample
    }
    return peak
}

private fun scalePcmData(wav: PcmWavData, multiplier: Double): ByteArray {
    val bytesPerSample = wav.bitsPerSample / 8
    val maxValue = maxSignedValue(wav.bitsPerSample)
    val minValue = minSignedValue(wav.bitsPerSample)
    val out = ByteArray(wav.pcmData.size)
    var offset = 0
    while (offset + bytesPerSample <= wav.pcmData.size) {
        val scaled = (readSignedLE(wav.pcmData, offset, wav.bitsPerSample) * multiplier).roundToInt().coerceIn(minValue, maxValue)
        writeSignedLE(out, offset, scaled, wav.bitsPerSample)
        offset += bytesPerSample
    }
    return out
}

private fun maxAmplitude(bits: Int): Double = maxSignedValue(bits).toDouble()
private fun maxSignedValue(bits: Int): Int = if (bits == 32) Int.MAX_VALUE else (1 shl (bits - 1)) - 1
private fun minSignedValue(bits: Int): Int = if (bits == 32) Int.MIN_VALUE else -(1 shl (bits - 1))

private fun readSignedLE(data: ByteArray, offset: Int, bits: Int): Int {
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

private fun writeSignedLE(data: ByteArray, offset: Int, value: Int, bits: Int) {
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

private fun writeSignedLE(out: ByteArrayOutputStream, value: Int, bits: Int) {
    val bytes = ByteArray(bits / 8)
    writeSignedLE(bytes, 0, value, bits)
    out.write(bytes)
}

private fun writePcmWav(file: File, pcmData: ByteArray, channels: Int, sampleRate: Int, bitsPerSample: Int) {
    file.parentFile?.mkdirs()
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val riffSize = 36L + pcmData.size.toLong()
    require(riffSize <= 0xFFFF_FFFFL) { "WAV 文件过大，RIFF size 超过 4GB" }
    ByteArrayOutputStream().use { out ->
        out.writeAscii("RIFF")
        out.writeLittleInt(riffSize.toInt())
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeLittleInt(16)
        out.writeLittleShort(1)
        out.writeLittleShort(channels)
        out.writeLittleInt(sampleRate)
        out.writeLittleInt(byteRate)
        out.writeLittleShort(blockAlign)
        out.writeLittleShort(bitsPerSample)
        out.writeAscii("data")
        out.writeLittleInt(pcmData.size)
        out.write(pcmData)
        file.writeBytes(out.toByteArray())
    }
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

private fun RandomAccessFile.readAscii(length: Int): String = ByteArray(length).also { readFully(it) }.decodeToString()
private fun RandomAccessFile.readLittleInt(): Int = Integer.reverseBytes(readInt())
private fun RandomAccessFile.readLittleUnsignedInt(): Long = readLittleInt().toLong() and 0xFFFF_FFFFL
private fun RandomAccessFile.readLittleShort(): Int = java.lang.Short.reverseBytes(readShort()).toInt() and 0xFFFF
private fun ByteArray.isPcmSubFormatGuid(): Boolean = contentEquals(
    byteArrayOf(
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00,
        0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )
)
private fun ByteArrayOutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))
private fun ByteArrayOutputStream.writeLittleInt(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}
private fun ByteArrayOutputStream.writeLittleShort(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}
