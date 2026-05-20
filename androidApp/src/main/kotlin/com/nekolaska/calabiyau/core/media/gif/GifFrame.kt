package com.nekolaska.calabiyau.core.media.gif

import androidx.annotation.ColorInt
import androidx.annotation.IntDef

/**
 * Inner model class housing metadata for each frame.
 * 
 * @see [GIF 89a Specification](https://www.w3.org/Graphics/GIF/spec-gif89a.txt)
 */
class GifFrame {
    /**
     * 
     * **GIF89a**:
     * *Indicates the way in which the graphic is to be treated after being displayed.*
     * Disposal methods 0-3 are defined, 4-7 are reserved for future use.
     * 
     * @see .DISPOSAL_UNSPECIFIED
     * 
     * @see .DISPOSAL_NONE
     * 
     * @see .DISPOSAL_BACKGROUND
     * 
     * @see .DISPOSAL_PREVIOUS
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [DISPOSAL_UNSPECIFIED, DISPOSAL_NONE, DISPOSAL_BACKGROUND, DISPOSAL_PREVIOUS])
    private annotation class GifDisposalMethod

    @JvmField
    var ix: Int = 0
    @JvmField
    var iy: Int = 0
    @JvmField
    var iw: Int = 0
    @JvmField
    var ih: Int = 0

    /**
     * Control Flag.
     */
    @JvmField
    var interlace: Boolean = false

    /**
     * Control Flag.
     */
    @JvmField
    var transparency: Boolean = false

    /**
     * Disposal Method.
     */
    @JvmField
    @GifDisposalMethod
    var dispose: Int = 0

    /**
     * Transparency Index.
     */
    @JvmField
    var transIndex: Int = 0

    /**
     * Delay, in milliseconds, to next frame.
     */
    @JvmField
    var delay: Int = 0

    /**
     * Index in the raw buffer where we need to start reading to decode.
     */
    @JvmField
    var bufferFrameStart: Int = 0

    /**
     * Local Color Table.
     */
    @JvmField
    @ColorInt
    var lct: IntArray? = null

    companion object {
        /**
         * GIF Disposal Method meaning take no action.
         * 
         * **GIF89a**: *No disposal specified.
         * The decoder is not required to take any action.*
         */
        const val DISPOSAL_UNSPECIFIED: Int = 0

        /**
         * GIF Disposal Method meaning leave canvas from previous frame.
         * 
         * **GIF89a**: *Do not dispose.
         * The graphic is to be left in place.*
         */
        const val DISPOSAL_NONE: Int = 1

        /**
         * GIF Disposal Method meaning clear canvas to background color.
         * 
         * **GIF89a**: *Restore to background color.
         * The area used by the graphic must be restored to the background color.*
         */
        const val DISPOSAL_BACKGROUND: Int = 2

        /**
         * GIF Disposal Method meaning clear canvas to frame before last.
         * 
         * **GIF89a**: *Restore to previous.
         * The decoder is required to restore the area overwritten by the graphic
         * with what was there prior to rendering the graphic.*
         */
        const val DISPOSAL_PREVIOUS: Int = 3
    }
}
