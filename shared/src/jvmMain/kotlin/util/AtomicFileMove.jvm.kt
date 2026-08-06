package util

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual fun atomicReplaceFile(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } catch (error: IOException) {
        // Windows may reject ATOMIC_MOVE when the destination already exists.
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (fallbackError: IOException) {
            fallbackError.addSuppressed(error)
            throw fallbackError
        }
    } catch (error: SecurityException) {
        throw IOException("Atomic file replacement was denied", error)
    }
}
