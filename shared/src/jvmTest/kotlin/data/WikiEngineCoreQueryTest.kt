package data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
}
