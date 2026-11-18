package com.nekolaska.calabiyau

import kotlin.io.path.createTempDirectory
import kotlin.test.*

class CrashReportStoreTest {
    @Test fun reportReopensAcrossOwnersAndOnlyTheIdIsNeeded() {
        val dir = createTempDirectory().toFile()
        try {
            val writer = CrashReportStore(dir)
            val id = writer.save("x".repeat(MAX_CRASH_CHARS * 2))
            assertEquals(id, writer.newestPending())
            val reader = CrashReportStore(dir)
            assertEquals(MAX_CRASH_CHARS, reader.open(id)?.length)
            assertNull(writer.newestPending())
            assertEquals(reader.open(id), CrashReportStore(dir).open(id))
            reader.finish(id); assertNull(reader.open(id))
            assertNull(reader.open("../outside"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun cyclicCausesAreBoundedAndNotDuplicated() {
        val a = Exception("unique-first"); val b = Exception("unique-second", a); a.initCause(b)
        val trace = boundedStackTrace(a)
        assertEquals(1, Regex("unique-second").findAll(trace).count())
        assertTrue("[circular cause]" in trace)
    }
}
