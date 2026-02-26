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

fun File.clearAll(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return false
    // 遍历目录下的所有文件
    listFiles()?.forEach { it.delete() }
    return true
}