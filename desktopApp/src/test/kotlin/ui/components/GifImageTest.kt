package ui.components

import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifImageTest {
    @Test fun gifPreviewPathUsesTheFileExtensionOnly() {
        assertTrue(isGifUrl("https://example.test/a.GIF?v=1.2#p"))
        assertFalse(isGifUrl("https://example.test/a.jpg?name=other.gif"))
    }
    @Test
    fun acceptsAnimationWithinDecodeBudget() {
        assertTrue(isGifDecodeBudgetAllowed(width = 320, height = 240, frameCount = 30))
    }

    @Test
    fun rejectsOversizedCanvasOrFrameBudget() {
        assertFalse(isGifDecodeBudgetAllowed(width = 5000, height = 5000, frameCount = 2))
        assertFalse(isGifDecodeBudgetAllowed(width = 1920, height = 1080, frameCount = 100))
        assertFalse(isGifDecodeBudgetAllowed(Int.MAX_VALUE, Int.MAX_VALUE, 500))
        assertFalse(isGifDecodeBudgetAllowed(width = 320, height = 240, frameCount = 501))
    }

    @Test
    fun decodesRealMultiFrameGif() = runBlocking {
        val frames = assertNotNull(decodeGifFramesAsync(twoFrameGif()))
        assertEquals(2, frames.size)
        assertEquals(4, frames.first().bitmap.width)
        assertEquals(4, frames.first().bitmap.height)
    }

    @Test
    fun fallsBackToFirstFrameOnlyWhenItFits() = runBlocking {
        // Tiny frame rectangles, large logical screen: the composited canvas determines cost.
        val bytes = twoFrameGif()
        fun canvas(width: Int, height: Int) = bytes.copyOf().apply {
            this[6] = width.toByte(); this[7] = (width shr 8).toByte()
            this[8] = height.toByte(); this[9] = (height shr 8).toByte()
        }
        val first = assertNotNull(decodeGifFramesAsync(canvas(1800, 1600))).single()
        assertEquals(1800, first.bitmap.width)
        assertNull(decodeGifFramesAsync(canvas(65535, 65535)))
    }

    private fun twoFrameGif(): ByteArray {
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

        return output.toByteArray()
    }
}
