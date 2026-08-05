package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Files

class WikiWebViewDownloadTest {

    @Test
    fun normalizesUntrustedFileNames() {
        assertEquals("payload.bin", normalizeWikiDownloadFileName("../../payload.bin"))
        assertEquals("payload.bin", normalizeWikiDownloadFileName("..\\..\\payload.bin"))
        assertEquals("report_.txt", normalizeWikiDownloadFileName("report\u0000.txt"))
        assertNull(normalizeWikiDownloadFileName(".."))
    }

    @Test
    fun decodesPercentEncodedAndUnicodeData() {
        val decoded = decodePercentEncodedDataUrl("A%20中😀", maxBytes = 16)

        assertContentEquals("A 中😀".toByteArray(), decoded)
    }

    @Test
    fun decodesPercentEncodedDataFromOffset() {
        val dataUrl = "data:text/plain,A%20中"

        val decoded = decodePercentEncodedDataUrl(
            value = dataUrl,
            maxBytes = 8,
            startIndex = dataUrl.indexOf(',') + 1
        )

        assertContentEquals("A 中".toByteArray(), decoded)
    }

    @Test
    fun rejectsDecodedDataOverLimit() {
        assertNull(decodePercentEncodedDataUrl("%FF%FF", maxBytes = 1))
        assertNull(decodePercentEncodedDataUrl("中", maxBytes = 2))
        assertNull(decodePercentEncodedDataUrl("😀", maxBytes = 3))
    }

    @Test
    fun reservesUniqueFileWithoutOverwritingExistingFile() {
        val directory = Files.createTempDirectory("wiki-download-test").toFile()
        try {
            val existing = directory.resolve("report.txt").apply { writeText("original") }

            val (reserved, name) = reserveUniqueDownloadFile(directory, "report.txt")!!

            assertEquals("report (1).txt", name)
            assertTrue(reserved.exists())
            assertEquals("original", existing.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
