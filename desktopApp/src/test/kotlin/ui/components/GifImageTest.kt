package ui.components

import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GifImageTest {
    @Test
    fun acceptsAnimationWithinDecodeBudget() {
        assertTrue(isGifDecodeBudgetAllowed(width = 320, height = 240, frameCount = 30))
    }

    @Test
    fun rejectsOversizedCanvasOrFrameBudget() {
        assertFalse(isGifDecodeBudgetAllowed(width = 5000, height = 5000, frameCount = 2))
        assertTrue(isGifDecodeBudgetAllowed(width = 1920, height = 1080, frameCount = 100))
        assertFalse(isGifDecodeBudgetAllowed(width = 320, height = 240, frameCount = 501))
    }

    @Test
    fun decodesRealMultiFrameGif() = runBlocking {
        val output = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder().apply {
            setSize(4, 4)
            setDelay(100)
            setRepeat(0)
        }
        assertTrue(encoder.start(output))
        assertTrue(encoder.addFrame(IntArray(16) { 0xffff0000.toInt() }, 4, 4))
        assertTrue(encoder.addFrame(IntArray(16) { 0xff00ff00.toInt() }, 4, 4))
        assertTrue(encoder.finish())

        val frames = assertNotNull(decodeGifFramesAsync(output.toByteArray()))
        assertEquals(2, frames.size)
        assertEquals(4, frames.first().bitmap.width)
        assertEquals(4, frames.first().bitmap.height)
    }
}
