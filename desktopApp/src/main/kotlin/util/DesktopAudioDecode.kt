package util

import jna.flac.openNativeFlacPcmStream
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

internal val SUPPORTED_PCM_BITS = setOf(8, 16, 24, 32)

internal fun normalizedPcmBits(bits: Int): Int = bits.takeIf { it in SUPPORTED_PCM_BITS } ?: 16

internal fun normalizedPcmSampleRate(sampleRate: Float): Float = sampleRate.takeIf { it.isFinite() && it > 0f } ?: 44100f

internal fun normalizedPcmChannels(channels: Int): Int = channels.takeIf { it > 0 } ?: 2

internal fun decodeDesktopAudioToPcmWav(source: File, target: File) {
    val sourceFormat = openDesktopAudioInputStream(source).use { input -> input.format }
    val sampleRate = normalizedPcmSampleRate(sourceFormat.sampleRate)
    val channels = normalizedPcmChannels(sourceFormat.channels)
    val bits = normalizedPcmBits(sourceFormat.sampleSizeInBits)
    val pcmFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate,
        bits,
        channels,
        channels * (bits / 8),
        sampleRate,
        sourceFormat.isBigEndian
    )
    try {
        writeDecodedDesktopPcmTransactional(source, pcmFormat, target)
    } catch (firstError: Throwable) {
        if (firstError is InterruptedIOException || Thread.currentThread().isInterrupted) {
            throw firstError
        }
        val fallback = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            sourceFormat.isBigEndian
        )
        try {
            writeDecodedDesktopPcmTransactional(source, fallback, target)
        } catch (fallbackError: Throwable) {
            fallbackError.addSuppressed(firstError)
            throw fallbackError
        }
    }
}

internal fun openDesktopAudioInputStream(source: File) =
    if (source.extension.equals("flac", ignoreCase = true)) {
        openNativeFlacPcmStream(source.inputStream().buffered())
    } else if (source.extension.equals("mp3", ignoreCase = true)) {
        MpegAudioFileReader().getAudioInputStream(source)
    } else {
        AudioSystem.getAudioInputStream(source)
    }

internal fun writeWavStream(stream: javax.sound.sampled.AudioInputStream, target: File) {
    target.parentFile?.mkdirs()
    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target)
}

internal fun uniqueSiblingFile(directory: File, baseName: String, extension: String): File {
    directory.mkdirs()
    val ext = extension.trimStart('.')
    var candidate = File(directory, "$baseName.$ext")
    var index = 2
    while (candidate.exists()) {
        candidate = File(directory, "$baseName ($index).$ext")
        index++
    }
    return candidate
}

private fun writeDecodedDesktopPcm(source: File, format: AudioFormat, target: File) {
    openDesktopAudioInputStream(source).use { input ->
        val decoded = if (input.format.matches(format)) input else AudioSystem.getAudioInputStream(format, input)
        decoded.use {
            if (format.isBigEndian && format.sampleSizeInBits > 8) {
                val littleEndian = AudioFormat(
                    format.encoding,
                    format.sampleRate,
                    format.sampleSizeInBits,
                    format.channels,
                    format.frameSize,
                    format.frameRate,
                    false
                )
                AudioSystem.getAudioInputStream(littleEndian, decoded).use { pcm ->
                    writeWavStream(pcm, target)
                }
            } else {
                writeWavStream(decoded, target)
            }
        }
    }
}

internal fun AudioFormat.matches(other: AudioFormat): Boolean =
    encoding == other.encoding &&
        sampleRate == other.sampleRate &&
        sampleSizeInBits == other.sampleSizeInBits &&
        channels == other.channels &&
        frameSize == other.frameSize &&
        frameRate == other.frameRate &&
        isBigEndian == other.isBigEndian

private fun writeDecodedDesktopPcmTransactional(source: File, format: AudioFormat, target: File) {
    val parent = target.absoluteFile.parentFile ?: error("输出文件没有父目录")
    parent.mkdirs()
    val temp = Files.createTempFile(parent.toPath(), ".${target.name}-", ".tmp").toFile()
    try {
        writeDecodedDesktopPcm(source, format, temp)
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Audio decoding was interrupted")
        replaceDesktopAudioFile(temp, target)
    } finally {
        Files.deleteIfExists(temp.toPath())
    }
}

private fun replaceDesktopAudioFile(temp: File, target: File) {
    try {
        Files.move(
            temp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: IOException) {
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
