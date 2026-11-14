package com.nekolaska.calabiyau.feature.wiki.gamesettings

import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import com.nekolaska.calabiyau.feature.wiki.gamesettings.api.PcGameSettingsApi
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    @Test fun rejectedHttpSuccessIsParseFailureWhileNoResponseRemainsNetworkFailure() = runBlocking {
        val result = PcGameSettingsApi.loadSource {
            PcGameSettingsRemoteSource.fetchValidated { validate ->
                fetchValidatedCachePayload(true, { payload("<div class='mw-parser-output'>页面结构已变化</div>") },
                    { null }, { error("Invalid data must not be published") }, validate)
            }
        }
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(result).kind)
        val network = PcGameSettingsApi.loadSource {
            PcGameSettingsRemoteSource.fetchValidated { null }
        }
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(network).kind)
    }

    private fun payload(html: String, title: String = "游戏设置PC端") = buildJsonObject {
        putJsonObject("parse") { put("title", title); putJsonObject("text") { put("*", html) } }
    }.toString()
}
