package com.nekolaska.calabiyau.feature.wiki.gamesettings

import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import com.nekolaska.calabiyau.feature.wiki.gamesettings.api.PcGameSettingsApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.network.NetworkMonitor.Route
import com.sun.net.httpserver.HttpServer
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import util.HttpTextResponse
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PcGameSettingsSourceTest {
    @Test fun blockedWrongTitleAndPartialParsePayloadsCannotReplaceGoodDiskData() {
        val fixture = checkNotNull(javaClass.getResource("/fixtures/gamesettings/pc_settings_variants.html")).readText(Charsets.UTF_8)
        val partial = payload(fixture)
        assertFalse(PcGameSettingsRemoteSource.isValidPayload(partial))
        assertFalse(PcGameSettingsRemoteSource.isValidPayload("<html>CDN challenge</html>"))
        assertFalse(PcGameSettingsRemoteSource.isValidPayload("""{"error":{"code":"missingtitle"}}"""))
        assertNull(PcGameSettingsRemoteSource.htmlFromPayload(payload(fixture, "游戏设置移动端")))
        assertNull(PcGameSettingsRemoteSource.htmlFromPayload("""{"parse":{"text":{"*":42}}}"""))
    }

    @Test fun siteErrorsKeepTheirKindAndDoNotReplayOrGetMaskedByCorruptDisk() = runBlocking {
        suspend fun failure(get: suspend () -> HttpTextResponse) = assertIs<ApiResult.Error>(PcGameSettingsApi.loadSource {
            PcGameSettingsRemoteSource.toSource(PcGameSettingsRemoteSource.loadPayload(false, { Route.VALIDATED },
                { OfflineCache.CacheResult("corrupt", true, 456) }, { error("Published invalid payload") },
                { error("Unexpected retry") }, get))
        })
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            // A replay must not mask the first response or spend another request.
            val status = if (calls.incrementAndGet() == 1) exchange.requestURI.path.drop(1).toInt() else 404
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        try {
            for (status in listOf(429, 503)) {
                calls.set(0)
                val blocked = failure { PcGameSettingsRemoteSource.get("http://127.0.0.1:${server.address.port}/$status") }
                assertEquals(ErrorKind.CDN_BLOCKED, blocked.kind)
                assertEquals(status, blocked.httpStatus)
                assertEquals(1, calls.get())
            }
        } finally { server.stop(0) }
        assertEquals(ErrorKind.UNKNOWN, failure { HttpTextResponse(502, "", Headers.headersOf("Retry-After", "30")) }.kind)
        assertEquals(ErrorKind.NOT_FOUND, failure { HttpTextResponse(200, """{"error":{"code":"missingtitle"}}""", Headers.headersOf()) }.kind)
        assertEquals(ErrorKind.PARSE, failure { HttpTextResponse(200, "<html>Unexpected page</html>", Headers.headersOf()) }.kind)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(PcGameSettingsApi.loadSource {
            PcGameSettingsRemoteSource.toSource(OfflineCache.CacheResult("corrupt", true, 1))
        }).kind)
        assertEquals(ErrorKind.NOT_FOUND, assertIs<ApiResult.Error>(PcGameSettingsApi.loadSource { null }).kind)
    }

    @Test fun offlineMissAndRepeatedTimeoutHaveFiniteAttemptBudgets() = runBlocking {
        for (route in listOf(Route.NONE, Route.UNVALIDATED)) for (force in listOf(false, true)) {
            var attempts = 0
            var waits = 0
            val result = PcGameSettingsApi.loadSource {
                PcGameSettingsRemoteSource.toSource(PcGameSettingsRemoteSource.loadPayload(force, { route }, { null },
                    { error("Unexpected write") }, { waits++ }) { attempts++; throw SocketTimeoutException() })
            }
            assertEquals(if (force) 0 else 1, waits)
            assertEquals(if (route == Route.NONE) 0 else if (force) 1 else 2, attempts)
            assertEquals(if (route == Route.NONE) ErrorKind.NETWORK else ErrorKind.TIMEOUT, assertIs<ApiResult.Error>(result).kind)
        }
    }

    @Test fun leavingDuringRecoveryCancelsThePendingSecondRead() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        var calls = 0
        val job = launch {
            PcGameSettingsRemoteSource.loadPayload(false, { Route.VALIDATED }, { null }, { error("Unexpected write") },
                { waiting.complete(Unit); awaitCancellation() }) { calls++; throw SocketTimeoutException() }
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(1, calls)
    }

    private fun payload(html: String, title: String = "游戏设置PC端") = buildJsonObject {
        putJsonObject("parse") { put("title", title); putJsonObject("text") { put("*", html) } }
    }.toString()
}
