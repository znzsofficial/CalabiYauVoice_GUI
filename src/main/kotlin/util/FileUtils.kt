package util

import com.formdev.flatlaf.util.SystemFileChooser
import java.io.File

val home: String = System.getProperty("user.home") ?: "C:\\"

fun jChoose(callback: (File) -> Unit) {
    SystemFileChooser(home).apply {
        fileSelectionMode = SystemFileChooser.DIRECTORIES_ONLY
        if (showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION) {
            val directory = selectedFile
            callback(directory)
        }
    }
}

