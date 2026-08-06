package jna.flac

import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream

private const val LIBFLAC_SHA256 = "f93499172875fc2c0df80b57086f32e3f39e835283952ee2a59a3d4ffb097644"
private const val STATE_END_OF_STREAM = 4
private const val STATE_ABORTED = 7
private const val READ_CONTINUE = 0
private const val READ_END_OF_STREAM = 1
private const val READ_ABORT = 2
private const val WRITE_CONTINUE = 0
private const val WRITE_ABORT = 1

internal fun openNativeFlacPcmStream(source: InputStream, outputBits: Int? = null): AudioInputStream {
    val decoded = try {
        NativeFlacPcmInputStream(source, outputBits)
    } catch (error: Throwable) {
        runCatching { source.close() }
        throw error
    }
    return try {
        AudioInputStream(decoded, decoded.format, AudioSystemNotSpecified)
    } catch (error: Throwable) {
        decoded.close()
        throw error
    }
}

private class NativeFlacPcmInputStream(
    private val source: InputStream,
    private val requestedOutputBits: Int?
) : InputStream() {
    private val native = NativeFlacLibrary.instance
    private val decoder: Pointer = native.FLAC__stream_decoder_new()
        ?: throw IOException("libFLAC could not allocate a decoder")
    private val nativeLock = Any()
    private val inputBuffer = ByteArray(64 * 1024)

    @Volatile private var closed = false
    private var deleted = false
    private var reachedEnd = false
    private var callbackFailure: IOException? = null
    private var decoderError: Int? = null
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    private var sourceBits = 0
    private var outputBits = 0
    private var channels = 0
    private var sampleRate = 0
    private var expectedSamples = 0L
    private var decodedSamples = 0L

    private val readCallback = LibFlac.ReadCallback { _, buffer, bytes, _ ->
        try {
            if (closed || Thread.currentThread().isInterrupted) {
                writeSizeT(bytes, 0)
                READ_ABORT
            } else {
                val requested = readSizeT(bytes).coerceAtMost(inputBuffer.size.toLong()).toInt()
                if (requested <= 0) {
                    writeSizeT(bytes, 0)
                    READ_ABORT
                } else {
                    val count = source.read(inputBuffer, 0, requested)
                    if (count < 0) {
                        writeSizeT(bytes, 0)
                        READ_END_OF_STREAM
                    } else {
                        buffer.write(0, inputBuffer, 0, count)
                        writeSizeT(bytes, count.toLong())
                        READ_CONTINUE
                    }
                }
            }
        } catch (error: Throwable) {
            callbackFailure = error.asIOException("Failed to read FLAC input")
            writeSizeT(bytes, 0)
            READ_ABORT
        }
    }

    private val writeCallback = LibFlac.WriteCallback { activeDecoder, _, buffers, _ ->
        try {
            val blockSize = native.FLAC__stream_decoder_get_blocksize(activeDecoder)
            val frameChannels = native.FLAC__stream_decoder_get_channels(activeDecoder)
            val frameBits = native.FLAC__stream_decoder_get_bits_per_sample(activeDecoder)
            val frameSampleRate = native.FLAC__stream_decoder_get_sample_rate(activeDecoder)
            if (channels == 0) {
                sourceBits = frameBits
                channels = frameChannels
                sampleRate = frameSampleRate
                outputBits = requestedOutputBits ?: when (sourceBits) {
                    in 4..8 -> 8
                    in 9..16 -> 16
                    in 17..24 -> 24
                    else -> 32
                }
            }
            if (blockSize <= 0 || frameChannels != channels || frameBits != sourceBits || frameSampleRate != sampleRate) {
                throw IOException("FLAC frame format changed unexpectedly")
            }
            pending = interleavePcm(buffers, blockSize)
            pendingOffset = 0
            decodedSamples = Math.addExact(decodedSamples, blockSize.toLong())
            WRITE_CONTINUE
        } catch (error: Throwable) {
            callbackFailure = error.asIOException("Failed to convert decoded FLAC samples")
            WRITE_ABORT
        }
    }

    private val errorCallback = LibFlac.ErrorCallback { _, status, _ -> decoderError = status }

    val format: AudioFormat

    init {
        try {
            if (requestedOutputBits != null && requestedOutputBits !in setOf(8, 16, 24, 32)) {
                throw IllegalArgumentException("Unsupported FLAC PCM output depth: $requestedOutputBits")
            }
            native.FLAC__stream_decoder_set_md5_checking(decoder, 1)
            val initStatus = native.FLAC__stream_decoder_init_stream(
                decoder,
                readCallback,
                null,
                null,
                null,
                null,
                writeCallback,
                null,
                errorCallback,
                null
            )
            if (initStatus != 0) throw IOException("libFLAC decoder initialization failed (status $initStatus)")

            if (native.FLAC__stream_decoder_process_until_end_of_metadata(decoder) == 0) {
                callbackFailure?.let { throw it }
                throw IOException("libFLAC metadata decode failed: ${native.FLAC__stream_decoder_get_resolved_state_string(decoder)}")
            }
            callbackFailure?.let { throw it }
            decoderError?.let { throw IOException("libFLAC rejected the metadata (error status $it)") }
            expectedSamples = native.FLAC__stream_decoder_get_total_samples(decoder)
            processNextFrame()
            if (sourceBits !in 4..32 || channels !in 1..8 || sampleRate <= 0) {
                throw IOException("Invalid FLAC stream format: $sampleRate Hz, $sourceBits bit, $channels channels")
            }
            val frameSize = channels * (outputBits / 8)
            format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate.toFloat(),
                outputBits,
                channels,
                frameSize,
                sampleRate.toFloat(),
                false
            )
        } catch (error: Throwable) {
            cleanup(false)
            throw error
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > bytes.size - offset) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        checkOpen()

        var written = 0
        while (written < length) {
            if (pendingOffset < pending.size) {
                val count = minOf(length - written, pending.size - pendingOffset)
                pending.copyInto(bytes, offset + written, pendingOffset, pendingOffset + count)
                pendingOffset += count
                written += count
                continue
            }
            if (reachedEnd) break
            processNextFrame()
        }
        return if (written == 0 && reachedEnd) -1 else written
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { source.close() }
        synchronized(nativeLock) { cleanup(reachedEnd) }
    }

    private fun processNextFrame() {
        pending = ByteArray(0)
        pendingOffset = 0
        while (pending.isEmpty() && !reachedEnd) processOne()
    }

    private fun processOne() = synchronized(nativeLock) {
        checkOpen()
        checkInterrupted()
        callbackFailure?.let { throw it }
        decoderError = null
        if (native.FLAC__stream_decoder_process_single(decoder) == 0) {
            callbackFailure?.let { throw it }
            throw IOException("libFLAC decode failed: ${native.FLAC__stream_decoder_get_resolved_state_string(decoder)}")
        }
        callbackFailure?.let { throw it }
        checkInterrupted()
        decoderError?.let { throw IOException("libFLAC rejected the stream (error status $it)") }
        when (native.FLAC__stream_decoder_get_state(decoder)) {
            STATE_END_OF_STREAM -> finishAtEnd()
            STATE_ABORTED -> throw IOException("libFLAC decoding was aborted")
        }
    }

    private fun finishAtEnd() {
        if (reachedEnd) return
        reachedEnd = true
        if (native.FLAC__stream_decoder_finish(decoder) == 0) {
            throw IOException("FLAC MD5 verification failed")
        }
        if (expectedSamples > 0 && decodedSamples != expectedSamples) {
            throw IOException("FLAC sample count mismatch: decoded $decodedSamples, expected $expectedSamples")
        }
    }

    private fun interleavePcm(buffers: Pointer, blockSize: Int): ByteArray {
        val bytesPerSample = outputBits / 8
        val result = ByteArray(Math.multiplyExact(Math.multiplyExact(blockSize, channels), bytesPerSample))
        val shift = outputBits - sourceBits
        val channelSamples = Array(channels) { channel ->
            buffers.getPointer(channel.toLong() * Native.POINTER_SIZE)
                ?.getIntArray(0, blockSize)
                ?: throw IOException("libFLAC returned a null channel buffer")
        }
        var destination = 0
        for (sampleIndex in 0 until blockSize) {
            for (channel in 0 until channels) {
                val sourceSample = channelSamples[channel][sampleIndex]
                val sample = if (shift >= 0) sourceSample shl shift else sourceSample shr -shift
                for (byteIndex in 0 until bytesPerSample) {
                    result[destination++] = (sample ushr (byteIndex * 8)).toByte()
                }
            }
        }
        return result
    }

    private fun cleanup(verifyMd5: Boolean) {
        if (deleted) return
        deleted = true
        if (!reachedEnd || !verifyMd5) runCatching { native.FLAC__stream_decoder_finish(decoder) }
        native.FLAC__stream_decoder_delete(decoder)
        runCatching { source.close() }
    }

    private fun checkOpen() {
        if (closed) throw IOException("FLAC stream is closed")
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("FLAC decoding was interrupted")
    }
}

private object NativeFlacLibrary {
    val instance: LibFlac by lazy {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val arch = System.getProperty("os.arch", "").lowercase(Locale.ROOT)
        if (!os.contains("windows") || arch !in setOf("amd64", "x86_64", "x64")) {
            throw IOException("libFLAC is packaged only for Windows x64 (current: $os/$arch)")
        }
        val dll = resolveLibraryFile()
        val digest = sha256(dll)
        if (!digest.equals(LIBFLAC_SHA256, ignoreCase = true)) {
            throw IOException("Unexpected libFLAC.dll SHA-256 at ${dll.absolutePath}: $digest")
        }
        Native.load(dll.absolutePath, LibFlac::class.java)
    }

    private fun resolveLibraryFile(): File {
        val explicit = System.getProperty("calabiyau.libflac.path")?.takeIf(String::isNotBlank)?.let(::File)
        val resources = System.getProperty("compose.application.resources.dir")
            ?.takeIf(String::isNotBlank)
            ?.let { File(it, "libFLAC.dll") }
        val candidates = listOfNotNull(
            explicit,
            resources,
            File("appResources/windows-x64/libFLAC.dll"),
            File("desktopApp/appResources/windows-x64/libFLAC.dll")
        )
        return candidates.firstOrNull(File::isFile)
            ?: throw IOException("libFLAC.dll was not found; checked ${candidates.joinToString { it.absolutePath }}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath()).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private const val AudioSystemNotSpecified = -1L

private fun readSizeT(pointer: Pointer): Long =
    if (Native.SIZE_T_SIZE == Long.SIZE_BYTES) pointer.getLong(0) else pointer.getInt(0).toLong() and 0xffff_ffffL

private fun writeSizeT(pointer: Pointer, value: Long) {
    if (Native.SIZE_T_SIZE == Long.SIZE_BYTES) pointer.setLong(0, value) else pointer.setInt(0, value.toInt())
}

private fun Throwable.asIOException(message: String): IOException =
    this as? IOException ?: IOException(message, this)
