package util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import java.io.File

val home: String = System.getProperty("user.home") ?: "C:\\"

suspend fun pickDirectory(initialPath: String? = null): File? {
    val initialDirectory = initialPath
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::isDirectory)
        ?.let(::PlatformFile)
    return FileKit.openDirectoryPicker(directory = initialDirectory)?.path?.let(::File)
}
