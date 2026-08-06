package jna.flac

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.sound.sampled.AudioFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeFlacDecoderTest {
    @Test
    fun officialLibraryHasExpectedDigest() {
        val dll = File("appResources/windows-x64/libFLAC.dll")
            .takeIf(File::isFile)
            ?: File("desktopApp/appResources/windows-x64/libFLAC.dll")

        assertEquals(LIBFLAC_DIGEST, sha256(dll))
    }

    @Test
    fun decodesSupportedDepthsExactly() {
        fixtures.forEach { fixture ->
            openNativeFlacPcmStream(stream(fixture.encoded)).use { pcm ->
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, pcm.format.encoding)
                assertEquals(fixture.sampleRate.toFloat(), pcm.format.sampleRate)
                assertEquals(fixture.bits, pcm.format.sampleSizeInBits)
                assertEquals(2, pcm.format.channels)
                assertEquals(false, pcm.format.isBigEndian)
                assertEquals(-1, pcm.frameLength)
                assertContentEquals(expectedPcm(64 * 2 * (fixture.bits / 8)), pcm.readAllBytes())
            }
        }
    }

    @Test
    fun convertsTwentyFourBitPlaybackToSixteenBit() {
        val fixture = fixtures.last()
        val expected24 = expectedPcm(64 * 2 * 3)

        openNativeFlacPcmStream(stream(fixture.encoded), outputBits = 16).use { pcm ->
            assertEquals(16, pcm.format.sampleSizeInBits)
            assertContentEquals(convert24To16(expected24), pcm.readAllBytes())
        }
    }

    @Test
    fun skipsLeadingId3Tag() {
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0
        )
        val encoded = id3Header + Base64.getDecoder().decode(fixtures[0].encoded)

        openNativeFlacPcmStream(ByteArrayInputStream(encoded)).use { pcm ->
            assertContentEquals(expectedPcm(64 * 2), pcm.readAllBytes())
        }
    }

    @Test
    fun truncatedFrameFailsWithIOException() {
        val encoded = Base64.getDecoder().decode(fixtures[1].encoded)
        val truncated = encoded.copyOf(encoded.size / 2)

        assertFailsWith<IOException> {
            openNativeFlacPcmStream(ByteArrayInputStream(truncated)).use { it.readAllBytes() }
        }
    }

    @Test
    fun rejectsMismatchedMd5AtPhysicalEndOfStream() {
        val encoded = Base64.getDecoder().decode(fixtures[1].encoded)
        encoded[26] = (encoded[26].toInt() xor 1).toByte()

        assertFailsWith<IOException> {
            openNativeFlacPcmStream(ByteArrayInputStream(encoded)).use { it.readAllBytes() }
        }
    }

    @Test
    fun rejectsUnderstatedAndOverstatedSampleCounts() {
        listOf(32L, 96L).forEach { totalSamples ->
            val encoded = Base64.getDecoder().decode(fixtures[1].encoded)
            setTotalSamples(encoded, totalSamples)
            assertFailsWith<IOException> {
                openNativeFlacPcmStream(ByteArrayInputStream(encoded)).use { it.readAllBytes() }
            }
        }
    }

    private fun stream(encoded: String) = ByteArrayInputStream(Base64.getDecoder().decode(encoded))

    private fun expectedPcm(length: Int) = ByteArray(length) { index -> (index * 31 + 17).toByte() }

    private fun setTotalSamples(encoded: ByteArray, totalSamples: Long) {
        encoded[21] = ((encoded[21].toInt() and 0xf0) or ((totalSamples ushr 32).toInt() and 0x0f)).toByte()
        for (index in 0 until 4) {
            encoded[22 + index] = (totalSamples ushr ((3 - index) * 8)).toByte()
        }
    }

    private fun convert24To16(source: ByteArray): ByteArray {
        val result = ByteArray(source.size / 3 * 2)
        var sourceIndex = 0
        var destination = 0
        while (sourceIndex < source.size) {
            val sample = (source[sourceIndex].toInt() and 0xff) or
                ((source[sourceIndex + 1].toInt() and 0xff) shl 8) or
                (source[sourceIndex + 2].toInt() shl 16)
            val converted = sample shr 8
            result[destination++] = converted.toByte()
            result[destination++] = (converted ushr 8).toByte()
            sourceIndex += 3
        }
        return result
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class Fixture(val encoded: String, val sampleRate: Int, val bits: Int)

    private companion object {
        const val LIBFLAC_DIGEST = "f93499172875fc2c0df80b57086f32e3f39e835283952ee2a59a3d4ffb097644"

        val fixtures = listOf(
            Fixture(
                "ZkxhQwAAACIAQABAAACMAACMBWIicAAAAEDE+jXfeNKSeVbeywH7SfO8gQAAKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/+GaiAD+9RiBenNrm+GLo+dI0iVALPeV6+pQ8wTfRz4Uc+y2r2fqUHFqhBwjyoidy25mPaNWG1SDqmuqL9HXm1co1IMs0XWQgA3379+/fv379+gIX379AQvv36Ahffv0BC+/fv379+/fv379+/fv379+/QEL79+gIX379AQvv36Ahffv379+/foBbIA==",
                22050,
                8
            ),
            Fixture(
                "ZkxhQwAAACIAQABAAAEGAAEGCsRC8AAAAEA6Vj4kzI26BV6WP0YbZOVAgQAAKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/+GkYAD8CQjARrI3m/DgweAbY5rxnI/acuwS6c/VkgP0ZKTBajxAAaFFuEqnJ/ZJoRFX4JHQ8nVLXm3Xm9tFqBz63WfEFIJuPBTM9MasM71lj/5kreN6c3qygPyMlTBZR4q9cABihVbie2lEEPVBd+D00PsqWKOERkO1Xm/TJ4e2sG2jgUJuT+rL5vv5MBgGoXCDb7bJt3Vos4b2TLlpYuOmNe+64qVJ30VKrzuq2HvlXtrnozeXqA3IsFMdlGGsPQAGJxcXKnSgqSOERN83R7fDVTv5M1/HYya88zB30wLiHtlp0/jXUrfXGtZ++ti5u/TV/fozklwVJ6AA0OsTBa7VlgFX3",
                44100,
                16
            ),
            Fixture(
                "ZkxhQwAAACIAQABAAAGLAAGLF3ADcAAAAECGEYC6c4F1F8rXlzXVuXW3gQAAKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/+GscAD+FAk8wEQnqy8OkhX1ePzcY+fHSs6uMbWVGJx8A4dm6m5N0VU0uDwfoycGig3tcPTUW9+/QsamKa2NEJR3+39e4mZFyU0ssDQXmx7+ggXlaOzMU9e3Or6eIaWFCIxv83dW2l49wUUkqCwPkxb2ef3dYOTES8+vMraWGZ19AIRn629O0lY1uT0coCQHiw7ucfXVWNy8Q8enKq6OEZV0+Hxf42dGyk4tsTUUmB//gwbmae3NUNS0O7+fIqaGCY1s8HRX21wKsjW5mRyggAeLau5yUdVZOLxAI6crCo4R8XT42F/jw0bKqi2xkRSYe/+DYuZqSc1RMLQ4G58jAoYJ6Wzw0Ffbuz7CoiWpiQyQc/d7Wt5iQcVJKKwwE5ca+n4B4WToyE/Tsza6mh2hgQSIa+9zUtZaOb1BIKQoC48S8nX52VzgwEfLqy6ykhWZePyAY+drSs5SMbU5GJwgA4cK6m3x0VTYuD/Doyaqig2RcPR4W99jQsZKKa0xEJQb+38C4mXpyUzR1Xg==",
                96000,
                24
            )
        )
    }
}
