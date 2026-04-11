package com.nekolaska.calabiyau.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Android 端音频播放器管理器（单例），基于 MediaPlayer。
 * 支持播放本地文件和网络 URL，同一时间只播放一个音频。
 */
object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSource: String? = null
    private var _isPlaying = mutableStateOf(false)
    private var _playingSource = mutableStateOf<String?>(null)
    private var _progress = mutableFloatStateOf(0f)

    val isPlaying: State<Boolean> = _isPlaying
    val playingSource: State<String?> = _playingSource

    private fun isCurrentSource(source: String): Boolean = currentSource == source

    fun play(source: String) {
        // 同一来源正在播放 → 暂停
        if (currentSource == source && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _isPlaying.value = false
            return
        }
        // 同一来源暂停中 → 继续
        if (currentSource == source && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
                return
            } catch (_: Exception) {
                // 播放器状态异常，释放后重新创建
                release()
            }
        }
        // 新来源或之前的已释放 → 创建新的
        release()
        currentSource = source
        _playingSource.value = source
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(source)
                setOnPreparedListener {
                    if (!isCurrentSource(source)) return@setOnPreparedListener
                    start()
                    _isPlaying.value = true
                }
                setOnCompletionListener {
                    if (!isCurrentSource(source)) return@setOnCompletionListener
                    _isPlaying.value = false
                    _playingSource.value = null
                    currentSource = null
                    _progress.floatValue = 0f
                }
                setOnErrorListener { _, _, _ ->
                    if (!isCurrentSource(source)) return@setOnErrorListener false
                    _isPlaying.value = false
                    _playingSource.value = null
                    currentSource = null
                    false
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            if (isCurrentSource(source)) {
                _isPlaying.value = false
                _playingSource.value = null
                currentSource = null
            }
        }
    }

    fun stop() {
        release()
    }

    fun getProgress(): Float {
        val mp = mediaPlayer ?: return 0f
        return try {
            if (mp.duration > 0) mp.currentPosition.toFloat() / mp.duration else 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun release() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) { }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        _isPlaying.value = false
        _playingSource.value = null
        _progress.floatValue = 0f
        currentSource = null
    }
}

/**
 * 小型音频播放按钮，适用于列表项中（文件管理器、分类文件列表等）。
 * @param source 音频来源（本地路径或网络 URL）
 * @param size 按钮大小
 */
@Composable
fun AudioPlayButton(
    source: String,
    size: Int = 36,
    modifier: Modifier = Modifier
) {
    val isPlaying by AudioPlayerManager.isPlaying
    val playingSource by AudioPlayerManager.playingSource
    val isThisPlaying = isPlaying && playingSource == source

    // 进度条定时刷新
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isThisPlaying) {
        while (isThisPlaying) {
            progress = AudioPlayerManager.getProgress()
            delay(200)
        }
        if (!isThisPlaying) progress = 0f
    }

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        // 进度圆环
        if (isThisPlaying) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(size.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        FilledTonalIconButton(
            onClick = { AudioPlayerManager.play(source) },
            modifier = Modifier.size((size - 4).dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isThisPlaying)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "暂停" else "播放",
                modifier = Modifier
                    .size((size * 0.45).dp),
                tint = if (isThisPlaying)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


