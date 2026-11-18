package util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

/** Private ownership ledger, scoped to one canonical download directory. Recovery is called
 * under WikiEngineCore's directory lock. Never sweep users' files by name/prefix. */
class ResourceDownloadStaging(privateRoot: File, directory: File) {
    private val destination = directory.canonicalFile
    private val records = File(privateRoot, MessageDigest.getInstance("SHA-256")
        .digest(destination.path.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })

    /** JVM mutexes do not protect a live transfer in another Desktop process. The OS releases
     * this private lock when the process dies. It is held across recovery and all publishers. */
    internal fun acquireDirectoryLease(): AutoCloseable {
        if (!records.isDirectory && !records.mkdirs() && !records.isDirectory) throw IOException("无法创建下载暂存记录")
        val handle = RandomAccessFile(File(records, "directory.lock"), "rw")
        try {
            if (handle.channel.tryLock() == null) throw IOException("另一进程正在下载到此目录")
            return handle
        } catch (e: Exception) { handle.close(); throw e }
    }

    internal fun allocate(): File {
        if (!records.isDirectory && !records.mkdirs() && !records.isDirectory) throw IOException("无法创建下载暂存记录")
        while (true) {
            val id = UUID.randomUUID().toString()
            val temp = File(destination, "wiki-download-$id.tmp")
            val marker = File(records, id)
            if (temp.exists() || !marker.createNewFile()) continue
            try {
                if (temp.createNewFile()) return temp
            } catch (e: Exception) { marker.delete(); throw e }
            marker.delete()
        }
    }

    internal fun release(temp: File) {
        if (temp.delete() || !temp.exists()) File(records, temp.name.removePrefix("wiki-download-").removeSuffix(".tmp")).delete()
    }

    internal fun recover() {
        records.listFiles()?.forEach { marker ->
            val id = marker.name
            if (marker.isFile && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
                release(File(destination, "wiki-download-$id.tmp"))
            }
        }
    }
}

/** Caller owns integrity decisions; this adapter must replace even an existing nonempty target. */
suspend fun downloadResourceAtomically(
    url: String,
    target: File,
    fetch: suspend (String, File) -> Boolean,
    publish: (File, File) -> Unit = ::atomicReplaceFile,
    staging: ResourceDownloadStaging? = null
) {
    val tmp = staging?.allocate() ?: File.createTempFile("wiki-download-", ".tmp", target.parentFile)
    try {
        if (!fetch(url, tmp)) throw IOException("HTTP download failed for $url")
        currentCoroutineContext().ensureActive()
        if (tmp.length() == 0L) throw IOException("Empty response body for $url")
        publish(tmp, target)
    } finally { if (staging != null) staging.release(tmp) else tmp.delete() }
}
