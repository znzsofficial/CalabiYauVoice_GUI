package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jsoup.Jsoup

class DecorationHtmlCachePayloadTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/decoration/$name.html")).readText(Charsets.UTF_8)
    private fun payload(html: String, title: String = "勋章") = buildJsonObject {
        putJsonObject("parse") { put("title", title); putJsonObject("text") { put("*", html) } }
    }.toString()

    @Test fun validJsonWithBadDirectoryCannotReplaceGoodDiskSnapshot() = runBlocking {
        val good = payload(fixture("modern-contract"))
        var disk = good
        val partial = Jsoup.parse(fixture("modern-contract")).apply {
            selectFirst(".gallerygrid-item")!!.select(".roomgrid__image-bg").remove()
        }.outerHtml()
        for (bad in listOf(payload(""), payload("<div class='mw-parser-output'>模板错误</div>"),
            payload(partial), payload(fixture("modern-contract"), "其他页面"))) {
            val result = fetchValidatedCachePayload(true, { bad }, { OfflineCache.CacheResult(disk, true, 45) },
                { disk = it }, { DecorationHtmlCachePayload.isValid("勋章", it) })
            assertEquals(good, disk)
            assertEquals(good, result?.payload)
            assertTrue(result?.isFromCache == true)
            assertEquals(45L, result.ageMs)
        }
    }

    @Test fun oldSmallCompleteAndNewMissingArtworkDirectoriesRemainPublishable() = runBlocking {
        for (name in listOf("legacy-gallery-room", "namecards", "missing-main-artwork")) {
            val response = payload(fixture(name))
            var disk: String? = null
            val result = fetchValidatedCachePayload(true, { response }, { null }, { disk = it },
                { DecorationHtmlCachePayload.isValid("勋章", it) })
            assertEquals(response, disk)
            assertEquals(false, result?.isFromCache)
            assertTrue(DecorationHtmlCachePayload.html("勋章", response) != null)
        }
    }

    @Test fun invalidEnvelopeAndInvalidDiskBodyAreRejectedOnReadToo() {
        for (bad in listOf("{}", "<html>edge error</html>", """{"error":{},"parse":{}}""",
            """{"parse":{"title":123,"text":{"*":""}}}""",
            """{"parse":{"title":"勋章","text":{"*":123}}}""",
            """{"parse":{"text":{"*":""}}}""", payload("<div class='mw-parser-output'></div>"))) {
            assertFalse(DecorationHtmlCachePayload.isValid("勋章", bad))
            assertNull(DecorationHtmlCachePayload.html("勋章", bad))
        }
    }
}
