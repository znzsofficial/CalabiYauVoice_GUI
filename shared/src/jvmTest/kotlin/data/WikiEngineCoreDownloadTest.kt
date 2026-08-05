package data

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WikiEngineCoreDownloadTest {

    @Test
    fun rejectsNonPositiveConcurrencyBeforeEmptyListReturn() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                WikiEngineCore.downloadSpecificFiles(
                    files = emptyList(),
                    saveDir = File("unused"),
                    maxConcurrency = 0,
                    onLog = {},
                    onProgress = { _, _, _ -> },
                    downloadFileFn = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun assignsDeterministicUniqueNamesBeforeConcurrentDownloads() = runBlocking {
        val saveDir = Files.createTempDirectory("wiki-download-test").toFile()
        val targetsByUrl = ConcurrentHashMap<String, String>()
        try {
            WikiEngineCore.downloadSpecificFiles(
                files = listOf(
                    "voice/a.wav" to "https://example.test/1.wav",
                    "voice:a.wav" to "https://example.test/2.wav",
                    "VOICE_A.WAV" to "https://example.test/3.wav"
                ),
                saveDir = saveDir,
                maxConcurrency = 3,
                onLog = {},
                onProgress = { _, _, _ -> },
                downloadFileFn = { url, target -> targetsByUrl[url] = target.name }
            )

            assertEquals("voice_a.wav", targetsByUrl["https://example.test/1.wav"])
            assertEquals("voice_a (2).wav", targetsByUrl["https://example.test/2.wav"])
            assertEquals("VOICE_A (3).WAV", targetsByUrl["https://example.test/3.wav"])
        } finally {
            saveDir.deleteRecursively()
        }
    }

    @Test
    fun reportsIndividualFailuresAndFailsTheBatch() {
        val saveDir = Files.createTempDirectory("wiki-download-failure-test").toFile()
        val logs = ConcurrentHashMap.newKeySet<String>()
        try {
            val error = assertFailsWith<java.io.IOException> {
                runBlocking {
                    WikiEngineCore.downloadSpecificFiles(
                        files = listOf("broken.wav" to "https://example.test/broken.wav"),
                        saveDir = saveDir,
                        maxConcurrency = 1,
                        onLog = logs::add,
                        onProgress = { _, _, _ -> },
                        downloadFileFn = { _, _ -> throw java.io.IOException("HTTP 404") }
                    )
                }
            }

            assertEquals("1 of 1 downloads failed", error.message)
            assertTrue(logs.any { it.contains("broken.wav") && it.contains("HTTP 404") })
        } finally {
            saveDir.deleteRecursively()
        }
    }
}
