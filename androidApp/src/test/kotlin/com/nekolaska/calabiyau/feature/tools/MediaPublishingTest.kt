package com.nekolaska.calabiyau.feature.tools

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.test.*

class MediaPublishingTest {
    private val rename: (File, File) -> Unit = { from, to -> Files.move(from.toPath(), to.toPath(), REPLACE_EXISTING) }

    @Test fun imageEncoderFalseAfterPartialWriteDoesNotPublishOrReplaceOldImage() {
        val dir = Files.createTempDirectory("frame-export").toFile()
        try {
            val old = File(dir, "frame.png").apply { writeText("old") }
            assertFailsWith<IllegalStateException> { publishEncodedImage(old, rename) { it.write(1); false } }
            assertEquals(listOf(old), dir.listFiles()!!.toList()); assertEquals("old", old.readText())
            val actual = publishEncodedImage(old, rename) { it.write(2); true }
            assertNotEquals(old, actual); assertContentEquals(byteArrayOf(2), actual.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun disposingAnOldPlaybackOwnerDoesNotStopTheReplacement() {
        val owners = com.nekolaska.calabiyau.core.media.PlaybackOwnership()
        val old = Any(); val active = Any(); var stops = 0
        owners.claim(old); owners.claim(active)
        owners.releaseIfOwned(old) { stops++ }
        assertEquals(0, stops)
        owners.releaseIfOwned(active) { stops++; owners.claim(null) }
        owners.releaseIfOwned(active) { stops++ }
        assertEquals(1, stops)
    }

    @Test fun rawPrivacyCopyNeverVisibleAndNeitherFailureNorCancellationAffectsExistingOutput() {
        val root = Files.createTempDirectory("exif-boundary").toFile()
        try {
            val privateDir = File(root, "private").apply { mkdir() }
            val public = File(root, "public").apply { mkdir() }
            val target = File(public, "photo_clean.jpg").apply { writeText("older") }
            fun copy(f: File) { assertEquals(privateDir, f.parentFile); f.writeText("GPS:secret\nimage") }
            assertFailsWith<IOException> { publishSanitizedCopy(privateDir, target, { copy(it); throw IOException() }, { true }, rename) }
            assertFailsWith<IllegalStateException> { publishSanitizedCopy(privateDir, target, ::copy, { false }, rename) }
            val job = Job(); toolTaskJob.set(job)
            try {
                assertFailsWith<CancellationException> { publishSanitizedCopy(privateDir, target, ::copy, { job.cancel(); true }, rename) }
            } finally { toolTaskJob.remove() }
            assertEquals(listOf(target), public.listFiles()!!.toList())
            val result = publishSanitizedCopy(privateDir, target, ::copy, { file ->
                assertEquals(listOf(target), public.listFiles()!!.toList()); file.writeText("image"); true
            }, rename)
            assertEquals("image", result.readText()); assertEquals("older", target.readText()); assertTrue(privateDir.list()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun lateMuxFailureOrSecondPublishFailureLeavesNoPartialSplit() {
        val root = Files.createTempDirectory("mux-batch").toFile()
        try {
            val video = File(root, "video.mp4").apply { writeText("old video") }; val audio = File(root, "audio.m4a")
            for (failStop in listOf(true, false)) {
                var published = 0
                assertFailsWith<IOException> { ToolOutputBatch { from, to ->
                    if (++published == 2) throw IOException("volume gone") else rename(from, to)
                }.use { batch ->
                    batch.stage(video) { it.writeText("video") }
                    batch.stage(audio) { file -> writeFinalizedTracks({ if (failStop) throw IOException("finalize failed") }) {
                        file.writeText("samples")
                    } }
                    batch.commit()
                } }
                assertEquals(if (failStop) 0 else 2, published)
                assertEquals(listOf(video), root.listFiles()!!.toList()); assertEquals("old video", video.readText())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun boundedCopyRejectsUnknownLengthBeforeWritingOversizeData() {
        val out = java.io.ByteArrayOutputStream()
        assertFailsWith<IllegalArgumentException> { ByteArray(257).inputStream().copyToolBytes(out, 256) }
        assertEquals(0, out.size())
        ByteArray(256).inputStream().copyToolBytes(out, 256); assertEquals(256, out.size())
    }

    @Test fun cancellationDuringReadDoesNotWriteTheReturnedChunkOrPublish() {
        val dir = Files.createTempDirectory("copy-cancel").toFile()
        val job = Job(); toolTaskJob.set(job)
        try {
            val old = File(dir, "audio.wav").apply { writeText("old") }
            val source = object : java.io.ByteArrayInputStream(byteArrayOf(1, 2)) {
                override fun read(bytes: ByteArray, off: Int, len: Int): Int = super.read(bytes, off, len).also { job.cancel() }
            }
            assertFailsWith<CancellationException> { publishToolOutput(old, rename) { tmp ->
                tmp.outputStream().use { out -> source.copyToolBytes(out) }
            } }
            assertEquals("old", old.readText()); assertEquals(listOf(old), dir.listFiles()!!.toList())
        } finally { toolTaskJob.remove(); dir.deleteRecursively() }
    }
}
