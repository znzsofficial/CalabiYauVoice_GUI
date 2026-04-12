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
    private val playerLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var currentSource: String? = null
    private var playbackToken: Long = 0L
    private var _isPlaying = mutableStateOf(false)
    private var _playingSource = mutableStateOf<String?>(null)
    private var _progress = mutableFloatStateOf(0f)

    val isPlaying: State<Boolean> = _isPlaying
    val playingSource: State<String?> = _playingSource

    private fun isActivePlayback(source: String, token: Long, player: MediaPlayer): Boolean =
        currentSource == source && playbackToken == token && mediaPlayer === player

    fun play(source: String) {
        synchronized(playerLock) {
            val existingPlayer = mediaPlayer

            // 同一来源正在播放 → 暂停
            if (currentSource == source && existingPlayer?.isPlaying == true) {
                try {
                    existingPlayer.pause()
                    _isPlaying.value = false
                    return
                } catch (_: Exception) {
                    // 播放器状态异常，释放后重新创建
                    releaseLocked()
                }
            }

            // 同一来源暂停中 → 继续
            if (currentSource == source && existingPlayer != null) {
                try {
                    existingPlayer.start()
                    _isPlaying.value = true
                    return
                } catch (_: Exception) {
                    releaseLocked()
                }
            }

            // 新来源或之前的已释放 → 创建新的
            releaseLocked()
            currentSource = source
            _playingSource.value = source
            val token = ++playbackToken

            try {
                val player = MediaPlayer()
                mediaPlayer = player
                player.apply {
                    setDataSource(source)
                    setOnPreparedListener {
                        synchronized(playerLock) {
                            if (!isActivePlayback(source, token, player)) return@setOnPreparedListener
                            try {
                                player.start()
                                _isPlaying.value = true
                            } catch (_: Exception) {
                                releaseLocked(player)
                            }
                        }
                    }
                    setOnCompletionListener {
                        synchronized(playerLock) {
                            if (!isActivePlayback(source, token, player)) return@setOnCompletionListener
                            releaseLocked(player)
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        synchronized(playerLock) {
                            if (isActivePlayback(source, token, player)) {
                                releaseLocked(player)
                            }
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (_: Exception) {
                if (currentSource == source && playbackToken == token) {
                    releaseLocked()
                }
            }
        }
    }

    fun stop() {
        synchronized(playerLock) { releaseLocked() }
    }

    fun getProgress(): Float {
        val mp = synchronized(playerLock) { mediaPlayer } ?: return 0f
        return try {
            if (mp.duration > 0) mp.currentPosition.toFloat() / mp.duration else 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun getDuration(): Int {
        return try {
            synchronized(playerLock) { mediaPlayer?.duration ?: 0 }
        } catch (_: Exception) {
            0
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            synchronized(playerLock) { mediaPlayer?.currentPosition ?: 0 }
        } catch (_: Exception) {
            0
        }
    }

    private fun releaseLocked(expectedPlayer: MediaPlayer? = null) {
        val player = mediaPlayer ?: return clearPlaybackState()
        if (expectedPlayer != null && player !== expectedPlayer) return
        try {
            player.stop()
        } catch (_: Exception) { }
        try {
            player.release()
        } catch (_: Exception) { }
        playbackToken++
        mediaPlayer = null
        clearPlaybackState()
    }

    private fun clearPlaybackState() {
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


