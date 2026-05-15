package com.nekolaska.calabiyau.core.media.gif

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var fixedWidth = 0
    private var fixedHeight = 0
    private var transparent: Int? = null
    private var transIndex = 0
    private var repeat = -1
    private var delay = 0
    private var started = false
    private var out: OutputStream? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 0
    private var colorTab: ByteArray? = null
    private val usedEntry = BooleanArray(256)
    private var palSize = 7
    private var dispose = -1
    private var closeStream = false
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10
    private var hasTransparentPixels = false

    fun setDelay(ms: Int) {
        delay = (ms / 10.0f).roundToInt()
    }

    fun setDispose(code: Int) {
        if (code >= 0) dispose = code
    }

    fun setRepeat(iter: Int) {
        if (iter >= 0) repeat = iter
    }

    fun setTransparent(color: Int) {
        transparent = color
    }

    fun setFrameRate(fps: Float) {
        if (fps != 0f) delay = (100f / fps).roundToInt()
    }

    fun setQuality(quality: Int) {
        sample = quality.coerceAtLeast(1)
    }

    fun setSize(w: Int, h: Int) {
        if (started) return
        fixedWidth = if (w < 1) 320 else w
        fixedHeight = if (h < 1) 240 else h
        sizeSet = true
    }

    fun start(os: OutputStream?): Boolean {
        if (os == null) return false
        var ok = true
        closeStream = false
        out = os
        try {
            writeString("GIF89a")
        } catch (_: IOException) {
            ok = false
        }
        return ok.also { started = it }
    }

    fun start(file: String): Boolean {
        var ok: Boolean
        try {
            out = BufferedOutputStream(FileOutputStream(file))
            ok = start(out)
            closeStream = true
        } catch (_: IOException) {
            ok = false
        }
        return ok.also { started = it }
    }

    fun addFrame(argbPixels: IntArray?, frameWidth: Int, frameHeight: Int, x: Int = 0, y: Int = 0): Boolean {
        if (argbPixels == null || !started || frameWidth <= 0 || frameHeight <= 0) return false
        var ok = true
        try {
            if (sizeSet) setFrameSize(fixedWidth, fixedHeight) else setFrameSize(frameWidth, frameHeight)
            imagePixels(argbPixels, frameWidth, frameHeight)
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc(x, y)
            if (!firstFrame) writePalette()
            writePixels()
            firstFrame = false
        } catch (_: IOException) {
            ok = false
        }
        return ok
    }

    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out!!.write(0x3b)
            out!!.flush()
            if (closeStream) out!!.close()
        } catch (_: IOException) {
            ok = false
        }
        transIndex = 0
        out = null
        pixels = null
        indexedPixels = null
        colorTab = null
        closeStream = false
        firstFrame = true
        return ok
    }

    private fun setFrameSize(w: Int, h: Int) {
        width = w
        height = h
    }

    private fun analyzePixels() {
        val sourcePixels = requireNotNull(pixels)
        val len = sourcePixels.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(sourcePixels, len, sample)
        colorTab = nq.process()
        var i = 0
        while (i < colorTab!!.size) {
            val temp = colorTab!![i]
            colorTab!![i] = colorTab!![i + 2]
            colorTab!![i + 2] = temp
            usedEntry[i / 3] = false
            i += 3
        }
        var k = 0
        for (pixelIndex in 0 until nPix) {
            val index = nq.map(sourcePixels[k++].toInt() and 0xff, sourcePixels[k++].toInt() and 0xff, sourcePixels[k++].toInt() and 0xff)
            usedEntry[index] = true
            indexedPixels!![pixelIndex] = index.toByte()
        }
        pixels = null
        colorDepth = 8
        palSize = 7
        transIndex = when {
            transparent != null -> findClosest(transparent!!)
            hasTransparentPixels -> findClosest(0x00000000)
            else -> transIndex
        }
    }

    private fun findClosest(color: Int): Int {
        if (colorTab == null) return -1
        val r = (color ushr 16) and 0xff
        val g = (color ushr 8) and 0xff
        val b = color and 0xff
        var minpos = 0
        var dmin = 256 * 256 * 256
        val len = colorTab!!.size
        var i = 0
        while (i < len) {
            val dr = r - (colorTab!![i++].toInt() and 0xff)
            val dg = g - (colorTab!![i++].toInt() and 0xff)
            val db = b - (colorTab!![i].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            val index = i / 3
            if (usedEntry[index] && d < dmin) {
                dmin = d
                minpos = index
            }
            i++
        }
        return minpos
    }

    private fun imagePixels(argbPixels: IntArray, frameWidth: Int, frameHeight: Int) {
        val sourcePixels = if (frameWidth == width && frameHeight == height) {
            argbPixels
        } else {
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x < frameWidth && y < frameHeight) argbPixels[y * frameWidth + x] else 0x00000000
            }
        }
        pixels = ByteArray(sourcePixels.size * 3)
        var pixelsIndex = 0
        hasTransparentPixels = false
        var transparentCount = 0
        for (pixel in sourcePixels) {
            if ((pixel ushr 24) == 0) transparentCount++
            pixels!![pixelsIndex++] = (pixel and 0xff).toByte()
            pixels!![pixelsIndex++] = ((pixel shr 8) and 0xff).toByte()
            pixels!![pixelsIndex++] = ((pixel shr 16) and 0xff).toByte()
        }
        hasTransparentPixels = 100 * transparentCount / sourcePixels.size.toDouble() > MIN_TRANSPARENT_PERCENTAGE
    }

    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        out!!.write(0x21)
        out!!.write(0xf9)
        out!!.write(4)
        val transp: Int
        var disp: Int
        if (transparent == null && !hasTransparentPixels) {
            transp = 0
            disp = 0
        } else {
            transp = 1
            disp = 2
        }
        if (dispose >= 0) disp = dispose and 7
        disp = disp shl 2
        out!!.write(0 or disp or 0 or transp)
        writeShort(delay)
        out!!.write(transIndex)
        out!!.write(0)
    }

    @Throws(IOException::class)
    private fun writeImageDesc(x: Int, y: Int) {
        out!!.write(0x2c)
        writeShort(x)
        writeShort(y)
        writeShort(width)
        writeShort(height)
        if (firstFrame) out!!.write(0) else out!!.write(0x80 or 0 or 0 or 0 or palSize)
    }

    @Throws(IOException::class)
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out!!.write(0x80 or 0x70 or 0x00 or palSize)
        out!!.write(0)
        out!!.write(0)
    }

    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        out!!.write(0x21)
        out!!.write(0xff)
        out!!.write(11)
        writeString("NETSCAPE" + "2.0")
        out!!.write(3)
        out!!.write(1)
        writeShort(repeat)
        out!!.write(0)
    }

    @Throws(IOException::class)
    private fun writePalette() {
        out!!.write(colorTab, 0, colorTab!!.size)
        val n = (3 * 256) - colorTab!!.size
        for (i in 0 until n) out!!.write(0)
    }

    @Throws(IOException::class)
    private fun writePixels() {
        LZWEncoder(width, height, requireNotNull(indexedPixels), colorDepth).encode(requireNotNull(out))
    }

    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }

    @Throws(IOException::class)
    private fun writeString(s: String) {
        for (i in 0 until s.length) out!!.write(s[i].code.toByte().toInt())
    }

    companion object {
        private const val MIN_TRANSPARENT_PERCENTAGE = 4.0
    }
}
