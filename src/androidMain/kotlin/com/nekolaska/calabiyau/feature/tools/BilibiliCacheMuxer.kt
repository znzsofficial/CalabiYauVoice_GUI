package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
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

internal fun muxBilibiliM4sCache(
    context: Context,
    videoInput: PickedInput,
    audioInput: PickedInput,
    outputDir: File
): BilibiliMuxResult {
    val videoFile = materializeMuxInput(context, videoInput, "video", "m4s")
    val audioFile = materializeMuxInput(context, audioInput, "audio", "m4s")
    val deleteVideo = videoInput.file == null
    val deleteAudio = audioInput.file == null
    try {
        val baseName = videoFile.nameWithoutExtension
            .replace(Regex("(?i)(^|[_-])video($|[_-])"), "_")
            .trim('_', '-', ' ')
            .ifBlank { "bilibili_cache" }
        val outputFile = buildUniqueFile(outputDir, sanitizeFileName(baseName), "mp4")
        return muxTracks(videoFile, audioFile, outputFile)
    } finally {
        if (deleteVideo) runCatching { videoFile.delete() }
        if (deleteAudio) runCatching { audioFile.delete() }
    }
}

private fun materializeMuxInput(context: Context, input: PickedInput, prefix: String, fallbackExt: String): File {
    input.file?.takeIf { it.exists() }?.let { return it }
    val uri = input.uri ?: error("未选择${prefix}文件")
    val displayName = context.queryDisplayName(uri) ?: "$prefix.$fallbackExt"
    val ext = displayName.substringAfterLast('.', fallbackExt).ifBlank { fallbackExt }
    val temp = File.createTempFile("bili_${prefix}_", ".$ext", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        temp.outputStream().use { output -> inputStream.copyTo(output) }
    } ?: error("无法读取${displayName}")
    return temp
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
        val videoResult = extractSingleTrack(sourceFile, "video/", "视频", outputDir, "${baseName}_video")
        val audioResult = extractSingleTrack(sourceFile, "audio/", "音频", outputDir, "${baseName}_audio")
        val videoSamples = videoResult?.second ?: 0
        val audioSamples = audioResult?.second ?: 0
        if (videoSamples == 0 && audioSamples == 0) error("未找到可拆分的视频或音频轨道")
        return MediaSplitResult(
            videoFile = videoResult?.first,
            audioFile = audioResult?.first,
            videoSamples = videoSamples,
            audioSamples = audioSamples
        )
    } finally {
        if (deleteSource) runCatching { sourceFile.delete() }
    }
}

private fun muxTracks(videoFile: File, audioFile: File, outputFile: File): BilibiliMuxResult {
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

        val videoSamples = copySamples(videoExtractor, muxer, outVideoTrack)
        val audioSamples = copySamples(audioExtractor, muxer, outAudioTrack)
        if (videoSamples == 0) error("视频轨道没有可写入数据")
        if (audioSamples == 0) error("音频轨道没有可写入数据")
        return BilibiliMuxResult(outputFile, videoSamples, audioSamples)
    } catch (e: Exception) {
        runCatching { outputFile.delete() }
        throw e
    } finally {
        runCatching { muxer?.stop() }
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
    label: String,
    outputDir: File,
    baseName: String
): Pair<File, Int>? {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var outputFile: File? = null
    return try {
        extractor.setDataSource(sourceFile.absolutePath)
        val track = findTrack(extractor, mimePrefix) ?: return null
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val target = selectOutputTarget(mime, mimePrefix)
        outputFile = buildUniqueFile(outputDir, baseName, target.extension)
        extractor.selectTrack(track)
        muxer = MediaMuxer(outputFile.absolutePath, target.muxerFormat)
        val outTrack = muxer.addTrack(format)
        muxer.start()
        val samples = copySamples(extractor, muxer, outTrack)
        if (samples == 0) {
            runCatching { outputFile.delete() }
            return null
        }
        outputFile to samples
    } catch (e: Exception) {
        runCatching { outputFile?.delete() }
        val mime = runCatching {
            val track = findTrack(extractor, mimePrefix)
            track?.let { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        }.getOrNull().orEmpty()
        error("$label 轨道${if (mime.isBlank()) "" else "($mime)"}无法无转码导出：${e.message ?: "系统不支持该封装"}")
    } finally {
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
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
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.set(
            0,
            size,
            extractor.sampleTime.coerceAtLeast(0L),
            extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC
        )
        muxer.writeSampleData(outputTrack, buffer, info)
        sampleCount++
        extractor.advance()
    }
    return sampleCount
}
