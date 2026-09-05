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
