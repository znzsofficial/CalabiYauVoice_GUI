package com.nekolaska.calabiyau.core.network

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.atomic.AtomicInteger
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import kotlin.test.*

class NetworkInfrastructureTest {
    @Test fun availableDoesNotImplyValidatedAndReplacedNetworksCannotChangeStatus() {
        val state = DefaultNetworkState<String>()
        assertFalse(state.available("wifi"))
        assertEquals(true, state.capabilities("wifi", true))
        assertFalse(state.available("cellular"))
        assertNull(state.capabilities("wifi", false)); assertNull(state.lost("wifi"))
        assertEquals(true, state.capabilities("cellular", true))
        assertEquals(false, state.capabilities("cellular", false))
        assertEquals(false, state.lost("cellular"))
    }

    @Test fun postAndOtherMutationsAreNeverReplayed() {
        assertFalse(WikiEngine.client.retryOnConnectionFailure)
        for (method in listOf("POST", "PUT", "DELETE")) {
            var calls = 0
            val client = OkHttpClient.Builder().addInterceptor(SafeReadRetryInterceptor(pause = {}, jitter = { 0 }))
                .addInterceptor { chain ->
                    calls++
                    assertTrue(chain.request().body?.isOneShot() == true)
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(503).message("fake").body("{}".toResponseBody()).build()
                }.build()
            val request = Request.Builder().url("https://example.test/api")
                .method(method, FormBody.Builder().build()).build()
            client.newCall(request).execute().use { assertEquals(503, it.code) }
            assertEquals(1, calls)
        }
    }

    @Test fun actualHttp503RetryAfterZeroDoesNotReplayAMutation() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(503, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            for (method in listOf("POST", "DELETE")) {
                requests.set(0)
                val request = Request.Builder().url("http://127.0.0.1:${server.address.port}/")
                    .method(method, if (method == "POST") FormBody.Builder().add("action", "edit").build() else null)
                    .build()
                WikiEngine.client.newCall(request).execute().use { assertEquals(503, it.code) }
                assertEquals(1, requests.get())
            }
        } finally { server.stop(0) }
    }

    @Test fun getRetriesAreBoundedAndCancellationInterruptsBackoff() {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(SafeReadRetryInterceptor(pause = {}, jitter = { 0 }))
            .addInterceptor { chain ->
                calls++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("fake").body("{}".toResponseBody()).build()
            }.build()
        client.newCall(Request.Builder().url("https://example.test").build()).execute().close()
        assertEquals(4, calls)
        lateinit var call: Call
        calls = 0
        val cancelled = client.newBuilder().apply { interceptors().clear() }
            .addInterceptor(SafeReadRetryInterceptor(pause = { call.cancel() }, jitter = { 0 }))
            .addInterceptor { chain ->
                calls++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("fake").body("{}".toResponseBody()).build()
            }.build()
        call = cancelled.newCall(Request.Builder().url("https://example.test").build())
        assertFailsWith<InterruptedIOException> { call.execute() }
        assertEquals(1, calls)
    }
}
