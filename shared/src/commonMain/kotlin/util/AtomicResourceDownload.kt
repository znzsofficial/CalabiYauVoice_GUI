package util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/** Caller owns integrity decisions; this adapter must replace even an existing nonempty target. */
suspend fun downloadResourceAtomically(
    url: String,
    target: File,
    fetch: suspend (String, File) -> Boolean,
    publish: (File, File) -> Unit = ::atomicReplaceFile
) {
    val tmp = File.createTempFile("wiki-download-", ".tmp", target.parentFile)
    try {
        if (!fetch(url, tmp)) throw IOException("HTTP download failed for $url")
        currentCoroutineContext().ensureActive()
        if (tmp.length() == 0L) throw IOException("Empty response body for $url")
        publish(tmp, target)
    } finally { tmp.delete() }
}
