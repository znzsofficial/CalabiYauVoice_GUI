package util

import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader
import java.io.BufferedInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

internal fun openDesktopStreamingAudioInputStream(
    source: BufferedInputStream,
    fileName: String?
): AudioInputStream {
    val extension = fileName
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('.', "")
        ?.lowercase()

    return if (extension == "mp3" || source.hasMp3Signature()) {
        MpegAudioFileReader().getAudioInputStream(source)
    } else {
        AudioSystem.getAudioInputStream(source)
    }
}

internal fun BufferedInputStream.hasMp3Signature(): Boolean {
    mark(10)
    return try {
        val header = ByteArray(3)
        val count = read(header)
        if (count != header.size) return false
        if (header.contentEquals(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))) return true

        val first = header[0].toInt() and 0xff
        val second = header[1].toInt() and 0xff
        val third = header[2].toInt() and 0xff
        val version = (second ushr 3) and 0x03
        val layer = (second ushr 1) and 0x03
        val bitrate = (third ushr 4) and 0x0f
        val sampleRate = (third ushr 2) and 0x03
        first == 0xff && (second and 0xe0) == 0xe0 &&
            version != 1 && layer != 0 && bitrate !in setOf(0, 15) && sampleRate != 3
    } finally {
        reset()
    }
}
