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
//    JFileChooser(home).apply {
//        dialogTitle = "选择文件夹"
//        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
//        if (showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
//            callback(selectedFile)
//        }
//    }
}

