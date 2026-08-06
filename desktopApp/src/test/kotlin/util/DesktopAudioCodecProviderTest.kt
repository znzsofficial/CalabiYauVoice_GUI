package util

import java.util.ServiceLoader
import javax.sound.sampled.spi.AudioFileReader
import javax.sound.sampled.spi.FormatConversionProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAudioCodecProviderTest {

    @Test
    fun vorbisAndAacReadersAreDiscoverable() {
        val readers = ServiceLoader.load(AudioFileReader::class.java)
            .map { it.javaClass.name }
            .toSet()

        assertTrue("com.github.axet.jvorbis.spi.file.OggVorbisAudioFileReader" in readers)
        assertTrue("net.sourceforge.jaad.spi.javasound.AACAudioFileReader" in readers)
        assertFalse(readers.any { it.startsWith("org.kc7bfi.jflac.") })
    }

    @Test
    fun vorbisAndAacConvertersAreDiscoverable() {
        val converters = ServiceLoader.load(FormatConversionProvider::class.java)
            .map { it.javaClass.name }
            .toSet()

        assertTrue("com.github.axet.jvorbis.spi.convert.OggVorbisFormatConversionProvider" in converters)
        assertTrue("net.sourceforge.jaad.spi.javasound.AACFormatConversionProvider" in converters)
        assertFalse(converters.any { it.startsWith("org.kc7bfi.jflac.") })
    }
}
