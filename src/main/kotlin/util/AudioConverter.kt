package util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/** 可选的采样率列表；null 表示"原采样率" */
val SAMPLE_RATE_OPTIONS: List<Float?> = listOf(null, 44100f, 48000f, 88200f, 96000f, 176400f, 192000f)

/** 可选位深列表（16-bit / 24-bit / 浮点32-bit） */
val BIT_DEPTH_OPTIONS: List<Int> = listOf(16, 24, 32)

/** 默认位深索引（16-bit） */
const val DEFAULT_BIT_DEPTH_INDEX = 0

fun sampleRateLabel(rate: Float?): String = if (rate == null) "原采样率" else "${rate.toInt()} Hz"
fun bitDepthLabel(bits: Int): String = if (bits == 32) "浮点 32 bit" else "$bits bit"

/**
 * 使用 soundlibs (mp3spi + tritonus-share) 将目录下所有 MP3 文件批量转换为 WAV。
 *
 * @param dir              需要扫描的根目录（递归子目录）
 * @param deleteOriginal   转换成功后是否删除原 MP3，默认 true
 * @param targetSampleRate 目标采样率（Hz）；null = 保留原始采样率
 * @param targetBitDepth   目标位深（8 / 16 / 24 / 32），默认 16
 * @param onLog            日志回调
 * @param onProgress       进度回调 (已完成数, 总数, 当前文件名)
 */
suspend fun batchConvertMp3ToWav(
    dir: File,
    deleteOriginal: Boolean = true,
    targetSampleRate: Float? = null,
    targetBitDepth: Int = 16,
    onLog: (String) -> Unit = {},
    onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
) = withContext(Dispatchers.IO) {
    val mp3Files = dir.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() == "mp3" }
        .toList()

    if (mp3Files.isEmpty()) {
        onLog("未找到需要转换的 MP3 文件。")
        return@withContext
    }

    val rateDesc = sampleRateLabel(targetSampleRate)
    val depthDesc = bitDepthLabel(targetBitDepth)
    onLog("找到 ${mp3Files.size} 个 MP3 文件，开始批量转换为 WAV ($rateDesc / $depthDesc)…")
    var successCount = 0
    var failCount = 0

    mp3Files.forEachIndexed { index, mp3File ->
        if (!isActive) return@withContext

        val wavFile = File(mp3File.parent, mp3File.nameWithoutExtension + ".wav")
        onProgress(index, mp3Files.size, mp3File.name)

        try {
            convertMp3ToWav(mp3File, wavFile, targetSampleRate, targetBitDepth)
            successCount++
            if (deleteOriginal) mp3File.delete()
        } catch (e: Exception) {
            failCount++
            onLog("[转换失败] ${mp3File.name}: ${e.message}")
            if (wavFile.exists() && wavFile.length() == 0L) wavFile.delete()
        }
    }

    onProgress(mp3Files.size, mp3Files.size, "")
    onLog("转换完成：成功 $successCount 个，失败 $failCount 个。")
}

/**
 * 将单个 MP3 文件转换为 WAV 文件。
 * 使用 mp3spi 提供的 MpegAudioFileReader（通过 Java SPI 自动注册）。
 *
 * @param targetSampleRate 目标采样率；null = 保留原始
 * @param targetBitDepth   目标位深（8 / 16 / 24 / 32），默认 16
 */
fun convertMp3ToWav(
    mp3File: File,
    wavFile: File,
    targetSampleRate: Float? = null,
    targetBitDepth: Int = 16
) {
    // 1. 打开 MP3 输入流（mp3spi SPI 自动接管）
    val mp3Stream: AudioInputStream = AudioSystem.getAudioInputStream(mp3File)
    val baseFormat: AudioFormat = mp3Stream.format

    val sampleRate = targetSampleRate ?: baseFormat.sampleRate
    val channels = baseFormat.channels

    // 2. 构造目标 PCM 格式（32-bit 使用浮点编码）
    val encoding = if (targetBitDepth == 32) AudioFormat.Encoding.PCM_FLOAT else AudioFormat.Encoding.PCM_SIGNED
    val pcmFormat = AudioFormat(
        encoding,
        sampleRate,
        targetBitDepth,
        channels,
        channels * (targetBitDepth / 8),
        sampleRate,
        false   // little-endian
    )

    // 3. 解码为 PCM 流
    val pcmStream: AudioInputStream = AudioSystem.getAudioInputStream(pcmFormat, mp3Stream)

    // 4. 写出 WAV
    try {
        AudioSystem.write(pcmStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile)
    } finally {
        pcmStream.close()
        mp3Stream.close()
    }
}

