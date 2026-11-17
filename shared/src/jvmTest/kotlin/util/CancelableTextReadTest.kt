package util

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

class CancelableTextReadTest {
    @Test fun cancellationAfterHeadersClosesBodySocket() = runBlocking {
        val server = ServerSocket(0)
        val headers = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val peer = thread(isDaemon = true) {
            server.use {
                it.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\n{".toByteArray())
                        flush()
                    }
                    headers.countDown()
                    if (socket.getInputStream().read() == -1) disconnected.countDown()
                }
            }
        }
        val client = OkHttpClient()
        try {
            val read = async(Dispatchers.IO) { client.awaitGetString("http://127.0.0.1:${server.localPort}/") }
            assertTrue(withContext(Dispatchers.IO) { headers.await(5, TimeUnit.SECONDS) })
            read.cancelAndJoin()
            assertTrue(withContext(Dispatchers.IO) { disconnected.await(5, TimeUnit.SECONDS) }, "Body socket must close on cancellation")
        } finally { server.close(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll(); peer.join(5000) }
    }
}
