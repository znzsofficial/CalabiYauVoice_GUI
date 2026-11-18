package com.nekolaska.calabiyau.feature.wiki.balance

import com.nekolaska.calabiyau.feature.wiki.balance.source.BalanceDataRemoteSource
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import kotlin.test.*

class BalanceSourceCancellationTest {
    @Test fun cancellingOldQueryClosesBodyAndAllowsNewPost() = runBlocking {
        val server = ServerSocket(0); val header = CompletableDeferred<Unit>()
        val peer = async(Dispatchers.IO) {
            server.soTimeout = 5000
            repeat(2) { index -> server.accept().use { socket ->
                socket.soTimeout = 5000
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                assertTrue(reader.readLine().startsWith("POST "))
                var length = 0
                while (true) { val line = reader.readLine(); if (line.isNullOrBlank()) break
                    if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt() }
                repeat(length) { assertTrue(reader.read() >= 0) }
                val response = if (index == 0) "HTTP/1.1 200 OK\r\nContent-Length: 9999\r\n\r\n{" else "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}"
                socket.getOutputStream().apply { write(response.toByteArray()); flush() }
                if (index == 0) { header.complete(Unit); assertEquals(-1, reader.read()) }
            } }
        }
        val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
        val url = "http://127.0.0.1:${server.localPort}/"
        try {
            val old = launch { BalanceDataRemoteSource.postQuery(client, url, "old") }
            withTimeout(5000) { header.await() }; old.cancelAndJoin()
            assertEquals("{}", BalanceDataRemoteSource.postQuery(client, url, "new").body)
            peer.await()
        } finally { server.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }
}
