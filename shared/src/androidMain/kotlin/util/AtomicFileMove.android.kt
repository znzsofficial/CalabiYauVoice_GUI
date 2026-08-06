package util

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException

internal actual fun atomicReplaceFile(source: File, target: File) {
    try {
        Os.rename(source.absolutePath, target.absolutePath)
    } catch (error: ErrnoException) {
        throw IOException("Atomic file replacement failed", error)
    }
}
