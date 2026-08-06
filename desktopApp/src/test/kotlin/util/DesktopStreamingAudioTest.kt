package util

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStreamingAudioTest {
    @Test
    fun recognizesId3WithoutConsumingTheStream() {
        val bytes = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0)
        BufferedInputStream(ByteArrayInputStream(bytes)).use { source ->
            assertTrue(source.hasMp3Signature())
            assertTrue(source.read() == 'I'.code)
        }
    }

    @Test
    fun recognizesMpegFrameSync() {
        BufferedInputStream(ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte()))).use {
            assertTrue(it.hasMp3Signature())
        }
    }

    @Test
    fun rejectsAacAdtsSignature() {
        BufferedInputStream(ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x50.toByte()))).use {
            assertFalse(it.hasMp3Signature())
        }
    }

    @Test
    fun localMp3FilesUseMpegReaderInsteadOfServiceLoaderGuessing() {
        val file = Files.createTempFile("audio-route", ".mp3").toFile()
        try {
            assertFailsWith<UnsupportedAudioFileException> {
                openDesktopAudioInputStream(file)
            }
        } finally {
            file.delete()
        }
    }
}
