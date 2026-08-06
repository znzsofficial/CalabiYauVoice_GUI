package util

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PcmDownmixTest {
    @Test
    fun downmixesAllChannelsToStereoLittleEndianPcm() {
        val sourceFormat = AudioFormat(48_000f, 16, 4, true, false)
        val sourceBytes = byteArrayOf(
            0xE8.toByte(), 0x03, 0xD0.toByte(), 0x07, 0xB8.toByte(), 0x0B, 0xA0.toByte(), 0x0F,
            0x18.toByte(), 0xFC.toByte(), 0x30, 0xF8.toByte(), 0x48, 0xF4.toByte(), 0x60, 0xF0.toByte()
        )
        AudioInputStream(ByteArrayInputStream(sourceBytes), sourceFormat, 2).use { source ->
            source.downmixToStereo().use { downmixed ->
                assertEquals(2, downmixed.format.channels)
                assertEquals(48_000f, downmixed.format.sampleRate)
                assertContentEquals(
                    byteArrayOf(0xD0.toByte(), 0x07, 0xB8.toByte(), 0x0B, 0x30, 0xF8.toByte(), 0x48, 0xF4.toByte()),
                    downmixed.readAllBytes()
                )
            }
        }
    }
}
