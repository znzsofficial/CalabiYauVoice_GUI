package com.nekolaska.calabiyau.feature.tools

import java.nio.file.Files
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

class ToolOutputTransactionTest {
    private fun publishToolOutput(file: java.io.File, write: (java.io.File) -> Unit) =
        com.nekolaska.calabiyau.feature.tools.publishToolOutput(file, rename = { from, to ->
            Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }, write = write)
    @Test fun repeatedNamesNeverReplacePreviousOutputs() {
        val dir = Files.createTempDirectory("tool-output").toFile()
        try {
            val request = dir.resolve("image_1_1.png")
            request.writeText("old")
            val a = publishToolOutput(request) { it.writeText("a") }
            val b = publishToolOutput(request) { it.writeText("b") }
            assertEquals("old", request.readText())
            assertNotEquals(a, b); assertEquals("a", a.readText()); assertEquals("b", b.readText())
            assertEquals(3, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun failedOrCancelledEncodingLeavesNoNewFiles() {
        val dir = Files.createTempDirectory("tool-output").toFile()
        try {
            val request = dir.resolve("image.png").apply { writeText("old") }
            assertFailsWith<IllegalStateException> { publishToolOutput(request) { it.writeText("partial"); error("disk full") } }
            val job = Job()
            toolTaskJob.set(job)
            assertFailsWith<CancellationException> { publishToolOutput(request) { it.writeText("partial"); job.cancel() } }
            assertEquals("old", request.readText()); assertEquals(1, dir.listFiles()!!.size)
        } finally { toolTaskJob.remove(); dir.deleteRecursively() }
    }
    @Test fun rejectsOverflowAndOversizedPixelBudgets() {
        requireImageBudget(100, 100)
        assertFailsWith<IllegalArgumentException> { requireImageBudget(Int.MAX_VALUE, Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { requireImageBudget(0, 20) }
    }
    @Test fun previewFailureIsAnErrorNotAnEscapingException(): Unit = runBlocking {
        val result = loadToolPreview<String> { requireImageBudget(4000, 3000); "image" }
        assertNull(result.data); assertNotNull(result.error)
        assertNotNull(loadToolPreview<String> { null }.error)
    }
    @Test fun previewGetsItsOwnJobAndCancellationIsNotSwallowed() = runBlocking {
        val began = CompletableDeferred<Unit>()
        val preview = async {
            val job = currentCoroutineContext().job
            loadToolPreview {
                assertSame(job, toolTaskJob.get())
                began.complete(Unit)
                while (true) checkToolCancellation()
                @Suppress("UNREACHABLE_CODE") "unused"
            }
        }
        began.await(); preview.cancelAndJoin()
        assertTrue(preview.isCancelled)
    }
}
