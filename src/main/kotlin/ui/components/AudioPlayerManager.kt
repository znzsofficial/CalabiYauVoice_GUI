package ui.components

import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * 全局单例音频播放器，供多处 UI 共享。
 * 同一时刻只播放一个音频，使用流式播放避免卡顿。
 * 支持多个监听者，不会互相覆盖。
 */
object AudioPlayerManager {
    private var playThread: Thread? = null
    private var line: SourceDataLine? = null
    private val stopFlag = AtomicBoolean(false)

    @Volatile private var currentUrl: String? = null
    @Volatile private var loadingUrl: String? = null

    private val stoppedListeners = mutableListOf<(String) -> Unit>()
    private val loadingListeners = mutableListOf<(String, Boolean) -> Unit>()

    fun addOnPlaybackStopped(listener: (String) -> Unit) {
        synchronized(stoppedListeners) { stoppedListeners.add(listener) }
    }
    fun removeOnPlaybackStopped(listener: (String) -> Unit) {
        synchronized(stoppedListeners) { stoppedListeners.remove(listener) }
    }
    fun addOnLoadingChanged(listener: (String, Boolean) -> Unit) {
        synchronized(loadingListeners) { loadingListeners.add(listener) }
    }
    fun removeOnLoadingChanged(listener: (String, Boolean) -> Unit) {
        synchronized(loadingListeners) { loadingListeners.remove(listener) }
    }

    private fun notifyStopped(url: String) =
        synchronized(stoppedListeners) { stoppedListeners.toList() }.forEach { it(url) }
    private fun notifyLoading(url: String, loading: Boolean) =
        synchronized(loadingListeners) { loadingListeners.toList() }.forEach { it(url, loading) }

    fun play(url: String) {
        if (currentUrl == url && isPlaying(url)) return
        stop()
        currentUrl = url
        loadingUrl = url
        stopFlag.set(false)
        notifyLoading(url, true)

        playThread = Thread {
            try {
                val inputStream = AudioSystem.getAudioInputStream(URL(url))
                val baseFormat = inputStream.format
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate, 16,
                    baseFormat.channels, baseFormat.channels * 2,
                    baseFormat.sampleRate, false
                )
                val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inputStream)
                val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                val sourceLine = (AudioSystem.getLine(info) as SourceDataLine).also {
                    it.open(decodedFormat)
                    it.start()
                }
                line = sourceLine

                // 打开完成，结束加载状态
                if (currentUrl == url) {
                    loadingUrl = null
                    notifyLoading(url, false)
                }

                val buffer = ByteArray(8192)
                while (!stopFlag.get()) {
                    val n = decodedStream.read(buffer)
                    if (n == -1) break
                    if (n > 0) sourceLine.write(buffer, 0, n)
                }
                sourceLine.drain()
                sourceLine.stop()
                sourceLine.close()
                decodedStream.close()
                inputStream.close()
            } catch (e: Exception) {
                if (!stopFlag.get()) {
                    System.err.println("[AudioPlayerManager] 播放失败: ${e::class.simpleName}: ${e.message}")
                }
            } finally {
                line = null
                loadingUrl = null
                val stoppedUrl = currentUrl
                if (stoppedUrl == url) {
                    currentUrl = null
                    notifyLoading(url, false)
                    notifyStopped(url)
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        val stoppedUrl = currentUrl
        stopFlag.set(true)
        currentUrl = null
        loadingUrl = null
        line?.stop()
        line?.close()
        line = null
        playThread?.interrupt()
        playThread = null
        if (stoppedUrl != null) {
            notifyLoading(stoppedUrl, false)
            notifyStopped(stoppedUrl)
        }
    }

    fun isPlaying(url: String): Boolean =
        currentUrl == url && playThread?.isAlive == true && !stopFlag.get()

    fun isLoading(url: String): Boolean = loadingUrl == url
}





