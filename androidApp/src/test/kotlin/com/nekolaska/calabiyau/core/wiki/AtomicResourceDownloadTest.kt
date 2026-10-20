package com.nekolaska.calabiyau.core.wiki

import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.test.*
import util.downloadResourceAtomically

class AtomicResourceDownloadTest {
    @Test fun existingNonzeroAndZeroFilesAreReplacedWithoutChangingTheirNames() = runBlocking {
        val dir = Files.createTempDirectory("android-download-adapter").toFile()
        try {
            for (old in listOf("broken!", "")) {
                val target = File(dir, "voice.wav").apply { writeText(old) }
                var requests = 0
                downloadResourceAtomically("local", target, { _, tmp -> requests++; tmp.writeText("correct"); true },
                    { source, out -> Files.move(source.toPath(), out.toPath(), REPLACE_EXISTING) })
                assertEquals(1, requests); assertEquals("correct", target.readText())
                assertEquals(listOf("voice.wav"), dir.list()?.toList())
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedOrEmptyDownloadPreservesOldFileAndRemovesTemp() = runBlocking {
        val dir = Files.createTempDirectory("android-download-failure").toFile()
        try {
            val target = File(dir, "voice.wav").apply { writeText("old") }
            for (success in listOf(false, true)) {
                assertFailsWith<IOException> {
                    downloadResourceAtomically("local", target, { _, _ -> success }, { _, _ -> error("must not publish") })
                }
                assertEquals("old", target.readText()); assertEquals(1, dir.list()?.size)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationRemovesPartialOutputWithoutPublishingIt() = runBlocking {
        val dir = Files.createTempDirectory("android-download-cancel").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val target = File(dir, "voice.wav").apply { writeText("old") }
            val job = launch {
                downloadResourceAtomically("local", target, { _, tmp ->
                    tmp.writeText("partial"); started.complete(Unit); awaitCancellation()
                }, { _, _ -> error("must not publish") })
            }
            started.await(); job.cancelAndJoin()
            assertEquals("old", target.readText()); assertEquals(1, dir.list()?.size)
        } finally { dir.deleteRecursively() }
    }
}
