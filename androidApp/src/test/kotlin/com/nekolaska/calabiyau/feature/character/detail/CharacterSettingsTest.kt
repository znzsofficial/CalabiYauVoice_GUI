package com.nekolaska.calabiyau.feature.character.detail

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import data.ApiResult
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class CharacterSettingsTest {
    private fun fixture() = checkNotNull(javaClass.getResource("/fixtures/character/settings.html")).readText(Charsets.UTF_8)
    private fun payload(html: String = fixture()) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()
    private fun response(offline: Boolean = false, age: Long = 0) = OfflineCache.CacheResult(payload(), offline, age)

    @Test fun keepsTraitsWrappedDefinitionsRelationsAndCanonicalNames() {
        val result = CharacterSettingsParser.parse(fixture())
        assertTrue(result.canCache, result.issues.toString())
        val michelle = result.entries.getValue(CharacterSettingsParser.identity("米雪儿・李"))
        assertTrue(michelle.lifeInfo.single { it.label == "萌点" }.value.contains("猫奴"))
        assertEquals(listOf("父亲", "小队队长", "教官&小队副队长"), michelle.relations.map { it.label })
        assertEquals(listOf("乔治·李", "信", "心夏"), michelle.relations.map { it.value })
        assertTrue(result.entries.getValue(CharacterSettingsParser.identity("奥黛丽")).relations.isEmpty())
        assertNull(result.entries[CharacterSettingsParser.identity("米雪儿")])
    }

    @Test fun linkedNamesLineBreaksFutureFieldsAndEmptyNarrativeSlotsSurvive() {
        val html = fixture().replace("米雪儿·李<span", "<a href='/klbq/米雪儿·李'>米雪儿</a><span")
            .replace("<dt>生日</dt><dd>3月25日</dd>", "<dt>新增字段：</dt><dd>第一行<br>第二行<sup class='reference'>[1]</sup></dd>")
        val result = CharacterSettingsParser.parse(html)
        assertTrue(result.canCache, result.issues.toString())
        assertEquals("第一行\n第二行", result.entries.getValue("米雪儿李").lifeInfo.single { it.label == "新增字段" }.value)
        assertEquals("", result.entries.getValue("奥黛丽格罗夫").settingObserverQuote)
    }

    @Test fun relationsDoNotDependOnParagraphOrSpanWrappersAndAcceptAsciiColon() {
        val document = Jsoup.parse(fixture())
        document.select(".data span, .data p").toList().reversed().forEach { it.unwrap() }
        val result = CharacterSettingsParser.parse(document.html().replace("父亲：", "父亲:"))
        assertTrue(result.canCache, result.issues.toString())
        assertEquals(listOf("乔治·李", "信", "心夏"), result.entries.getValue("米雪儿李").relations.map { it.value })
    }

    @Test fun identicalDuplicatesDeduplicateButConflictingOrPartialBlocksRejectCache() {
        assertTrue(CharacterSettingsParser.parse(fixture() + fixture()).canCache)
        assertEquals(2, CharacterSettingsParser.parse(fixture() + fixture()).entries.size)
        val conflict = CharacterSettingsParser.parse(fixture() + fixture().replace("大小姐", "不同设定"))
        assertFalse(conflict.canCache)
        assertFalse("奥黛丽格罗夫" in conflict.entries)
        for (html in listOf("<p>验证访问</p>", fixture().replace("<dt>萌点</dt>", ""),
            fixture().replace("<dt>观测语录</dt><dd></dd>", ""), fixture() + "<div class='klbq-role-info'></div>")) {
            assertFalse(CharacterSettingsParser.parse(html).canCache)
            assertFalse(CharacterSettingsApi.isValidPayload(payload(html)))
        }
    }

    @Test fun mergeSuppressesOnlyIdenticalMainFieldsAndKeepsDifferentValuesAndTraits() {
        val base = CharacterDetailApi.CharacterDetail(name = "米雪儿·李", englishName = "", japaneseName = "",
            gender = "", role = "", faction = "", summary = "主页面简介", identity = "", activeArea = "",
            height = "", weight = "", birthday = "3月25日", age = "", cnVoiceActor = "", jpVoiceActor = "",
            weaponName = "", weaponType = "", weaponIntro = "", traits = "", hobbies = "", diet = "",
            quote = "", description = "", observerQuote = "", avatarUrl = null, subPages = emptyList(),
            skills = emptyList(), augmentationModes = emptyList(), stories = emptyList())
        val extra = CharacterSettingsParser.parse(fixture()).entries.getValue("米雪儿李")
        val merged = CharacterDetailApi.mergeExtraInfo(base, extra)
        assertFalse(merged.lifeInfo.any { it.label == "生日" })
        assertTrue(merged.lifeInfo.any { it.label == "萌点" })
        assertEquals(extra.settingSummary, merged.settingSummary)
        assertEquals(base.summary, merged.summary)
        assertTrue(CharacterDetailApi.mergeExtraInfo(base.copy(birthday = "未知"), extra).lifeInfo.any { it.label == "生日" })
    }

    @Test fun failedAndOfflineSettingsRemainRetryableWhileSuccessfulRefreshReplacesMemory() = runBlocking {
        var calls = 0
        var next: OfflineCache.CacheResult? = null
        val api = CharacterSettingsApi(disk = { response(true, 90) }, network = { calls++; next })
        assertIs<ApiResult.Error>(api.fetch())
        next = response(true, 123)
        repeat(2) { assertEquals(123, assertIs<ApiResult.Success<*>>(api.fetch()).cacheAgeMs) }
        assertEquals(3, calls)
        assertTrue(assertIs<ApiResult.Success<*>>(api.fetch(cacheOnly = true)).isOffline)
        assertEquals(3, calls)
        next = response()
        api.fetch()
        api.fetch()
        assertEquals(4, calls)
        val oldSnapshot = api.currentSnapshot()
        next = OfflineCache.CacheResult(payload(fixture().replace("猫奴", "爱猫")), false, 0)
        val refreshed = assertIs<ApiResult.Success<Map<String, CharacterSettingInfo>>>(api.fetch(forceRefresh = true))
        assertTrue(refreshed.value.getValue("米雪儿李").lifeInfo.first().value.contains("爱猫"))
        assertNotSame(oldSnapshot, api.currentSnapshot())
    }

    @Test fun clearingCacheInvalidatesInFlightSettingsAndCancellationPropagates() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val api = CharacterSettingsApi(network = {
            calls++
            if (calls == 1) { started.complete(Unit); release.await() }
            response()
        })
        val pending = async { api.fetch() }
        started.await()
        MemoryCacheRegistry.clearAll()
        assertNull(api.currentSnapshot())
        release.complete(Unit)
        pending.await()
        assertNull(api.currentSnapshot())
        api.fetch()
        assertEquals(2, calls)
        assertFailsWith<CancellationException> {
            CharacterSettingsApi(network = { throw CancellationException("cancel") }).fetch()
        }
        Unit
    }

    @Test fun fullCurrentSnapshotPreservesEveryFieldForAll24Characters() {
        val file = System.getenv("CHARACTER_SETTINGS_AUDIT_FILE")?.let(::File)
        assumeTrue("Provide the November 12 full setting page", file?.isFile == true)
        val html = file!!.readText(Charsets.UTF_8)
        val result = CharacterSettingsParser.parse(html)
        assertTrue(result.canCache, result.issues.toString())
        assertEquals(24, result.entries.size)
        Jsoup.parse(html).select(".klbq-role-info").forEach { block ->
            val key = CharacterSettingsParser.identity(block.selectFirst(".name")!!.ownText())
            val entry = result.entries.getValue(key)
            assertEquals(10, entry.lifeInfo.size, key)
            assertTrue(entry.settingSummary.isNotBlank(), key)
            assertTrue(entry.settingObserverQuote.isNotBlank(), key)
            block.select("dl > div").forEach { field ->
                val label = field.selectFirst("dt")!!.text()
                val expected = com.nekolaska.calabiyau.core.wiki.HtmlText.clean(field.selectFirst("dd")!!.html())
                val actual = when (label) {
                    "简介" -> entry.settingSummary
                    "观测语录" -> entry.settingObserverQuote
                    else -> entry.lifeInfo.single { it.label == label }.value
                }
                // Template-injected style nodes affect serialization whitespace, not visible content.
                assertEquals(expected.replace(Regex("\\s+"), ""), actual.replace(Regex("\\s+"), ""), "$key/$label")
            }
            assertTrue(entry.relations.none { it.label.contains("CV") }, key)
        }
    }
}
