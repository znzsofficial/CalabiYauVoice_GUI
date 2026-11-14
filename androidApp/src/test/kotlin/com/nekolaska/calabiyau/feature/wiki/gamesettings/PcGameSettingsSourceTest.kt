package com.nekolaska.calabiyau.feature.wiki.gamesettings

import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    private fun payload(html: String, title: String = "游戏设置PC端") = buildJsonObject {
        putJsonObject("parse") { put("title", title); putJsonObject("text") { put("*", html) } }
    }.toString()
}
