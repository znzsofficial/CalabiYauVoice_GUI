package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

internal data class BilibiliMuxResult(
    val outputFile: File,
    val videoSamples: Int,
    val audioSamples: Int
)

internal data class MediaSplitResult(
    val videoFile: File?,
    val audioFile: File?,
    val videoSamples: Int,
    val audioSamples: Int
)

private data class TrackOutputTarget(
    val extension: String,
    val muxerFormat: Int
)

/** MediaMuxer.stop writes the trailer and can fail even after every sample was accepted. */
internal inline fun <T> writeFinalizedTracks(finish: () -> Unit, write: () -> T): T {
    val result = write()
    checkToolCancellation()
    finish()
    return result
}

internal fun muxBilibiliM4sCache(
    context: Context,
    videoInput: PickedInput,
    audioInput: PickedInput,
    outputDir: File
): BilibiliMuxResult {
    val videoFile = materializeMuxInput(context, videoInput, "video", "m4s")
    var ownedAudio: File? = null
    val deleteVideo = videoInput.file == null
    try {
        val audioFile = materializeMuxInput(context, audioInput, "audio", "m4s")
        if (audioInput.file == null) ownedAudio = audioFile
        val baseName = videoFile.nameWithoutExtension
            .replace(Regex("(?i)(^|[_-])video($|[_-])"), "_")
            .trim('_', '-', ' ')
            .ifBlank { "bilibili_cache" }
        val outputFile = buildUniqueFile(outputDir, sanitizeFileName(baseName), "mp4")
        lateinit var counts: Pair<Int, Int>
        val published = publishToolOutput(outputFile) { counts = muxTracks(videoFile, audioFile, it) }
        return BilibiliMuxResult(published, counts.first, counts.second)
    } finally {
        if (deleteVideo) runCatching { videoFile.delete() }
        ownedAudio?.delete()
    }
}

private fun materializeMuxInput(context: Context, input: PickedInput, prefix: String, fallbackExt: String): File {
    input.file?.takeIf { it.exists() }?.let { return it }
    val uri = input.uri ?: error("未选择${prefix}文件")
    val displayName = context.queryDisplayName(uri) ?: "$prefix.$fallbackExt"
    val ext = displayName.substringAfterLast('.', fallbackExt).ifBlank { fallbackExt }
    val temp = File.createTempFile("bili_${prefix}_", ".$ext", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            temp.outputStream().use { output -> inputStream.copyToolBytes(output) }
        } ?: error("无法读取${displayName}")
        return temp
    } catch (e: Throwable) { temp.delete(); throw e }
}

internal fun splitMediaTracks(
    context: Context,
    input: PickedInput,
    outputDir: File
): MediaSplitResult {
    val sourceFile = materializeMuxInput(context, input, "media", "mp4")
    val deleteSource = input.file == null
    try {
        val baseName = sanitizeFileName(sourceFile.nameWithoutExtension.ifBlank { "media" })
        return ToolOutputBatch().use { batch ->
            val videoSamples = extractSingleTrack(sourceFile, "video/", outputDir, "${baseName}_video", batch)
            val audioSamples = extractSingleTrack(sourceFile, "audio/", outputDir, "${baseName}_audio", batch)
            if (videoSamples == 0 && audioSamples == 0) error("未找到可拆分的视频或音频轨道")
            val published = batch.commit().iterator()
            MediaSplitResult(
                videoFile = if (videoSamples > 0) published.next() else null,
                audioFile = if (audioSamples > 0) published.next() else null,
                videoSamples = videoSamples,
                audioSamples = audioSamples
            )
        }
    } finally {
        if (deleteSource) runCatching { sourceFile.delete() }
    }
}

private fun muxTracks(videoFile: File, audioFile: File, outputFile: File): Pair<Int, Int> {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)

        val videoTrack = findTrack(videoExtractor, "video/") ?: error("video.m4s 中未找到视频轨道")
        val audioTrack = findTrack(audioExtractor, "audio/") ?: error("audio.m4s 中未找到音频轨道")
        val videoFormat = videoExtractor.getTrackFormat(videoTrack)
        val audioFormat = audioExtractor.getTrackFormat(audioTrack)

        videoExtractor.selectTrack(videoTrack)
        audioExtractor.selectTrack(audioTrack)
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outVideoTrack = muxer.addTrack(videoFormat)
        val outAudioTrack = muxer.addTrack(audioFormat)
        muxer.start()

        return writeFinalizedTracks(muxer::stop) {
            val videoSamples = copySamples(videoExtractor, muxer, outVideoTrack)
            val audioSamples = copySamples(audioExtractor, muxer, outAudioTrack)
            check(videoSamples > 0) { "视频轨道没有可写入数据" }
            check(audioSamples > 0) { "音频轨道没有可写入数据" }
            videoSamples to audioSamples
        }
    } finally {
        runCatching { muxer?.release() }
        videoExtractor.release()
        audioExtractor.release()
    }
}

private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int? {
    return (0 until extractor.trackCount).firstOrNull { index ->
        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
    }
}

private fun selectOutputTarget(mime: String, mimePrefix: String): TrackOutputTarget {
    val lowerMime = mime.lowercase()
    val webmCapable = lowerMime == "video/x-vnd.on2.vp8" || lowerMime == "video/x-vnd.on2.vp9" || lowerMime == "audio/vorbis" || lowerMime == "audio/opus"
    return when {
        webmCapable -> TrackOutputTarget("webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
        mimePrefix == "audio/" -> TrackOutputTarget("m4a", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        else -> TrackOutputTarget("mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }
}

private fun extractSingleTrack(
    sourceFile: File,
    mimePrefix: String,
    outputDir: File,
    baseName: String,
    batch: ToolOutputBatch
): Int {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(sourceFile.absolutePath)
        val track = findTrack(extractor, mimePrefix) ?: return 0
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val target = selectOutputTarget(mime, mimePrefix)
        val requested = File(outputDir, "$baseName.${target.extension}")
        extractor.selectTrack(track)
        var count = 0
        batch.stage(requested) { temporary ->
            val muxer = MediaMuxer(temporary.absolutePath, target.muxerFormat)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                count = writeFinalizedTracks(muxer::stop) {
                    copySamples(extractor, muxer, outTrack).also { check(it > 0) { "轨道没有可写入数据" } }
                }
            } finally { runCatching { muxer.release() } }
        }
        count
    } finally {
        extractor.release()
    }
}

private fun copySamples(extractor: MediaExtractor, muxer: MediaMuxer, outputTrack: Int): Int {
    val maxInputSize = (0 until extractor.trackCount)
        .map { extractor.getTrackFormat(it) }
        .mapNotNull { format -> if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else null }
        .maxOrNull()
        ?.coerceAtLeast(1 * 1024 * 1024)
        ?: (2 * 1024 * 1024)
    val buffer = ByteBuffer.allocateDirect(maxInputSize)
    val info = MediaCodec.BufferInfo()
    var sampleCount = 0

    while (true) {
        checkToolCancellation()
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        info.set(
            0,
            size,
            extractor.sampleTime.coerceAtLeast(0L),
            flags
        )
        checkToolCancellation()
        muxer.writeSampleData(outputTrack, buffer, info)
        sampleCount++
        extractor.advance()
    }
    return sampleCount
}
