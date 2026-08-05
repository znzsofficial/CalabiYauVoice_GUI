package ui.components

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * 全局单例音频播放器，供多处 UI 共享。
 * 同一时刻只播放一个音频，使用流式播放避免卡顿。
 * 支持多个监听者，不会互相覆盖。
 */
object AudioPlayerManager {
    private class PlaybackSession(val url: String) {
        val stopRequested = AtomicBoolean(false)
        val loading = AtomicBoolean(false)
        val stoppedNotified = AtomicBoolean(false)

        @Volatile var thread: Thread? = null

        private val resourceLock = Any()
        private var inputStream: AudioInputStream? = null
        private var decodedStream: AudioInputStream? = null
        private var line: SourceDataLine? = null

        fun registerInputStream(stream: AudioInputStream): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) false else true.also { inputStream = stream }
        }

        fun registerDecodedStream(stream: AudioInputStream): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) false else true.also { decodedStream = stream }
        }

        fun openLine(sourceLine: SourceDataLine, format: AudioFormat): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) return@synchronized false
            line = sourceLine
            sourceLine.open(format)
            sourceLine.start()
            true
        }

        fun requestStop() {
            stopRequested.set(true)
            thread?.interrupt()
            closeResources()
        }

        fun closeResources() {
            val resources = synchronized(resourceLock) {
                val result = Triple(line, decodedStream, inputStream)
                line = null
                decodedStream = null
                inputStream = null
                result
            }

            resources.first?.let {
                runCatching { it.stop() }
                runCatching { it.close() }
            }
            runCatching { resources.second?.close() }
            runCatching { resources.third?.close() }
        }
    }

    private val controlLock = Any()
    @Volatile private var activeSession: PlaybackSession? = null

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
        synchronized(controlLock) {
            val current = activeSession
            if (current?.url == url && current.thread?.isAlive == true && !current.stopRequested.get()) return

            val session = PlaybackSession(url)
            val thread = Thread { playSession(session) }.apply { isDaemon = true }
            session.thread = thread
            activeSession = session

            current?.let { stopSession(it) }
            if (activeSession !== session || session.stopRequested.get()) return

            session.loading.set(true)
            notifyLoading(url, true)
            if (activeSession === session && !session.stopRequested.get()) {
                thread.start()
            }
        }
    }

    fun stop() {
        synchronized(controlLock) {
            val session = activeSession ?: return
            activeSession = null
            stopSession(session)
        }
    }

    private fun playSession(session: PlaybackSession) {
        try {
            val inputStream = AudioSystem.getAudioInputStream(URI(session.url).toURL())
            if (!session.registerInputStream(inputStream)) {
                inputStream.close()
                return
            }

            val baseFormat = inputStream.format
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate, 16,
                baseFormat.channels, baseFormat.channels * 2,
                baseFormat.sampleRate, false
            )
            val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inputStream)
            if (!session.registerDecodedStream(decodedStream)) {
                decodedStream.close()
                return
            }

            val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
            val sourceLine = AudioSystem.getLine(info) as SourceDataLine
            if (!session.openLine(sourceLine, decodedFormat)) {
                sourceLine.close()
                return
            }

            finishLoading(session)

            val buffer = ByteArray(8192)
            while (!session.stopRequested.get()) {
                val count = decodedStream.read(buffer)
                if (count == -1) break
                if (count > 0) sourceLine.write(buffer, 0, count)
            }
            if (!session.stopRequested.get()) sourceLine.drain()
        } catch (e: Exception) {
            if (!session.stopRequested.get()) {
                System.err.println("[AudioPlayerManager] 播放失败: ${e::class.simpleName}: ${e.message}")
            }
        } finally {
            session.closeResources()
            synchronized(controlLock) {
                if (activeSession === session) activeSession = null
                finishLoading(session)
                notifyStoppedOnce(session)
            }
        }
    }

    private fun stopSession(session: PlaybackSession) {
        session.requestStop()
        finishLoading(session)
        notifyStoppedOnce(session)
    }

    private fun finishLoading(session: PlaybackSession) {
        if (session.loading.compareAndSet(true, false)) notifyLoading(session.url, false)
    }

    private fun notifyStoppedOnce(session: PlaybackSession) {
        if (session.stoppedNotified.compareAndSet(false, true)) notifyStopped(session.url)
    }

    fun isPlaying(url: String): Boolean {
        val session = activeSession
        return session?.url == url && session.thread?.isAlive == true && !session.stopRequested.get()
    }

    @Suppress("unused")
    fun isLoading(url: String): Boolean {
        val session = activeSession
        return session?.url == url && session.loading.get()
    }
}
