package com.nekolaska.calabiyau.feature.tools

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*

class AudioEditingTransactionTest {
    @Test fun oddAndEvenMonoWavDataRoundTripAndRiffSizeIncludesPadding() {
        val target = Files.createTempFile("wav-roundtrip", ".wav").toFile()
        try {
            for (bits in listOf(8, 24)) for (frames in 3..4) {
                val pcm = ByteArray(frames * (bits / 8)) { it.toByte() }
                writePcmWav(target, pcm, 1, 8000, bits)
                val bytes = target.readBytes()
                assertEquals(44 + pcm.size + (pcm.size and 1), bytes.size)
                assertEquals(bytes.size - 8, bytes[4].toInt())
                assertEquals(pcm.size, bytes[40].toInt())
                assertContentEquals(pcm, assertNotNull(readPcmWav(target)).pcmData)
            }
        } finally { target.delete() }
    }

    @Test fun failedReplacementAndBranchCreationPreserveBothOldNodesAndExport() = runBlocking {
        val dir = Files.createTempDirectory("wav-history").toFile()
        try {
            val history = AudioHistoryController(File(dir, "history"))
            val input = File(dir, "borrowed.wav")
            writePcmWav(input, byteArrayOf(0, 1, 2), 1, 8000, 8)
            val wav = assertNotNull(loadWavAudioFileForPreview(input, false))
            val meta = AudioMeta("original.wav", "audio/wav", 0, null, null, null, input.length())
            val preview = AudioPreviewState(LoadedAudioAsset(wav.input, input, meta, wav), true)
            history.push("one", preview, true); history.push("two", preview)
            history.select(0)
            val nodes = history.steps.toList()
            val expected = input.readBytes()
            input.delete() // candidate copy fails
            assertFailsWith<java.io.IOException> { history.push("replace", preview, true) }
            input.writeText("bad") // write succeeds, candidate readback fails
            assertFailsWith<IllegalStateException> { history.push("replace", preview, true) }
            assertFailsWith<IllegalStateException> { history.push("branch", preview) }
            assertEquals(nodes, history.steps.toList()); assertEquals(0, history.currentIndex)
            assertEquals(2, File(dir, "history").listFiles()!!.size)
            val redo = assertNotNull(history.select(1)).second.asset.wav!!
            val exported = File(dir, "export.wav"); redo.file.copyToolFile(exported)
            assertContentEquals(expected, exported.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun workspacesRemoveOnlyOwnedCopiesOnSuccessFailureAndCancellation() = runBlocking {
        val cache = Files.createTempDirectory("audio-work").toFile()
        try {
            val keep = File(cache, "same.wav").apply { writeText("borrowed") }
            withAudioWorkspace(cache) { a ->
                File(a, keep.name).writeText("first")
                assertFailsWith<IllegalStateException> { withAudioWorkspace(cache) { b ->
                    File(b, keep.name).writeText("second"); error("readback failed")
                } }
                assertEquals("first", File(a, keep.name).readText())
            }
            val started = CompletableDeferred<Unit>()
            val task = launch { withAudioWorkspace(cache) { dir ->
                File(dir, "decoded.wav").writeText("partial"); started.complete(Unit); awaitCancellation()
            } }
            started.await(); task.cancelAndJoin()
            assertEquals(listOf(keep), cache.listFiles()?.toList()); assertEquals("borrowed", keep.readText())
        } finally { cache.deleteRecursively() }
    }
}
