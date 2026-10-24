package com.nekolaska.calabiyau.core.wiki

import java.io.File
import java.security.MessageDigest

/** Internal records belong in filesDir, not beside user media or in an evictable cache. */
internal fun privateDownloadManifestFile(filesDir: File, saveDir: File): File {
    val directoryKey = MessageDigest.getInstance("SHA-256")
        .digest(saveDir.canonicalPath.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return File(File(filesDir, "download_manifests"), "$directoryKey.tsv")
}
