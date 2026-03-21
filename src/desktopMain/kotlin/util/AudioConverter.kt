package util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import kotlin.math.abs
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/** 可选的采样率列表；null 表示"原采样率" */
val SAMPLE_RATE_OPTIONS: List<Float?> = listOf(null, 44100f, 48000f, 88200f, 96000f, 176400f, 192000f)

/** 可选位深列表；null 表示"原位深" */
val BIT_DEPTH_OPTIONS: List<Int?> = listOf(null, 16, 24, 32)

/** 默认位深索引（原位深） */
const val DEFAULT_BIT_DEPTH_INDEX = 0

val SUPPORTED_AUDIO_SOURCE_EXTENSIONS: Set<String> = setOf("mp3", "flac")
private const val SUPPORTED_AUDIO_SOURCE_LABEL = "MP3/FLAC"
private const val GENERATED_MERGED_TAG = "_merged"
private const val AUDIO_RATE_TOLERANCE = 0.5f

fun sampleRateLabel(rate: Float?): String = if (rate == null) "原采样率" else "${rate.toInt()} Hz"
fun bitDepthLabel(bits: Int?): String = when (bits) { null -> "原位深"; 32 -> "浮点 32 bit"; else -> "$bits bit" }
fun isSupportedAudioSource(file: File): Boolean = file.isFile && file.extension.lowercase() in SUPPORTED_AUDIO_SOURCE_EXTENSIONS

/**
 * 使用 Java Sound SPI 将目录下所有 MP3/FLAC 文件批量转换为 WAV。
 *
 * @param dir              需要扫描的根目录（递归子目录）
 * @param deleteOriginal   转换成功后是否删除原始音频，默认 true
 * @param targetSampleRate 目标采样率（Hz）；null = 保留原始采样率
 * @param targetBitDepth   目标位深（16 / 24 / 32），默认 16
 * @param onLog            日志回调
 * @param onProgress       进度回调 (已完成数, 总数, 当前文件名)
 */
suspend fun batchConvertAudioToWav(
    dir: File,
    deleteOriginal: Boolean = true,
    targetSampleRate: Float? = null,
    targetBitDepth: Int? = null,
    onLog: (String) -> Unit = {},
    onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
) = withContext(Dispatchers.IO) {
    validateTargetFormat(targetSampleRate, targetBitDepth)

    val sourceFiles = dir.walkTopDown()
        .filter(::isSupportedAudioSource)
        .toList()

    if (sourceFiles.isEmpty()) {
        onLog("未找到需要转换的 $SUPPORTED_AUDIO_SOURCE_LABEL 文件。")
        return@withContext
    }

    val rateDesc = sampleRateLabel(targetSampleRate)
    val depthDesc = bitDepthLabel(targetBitDepth)
    onLog("找到 ${sourceFiles.size} 个 $SUPPORTED_AUDIO_SOURCE_LABEL 文件，开始批量转换为 WAV ($rateDesc / $depthDesc)…")
    var successCount = 0
    var failCount = 0

    sourceFiles.forEachIndexed { index, sourceFile ->
        if (!isActive) return@withContext

        val wavFile = File(sourceFile.parentFile ?: dir, sourceFile.nameWithoutExtension + ".wav")
        onProgress(index, sourceFiles.size, sourceFile.name)

        try {
            convertAudioToWav(sourceFile, wavFile, targetSampleRate, targetBitDepth)
            successCount++
            if (deleteOriginal && !sourceFile.delete()) {
                onLog("[删除失败] ${sourceFile.name}")
            }
        } catch (e: Exception) {
            failCount++
            onLog("[转换失败] ${sourceFile.name}: ${e.message}")
            cleanupFileQuietly(wavFile)
            cleanupFileQuietly(tempSiblingOf(wavFile))
        }
    }

    onProgress(sourceFiles.size, sourceFiles.size, "")
    onLog("转换完成：成功 $successCount 个，失败 $failCount 个。")
}


/**
 * 将单个 MP3/FLAC 文件转换为 WAV 文件。
 * 使用 Java Sound SPI 提供的解码器（通过 SPI 自动注册）。
 *
 * @param targetSampleRate 目标采样率；null = 保留原始
 * @param targetBitDepth   目标位深（16 / 24 / 32），默认 16
 */
fun convertAudioToWav(
    sourceFile: File,
    wavFile: File,
    targetSampleRate: Float? = null,
    targetBitDepth: Int? = null
) {
    require(sourceFile.isFile) { "音频文件不存在：${sourceFile.absolutePath}" }
    require(isSupportedAudioSource(sourceFile)) {
        "不支持的音频格式：${sourceFile.extension.ifBlank { "<none>" }}，仅支持 ${SUPPORTED_AUDIO_SOURCE_EXTENSIONS.joinToString("/")}" }
    validateTargetFormat(targetSampleRate, targetBitDepth)
    wavFile.parentFile?.mkdirs()

    // 1. 打开音频输入流（mp3spi/jflac SPI 自动接管）
    AudioSystem.getAudioInputStream(sourceFile).use { sourceStream ->
        val baseFormat = sourceStream.format

        // mp3spi 对压缩格式报告的 sampleSizeInBits 为 NOT_SPECIFIED (-1)，
        // 需回退到合理默认值；mp3spi 始终将 MP3 解码为 PCM_SIGNED 16-bit。
        val naturalBits = baseFormat.sampleSizeInBits.takeIf { it > 0 } ?: 16
        val naturalRate = baseFormat.sampleRate.takeIf { it.isFinite() && it > 0f } ?: 44100f
        val channels    = baseFormat.channels.takeIf { it > 0 } ?: 2

        val outRate  = targetSampleRate ?: naturalRate
        val outBits  = targetBitDepth   ?: naturalBits
        val outFloat = targetBitDepth == 32   // 用户显式选了"浮点 32 bit"

        // 第一步：将压缩流解码为 PCM_SIGNED LE（固定用源采样率）。
        // mp3spi 只支持 PCM_SIGNED 16-bit 输出，不在此步骤指定目标格式，
        // 避免 SPI 因不支持的格式抛出异常。
        val naturalFmt = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, naturalRate, naturalBits,
            channels, channels * (naturalBits / 8), naturalRate, false
        )

        val tempFile = tempSiblingOf(wavFile)
        cleanupFileQuietly(tempFile)
        try {
            AudioSystem.getAudioInputStream(naturalFmt, sourceStream).use { pcm ->
                val sameRate  = approximatelyEquals(outRate, naturalRate)
                val sameBits  = outBits == naturalBits
                // 降位深：源高于目标且目标不是浮点 → 用算术右移缩放，避免溢出
                // Manual bit depth conversion logic (handle both up/down scaling)
                // Java Sound's specific PCM conversion capability varies, manual shifting is more reliable for 16<->24/32 integer PCM.
                val needsBitDepthChange = !outFloat && naturalBits != outBits

                when {
                    // 无需任何格式变换，直接写出
                    sameRate && !needsBitDepthChange && !outFloat ->
                        AudioSystem.write(pcm, AudioFileFormat.Type.WAVE, tempFile)

                    // 整数位深变换（含可选的采样率转换）：手动位移缩放
                    needsBitDepthChange -> {
                        val srcStream = if (!sameRate) {
                            val rsFmt = AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED, outRate, naturalBits,
                                channels, channels * (naturalBits / 8), outRate, false
                            )
                            AudioSystem.getAudioInputStream(rsFmt, pcm)
                        } else pcm

                        val targetFmt = AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED, outRate, outBits,
                            channels, channels * (outBits / 8), outRate, false
                        )
                        AudioInputStream(
                            BitDepthConversionInputStream(srcStream, naturalBits, outBits),
                            targetFmt,
                            AudioSystem.NOT_SPECIFIED.toLong() // Resampling/Conversion might change length, safest to use unknown
                        ).use { scaled ->
                            AudioSystem.write(scaled, AudioFileFormat.Type.WAVE, tempFile)
                        }
                    }

                    // 浮点转换或仅采样率变换（位深不变）：交给 Java Sound
                    else -> {
                        val enc = if (outFloat) AudioFormat.Encoding.PCM_FLOAT else AudioFormat.Encoding.PCM_SIGNED
                        val tgtFmt = AudioFormat(
                            enc, outRate, outBits,
                            channels, channels * (outBits / 8), outRate, false
                        )
                        AudioSystem.getAudioInputStream(tgtFmt, pcm).use { conv ->
                            AudioSystem.write(conv, AudioFileFormat.Type.WAVE, tempFile)
                        }
                    }
                }
            }
            replaceFile(tempFile, wavFile)
        } catch (e: Exception) {
            cleanupFileQuietly(tempFile)
            throw e
        }
    }
}


/**
 * 将目录下所有 WAV 文件按顺序合并为一个（或多个）WAV 文件。
 *
 * @param dir            WAV 文件所在目录（递归扫描）
 * @param maxPerFile     每个合并文件最多包含的源文件数；0 或负数表示全部合并为一个
 * @param deleteOriginal 合并成功后是否删除原始单个 WAV
 * @param onLog          日志回调
 * @param onProgress     进度回调 (已完成组数, 总组数, 当前输出文件名)
 */
suspend fun mergeWavFiles(
    dir: File,
    maxPerFile: Int = 0,
    deleteOriginal: Boolean = false,
    onLog: (String) -> Unit = {},
    onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
) = withContext(Dispatchers.IO) {
    val allWavFiles = dir.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() == "wav" }
        .sortedBy { it.name.lowercase() }
        .toList()

    val wavFiles = allWavFiles.filterNot(::isGeneratedMergedWav)
    val skippedMergedCount = allWavFiles.size - wavFiles.size
    if (skippedMergedCount > 0) {
        onLog("已跳过 $skippedMergedCount 个已生成的合并 WAV 文件。")
    }

    if (wavFiles.isEmpty()) {
        onLog("未找到需要合并的 WAV 文件。")
        return@withContext
    }

    val chunkSize = if (maxPerFile > 0) maxPerFile else wavFiles.size
    val chunks = wavFiles.chunked(chunkSize)
    onLog("共 ${wavFiles.size} 个 WAV，将合并为 ${chunks.size} 个文件（每组最多 $chunkSize 个）…")

    chunks.forEachIndexed { idx, chunk ->
        if (!isActive) return@withContext

        val suffix = if (chunks.size > 1) "${GENERATED_MERGED_TAG}_${idx + 1}" else GENERATED_MERGED_TAG
        val outFile = File(dir, "${dir.name}$suffix.wav")
        onProgress(idx, chunks.size, outFile.name)

        try {
            mergeWavChunk(chunk, outFile)
            onLog("已合并 → ${outFile.name}（${chunk.size} 个文件）")
            if (deleteOriginal) {
                chunk.forEach { wav ->
                    if (!wav.delete()) onLog("[删除失败] ${wav.name}")
                }
            }
        } catch (e: Exception) {
            onLog("[合并失败] ${outFile.name}: ${e.message}")
            cleanupFileQuietly(outFile)
            cleanupFileQuietly(tempSiblingOf(outFile))
        }
    }

    onProgress(chunks.size, chunks.size, "")
    onLog("合并完成。")
}

/**
 * 将一组 WAV 文件顺序拼接写入 [outFile]。
 * 以第一个文件的格式为基准，其余文件若格式不符则尝试转码后合并。
 */
private fun mergeWavChunk(files: List<File>, outFile: File) {
    require(files.isNotEmpty())
    outFile.parentFile?.mkdirs()

    val format: AudioFormat = AudioSystem.getAudioInputStream(files.first()).use { it.format }
    val streams = mutableListOf<AudioInputStream>()

    try {
        files.forEach { file ->
            val sourceStream = AudioSystem.getAudioInputStream(file)
            try {
                val stream = if (audioFormatEquals(sourceStream.format, format)) {
                    sourceStream
                } else {
                    AudioSystem.getAudioInputStream(format, sourceStream)
                }
                streams += stream
            } catch (e: Exception) {
                runCatching { sourceStream.close() }
                throw e
            }
        }

        val totalFrames = streams.fold(0L) { acc, stream ->
            if (acc == AudioSystem.NOT_SPECIFIED.toLong() || stream.frameLength == AudioSystem.NOT_SPECIFIED.toLong()) {
                AudioSystem.NOT_SPECIFIED.toLong()
            } else {
                acc + stream.frameLength
            }
        }

        val combinedInput = SequenceInputStream(Collections.enumeration(streams))
        val combined = AudioInputStream(combinedInput, format, totalFrames)
        val tempFile = tempSiblingOf(outFile)
        cleanupFileQuietly(tempFile)

        try {
            combined.use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, tempFile)
            }
            replaceFile(tempFile, outFile)
        } catch (e: Exception) {
            cleanupFileQuietly(tempFile)
            throw e
        }
    } finally {
        streams.forEach { runCatching { it.close() } }
    }
}

/** AudioFormat 没有重写 equals，手动比较各字段 */
private fun audioFormatEquals(a: AudioFormat, b: AudioFormat): Boolean =
    a.encoding == b.encoding &&
        approximatelyEquals(a.sampleRate, b.sampleRate) &&
        a.sampleSizeInBits == b.sampleSizeInBits &&
        a.channels == b.channels &&
        a.frameSize == b.frameSize &&
        approximatelyEquals(a.frameRate, b.frameRate) &&
        a.isBigEndian == b.isBigEndian

private fun validateTargetFormat(targetSampleRate: Float?, targetBitDepth: Int?) {
    require(targetBitDepth == null || targetBitDepth in BIT_DEPTH_OPTIONS) {
        "不支持的目标位深：$targetBitDepth，仅支持 ${BIT_DEPTH_OPTIONS.filterNotNull().joinToString("/")}"
    }
    require(targetSampleRate == null || (targetSampleRate.isFinite() && targetSampleRate > 0f)) {
        "目标采样率必须为正数：$targetSampleRate"
    }
}

private fun approximatelyEquals(a: Float, b: Float): Boolean {
    if (a == b) return true
    if (!a.isFinite() || !b.isFinite()) return a == b
    return abs(a - b) < AUDIO_RATE_TOLERANCE
}

private fun isGeneratedMergedWav(file: File): Boolean {
    val lowerName = file.name.lowercase()
    return lowerName.endsWith(".wav") && lowerName.contains(GENERATED_MERGED_TAG)
}

private fun tempSiblingOf(file: File): File =
    File(file.parentFile ?: File("."), ".${file.name}.tmp")

private fun cleanupFileQuietly(file: File) {
    if (file.exists()) runCatching { Files.deleteIfExists(file.toPath()) }
}

private fun replaceFile(tempFile: File, targetFile: File) {
    try {
        Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: IOException) {
        Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

/**
 * 将已解码的 PCM_SIGNED little-endian 流从 [sourceBits] 位深转换至 [targetBits]。
 *
 * - 降位深 (Downscale): 算术右移 (shr)，避免溢出。
 * - 升位深 (Upscale): 左移 (shl)，并在低位补零 (保持有效幅度)。
 * 仅适用于两者均为 8 的整数倍的场景。
 */
private class BitDepthConversionInputStream(
    private val source: AudioInputStream,
    private val sourceBits: Int,
    private val targetBits: Int
) : InputStream() {
    private val srcBytesPerSample = sourceBits / 8
    private val dstBytesPerSample = targetBits / 8
    private val channels = source.format.channels
    private val srcFrameBuf = ByteArray(channels * srcBytesPerSample)
    private val dstFrameBuf = ByteArray(channels * dstBytesPerSample)
    private var dstPos = dstFrameBuf.size // 初始设为末尾，触发首次填充

    // 计算位移量
    // 降位深: source > target, shift > 0 (右移)
    // 升位深: source < target, shift < 0 (左移 = shift absolute value)
    private val shift = sourceBits - targetBits

    override fun read(): Int {
        if (dstPos >= dstFrameBuf.size && !nextFrame()) return -1
        return dstFrameBuf[dstPos++].toInt() and 0xFF
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var written = 0
        while (written < len) {
            if (dstPos >= dstFrameBuf.size && !nextFrame()) return if (written == 0) -1 else written
            val toCopy = minOf(len - written, dstFrameBuf.size - dstPos)
            System.arraycopy(dstFrameBuf, dstPos, buf, off + written, toCopy)
            dstPos += toCopy
            written += toCopy
        }
        return written
    }

    private fun nextFrame(): Boolean {
        var read = 0
        while (read < srcFrameBuf.size) {
            val n = source.read(srcFrameBuf, read, srcFrameBuf.size - read)
            if (n < 0) return false
            read += n
        }
        for (ch in 0 until channels) {
            val sample = readSignedLE(srcFrameBuf, ch * srcBytesPerSample, sourceBits)
            val converted = if (shift > 0) {
                sample shr shift // Downscale
            } else {
                sample shl (-shift) // Upscale
            }
            writeSignedLE(dstFrameBuf, ch * dstBytesPerSample, converted, targetBits)
        }
        dstPos = 0
        return true
    }

    private fun readSignedLE(b: ByteArray, off: Int, bits: Int): Int {
        val bytes = bits / 8
        var result = 0
        for (i in 0 until bytes - 1) {
            result = result or ((b[off + i].toInt() and 0xFF) shl (i * 8))
        }
        return result or (b[off + bytes - 1].toInt() shl ((bytes - 1) * 8)) // MSB 保留符号
    }

    private fun writeSignedLE(b: ByteArray, off: Int, value: Int, bits: Int) {
        val bytes = bits / 8
        for (i in 0 until bytes) {
            b[off + i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
    }

    override fun close() = source.close()
}
