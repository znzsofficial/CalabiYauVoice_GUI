package util

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

class CancelableTextReadTest {
    @Test fun callerCanClassifyStructuredErrorsAndInspectResponseHeaders() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(409).message("Conflict").header("X-Test-Revision", "42")
                .body("{\"errorCode\":\"CONFLICT\"}".toResponseBody()).build()
        }.build()
        try {
            val text = client.newCall(okhttp3.Request.Builder().url("https://test.invalid/").build()).awaitResponseText()
            assertEquals(409, text.code); assertFalse(text.isSuccessful)
            assertEquals("42", text.headers["X-Test-Revision"]); assertTrue("CONFLICT" in text.body)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun cancellationAfterHeadersClosesBodySocket() = verifyDisconnect { client, origin -> client.awaitGetString(origin) }

    @Test fun leavingDiscussionCancelsTheActualRepliesBody() = verifyDisconnect { client, origin ->
        data.CustomUserApi.fetchCommentsFrom(client, origin, "__public__", 1, 20, null, 17, null)
    }

    private fun verifyDisconnect(request: suspend (OkHttpClient, String) -> Unit) = runBlocking {
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
            val read = async(Dispatchers.IO) { request(client, "http://127.0.0.1:${server.localPort}") }
            assertTrue(withContext(Dispatchers.IO) { headers.await(5, TimeUnit.SECONDS) })
            read.cancelAndJoin()
            assertTrue(withContext(Dispatchers.IO) { disconnected.await(5, TimeUnit.SECONDS) }, "Body socket must close on cancellation")
        } finally { server.close(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll(); peer.join(5000) }
    }
}
