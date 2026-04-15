package com.nekolaska.calabiyau.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt


/**
 * Class AnimatedGifEncoder - Encodes a GIF file consisting of one or more
 * frames.
 * 
 * <pre>
 * Example:
 * AnimatedGifEncoder e = new AnimatedGifEncoder();
 * e.start(outputFileName);
 * e.setDelay(1000);   // 1 frame per sec
 * e.addFrame(image1);
 * e.addFrame(image2);
 * e.addFrame(image3, 100, 100);    // set position of the frame
 * e.finish();
</pre> * 
 * 
 * No copyright asserted on the source code of this class. May be used for any
 * purpose, however, refer to the Unisys LZW patent for restrictions on use of
 * the associated LZWEncoder class. Please forward any corrections to
 * kweiner@fmsware.com.
 * 
 * @author Kevin Weiner, FM Software
 * @version 1.03 November 2003
 */
class AnimatedGifEncoder {
    private var width = 0 // image size

    private var height = 0

    private var fixedWidth = 0 // set by setSize()

    private var fixedHeight = 0

    private var transparent: Int? = null // transparent color if given

    private var transIndex = 0 // transparent index in color table

    private var repeat = -1 // no repeat

    private var delay = 0 // frame delay (hundredths)

    private var started = false // ready to output frames

    private var out: OutputStream? = null

    private var image: Bitmap? = null // current frame

    private var pixels: ByteArray? = null // BGR byte array from frame

    private var indexedPixels: ByteArray? = null // converted frame indexed to palette

    private var colorDepth = 0 // number of bit planes

    private var colorTab: ByteArray? = null // RGB palette

    private val usedEntry = BooleanArray(256) // active palette entries

    private var palSize = 7 // color table size (bits-1)

    private var dispose = -1 // disposal code (-1 = use default)

    private var closeStream = false // close stream when finished

    private var firstFrame = true

    private var sizeSet = false // if false, get size from first frame

    private var sample = 10 // default sample interval for quantizer

    private var hasTransparentPixels = false

    /**
     * Sets the delay time between each frame, or changes it for subsequent frames
     * (applies to last frame added).
     * 
     * @param ms
     * int delay time in milliseconds
     */
    fun setDelay(ms: Int) {
        delay = (ms / 10.0f).roundToInt()
    }

    /**
     * Sets the GIF frame disposal code for the last added frame and any
     * subsequent frames. Default is 0 if no transparent color has been set,
     * otherwise 2.
     * 
     * @param code
     * int disposal code.
     */
    fun setDispose(code: Int) {
        if (code >= 0) {
            dispose = code
        }
    }

    /**
     * Sets the number of times the set of GIF frames should be played. Default is
     * 1; 0 means play indefinitely. Must be invoked before the first image is
     * added.
     * 
     * @param iter
     * int number of iterations.
     */
    fun setRepeat(iter: Int) {
        if (iter >= 0) {
            repeat = iter
        }
    }

    /**
     * Sets the transparent color for the last added frame and any subsequent
     * frames. Since all colors are subject to modification in the quantization
     * process, the color in the final palette for each frame closest to the given
     * color becomes the transparent color for that frame. May be set to null to
     * indicate no transparent color.
     * 
     * @param color
     * Color to be treated as transparent on display.
     */
    fun setTransparent(color: Int) {
        transparent = color
    }

    /**
     * Adds next GIF frame to the specified position. The frame is not written immediately, but is
     * actually deferred until the next frame is received so that timing data can be inserted.
     * Invoking `finish()` flushes all frames. If `setSize` was invoked, the
     * size is used for all subsequent frames. Otherwise, the actual size of the image is used for
     * each frame.
     * 
     * See page 11 of http://giflib.sourceforge.net/gif89.txt for the position of the frame
     * 
     * @param im
     * BufferedImage containing frame to write.
     * @param x
     * Column number, in pixels, of the left edge of the image, with respect to the left
     * edge of the Logical Screen.
     * @param y
     * Row number, in pixels, of the top edge of the image with respect to the top edge of
     * the Logical Screen.
     * @return true if successful.
     */
    /**
     * Adds next GIF frame. The frame is not written immediately, but is actually
     * deferred until the next frame is received so that timing data can be
     * inserted. Invoking `finish()` flushes all frames. If
     * `setSize` was invoked, the size is used for all subsequent frames.
     * Otherwise, the actual size of the image is used for each frames.
     * 
     * @param im
     * BufferedImage containing frame to write.
     * @return true if successful.
     */
    @JvmOverloads
    fun addFrame(im: Bitmap?, x: Int = 0, y: Int = 0): Boolean {
        if ((im == null) || !started) {
            return false
        }
        var ok = true
        try {
            if (sizeSet) {
                setFrameSize(fixedWidth, fixedHeight)
            } else {
                setFrameSize(im.getWidth(), im.getHeight())
            }
            image = im
            this.imagePixels // convert to correct format if necessary
            analyzePixels() // build color table & map pixels
            if (firstFrame) {
                writeLSD() // logical screen descriptor
                writePalette() // global color table
                if (repeat >= 0) {
                    // use NS app extension to indicate reps
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt() // write graphic control extension
            writeImageDesc(x, y) // image descriptor
            if (!firstFrame) {
                writePalette() // local color table
            }
            writePixels() // encode and write pixel data
            firstFrame = false
        } catch (e: IOException) {
            ok = false
        }

        return ok
    }

    /**
     * Flushes any pending data and closes output file. If writing to an
     * OutputStream, the stream is not closed.
     */
    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out!!.write(0x3b) // GIF trailer
            out!!.flush()
            if (closeStream) {
                out!!.close()
            }
        } catch (e: IOException) {
            ok = false
        }

        // reset for subsequent use
        transIndex = 0
        out = null
        image = null
        pixels = null
        indexedPixels = null
        colorTab = null
        closeStream = false
        firstFrame = true

        return ok
    }

    /**
     * Sets frame rate in frames per second. Equivalent to
     * `setDelay(1000/fps)`.
     * 
     * @param fps
     * float frame rate (frames per second)
     */
    fun setFrameRate(fps: Float) {
        if (fps != 0f) {
            delay = (100f / fps).roundToInt()
        }
    }

    /**
     * Sets quality of color quantization (conversion of images to the maximum 256
     * colors allowed by the GIF specification). Lower values (minimum = 1)
     * produce better colors, but slow processing significantly. 10 is the
     * default, and produces good color mapping at reasonable speeds. Values
     * greater than 20 do not yield significant improvements in speed.
     * 
     * @param quality int greater than 0.
     */
    fun setQuality(quality: Int) {
        var quality = quality
        if (quality < 1) quality = 1
        sample = quality
    }

    /**
     * Sets the fixed GIF frame size for all the frames.
     * This should be called before start.
     * 
     * @param w
     * int frame width.
     * @param h
     * int frame width.
     */
    fun setSize(w: Int, h: Int) {
        if (started) {
            return
        }

        fixedWidth = w
        fixedHeight = h
        if (fixedWidth < 1) {
            fixedWidth = 320
        }
        if (fixedHeight < 1) {
            fixedHeight = 240
        }

        sizeSet = true
    }

    /**
     * Sets current GIF frame size.
     * 
     * @param w
     * int frame width.
     * @param h
     * int frame width.
     */
    private fun setFrameSize(w: Int, h: Int) {
        width = w
        height = h
    }

    /**
     * Initiates GIF file creation on the given stream. The stream is not closed
     * automatically.
     * 
     * @param os
     * OutputStream on which GIF images are written.
     * @return false if initial write failed.
     */
    fun start(os: OutputStream?): Boolean {
        if (os == null) return false
        var ok = true
        closeStream = false
        out = os
        try {
            writeString("GIF89a") // header
        } catch (e: IOException) {
            ok = false
        }
        return ok.also { started = it }
    }

    /**
     * Initiates writing of a GIF file with the specified name.
     * 
     * @param file
     * String containing output file name.
     * @return false if open or initial write failed.
     */
    fun start(file: String): Boolean {
        var ok: Boolean
        try {
            out = BufferedOutputStream(FileOutputStream(file))
            ok = start(out)
            closeStream = true
        } catch (e: IOException) {
            ok = false
        }
        return ok.also { started = it }
    }

    /**
     * Analyzes image colors and creates color map.
     */
    private fun analyzePixels() {
        val sourcePixels = requireNotNull(pixels)
        val len = sourcePixels.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(sourcePixels, len, sample)
        // initialize quantizer
        colorTab = nq.process() // create reduced palette
        // convert map from BGR to RGB
        run {
            var i = 0
            while (i < colorTab!!.size) {
                val temp = colorTab!![i]
                colorTab!![i] = colorTab!![i + 2]
                colorTab!![i + 2] = temp
                usedEntry[i / 3] = false
                i += 3
            }
        }
        // map image pixels to new palette
        var k = 0
        for (i in 0..<nPix) {
            val index =
                nq.map(sourcePixels[k++].toInt() and 0xff, sourcePixels[k++].toInt() and 0xff, sourcePixels[k++].toInt() and 0xff)
            usedEntry[index] = true
            indexedPixels!![i] = index.toByte()
        }
        pixels = null
        colorDepth = 8
        palSize = 7
        // get closest match to transparent color if specified
        if (transparent != null) {
            transIndex = findClosest(transparent!!)
        } else if (hasTransparentPixels) {
            transIndex = findClosest(Color.TRANSPARENT)
        }
    }

    /**
     * Returns index of palette color closest to c
     * 
     */
    private fun findClosest(color: Int): Int {
        if (colorTab == null) return -1
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
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
            if (usedEntry[index] && (d < dmin)) {
                dmin = d
                minpos = index
            }
            i++
        }
        return minpos
    }

    private val imagePixels: Unit
        /**
         * Extracts image pixels into byte array "pixels"
         */
        get() {
            val w = image!!.getWidth()
            val h = image!!.getHeight()

            if ((w != width) || (h != height)) {
                // create new image with right size/format
                val temp =
                    createBitmap(width, height)
                val canvas = Canvas(temp)
                canvas.drawBitmap(temp, 0f, 0f, null)
                image = temp
            }
            val pixelsInt = IntArray(w * h)
            image!!.getPixels(pixelsInt, 0, w, 0, 0, w, h)

            // The algorithm requires 3 bytes per pixel as RGB.
            pixels = ByteArray(pixelsInt.size * 3)

            var pixelsIndex = 0
            hasTransparentPixels = false
            var totalTransparentPixels = 0
            for (pixel in pixelsInt) {
                if (pixel == Color.TRANSPARENT) {
                    totalTransparentPixels++
                }
                pixels!![pixelsIndex++] = (pixel and 0xFF).toByte()
                pixels!![pixelsIndex++] = ((pixel shr 8) and 0xFF).toByte()
                pixels!![pixelsIndex++] = ((pixel shr 16) and 0xFF).toByte()
            }

            val transparentPercentage = 100 * totalTransparentPixels / pixelsInt.size.toDouble()
            // Assume images with greater where more than n% of the pixels are transparent actually have
            // transparency. See issue #214.
            hasTransparentPixels = transparentPercentage > MIN_TRANSPARENT_PERCENTAGE
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                    TAG, ("got pixels for frame with " + transparentPercentage
                            + "% transparent pixels")
                )
            }
        }

    /**
     * Writes Graphic Control Extension
     */
    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        out!!.write(0x21) // extension introducer
        out!!.write(0xf9) // GCE label
        out!!.write(4) // data block size
        val transp: Int
        var disp: Int
        if (transparent == null && !hasTransparentPixels) {
            transp = 0
            disp = 0 // dispose = no action
        } else {
            transp = 1
            disp = 2 // force clear if using transparent color
        }
        if (dispose >= 0) {
            disp = dispose and 7 // user override
        }
        disp = disp shl 2

        // packed fields
        out!!.write(
            0 or  // 1:3 reserved
                    disp or  // 4:6 disposal
                    0 or  // 7 user input - 0 = none
                    transp
        ) // 8 transparency flag

        writeShort(delay) // delay x 1/100 sec
        out!!.write(transIndex) // transparent color index
        out!!.write(0) // block terminator
    }

    /**
     * Writes Image Descriptor
     */
    @Throws(IOException::class)
    private fun writeImageDesc(x: Int, y: Int) {
        out!!.write(0x2c) // image separator
        writeShort(x) // image position
        writeShort(y)
        writeShort(width) // image size
        writeShort(height)
        // packed fields
        if (firstFrame) {
            // no LCT - GCT is used for first (or only) frame
            out!!.write(0)
        } else {
            // specify normal LCT
            out!!.write(
                0x80 or  // 1 local color table 1=yes
                        0 or  // 2 interlace - 0=no
                        0 or  // 3 sorted - 0=no
                        0 or  // 4-5 reserved
                        palSize
            ) // 6-8 size of color table
        }
    }

    /**
     * Writes Logical Screen Descriptor
     */
    @Throws(IOException::class)
    private fun writeLSD() {
        // logical screen size
        writeShort(width)
        writeShort(height)
        // packed fields
        out!!.write(
            (0x80 or  // 1 : global color table flag = 1 (gct used)
                    0x70 or  // 2-4 : color resolution = 7
                    0x00 or  // 5 : gct sort flag = 0
                    palSize)
        ) // 6-8 : gct size

        out!!.write(0) // background color index
        out!!.write(0) // pixel aspect ratio - assume 1:1
    }

    /**
     * Writes Netscape application extension to define repeat count.
     */
    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        out!!.write(0x21) // extension introducer
        out!!.write(0xff) // app extension label
        out!!.write(11) // block size
        writeString("NETSCAPE" + "2.0") // app id + auth code
        out!!.write(3) // sub-block size
        out!!.write(1) // loop sub-block id
        writeShort(repeat) // loop count (extra iterations, 0=repeat forever)
        out!!.write(0) // block terminator
    }

    /**
     * Writes color table
     */
    @Throws(IOException::class)
    private fun writePalette() {
        out!!.write(colorTab, 0, colorTab!!.size)
        val n = (3 * 256) - colorTab!!.size
        for (i in 0..<n) {
            out!!.write(0)
        }
    }

    /**
     * Encodes and writes pixel data
     */
    @Throws(IOException::class)
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, requireNotNull(indexedPixels), colorDepth)
        encoder.encode(requireNotNull(out))
    }

    /**
     * Write 16-bit value to output stream, LSB first
     */
    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }

    /**
     * Writes string to output stream
     */
    @Throws(IOException::class)
    private fun writeString(s: String) {
        for (i in 0..<s.length) {
            out!!.write(s[i].code.toByte().toInt())
        }
    }

    companion object {
        private const val TAG = "AnimatedGifEncoder"

        // The minimum % of an images pixels that must be transparent for us to set a transparent index
        // automatically.
        private const val MIN_TRANSPARENT_PERCENTAGE = 4.0
    }
}
