package com.nekolaska.calabiyau.core.media.gif

/*
* Copyright (c) 2013 Xcellent Creations, Inc.
*
* Permission is hereby granted, free of charge, to any person obtaining
* a copy of this software and associated documentation files (the
* "Software"), to deal in the Software without restriction, including
* without limitation the rights to use, copy, modify, merge, publish,
* distribute, sublicense, and/or sell copies of the Software, and to
* permit persons to whom the Software is furnished to do so, subject to
* the following conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
* LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
* OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
* WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.ColorInt
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.min

/**
 * Reads frame data from a GIF image source and decodes it into individual frames for animation
 * purposes.  Image data can be read from either and InputStream source or a byte[].
 * 
 * 
 * This class is optimized for running animations with the frames, there are no methods to get
 * individual frame images, only to decode the next frame in the animation sequence.  Instead, it
 * lowers its memory footprint by only housing the minimum data necessary to decode the next frame
 * in the animation sequence.
 * 
 * 
 * The animation must be manually moved forward using [.advance] before requesting the
 * next frame.  This method must also be called before you request the first frame or an error
 * will occur.
 * 
 * 
 * Implementation adapted from sample code published in Lyons. (2004). *Java for
 * Programmers*, republished under the MIT Open Source License
 * 
 * @see [GIF 89a Specification](https://www.w3.org/Graphics/GIF/spec-gif89a.txt)
 */
class StandardGifDecoder(provider: GifDecoder.BitmapProvider) : GifDecoder {
    // Global File Header values and parsing flags.
    /**
     * Active color table.
     * Maximum size is 256, see GifHeaderParser.readColorTable
     */
    @ColorInt
    private var act: IntArray? = null

    /** Private color table that can be modified if needed.  */
    @ColorInt
    private val pct = IntArray(256)

    private val bitmapProvider: GifDecoder.BitmapProvider = provider

    /** Raw GIF data from input source.  */
    private var rawData: ByteBuffer? = null

    /** Raw data read working array.  */
    private var block: ByteArray? = null

    private var parser: GifHeaderParser? = null

    // LZW decoder working arrays.
    private var prefix: ShortArray? = null
    private var suffix: ByteArray? = null
    private var pixelStack: ByteArray? = null
    private var mainPixels: ByteArray? = null

    @ColorInt
    private var mainScratch: IntArray? = null

    override var currentFrameIndex: Int = 0
        private set
    private var header: GifHeader = GifHeader()
    private var previousImage: Bitmap? = null
    private var savePrevious = false

    @GifDecoder.GifDecodeStatus
    override var status: Int = 0
        private set
    private var sampleSize = 0
    private var downsampledHeight = 0
    private var downsampledWidth = 0
    private var isFirstFrameTransparent: Boolean? = null
    private var bitmapConfig = Bitmap.Config.ARGB_8888

    // Public API.
    @Suppress("unused")
    constructor(provider: GifDecoder.BitmapProvider, gifHeader: GifHeader, rawData: ByteBuffer) : this(
        provider,
        gifHeader,
        rawData,
        1 /*sampleSize*/
    )

    constructor(
        provider: GifDecoder.BitmapProvider, gifHeader: GifHeader, rawData: ByteBuffer,
        sampleSize: Int
    ) : this(provider) {
        setData(gifHeader, rawData, sampleSize)
    }

    override val width: Int
        get() = header.width

    override val height: Int
        get() = header.height

    override val data: ByteBuffer
        get() = requireNotNull(rawData)

    override fun advance() {
        this.currentFrameIndex = (this.currentFrameIndex + 1) % header.numFrames
    }

    override fun getDelay(n: Int): Int {
        var delay = -1
        if (n >= 0 && n < header.numFrames) {
            delay = header.frames[n].delay
        }
        return delay
    }

    override val nextDelay: Int
        get() {
            if (header.numFrames <= 0 || this.currentFrameIndex < 0) {
                return 0
            }

            return getDelay(this.currentFrameIndex)
        }

    override val frameCount: Int
        get() = header.numFrames

    override fun resetFrameIndex() {
        this.currentFrameIndex = INITIAL_FRAME_POINTER
    }

    @get:Deprecated("")
    override val loopCount: Int
        get() {
            if (header.loopCount == GifHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
                return 1
            }
            return header.loopCount
        }

    override val netscapeLoopCount: Int
        get() = header.loopCount

    override val totalIterationCount: Int
        get() {
            if (header.loopCount == GifHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
                return 1
            }
            if (header.loopCount == GifHeader.NETSCAPE_LOOP_COUNT_FOREVER) {
                return GifDecoder.TOTAL_ITERATION_COUNT_FOREVER
            }
            return header.loopCount + 1
        }

    override val byteSize: Int
        get() {
            val raw = requireNotNull(rawData)
            val pixels = requireNotNull(mainPixels)
            val scratch = requireNotNull(mainScratch)
            return raw.limit() + pixels.size + (scratch.size * BYTES_PER_INTEGER)
        }

    @get:Synchronized
    override val nextFrame: Bitmap?
        get() {
            val header = this.header
            if (header.numFrames <= 0 || this.currentFrameIndex < 0) {
                status = GifDecoder.STATUS_FORMAT_ERROR
            }
            if (status == GifDecoder.STATUS_FORMAT_ERROR || status == GifDecoder.STATUS_OPEN_ERROR) {
                return null
            }
            status = GifDecoder.STATUS_OK

            if (block == null) {
                block = bitmapProvider.obtainByteArray(255)
            }

            val currentFrame = header.frames[this.currentFrameIndex]
            var previousFrame: GifFrame? = null
            val previousIndex = this.currentFrameIndex - 1
            if (previousIndex >= 0) {
                previousFrame = header.frames[previousIndex]
            }

            // Set the appropriate color table.
            act = if (currentFrame.lct != null) currentFrame.lct else header.gct
            if (act == null) {
                // No color table defined.
                status = GifDecoder.STATUS_FORMAT_ERROR
                return null
            }

            // Reset the transparent pixel in the color table
            if (currentFrame.transparency) {
                // Prepare local copy of color table ("pct = act"), see #1068
                val activeColors = requireNotNull(act)
                System.arraycopy(activeColors, 0, pct, 0, activeColors.size)
                // Forget about act reference from shared header object, use copied version
                act = pct
                // Set transparent color if specified.
                pct[currentFrame.transIndex] = COLOR_TRANSPARENT_BLACK

                if (currentFrame.dispose == GifFrame.DISPOSAL_BACKGROUND && this.currentFrameIndex == 0) {
                    // TODO: We should check and see if all individual pixels are replaced. If they are, the
                    // first frame isn't actually transparent. For now, it's simpler and safer to assume
                    // drawing a transparent background means the GIF contains transparency.
                    isFirstFrameTransparent = true
                }
            }

            // Transfer pixel data to image.
            return setPixels(currentFrame, previousFrame)
        }

    override fun read(`is`: InputStream?, contentLength: Int): Int {
        if (`is` != null) {
            try {
                val capacity = if (contentLength > 0) (contentLength + 4 * 1024) else 16 * 1024
                val buffer = ByteArrayOutputStream(capacity)
                var nRead: Int
                val data = ByteArray(16 * 1024)
                while ((`is`.read(data, 0, data.size).also { nRead = it }) != -1) {
                    buffer.write(data, 0, nRead)
                }
                buffer.flush()

                read(buffer.toByteArray())
            } catch (e: IOException) {
                Log.w(TAG, "Error reading data from stream", e)
            }
        } else {
            status = GifDecoder.STATUS_OPEN_ERROR
        }

        try {
            `is`?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing stream", e)
        }

        return status
    }

    override fun clear() {
        header = GifHeader()
        mainPixels?.let { bitmapProvider.release(it) }
        mainScratch?.let { bitmapProvider.release(it) }
        previousImage?.let { bitmapProvider.release(it) }
        previousImage = null
        rawData = null
        isFirstFrameTransparent = null
        block?.let { bitmapProvider.release(it) }
    }

    @Synchronized
    override fun setData(header: GifHeader, data: ByteArray) {
        setData(header, ByteBuffer.wrap(data))
    }

    @Synchronized
    override fun setData(header: GifHeader, buffer: ByteBuffer) {
        setData(header, buffer, 1)
    }

    @Synchronized
    override fun setData(
        header: GifHeader, buffer: ByteBuffer,
        sampleSize: Int
    ) {
        var sampleSize = sampleSize
        require(sampleSize > 0) { "Sample size must be >=0, not: $sampleSize" }
        // Make sure sample size is a power of 2.
        sampleSize = Integer.highestOneBit(sampleSize)
        this.status = GifDecoder.STATUS_OK
        this.header = header
        this.currentFrameIndex = INITIAL_FRAME_POINTER
        // Initialize the raw data buffer.
        rawData = buffer.asReadOnlyBuffer().apply {
            position(0)
            order(ByteOrder.LITTLE_ENDIAN)
        }

        // No point in specially saving an old frame if we're never going to use it.
        savePrevious = false
        for (frame in header.frames) {
            if (frame.dispose == GifFrame.DISPOSAL_PREVIOUS) {
                savePrevious = true
                break
            }
        }

        this.sampleSize = sampleSize
        downsampledWidth = header.width / sampleSize
        downsampledHeight = header.height / sampleSize
        // Now that we know the size, init scratch arrays.
        // TODO Find a way to avoid this entirely or at least downsample it (either should be possible).
        mainPixels = bitmapProvider.obtainByteArray(header.width * header.height)
        mainScratch = bitmapProvider.obtainIntArray(downsampledWidth * downsampledHeight)
    }

    private val headerParser: GifHeaderParser
        get() {
            if (parser == null) {
                parser = GifHeaderParser()
            }
            return requireNotNull(parser)
        }

    @GifDecoder.GifDecodeStatus
    @Synchronized
    override fun read(data: ByteArray?): Int {
        this.header = this.headerParser.setData(data).parseHeader()
        if (data != null) {
            setData(header, data)
        }

        return status
    }

    override fun setDefaultBitmapConfig(format: Bitmap.Config) {
        require(!(format != Bitmap.Config.ARGB_8888 && format != Bitmap.Config.RGB_565)) {
            ("Unsupported format: " + format
                    + ", must be one of " + Bitmap.Config.ARGB_8888 + " or " + Bitmap.Config.RGB_565)
        }

        bitmapConfig = format
    }

    /**
     * Creates new frame image from current data (and previous frames as specified by their
     * disposition codes).
     */
    private fun setPixels(currentFrame: GifFrame, previousFrame: GifFrame?): Bitmap {
        // Final location of blended pixels.
        val dest = requireNotNull(mainScratch)

        // clear all pixels when meet first frame and drop prev image from last loop
        if (previousFrame == null) {
            previousImage?.let { bitmapProvider.release(it) }
            previousImage = null
            Arrays.fill(dest, COLOR_TRANSPARENT_BLACK)
        }

        // clear all pixels when dispose is 3 but previousImage is null.
        // When DISPOSAL_PREVIOUS and previousImage didn't be set, new frame should draw on
        // a empty image
        if (previousFrame != null && previousFrame.dispose == GifFrame.DISPOSAL_PREVIOUS && previousImage == null) {
            Arrays.fill(dest, COLOR_TRANSPARENT_BLACK)
        }

        // fill in starting image contents based on last image's dispose code
        if (previousFrame != null && previousFrame.dispose > GifFrame.DISPOSAL_UNSPECIFIED) {
            // We don't need to do anything for DISPOSAL_NONE, if it has the correct pixels so will our
            // mainScratch and therefore so will our dest array.
            if (previousFrame.dispose == GifFrame.DISPOSAL_BACKGROUND) {
                // Start with a canvas filled with the background color
                @ColorInt var c: Int = COLOR_TRANSPARENT_BLACK
                if (!currentFrame.transparency) {
                    c = header.bgColor
                    if (currentFrame.lct != null && header.bgIndex == currentFrame.transIndex) {
                        c = COLOR_TRANSPARENT_BLACK
                    }
                }
                // The area used by the graphic must be restored to the background color.
                val downsampledIH = previousFrame.ih / sampleSize
                val downsampledIY = previousFrame.iy / sampleSize
                val downsampledIW = previousFrame.iw / sampleSize
                val downsampledIX = previousFrame.ix / sampleSize
                val topLeft = downsampledIY * downsampledWidth + downsampledIX
                val bottomLeft = topLeft + downsampledIH * downsampledWidth
                var left = topLeft
                while (left < bottomLeft) {
                    val right = left + downsampledIW
                    for (pointer in left..<right) {
                        dest[pointer] = c
                    }
                    left += downsampledWidth
                }
            } else if (previousFrame.dispose == GifFrame.DISPOSAL_PREVIOUS && previousImage != null) {
                // Start with the previous frame
                previousImage?.getPixels(
                    dest, 0, downsampledWidth, 0, 0, downsampledWidth,
                    downsampledHeight
                )
            }
        }

        // Decode pixels for this frame into the global pixels[] scratch.
        decodeBitmapData(currentFrame)

        if (currentFrame.interlace || sampleSize != 1) {
            copyCopyIntoScratchRobust(currentFrame)
        } else {
            copyIntoScratchFast(currentFrame)
        }

        // Copy pixels into previous image
        if (savePrevious && (currentFrame.dispose == GifFrame.DISPOSAL_UNSPECIFIED
                || currentFrame.dispose == GifFrame.DISPOSAL_NONE)
        ) {
            val previous = previousImage ?: this.nextBitmap.also { previousImage = it }
            previous.setPixels(
                dest, 0, downsampledWidth, 0, 0, downsampledWidth,
                downsampledHeight
            )
        }

        // Set pixels for current image.
        val result = this.nextBitmap
        result.setPixels(dest, 0, downsampledWidth, 0, 0, downsampledWidth, downsampledHeight)
        return result
    }

    // Readability
    private fun copyIntoScratchFast(currentFrame: GifFrame) {
        val dest = requireNotNull(mainScratch)
        val downsampledIH = currentFrame.ih
        val downsampledIY = currentFrame.iy
        val downsampledIW = currentFrame.iw
        val downsampledIX = currentFrame.ix
        // Copy each source line to the appropriate place in the destination.
        val isFirstFrame = this.currentFrameIndex == 0
        val width = this.downsampledWidth
        val mainPixels = requireNotNull(this.mainPixels)
        val act = requireNotNull(this.act)
        var transparentColorIndex: Byte = -1
        for (i in 0..<downsampledIH) {
            val line = i + downsampledIY
            val k = line * width
            // Start of line in dest.
            var dx = k + downsampledIX
            // End of dest line.
            var dlim = dx + downsampledIW
            if (k + width < dlim) {
                // Past dest edge.
                dlim = k + width
            }
            // Start of line in source.
            var sx = i * currentFrame.iw

            while (dx < dlim) {
                val byteCurrentColorIndex = mainPixels[sx]
                val currentColorIndex = (byteCurrentColorIndex.toInt()) and MASK_INT_LOWEST_BYTE
                if (currentColorIndex != transparentColorIndex.toInt()) {
                    val color = act[currentColorIndex]
                    if (color != COLOR_TRANSPARENT_BLACK) {
                        dest[dx] = color
                    } else {
                        transparentColorIndex = byteCurrentColorIndex
                    }
                }
                ++sx
                ++dx
            }
        }

        isFirstFrameTransparent =
            (isFirstFrameTransparent != null && isFirstFrameTransparent == true)
                    || (isFirstFrameTransparent == null && isFirstFrame && transparentColorIndex.toInt() != -1)
    }

    private fun copyCopyIntoScratchRobust(currentFrame: GifFrame) {
        val dest = requireNotNull(mainScratch)
        val downsampledIH = currentFrame.ih / sampleSize
        val downsampledIY = currentFrame.iy / sampleSize
        val downsampledIW = currentFrame.iw / sampleSize
        val downsampledIX = currentFrame.ix / sampleSize
        // Copy each source line to the appropriate place in the destination.
        var pass = 1
        var inc = 8
        var iline = 0
        val isFirstFrame = this.currentFrameIndex == 0
        val sampleSize = this.sampleSize
        val downsampledWidth = this.downsampledWidth
        val downsampledHeight = this.downsampledHeight
        val mainPixels = requireNotNull(this.mainPixels)
        val act = requireNotNull(this.act)
        var isFirstFrameTransparent = this.isFirstFrameTransparent
        for (i in 0..<downsampledIH) {
            var line = i
            if (currentFrame.interlace) {
                if (iline >= downsampledIH) {
                    pass++
                    when (pass) {
                        2 -> iline = 4
                        3 -> {
                            iline = 2
                            inc = 4
                        }

                        4 -> {
                            iline = 1
                            inc = 2
                        }

                        else -> {}
                    }
                }
                line = iline
                iline += inc
            }
            line += downsampledIY
            val isNotDownsampling = sampleSize == 1
            if (line < downsampledHeight) {
                val k = line * downsampledWidth
                // Start of line in dest.
                var dx = k + downsampledIX
                // End of dest line.
                var dlim = dx + downsampledIW
                if (k + downsampledWidth < dlim) {
                    // Past dest edge.
                    dlim = k + downsampledWidth
                }
                // Start of line in source.
                var sx = i * sampleSize * currentFrame.iw
                if (isNotDownsampling) {
                    var averageColor: Int
                    while (dx < dlim) {
                        val currentColorIndex = (mainPixels[sx].toInt()) and MASK_INT_LOWEST_BYTE
                        averageColor = act[currentColorIndex]
                        if (averageColor != COLOR_TRANSPARENT_BLACK) {
                            dest[dx] = averageColor
                        } else if (isFirstFrame && isFirstFrameTransparent == null) {
                            isFirstFrameTransparent = true
                        }
                        sx += sampleSize
                        dx++
                    }
                } else {
                    var averageColor: Int
                    val maxPositionInSource = sx + ((dlim - dx) * sampleSize)
                    while (dx < dlim) {
                        // Map color and insert in destination.
                        // TODO: This is substantially slower (up to 50ms per frame) than just grabbing the
                        // current color index above, even with a sample size of 1.
                        averageColor = averageColorsNear(sx, maxPositionInSource, currentFrame.iw)
                        if (averageColor != COLOR_TRANSPARENT_BLACK) {
                            dest[dx] = averageColor
                        } else if (isFirstFrame && isFirstFrameTransparent == null) {
                            isFirstFrameTransparent = true
                        }
                        sx += sampleSize
                        dx++
                    }
                }
            }
        }

        if (this.isFirstFrameTransparent == null) {
            this.isFirstFrameTransparent = isFirstFrameTransparent ?: false
        }
    }

    @ColorInt
    private fun averageColorsNear(
        positionInMainPixels: Int, maxPositionInMainPixels: Int,
        currentFrameIw: Int
    ): Int {
        val mainPixels = requireNotNull(this.mainPixels)
        val act = requireNotNull(this.act)
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0

        var totalAdded = 0
        // Find the pixels in the current row.
        run {
            var i = positionInMainPixels
            while (i < positionInMainPixels + sampleSize && i < mainPixels.size && i < maxPositionInMainPixels) {
                val currentColorIndex = (mainPixels[i].toInt()) and MASK_INT_LOWEST_BYTE
                val currentColor = act[currentColorIndex]
                if (currentColor != 0) {
                    alphaSum += currentColor shr 24 and MASK_INT_LOWEST_BYTE
                    redSum += currentColor shr 16 and MASK_INT_LOWEST_BYTE
                    greenSum += currentColor shr 8 and MASK_INT_LOWEST_BYTE
                    blueSum += currentColor and MASK_INT_LOWEST_BYTE
                    totalAdded++
                }
                i++
            }
        }
        // Find the pixels in the next row.
        var i = positionInMainPixels + currentFrameIw
        while (i < positionInMainPixels + currentFrameIw + sampleSize && i < mainPixels.size && i < maxPositionInMainPixels) {
            val currentColorIndex = (mainPixels[i].toInt()) and MASK_INT_LOWEST_BYTE
            val currentColor = act[currentColorIndex]
            if (currentColor != 0) {
                alphaSum += currentColor shr 24 and MASK_INT_LOWEST_BYTE
                redSum += currentColor shr 16 and MASK_INT_LOWEST_BYTE
                greenSum += currentColor shr 8 and MASK_INT_LOWEST_BYTE
                blueSum += currentColor and MASK_INT_LOWEST_BYTE
                totalAdded++
            }
            i++
        }
        return if (totalAdded == 0) {
            COLOR_TRANSPARENT_BLACK
        } else {
            (((alphaSum / totalAdded) shl 24)
                    or ((redSum / totalAdded) shl 16)
                    or ((greenSum / totalAdded) shl 8)
                    or (blueSum / totalAdded))
        }
    }

    /**
     * Decodes LZW image data into pixel array. Adapted from John Cristy's BitmapMagick.
     */
    private fun decodeBitmapData(frame: GifFrame?) {
        if (frame != null) {
            // Jump to the frame start position.
            requireNotNull(rawData).position(frame.bufferFrameStart)
        }

        val npix = if (frame == null) header.width * header.height else frame.iw * frame.ih
        var available: Int
        val clear: Int
        var codeMask: Int
        var codeSize: Int
        val endOfInformation: Int
        var inCode: Int
        var oldCode: Int
        var bits: Int
        var code: Int
        var count: Int
        var i: Int
        var datum: Int
        val dataSize: Int
        var first: Int
        var top: Int
        var bi: Int
        var pi: Int

        if (mainPixels == null || requireNotNull(mainPixels).size < npix) {
            // Allocate new pixel array.
            mainPixels = bitmapProvider.obtainByteArray(npix)
        }
        val mainPixels = requireNotNull(this.mainPixels)
        if (prefix == null) {
            prefix = ShortArray(MAX_STACK_SIZE)
        }
        val prefix = requireNotNull(this.prefix)
        if (suffix == null) {
            suffix = ByteArray(MAX_STACK_SIZE)
        }
        val suffix = requireNotNull(this.suffix)
        if (pixelStack == null) {
            pixelStack = ByteArray(MAX_STACK_SIZE + 1)
        }
        val pixelStack = requireNotNull(this.pixelStack)

        // Initialize GIF data stream decoder.
        dataSize = readByte()
        clear = 1 shl dataSize
        endOfInformation = clear + 1
        available = clear + 2
        oldCode = NULL_CODE
        codeSize = dataSize + 1
        codeMask = (1 shl codeSize) - 1

        code = 0
        while (code < clear) {
            // XXX ArrayIndexOutOfBoundsException.
            prefix[code] = 0
            suffix[code] = code.toByte()
            code++
        }
        val block = requireNotNull(this.block)
        // Decode GIF pixel stream.
        bi = 0
        pi = bi
        top = pi
        first = top
        count = first
        bits = count
        datum = bits
        i = datum
        while (i < npix) {
            // Read a new data block.
            if (count == 0) {
                count = readBlock()
                if (count <= 0) {
                    status = GifDecoder.STATUS_PARTIAL_DECODE
                    break
                }
                bi = 0
            }

            datum += ((block[bi].toInt()) and MASK_INT_LOWEST_BYTE) shl bits
            bits += 8
            ++bi
            --count

            while (bits >= codeSize) {
                // Get the next code.
                code = datum and codeMask
                datum = datum shr codeSize
                bits -= codeSize

                // Interpret the code.
                if (code == clear) {
                    // Reset decoder.
                    codeSize = dataSize + 1
                    codeMask = (1 shl codeSize) - 1
                    available = clear + 2
                    oldCode = NULL_CODE
                    continue
                } else if (code == endOfInformation) {
                    break
                } else if (oldCode == NULL_CODE) {
                    mainPixels[pi] = suffix[code]
                    ++pi
                    ++i
                    oldCode = code
                    first = code
                    continue
                }

                inCode = code
                if (code >= available) {
                    pixelStack[top] = first.toByte()
                    ++top
                    code = oldCode
                }

                while (code >= clear) {
                    pixelStack[top] = suffix[code]
                    ++top
                    code = prefix[code].toInt()
                }
                first = (suffix[code].toInt()) and MASK_INT_LOWEST_BYTE

                mainPixels[pi] = first.toByte()
                ++pi
                ++i

                while (top > 0) {
                    // Pop a pixel off the pixel stack.
                    mainPixels[pi] = pixelStack[--top]
                    ++pi
                    ++i
                }

                // Add a new string to the string table.
                if (available < MAX_STACK_SIZE) {
                    prefix[available] = oldCode.toShort()
                    suffix[available] = first.toByte()
                    ++available
                    if ((available and codeMask) == 0 && available < MAX_STACK_SIZE) {
                        ++codeSize
                        codeMask += available
                    }
                }
                oldCode = inCode
            }
        }

        // Clear missing pixels.
        Arrays.fill(mainPixels, pi, npix, COLOR_TRANSPARENT_BLACK.toByte())
    }

    /**
     * Reads a single byte from the input stream.
     */
    private fun readByte(): Int {
        return requireNotNull(rawData).get().toInt() and MASK_INT_LOWEST_BYTE
    }

    /**
     * Reads next variable length block from input.
     * 
     * @return number of bytes stored in "buffer".
     */
    private fun readBlock(): Int {
        val blockSize = readByte()
        if (blockSize <= 0) {
            return blockSize
        }
        val raw = requireNotNull(rawData)
        raw.get(requireNotNull(block), 0, min(blockSize, raw.remaining()))
        return blockSize
    }

    private val nextBitmap: Bitmap
        get() {
            val config = if (isFirstFrameTransparent == null || isFirstFrameTransparent == true)
                Bitmap.Config.ARGB_8888
            else
                bitmapConfig
            val result = bitmapProvider.obtain(downsampledWidth, downsampledHeight, config)
            result.setHasAlpha(true)
            return result
        }

    companion object {
        private val TAG: String = StandardGifDecoder::class.java.getSimpleName()

        /** Maximum pixel stack size for decoding LZW compressed data.  */
        private const val MAX_STACK_SIZE = 4 * 1024

        private const val NULL_CODE = -1

        private const val INITIAL_FRAME_POINTER = -1

        private const val BYTES_PER_INTEGER = Integer.SIZE / 8

        private const val MASK_INT_LOWEST_BYTE = 0x000000FF

        @ColorInt
        private const val COLOR_TRANSPARENT_BLACK = 0x00000000
    }
}
