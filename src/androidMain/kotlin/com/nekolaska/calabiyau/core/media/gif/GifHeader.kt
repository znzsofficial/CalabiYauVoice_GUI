package com.nekolaska.calabiyau.core.media.gif

import androidx.annotation.ColorInt
import com.nekolaska.calabiyau.core.media.gif.GifDecoder.GifDecodeStatus

/**
 * A header object containing the number of frames in an animated GIF image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the GIF. Can
 * be shared by one or more [GifDecoder]s to play the same
 * animated GIF in multiple views.
 * 
 * @see [GIF 89a Specification](https://www.w3.org/Graphics/GIF/spec-gif89a.txt)
 */
class GifHeader {
    @JvmField
    @ColorInt
    var gct: IntArray? = null

    /**
     * Global status code of GIF data parsing.
     */
    @JvmField
    @get:GifDecodeStatus
    @GifDecodeStatus
    var status: Int = GifDecoder.STATUS_OK
    var numFrames: Int = 0

    @JvmField
    var currentFrame: GifFrame? = null
    @JvmField
    val frames: MutableList<GifFrame> = ArrayList()

    /** Logical screen size: Full image width.  */
    @JvmField
    var width: Int = 0

    /** Logical screen size: Full image height.  */
    @JvmField
    var height: Int = 0

    // 1 : global color table flag.
    @JvmField
    var gctFlag: Boolean = false

    /**
     * Size of Global Color Table.
     * The value is already computed to be a regular number, this field doesn't store the exponent.
     */
    @JvmField
    var gctSize: Int = 0

    /** Background color index into the Global/Local color table.  */
    @JvmField
    var bgIndex: Int = 0

    /**
     * Pixel aspect ratio.
     * Factor used to compute an approximation of the aspect ratio of the pixel in the original image.
     */
    @JvmField
    var pixelAspect: Int = 0

    @JvmField
    @ColorInt
    var bgColor: Int = 0
    @JvmField
    var loopCount: Int = NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST

    companion object {
        /** The "Netscape" loop count which means loop forever.  */
        const val NETSCAPE_LOOP_COUNT_FOREVER: Int = 0

        /** Indicates that this header has no "Netscape" loop count.  */
        const val NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST: Int = -1
    }
}
