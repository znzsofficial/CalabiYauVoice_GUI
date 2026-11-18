package com.nekolaska.calabiyau.core.media.gif

import android.graphics.Bitmap
import kotlin.test.*

class GifFrameIntegrityTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val header = "47494638396101000100800000000000ffffff"
    private val frame = "2c0000000001000100000202"
    private class PixelProbe : GifDecoder.BitmapProvider {
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap = error("No bitmap needed for pixel decode")
        override fun release(bitmap: Bitmap) = Unit
        override fun release(bytes: ByteArray) = Unit
        override fun release(array: IntArray) = Unit
    }

    @Test fun twoImagesWithoutControlBlocksDecodeToDifferentColors() {
        val bytes = hex(header + frame + "440100" + frame + "4c0100" + "3b")
        val probe = PixelProbe(); val decoder = StandardGifDecoder(probe)
        assertEquals(GifDecoder.STATUS_OK, decoder.read(bytes)); assertEquals(2, decoder.frameCount)
        val colors = (0..1).map {
            decoder.advance(); assertNotNull(decoder.decodeFramePixels()).single()
        }
        assertEquals(listOf(0xff000000.toInt(), 0xffffffff.toInt()), colors)
    }

    @Test fun invalidRectAndTruncationStayErrorsAndHighUnsignedWidthIsPreserved() {
        val decoder = StandardGifDecoder(PixelProbe())
        for (bad in listOf(header + frame + "440100", header + frame.replace("01000100", "02000100") + "4401003b")) {
            assertEquals(GifDecoder.STATUS_FORMAT_ERROR, decoder.read(hex(bad)))
        }
        val wide = hex(header.replace("6101000100", "6100800100") + frame + "4401003b")
        assertEquals(GifDecoder.STATUS_OK, decoder.read(wide)); assertEquals(32768, decoder.width)
    }
}
