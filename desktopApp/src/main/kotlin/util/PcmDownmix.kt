package util

import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream

/** Converts interleaved 16-bit little-endian PCM with more than two channels to stereo. */
internal fun AudioInputStream.downmixToStereo(): AudioInputStream {
    require(format.encoding == AudioFormat.Encoding.PCM_SIGNED)
    require(format.sampleSizeInBits == 16)
    require(!format.isBigEndian)
    require(format.channels > 2)

    val targetFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        format.sampleRate,
        16,
        2,
        4,
        format.sampleRate,
        false
    )
    return AudioInputStream(
        PcmDownmixInputStream(this, format.channels),
        targetFormat,
        frameLength
    )
}

private class PcmDownmixInputStream(
    private val source: InputStream,
    private val sourceChannels: Int
) : InputStream() {
    private val sourceFrame = ByteArray(sourceChannels * 2)
    private val outputFrame = ByteArray(4)
    private var outputOffset = outputFrame.size

    override fun read(): Int {
        val oneByte = ByteArray(1)
        return if (read(oneByte, 0, 1) == -1) -1 else oneByte[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > buffer.size - offset) throw IndexOutOfBoundsException()
        if (length == 0) return 0

        var written = 0
        while (written < length) {
            if (outputOffset == outputFrame.size) {
                if (!readSourceFrame()) return if (written == 0) -1 else written
            }
            val count = minOf(length - written, outputFrame.size - outputOffset)
            outputFrame.copyInto(buffer, offset + written, outputOffset, outputOffset + count)
            outputOffset += count
            written += count
        }
        return written
    }

    override fun close() = source.close()

    private fun readSourceFrame(): Boolean {
        var received = 0
        while (received < sourceFrame.size) {
            val count = source.read(sourceFrame, received, sourceFrame.size - received)
            if (count == -1) return false
            if (count == 0) continue
            received += count
        }

        var leftSum = 0L
        var rightSum = 0L
        var leftChannels = 0
        var rightChannels = 0
        for (channel in 0 until sourceChannels) {
            val index = channel * 2
            val sample = ((sourceFrame[index].toInt() and 0xff) or (sourceFrame[index + 1].toInt() shl 8)).toShort().toLong()
            if (channel % 2 == 0) {
                leftSum += sample
                leftChannels++
            } else {
                rightSum += sample
                rightChannels++
            }
        }
        val left = (leftSum / leftChannels).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val right = (rightSum / rightChannels.coerceAtLeast(1)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        outputFrame[0] = left.toByte()
        outputFrame[1] = (left shr 8).toByte()
        outputFrame[2] = right.toByte()
        outputFrame[3] = (right shr 8).toByte()
        outputOffset = 0
        return true
    }
}
