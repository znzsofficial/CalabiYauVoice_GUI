package com.nekolaska.calabiyau

import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.*

class GifEncoderLifecycleTest {
    @Test fun unsigned16BitWidthRoundTripsAndOverflowCannotReportSuccess() {
        for (width in listOf(32768, 65535)) {
            val bytes = ByteArrayOutputStream()
            AnimatedGifEncoder().use { encoder ->
                encoder.setSize(width, 1); assertTrue(encoder.start(bytes))
                assertTrue(encoder.addFrame(IntArray(width), width, 1)); assertTrue(encoder.finish())
            }
            val payload = bytes.toByteArray()
            assertEquals(width, (payload[6].toInt() and 255) or ((payload[7].toInt() and 255) shl 8))
        }
        AnimatedGifEncoder().use { encoder ->
            assertFailsWith<IllegalArgumentException> { encoder.setSize(65536, 1) }
            encoder.start(ByteArrayOutputStream()); assertFalse(encoder.addFrame(IntArray(65536), 65536, 1))
        }
    }
    @Test fun finishFailureStillClosesOwnedStreamAndCanAbortTwice() {
        var closed = 0
        val stream = object : ByteArrayOutputStream() {
            override fun flush() { throw IOException("full") }
            override fun close() { closed++ }
        }
        val encoder = AnimatedGifEncoder()
        assertTrue(encoder.start(stream, closeOnFinish = true))
        assertTrue(encoder.addFrame(intArrayOf(-1), 1, 1))
        assertFalse(encoder.finish())
        encoder.close(); encoder.close()
        assertEquals(1, closed)
    }
    @Test fun abortedOwnedStreamClosesButBorrowedStreamStaysOpen() {
        for (owned in listOf(true, false)) {
            var closed = false
            val stream = object : ByteArrayOutputStream() { override fun close() { closed = true } }
            val encoder = AnimatedGifEncoder()
            assertTrue(encoder.start(stream, closeOnFinish = owned))
            encoder.close()
            assertEquals(owned, closed)
            assertFalse(encoder.finish())
        }
    }
}
