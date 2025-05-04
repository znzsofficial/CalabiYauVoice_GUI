import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

val home: File? = FileSystemView.getFileSystemView().homeDirectory
fun jChoose(callback: (File) -> Unit) {
    JFileChooser(home).apply {
        dialogTitle = "Select a directory"
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        if (showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            callback(selectedFile)
        }
    }
}

fun File.clearAll(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return false
    // 遍历目录下的所有文件
    listFiles()?.forEach { it.delete() }
    return true
}