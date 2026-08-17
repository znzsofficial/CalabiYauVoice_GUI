package ui.components

import jna.flac.openNativeFlacPcmStream
import util.downmixToStereo
import util.openDesktopStreamingAudioInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Request
import data.WikiEngine
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.UnsupportedAudioFileException

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
        private var networkCall: Call? = null
        private var inputStream: Closeable? = null
        private var decodedStream: AudioInputStream? = null
        private var line: SourceDataLine? = null

        fun registerNetworkCall(call: Call): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) false else true.also { networkCall = call }
        }

        fun registerInputStream(stream: Closeable): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) false else true.also { inputStream = stream }
        }

        fun registerDecodedStream(stream: AudioInputStream): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) false else true.also { decodedStream = stream }
        }

        fun replaceDecodedStream(stream: AudioInputStream): Boolean = synchronized(resourceLock) {
            if (stopRequested.get()) return@synchronized false
            decodedStream = stream
            true
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
                val result = arrayOf(networkCall, line, decodedStream, inputStream)
                networkCall = null
                line = null
                decodedStream = null
                inputStream = null
                result
            }

            (resources[0] as? Call)?.cancel()
            (resources[1] as? SourceDataLine)?.let {
                runCatching { it.stop() }
                runCatching { it.close() }
            }
            runCatching { (resources[2] as? AudioInputStream)?.close() }
            runCatching { (resources[3] as? Closeable)?.close() }
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

    fun play(url: String, fileName: String? = null) {
        synchronized(controlLock) {
            val current = activeSession
            if (current?.url == url && current.thread?.isAlive == true && !current.stopRequested.get()) return

            val session = PlaybackSession(url)
            val thread = Thread { playSession(session, fileName) }.apply { isDaemon = true }
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

    private fun playSession(session: PlaybackSession, fileName: String?) {
        try {
            val call = WikiEngine.client.newCall(Request.Builder().url(session.url).build())
            if (!session.registerNetworkCall(call)) return
            val response = call.execute()
            if (!response.isSuccessful) {
                response.close()
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            if (!session.registerInputStream(response)) {
                response.close()
                return
            }
            val source = BufferedInputStream(response.body.byteStream())
            val effectiveFileName = fileName ?: session.url.substringAfterLast('/')
            val isFlac = effectiveFileName.substringBefore('?')
                .substringAfterLast('.', "")
                .equals("flac", ignoreCase = true)
            val inputStream = if (isFlac) {
                openNativeFlacPcmStream(source, outputBits = 16)
            } else {
                openDesktopStreamingAudioInputStream(source, effectiveFileName)
            }
            if (!session.registerDecodedStream(inputStream)) {
                inputStream.close()
                return
            }

            val baseFormat = inputStream.format
            val providerFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate, 16,
                baseFormat.channels, baseFormat.channels * 2,
                baseFormat.sampleRate, baseFormat.isBigEndian
            )
            val providerStream = if (inputStream.format.matches(providerFormat)) {
                inputStream
            } else {
                AudioSystem.getAudioInputStream(providerFormat, inputStream)
            }
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate, 16,
                baseFormat.channels, baseFormat.channels * 2,
                baseFormat.sampleRate, false
            )
            val pcmStream = if (providerFormat.isBigEndian) {
                AudioSystem.getAudioInputStream(decodedFormat, providerStream)
            } else {
                providerStream
            }
            val channelNormalizedStream = if (pcmStream.format.channels > 2) pcmStream.downmixToStereo() else pcmStream
            val decodedStream = selectPlaybackStream(channelNormalizedStream)
            if (decodedStream !== inputStream && !session.replaceDecodedStream(decodedStream)) {
                decodedStream.close()
                return
            }

            val playbackFormat = decodedStream.format
            val info = DataLine.Info(SourceDataLine::class.java, playbackFormat)
            val sourceLine = AudioSystem.getLine(info) as SourceDataLine
            if (!session.openLine(sourceLine, playbackFormat)) {
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
                val isUnsupportedAacProfile = e::class.simpleName == "AACException" &&
                    e.message?.contains("unsupported profile", ignoreCase = true) == true
                val message = if (isUnsupportedAacProfile) {
                    "AAC SSR profile is not supported by the bundled JavaSound AAC decoder"
                } else {
                    "${e::class.simpleName}: ${e.message}"
                }
                System.err.println("[AudioPlayerManager] Playback failed: $message")
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

private fun selectPlaybackStream(source: AudioInputStream): AudioInputStream {
    val sourceFormat = source.format
    val rates = buildList {
        add(sourceFormat.sampleRate)
        add(48_000f)
        add(44_100f)
        add(22_050f)
    }.filter { it.isFinite() && it > 0f }.distinct()

    var lastConversionError: Exception? = null
    for (rate in rates) {
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            rate,
            16,
            sourceFormat.channels,
            sourceFormat.channels * 2,
            rate,
            false
        )
        val lineInfo = DataLine.Info(SourceDataLine::class.java, target)
        if (!AudioSystem.isLineSupported(lineInfo)) continue
        if (sourceFormat.matches(target)) return source
        try {
            return AudioSystem.getAudioInputStream(target, source)
        } catch (error: Exception) {
            lastConversionError = error
        }
    }

    throw IllegalArgumentException(
        "No compatible audio output format for ${sourceFormat.sampleRate} Hz, ${sourceFormat.channels} channels",
        lastConversionError
    )
}

private fun AudioFormat.matches(other: AudioFormat): Boolean =
    encoding == other.encoding &&
        sampleRate == other.sampleRate &&
        sampleSizeInBits == other.sampleSizeInBits &&
        channels == other.channels &&
        frameSize == other.frameSize &&
        frameRate == other.frameRate &&
        isBigEndian == other.isBigEndian
