package data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger

class WikiEngineCoreQueryTest {
    @Test
    fun apiErrorJsonIsNotAnEmptyCategoryAndDoesNotPoisonNameCache() = runBlocking {
        for (body in listOf("""{"error":{"code":"maxlag","info":"busy"}}""", "{}", "not json")) {
            assertFailsWith<WikiQueryFailure> {
                WikiEngineCore.fetchFilesInCategory("Category:test", false, { body }, SharedJson)
            }
        }
        assertEquals(emptyList(), WikiEngineCore.fetchFilesInCategory("Category:empty", false,
            { """{"batchcomplete":""}""" }, SharedJson))
    }

    @Test
    fun siblingSearchFamiliesMergeByOfficialNameAndKeepEverySeed() = runBlocking {
        val cache = WikiEngineCore.CharacterNameCache().also { it.cache.add("星绘") }
        val groups = WikiEngineCore.searchAndGroupCharacters("星绘", true,
            { """{"query":{"search":[{"title":"Category:星绘战斗语音"},{"title":"Category:星绘互动语音"},{"title":"Category:星绘战斗语音"}]}}""" }, SharedJson, cache)
        assertEquals(1, groups.size)
        assertEquals("星绘", groups.single().characterName)
        assertEquals(setOf("Category:星绘战斗语音", "Category:星绘互动语音"), groups.single().subCategories.toSet())
        val requested = mutableSetOf<String>()
        val scanned = WikiEngineCore.scanCategoryTrees(groups.single().subCategories,
            { url -> synchronized(requested) { requested.add(url) }; """{"query":{"categorymembers":[]}}""" }, SharedJson)
        assertEquals(groups.single().subCategories.toSet(), scanned.toSet())
        assertEquals(2, requested.size)
    }

    @Test
    fun categoryForestDeduplicatesCyclesAcrossIndependentRoots() = runBlocking {
        val calls = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val scanned = WikiEngineCore.scanCategoryTrees(listOf("Category:A", "Category:B"), { url ->
            val category = java.net.URLDecoder.decode(url.substringAfter("cmtitle=").substringBefore('&'), "UTF-8")
            calls.computeIfAbsent(category) { AtomicInteger() }.incrementAndGet()
            val next = if (category == "Category:A") "Category:B" else "Category:A"
            """{"query":{"categorymembers":[{"ns":14,"title":"$next"}]}}"""
        }, SharedJson)
        assertEquals(listOf("Category:A", "Category:B"), scanned)
        assertTrue(calls.values.all { it.get() == 1 })
    }

    @Test
    fun fileSearchDoesNotReturnPrefixOnlyResultsWhenFullTextFails() = runBlocking {
        assertFailsWith<WikiQueryFailure> {
            WikiEngineCore.searchFiles("voice", false, { url ->
                if ("list=allimages" in url) """{"query":{"allimages":[{"name":"voice.wav","url":"https://example.test/voice.wav"}]}}"""
                else """{"error":{"code":"maxlag"}}"""
            }, SharedJson)
        }
        Unit
    }


    @Test
    fun searchAndGroupCharactersThrowsAfterRepeatedFailures() {
        val error = assertFailsWith<WikiQueryFailure> {
            runBlocking {
                WikiEngineCore.searchAndGroupCharacters(
                    keyword = "角色",
                    voiceOnly = true,
                    fetchStringFn = { null },
                    jsonParser = SharedJson,
                    nameCache = WikiEngineCore.CharacterNameCache()
                )
            }
        }
        assertTrue(error.message.orEmpty().contains("搜索"))
    }

    @Test
    fun searchAndGroupCharactersReturnsEmptyWhenWikiHasNoMatches() = runBlocking {
        val result = WikiEngineCore.searchAndGroupCharacters(
            keyword = "角色",
            voiceOnly = true,
            fetchStringFn = { """{"query":{"search":[]}}""" },
            jsonParser = SharedJson,
            nameCache = WikiEngineCore.CharacterNameCache()
        )
        assertEquals(emptyList(), result)
    }

    @Test
    fun searchFilesThrowsWhenPrefixSearchIsHtml() {
        assertFailsWith<WikiQueryFailure> {
            runBlocking {
                WikiEngineCore.searchFiles(
                    keyword = "voice",
                    audioOnly = false,
                    fetchStringFn = { "<html>blocked</html>" },
                    jsonParser = SharedJson
                )
            }
        }
    }

    @Test
    fun fetchFilesInCategoryThrowsWhenRequestFails() {
        assertFailsWith<WikiQueryFailure> {
            runBlocking {
                WikiEngineCore.fetchFilesInCategory(
                    category = "Category:测试",
                    audioOnly = true,
                    fetchStringFn = { null },
                    jsonParser = SharedJson
                )
            }
        }
    }

    @Test
    fun characterNameCacheWaitsForTheExistingLoad() = runBlocking {
        val cache = WikiEngineCore.CharacterNameCache()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<List<String>>()

        val first = async {
            cache.ensure {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()
        val second = async {
            cache.ensure { error("the second caller must not load again") }
        }

        release.complete(listOf("诺诺"))
        first.await()
        second.await()

        assertEquals(setOf("诺诺"), cache.cache)
    }

    @Test
    fun audioFilterAcceptsUppercaseAndAdditionalAudioExtensions() = runBlocking {
        val requests = AtomicInteger(0)
        val result = WikiEngineCore.fetchFilesInCategory(
            category = "Category:音频",
            audioOnly = true,
            fetchStringFn = {
                requests.incrementAndGet()
                """{"query":{"pages":{"1":{"title":"文件:VOICE.FLAC","imageinfo":[{"url":"https://example.test/VOICE.FLAC"}]}}}}"""
            },
            jsonParser = SharedJson
        )

        assertEquals(listOf("VOICE.FLAC" to "https://example.test/VOICE.FLAC"), result)
        assertEquals(1, requests.get())
    }
}
