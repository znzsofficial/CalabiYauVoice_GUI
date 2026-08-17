package data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WikiEngineCoreDownloadTest {

    @Test
    fun sanitizesDotSegmentsAndWindowsDeviceNames() {
        assertEquals("_", sanitizeFileName(".."))
        assertEquals("_", sanitizeFileName("."))
        assertEquals("_CON", sanitizeFileName("CON"))
        assertEquals("name", sanitizeFileName("name...  "))
    }

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
            val downloadedFiles = WikiEngineCore.downloadSpecificFiles(
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
            assertEquals(
                listOf("voice_a.wav", "voice_a (2).wav", "VOICE_A (3).WAV"),
                downloadedFiles.map(File::getName)
            )
        } finally {
            saveDir.deleteRecursively()
        }
    }

    @Test
    fun duplicateUrlsUseDeduplicatedProgressTotal() = runBlocking {
        val saveDir = Files.createTempDirectory("wiki-download-duplicate-test").toFile()
        val progress = mutableListOf<Pair<Int, Int>>()
        try {
            val results = WikiEngineCore.downloadSpecificFiles(
                files = listOf(
                    "first.wav" to "https://example.test/voice.wav",
                    "duplicate.wav" to "https://example.test/voice.wav"
                ),
                saveDir = saveDir,
                maxConcurrency = 1,
                onLog = {},
                onProgress = { current, total, _ -> progress += current to total },
                downloadFileFn = { _, target -> target.writeText("complete") }
            )

            assertEquals(1, results.size)
            assertEquals(listOf(1 to 1), progress)
        } finally {
            saveDir.deleteRecursively()
        }
    }

    @Test
    fun preservesUntrackedFileAndReusesTrackedTargetOnRetry() = runBlocking {
        val saveDir = Files.createTempDirectory("wiki-download-existing-test").toFile()
        val existing = File(saveDir, "voice.wav").apply { writeText("existing") }
        try {
            val firstDownload = WikiEngineCore.downloadSpecificFiles(
                files = listOf("voice.wav" to "https://example.test/new.wav"),
                saveDir = saveDir,
                maxConcurrency = 1,
                onLog = {},
                onProgress = { _, _, _ -> },
                downloadFileFn = { _, target -> target.writeText("new") }
            )

            assertEquals("existing", existing.readText())
            assertEquals("voice (2).wav", firstDownload.single().name)
            assertEquals("new", firstDownload.single().readText())

            val retry = WikiEngineCore.downloadSpecificFiles(
                files = listOf("voice.wav" to "https://example.test/new.wav"),
                saveDir = saveDir,
                maxConcurrency = 1,
                onLog = {},
                onProgress = { _, _, _ -> },
                downloadFileFn = { _, _ -> error("tracked target must not be downloaded again") }
            )
            assertEquals(firstDownload.single().canonicalFile, retry.single().canonicalFile)
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

    @Test
    fun repairsTrackedZeroByteTargetOnRetry() = runBlocking {
        val saveDir = Files.createTempDirectory("wiki-download-zero-byte-test").toFile()
        try {
            val url = "https://example.test/voice.wav"
            val first = WikiEngineCore.downloadSpecificFiles(
                files = listOf("voice.wav" to url),
                saveDir = saveDir,
                maxConcurrency = 1,
                onLog = {},
                onProgress = { _, _, _ -> },
                downloadFileFn = { _, target -> target.writeText("complete") }
            ).single()
            first.writeBytes(byteArrayOf())

            var downloads = 0
            val repaired = WikiEngineCore.downloadSpecificFiles(
                files = listOf("voice.wav" to url),
                saveDir = saveDir,
                maxConcurrency = 1,
                onLog = {},
                onProgress = { _, _, _ -> },
                downloadFileFn = { _, target ->
                    downloads++
                    target.writeText("repaired")
                }
            ).single()

            assertEquals(first.canonicalFile, repaired.canonicalFile)
            assertEquals(1, downloads)
            assertEquals("repaired", repaired.readText())
        } finally {
            saveDir.deleteRecursively()
        }
    }

    @Test
    fun looksLikeHtmlFileDetectsDoctypePrefix() {
        val file = Files.createTempFile("wiki-html-detect", ".wav").toFile()
        try {
            file.writeText("<!DOCTYPE html><html><body>blocked</body></html>")
            assertTrue(util.looksLikeHtmlFile(file))
            file.writeText("RIFF")
            assertTrue(!util.looksLikeHtmlFile(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun serializesConcurrentBatchesTargetingSameDirectory() = runBlocking {
        val saveDir = Files.createTempDirectory("wiki-download-concurrent-test").toFile()
        try {
            val results = listOf(
                async {
                    WikiEngineCore.downloadSpecificFiles(
                        files = listOf("voice.wav" to "https://example.test/a.wav"),
                        saveDir = saveDir,
                        maxConcurrency = 1,
                        onLog = {},
                        onProgress = { _, _, _ -> },
                        downloadFileFn = { url, target -> target.writeText(url) }
                    ).single()
                },
                async {
                    WikiEngineCore.downloadSpecificFiles(
                        files = listOf("voice.wav" to "https://example.test/b.wav"),
                        saveDir = saveDir,
                        maxConcurrency = 1,
                        onLog = {},
                        onProgress = { _, _, _ -> },
                        downloadFileFn = { url, target -> target.writeText(url) }
                    ).single()
                }
            ).awaitAll()

            assertEquals(2, results.map { it.name.lowercase() }.toSet().size)
            assertEquals(
                setOf("https://example.test/a.wav", "https://example.test/b.wav"),
                results.map { it.readText() }.toSet()
            )
        } finally {
            saveDir.deleteRecursively()
        }
    }
}
