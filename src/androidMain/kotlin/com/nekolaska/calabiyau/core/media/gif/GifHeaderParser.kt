package com.nekolaska.calabiyau.core.media.gif

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.pow
import kotlin.math.min

/**
 * A class responsible for creating [GifHeader]s from data
 * representing animated GIFs.
 * 
 * @see [GIF 89a Specification](https://www.w3.org/Graphics/GIF/spec-gif89a.txt)
 */
class GifHeaderParser {
    // Raw data read working array.
    private val block = ByteArray(MAX_BLOCK_SIZE)

    private var rawData: ByteBuffer? = null
    private var header: GifHeader? = null
    private var blockSize = 0

    fun setData(data: ByteBuffer): GifHeaderParser {
        reset()
        rawData = data.asReadOnlyBuffer()
        rawData!!.position(0)
        rawData!!.order(ByteOrder.LITTLE_ENDIAN)
        return this
    }

    fun setData(data: ByteArray?): GifHeaderParser {
        if (data != null) {
            setData(ByteBuffer.wrap(data))
        } else {
            rawData = null
            header!!.status = GifDecoder.STATUS_OPEN_ERROR
        }
        return this
    }

    fun clear() {
        rawData = null
        header = null
    }

    private fun reset() {
        rawData = null
        Arrays.fill(block, 0.toByte())
        header = GifHeader()
        blockSize = 0
    }

    fun parseHeader(): GifHeader {
        checkNotNull(rawData) { "You must call setData() before parseHeader()" }
        if (err()) {
            return header!!
        }

        readHeader()
        if (!err()) {
            readContents()
            if (header!!.numFrames < 0) {
                header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }

        return header!!
    }

    val isAnimated: Boolean
        /**
         * Determines if the GIF is animated by trying to read in the first 2 frames
         * This method re-parses the data even if the header has already been read.
         */
        get() {
            readHeader()
            if (!err()) {
                readContents(2 /* maxFrames */)
            }
            return header!!.numFrames > 1
        }

    /**
     * Main file parser. Reads GIF content blocks. Stops after reading maxFrames
     */
    /**
     * Main file parser. Reads GIF content blocks.
     */
    private fun readContents(maxFrames: Int = Int.MAX_VALUE) {
        // Read GIF file content blocks.
        var done = false
        while (!(done || err() || header!!.numFrames > maxFrames)) {
            val code = read()
            when (code) {
                IMAGE_SEPARATOR -> {
                    // The Graphic Control Extension is optional, but will always come first if it exists.
                    // If one did exist, there will be a non-null current frame which we should use.
                    // However if one did not exist, the current frame will be null
                    // and we must create it here. See issue #134.
                    if (header!!.currentFrame == null) {
                        header!!.currentFrame = GifFrame()
                    }
                    readBitmap()
                }

                EXTENSION_INTRODUCER -> {
                    val extensionLabel = read()
                    when (extensionLabel) {
                        LABEL_GRAPHIC_CONTROL_EXTENSION -> {
                            // Start a new frame.
                            header!!.currentFrame = GifFrame()
                            readGraphicControlExt()
                        }

                        LABEL_APPLICATION_EXTENSION -> {
                            readBlock()
                            val app = StringBuilder()
                            var i = 0
                            while (i < 11) {
                                app.append(Char(block[i].toUShort()))
                                i++
                            }
                            if (app.toString() == "NETSCAPE2.0") {
                                readNetscapeExt()
                            } else {
                                // Don't care.
                                skip()
                            }
                        }

                        LABEL_COMMENT_EXTENSION -> skip()
                        LABEL_PLAIN_TEXT_EXTENSION -> skip()
                        else ->               // Uninteresting extension.
                            skip()
                    }
                }

                TRAILER ->           // This block is a single-field block indicating the end of the GIF Data Stream.
                    done = true

                0x00 -> header!!.status = GifDecoder.STATUS_FORMAT_ERROR
                else -> header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }
    }

    /**
     * Reads Graphic Control Extension values.
     */
    private fun readGraphicControlExt() {
        // Block size.
        read()
        /*
     * Graphic Control Extension packed field:
     *      7 6 5 4 3 2 1 0
     *     +---------------+
     *  1  |     |     | | |
     *
     * Reserved                    3 Bits
     * Disposal Method             3 Bits
     * User Input Flag             1 Bit
     * Transparent Color Flag      1 Bit
     */
        val packed = read()
        // Disposal method.
        header!!.currentFrame!!.dispose = (packed and GCE_MASK_DISPOSAL_METHOD) shr GCE_DISPOSAL_METHOD_SHIFT
        if (header!!.currentFrame!!.dispose == GifFrame.DISPOSAL_UNSPECIFIED) {
            // Elect to keep old image if discretionary.
            header!!.currentFrame!!.dispose = GifFrame.DISPOSAL_NONE
        }
        header!!.currentFrame!!.transparency = (packed and GCE_MASK_TRANSPARENT_COLOR_FLAG) != 0
        // Delay in milliseconds.
        var delayInHundredthsOfASecond = readShort()
        // TODO: consider allowing -1 to indicate show forever.
        if (delayInHundredthsOfASecond < MIN_FRAME_DELAY) {
            delayInHundredthsOfASecond = DEFAULT_FRAME_DELAY
        }
        header!!.currentFrame!!.delay = delayInHundredthsOfASecond * 10
        // Transparent color index
        header!!.currentFrame!!.transIndex = read()
        // Block terminator
        read()
    }

    /**
     * Reads next frame image.
     */
    private fun readBitmap() {
        // (sub)image position & size.
        header!!.currentFrame!!.ix = readShort()
        header!!.currentFrame!!.iy = readShort()
        header!!.currentFrame!!.iw = readShort()
        header!!.currentFrame!!.ih = readShort()

        /*
     * Image Descriptor packed field:
     *     7 6 5 4 3 2 1 0
     *    +---------------+
     * 9  | | | |   |     |
     *
     * Local Color Table Flag     1 Bit
     * Interlace Flag             1 Bit
     * Sort Flag                  1 Bit
     * Reserved                   2 Bits
     * Size of Local Color Table  3 Bits
     */
        val packed = read()
        val lctFlag = (packed and DESCRIPTOR_MASK_LCT_FLAG) != 0
        val lctSize = 2.0.pow(((packed and DESCRIPTOR_MASK_LCT_SIZE) + 1).toDouble()).toInt()
        header!!.currentFrame!!.interlace = (packed and DESCRIPTOR_MASK_INTERLACE_FLAG) != 0
        if (lctFlag) {
            header!!.currentFrame!!.lct = readColorTable(lctSize)
        } else {
            // No local color table.
            header!!.currentFrame!!.lct = null
        }

        // Save this as the decoding position pointer.
        header!!.currentFrame!!.bufferFrameStart = rawData!!.position()

        // False decode pixel data to advance buffer.
        skipImageData()

        if (err()) {
            return
        }

        header!!.numFrames++
        // Add image to frame.
        header!!.currentFrame?.let { header!!.frames.add(it) }
    }

    /**
     * Reads Netscape extension to obtain iteration count.
     */
    private fun readNetscapeExt() {
        do {
            readBlock()
            if (block[0].toInt() == 1) {
                // Loop count sub-block.
                val b1 = (block[1].toInt()) and MASK_INT_LOWEST_BYTE
                val b2 = (block[2].toInt()) and MASK_INT_LOWEST_BYTE
                header!!.loopCount = (b2 shl 8) or b1
            }
        } while (blockSize > 0 && !err())
    }


    /**
     * Reads GIF file header information.
     */
    private fun readHeader() {
        val id = StringBuilder()
        for (i in 0..5) {
            id.append(read().toChar())
        }
        if (!id.toString().startsWith("GIF")) {
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            return
        }
        readLSD()
        if (header!!.gctFlag && !err()) {
            header!!.gct = readColorTable(header!!.gctSize)
            header!!.bgColor = header!!.gct!![header!!.bgIndex]
        }
    }

    /**
     * Reads Logical Screen Descriptor.
     */
    private fun readLSD() {
        // Logical screen size.
        header!!.width = readShort()
        header!!.height = readShort()
        /*
     * Logical Screen Descriptor packed field:
     *      7 6 5 4 3 2 1 0
     *     +---------------+
     *  4  | |     | |     |
     *
     * Global Color Table Flag     1 Bit
     * Color Resolution            3 Bits
     * Sort Flag                   1 Bit
     * Size of Global Color Table  3 Bits
     */
        val packed = read()
        header!!.gctFlag = (packed and LSD_MASK_GCT_FLAG) != 0
        header!!.gctSize = 2.0.pow((packed and LSD_MASK_GCT_SIZE) + 1).toInt()
        // Background color index.
        header!!.bgIndex = read()
        // Pixel aspect ratio
        header!!.pixelAspect = read()
    }

    /**
     * Reads color table as 256 RGB integer values.
     * 
     * @param nColors int number of colors to read.
     * @return int array containing 256 colors (packed ARGB with full alpha).
     */
    private fun readColorTable(nColors: Int): IntArray? {
        val nBytes = 3 * nColors
        var tab: IntArray? = null
        val c = ByteArray(nBytes)

        try {
            rawData!!.get(c)

            // TODO: what bounds checks are we avoiding if we know the number of colors?
            // Max size to avoid bounds checks.
            tab = IntArray(MAX_BLOCK_SIZE)
            var i = 0
            var j = 0
            while (i < nColors) {
                val r = (c[j++].toInt()) and MASK_INT_LOWEST_BYTE
                val g = (c[j++].toInt()) and MASK_INT_LOWEST_BYTE
                val b = (c[j++].toInt()) and MASK_INT_LOWEST_BYTE
                tab[i++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        } catch (e: BufferUnderflowException) {
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
        }

        return tab
    }

    /**
     * Skips LZW image data for a single frame to advance buffer.
     */
    private fun skipImageData() {
        // lzwMinCodeSize
        read()
        // data sub-blocks
        skip()
    }

    /**
     * Skips variable length blocks up to and including next zero length block.
     */
    private fun skip() {
        var blockSize: Int
        do {
            blockSize = read()
            val newPosition = min(rawData!!.position() + blockSize, rawData!!.limit())
            rawData!!.position(newPosition)
        } while (blockSize > 0)
    }

    /**
     * Reads next variable length block from input.
     */
    private fun readBlock() {
        blockSize = read()
        var n = 0
        if (blockSize > 0) {
            var count = 0
            try {
                while (n < blockSize) {
                    count = blockSize - n
                    rawData!!.get(block, n, count)

                    n += count
                }
            } catch (e: Exception) {
                header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }
    }

    /**
     * Reads a single byte from the input stream.
     */
    private fun read(): Int {
        var currByte = 0
        try {
            currByte = rawData!!.get().toInt() and MASK_INT_LOWEST_BYTE
        } catch (e: Exception) {
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
        }
        return currByte
    }

    /**
     * Reads next 16-bit value, LSB first.
     */
    private fun readShort(): Int {
        // Read 16-bit value.
        return rawData!!.getShort().toInt()
    }

    private fun err(): Boolean {
        return header!!.status != GifDecoder.STATUS_OK
    }

    companion object {
        private const val TAG = "GifHeaderParser"

        private const val MASK_INT_LOWEST_BYTE = 0x000000FF

        /** Identifies the beginning of an Image Descriptor.  */
        private const val IMAGE_SEPARATOR = 0x2C

        /** Identifies the beginning of an extension block.  */
        private const val EXTENSION_INTRODUCER = 0x21

        /** This block is a single-field block indicating the end of the GIF Data Stream.  */
        private const val TRAILER = 0x3B

        // Possible labels that identify the current extension block.
        private const val LABEL_GRAPHIC_CONTROL_EXTENSION = 0xF9
        private const val LABEL_APPLICATION_EXTENSION = 0xFF
        private const val LABEL_COMMENT_EXTENSION = 0xFE
        private const val LABEL_PLAIN_TEXT_EXTENSION = 0x01

        // Graphic Control Extension packed field masks
        /**
         * Mask (bits 4-2) to extract Disposal Method of the current frame.
         * 
         * @see GifFrame.GifDisposalMethod possible values
         */
        private const val GCE_MASK_DISPOSAL_METHOD = 28

        /**
         * Shift so the Disposal Method extracted from the packed value is on the least significant bit.
         */
        private const val GCE_DISPOSAL_METHOD_SHIFT = 2

        /**
         * Mask (bit 0) to extract Transparent Color Flag of the current frame.
         * 
         * **GIF89a**: *Indicates whether a transparency index is given
         * in the Transparent Index field.*
         * Possible values are:
         *  * 0 - Transparent Index is not given.
         *  * 1 - Transparent Index is given.
         * 
         */
        private const val GCE_MASK_TRANSPARENT_COLOR_FLAG = 1

        // Image Descriptor packed field masks (describing Local Color Table)
        /**
         * Mask (bit 7) to extract Local Color Table Flag of the current image.
         * 
         * **GIF89a**: *Indicates the presence of a Local Color Table
         * immediately following this Image Descriptor.*
         */
        private const val DESCRIPTOR_MASK_LCT_FLAG = 128

        /**
         * Mask (bit 6) to extract Interlace Flag of the current image.
         * 
         * **GIF89a**: *Indicates if the image is interlaced.
         * An image is interlaced in a four-pass interlace pattern.*
         * Possible values are:
         *  * 0 - Image is not interlaced.
         *  * 1 - Image is interlaced.
         * 
         */
        private const val DESCRIPTOR_MASK_INTERLACE_FLAG = 64

        /**
         * Mask (bits 2-0) to extract Size of the Local Color Table of the current image.
         * 
         * **GIF89a**: *If the Local Color Table Flag is set to 1, the value in this
         * field is used to calculate the number of bytes contained in the Local Color Table.
         * To determine that actual size of the color table, raise 2 to [the value of the field + 1].
         * This value should be 0 if there is no Local Color Table specified.*
         */
        private const val DESCRIPTOR_MASK_LCT_SIZE = 7

        // Logical Screen Descriptor packed field masks (describing Global Color Table)
        /**
         * Mask (bit 7) to extract Global Color Table Flag of the current image.
         * 
         * **GIF89a**: *Indicates the presence of a Global Color Table
         * immediately following this Image Descriptor.*
         * Possible values are:
         *  * 0 - No Global Color Table follows, the Background Color Index field is meaningless.
         *  * 1 - A Global Color Table will immediately follow,
         * the Background Color Index field is meaningful.
         * 
         */
        private const val LSD_MASK_GCT_FLAG = 128

        /**
         * Mask (bits 2-0) to extract Size of the Global Color Table of the current image.
         * 
         * **GIF89a**: *If the Global Color Table Flag is set to 1, the value in this
         * field is used to calculate the number of bytes contained in the Global Color Table.
         * To determine that actual size of the color table, raise 2 to [the value of the field + 1].
         * Even if there is no Global Color Table specified, set this field according to the above
         * formula so that decoders can choose the best graphics mode to display the stream in.*
         */
        private const val LSD_MASK_GCT_SIZE = 7

        /** The minimum frame delay in hundredths of a second.  */
        const val MIN_FRAME_DELAY: Int = 2

        /**
         * The default frame delay in hundredths of a second.
         * This is used for GIFs with frame delays less than the minimum.
         */
        const val DEFAULT_FRAME_DELAY: Int = 10

        private const val MAX_BLOCK_SIZE = 256
    }
}
