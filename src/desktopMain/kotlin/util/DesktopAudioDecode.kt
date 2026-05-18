package util

import java.io.File
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
        false
    )
    runCatching {
        writeDecodedDesktopPcm(source, pcmFormat, target)
    }.getOrElse { firstError ->
        val fallback = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false
        )
        runCatching { target.delete() }
        runCatching {
            writeDecodedDesktopPcm(source, fallback, target)
        }.getOrElse { throw firstError }
    }
}

internal fun openDesktopAudioInputStream(source: File) = AudioSystem.getAudioInputStream(source)

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
        AudioSystem.getAudioInputStream(format, input).use { pcm ->
            writeWavStream(pcm, target)
        }
    }
}
