package jna.flac

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer

internal interface LibFlac : Library {
    fun FLAC__stream_decoder_new(): Pointer?
    fun FLAC__stream_decoder_delete(decoder: Pointer)
    fun FLAC__stream_decoder_set_md5_checking(decoder: Pointer, value: Int): Int
    fun FLAC__stream_decoder_init_stream(
        decoder: Pointer,
        readCallback: ReadCallback,
        seekCallback: Callback?,
        tellCallback: Callback?,
        lengthCallback: Callback?,
        eofCallback: Callback?,
        writeCallback: WriteCallback,
        metadataCallback: Callback?,
        errorCallback: ErrorCallback,
        clientData: Pointer?
    ): Int
    fun FLAC__stream_decoder_process_single(decoder: Pointer): Int
    fun FLAC__stream_decoder_process_until_end_of_metadata(decoder: Pointer): Int
    fun FLAC__stream_decoder_get_state(decoder: Pointer): Int
    fun FLAC__stream_decoder_get_resolved_state_string(decoder: Pointer): String
    fun FLAC__stream_decoder_get_total_samples(decoder: Pointer): Long
    fun FLAC__stream_decoder_get_channels(decoder: Pointer): Int
    fun FLAC__stream_decoder_get_bits_per_sample(decoder: Pointer): Int
    fun FLAC__stream_decoder_get_sample_rate(decoder: Pointer): Int
    fun FLAC__stream_decoder_get_blocksize(decoder: Pointer): Int
    fun FLAC__stream_decoder_finish(decoder: Pointer): Int

    fun interface ReadCallback : Callback {
        fun invoke(decoder: Pointer, buffer: Pointer, bytes: Pointer, clientData: Pointer?): Int
    }

    fun interface WriteCallback : Callback {
        fun invoke(decoder: Pointer, frame: Pointer, buffers: Pointer, clientData: Pointer?): Int
    }

    fun interface ErrorCallback : Callback {
        fun invoke(decoder: Pointer, status: Int, clientData: Pointer?)
    }
}
