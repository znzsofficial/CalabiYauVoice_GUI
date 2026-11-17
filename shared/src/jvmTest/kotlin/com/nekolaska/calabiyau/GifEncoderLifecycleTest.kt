package com.nekolaska.calabiyau

import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.*

class GifEncoderLifecycleTest {
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
