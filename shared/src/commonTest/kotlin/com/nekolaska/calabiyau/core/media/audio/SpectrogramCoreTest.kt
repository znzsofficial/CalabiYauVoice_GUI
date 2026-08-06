package com.nekolaska.calabiyau.core.media.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpectrogramCoreTest {

    @Test
    fun limitsPixelAllocationForExtremeConfig() {
        val wav = silentWav(frameCount = 400_000, channels = 2)

        val pixels = buildSpectrogramPixels(
            wav,
            SpectrogramConfig(
                windowSize = 256,
                hopRatio = 0.05f,
                maxTimeBins = Int.MAX_VALUE,
                maxFrequencyBins = Int.MAX_VALUE
            )
        )

        assertTrue(pixels.argb.size <= MAX_SPECTROGRAM_PIXELS)
        assertEquals(pixels.width * pixels.height, pixels.argb.size)
    }

    @Test
    fun downsampledTimelineIncludesFinalWindow() {
        val frameCount = 1_280
        val windowSize = 256
        val pcm = ByteArray(frameCount * 2)
        for (frame in frameCount - windowSize until frameCount) {
            pcm[frame * 2] = 0xff.toByte()
            pcm[frame * 2 + 1] = 0x7f
        }
        val wav = PcmWavData(
            pcmData = pcm,
            channels = 1,
            sampleRate = 8_000,
            bitsPerSample = 16,
            blockAlign = 2
        )

        val pixels = buildSpectrogramPixels(
            wav,
            SpectrogramConfig(
                windowSize = windowSize,
                hopRatio = 0.25f,
                maxTimeBins = 4,
                maxFrequencyBins = 1,
                lowColorArgb = 0xff000000.toInt(),
                highColorArgb = 0xffffffff.toInt()
            )
        )

        assertEquals(4, pixels.width)
        assertTrue(pixels.argb.last() != pixels.argb.first())
    }

    private fun silentWav(frameCount: Int, channels: Int): PcmWavData = PcmWavData(
        pcmData = ByteArray(frameCount * channels * 2),
        channels = channels,
        sampleRate = 48_000,
        bitsPerSample = 16,
        blockAlign = channels * 2
    )
}
