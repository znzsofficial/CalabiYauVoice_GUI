package util

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
import com.nekolaska.calabiyau.core.media.audio.removePcmWavDcOffset
import com.nekolaska.calabiyau.core.media.audio.trimPcmWavSilence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.awt.image.BufferedImage

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

enum class DesktopWavPhaseMode(val label: String) {
    INVERT_ALL("整体反相"),
    INVERT_LEFT("左声道反相"),
    INVERT_RIGHT("右声道反相"),
    SWAP_STEREO("左右互换")
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
    val highColorArgb: Int = 0xFF38BDF8.toInt(),
    val gainDb: Double = 0.0,
    val gamma: Double = 1.0,
    val floorDb: Double = -60.0
)

private fun DesktopSpectrogramConfig.toSharedConfig(): SpectrogramConfig = SpectrogramConfig(
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
        peakPercent = calculatePcmPeakPercent(wav)
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
    val output = trimPcmWavSilence(
        wav,
        thresholdRatio,
        minSilenceMs,
        when (mode) {
            DesktopWavTrimMode.BOTH -> PcmWavTrimMode.BOTH
            DesktopWavTrimMode.START_ONLY -> PcmWavTrimMode.START_ONLY
            DesktopWavTrimMode.END_ONLY -> PcmWavTrimMode.END_ONLY
        }
    ) ?: error("裁剪参数过强，未保留有效音频")
    val out = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_trimmed", "wav")
    writePcmWav(out, output.wav.pcmData, output.wav.channels, output.wav.sampleRate, output.wav.bitsPerSample)
    return out
}

fun convertDesktopWavChannels(inputFile: File, outputDir: File, mode: DesktopWavChannelMode): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = convertPcmWavChannels(
        wav,
        when (mode) {
            DesktopWavChannelMode.MONO_TO_STEREO -> PcmWavChannelMode.MONO_TO_STEREO
            DesktopWavChannelMode.STEREO_TO_MONO -> PcmWavChannelMode.STEREO_TO_MONO
        }
    ) ?: error("当前声道不支持所选转换")
    val out = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_channels", "wav")
    writePcmWav(out, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
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
    val output = adjustPcmWavVolume(
        wav,
        when (mode) {
            DesktopWavVolumeMode.GAIN -> PcmWavVolumeMode.GAIN
            DesktopWavVolumeMode.NORMALIZE -> PcmWavVolumeMode.NORMALIZE
        },
        gainPercent,
        targetPeakPercent
    )?.wav ?: wav
    val out = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_volume", "wav")
    writePcmWav(out, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return out
}

fun applyDesktopWavNoiseGate(
    inputFile: File,
    outputDir: File,
    thresholdPercent: Double,
    reductionDb: Double
): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = applyPcmWavNoiseGate(wav, thresholdPercent, reductionDb) ?: error("WAV 没有可处理的采样")
    val outFile = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_gate", "wav")
    writePcmWav(outFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return outFile
}

fun applyDesktopWavFade(
    inputFile: File,
    outputDir: File,
    fadeInMs: Int,
    fadeOutMs: Int
): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = applyPcmWavFade(wav, fadeInMs, fadeOutMs) ?: error("WAV 没有可处理的采样")
    val outFile = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_fade", "wav")
    writePcmWav(outFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return outFile
}

fun removeDesktopWavDcOffset(inputFile: File, outputDir: File): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = removePcmWavDcOffset(wav) ?: error("WAV 没有可处理的采样")
    val outFile = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_dc", "wav")
    writePcmWav(outFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return outFile
}

fun applyDesktopWavPhase(inputFile: File, outputDir: File, mode: DesktopWavPhaseMode): File {
    val wav = readPcmWav(inputFile) ?: error("仅支持 PCM WAV")
    val output = applyPcmWavPhase(
        wav,
        when (mode) {
            DesktopWavPhaseMode.INVERT_ALL -> PcmWavPhaseMode.INVERT_ALL
            DesktopWavPhaseMode.INVERT_LEFT -> PcmWavPhaseMode.INVERT_LEFT
            DesktopWavPhaseMode.INVERT_RIGHT -> PcmWavPhaseMode.INVERT_RIGHT
            DesktopWavPhaseMode.SWAP_STEREO -> PcmWavPhaseMode.SWAP_STEREO
        }
    ) ?: error("WAV 没有可处理的采样")
    val outFile = uniqueSiblingFile(outputDir, inputFile.nameWithoutExtension + "_phase", "wav")
    writePcmWav(outFile, output.pcmData, output.channels, output.sampleRate, output.bitsPerSample)
    return outFile
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
    val pixels = buildSpectrogramPixels(wav, config.toSharedConfig())
    val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width)
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
        val expectedBlockAlign = expectedPcmBlockAlign(channels, bitsPerSample) ?: return null
        if (sampleRate <= 0 || blockAlign != expectedBlockAlign) return null
        val alignedLength = pcm.size - (pcm.size % blockAlign)
        if (alignedLength <= 0) return null
        val alignedPcm = if (alignedLength == pcm.size) pcm else pcm.copyOf(alignedLength)
        return PcmWavData(channels, sampleRate, bitsPerSample, blockAlign, alignedPcm)
    }
}

private fun writePcmWav(file: File, pcmData: ByteArray, channels: Int, sampleRate: Int, bitsPerSample: Int) {
    writePcmWav(file, pcmData, 0, pcmData.size, channels, sampleRate, bitsPerSample)
}

private fun writePcmWav(
    file: File,
    pcmData: ByteArray,
    offset: Int,
    length: Int,
    channels: Int,
    sampleRate: Int,
    bitsPerSample: Int
) {
    file.parentFile?.mkdirs()
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    require(expectedPcmBlockAlign(channels, bitsPerSample) == blockAlign && sampleRate > 0) { "无效的 PCM WAV 参数" }
    require(offset >= 0 && length >= 0 && offset + length <= pcmData.size && length % blockAlign == 0) { "PCM 数据不是完整采样帧" }
    val riffSize = 36L + length.toLong()
    require(riffSize <= 0xFFFF_FFFFL) { "WAV 文件过大，RIFF size 超过 4GB" }
    FileOutputStream(file).use { fileOut ->
        val out = ByteArrayOutputStream(44)
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
        out.writeLittleInt(length)
        fileOut.write(out.toByteArray())
        fileOut.write(pcmData, offset, length)
    }
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
