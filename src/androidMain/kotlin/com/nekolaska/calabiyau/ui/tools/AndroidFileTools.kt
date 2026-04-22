package com.nekolaska.calabiyau.ui.tools

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val idx = digitGroups.coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(idx.toDouble())) + " " + units[idx]
}

fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun getMimeType(file: File): String {
    val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

fun getFileUri(context: Context, file: File): Uri {
    return try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (_: Exception) {
        Uri.fromFile(file)
    }
}

fun openFile(context: Context, file: File, onError: (String) -> Unit) {
    try {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("无法打开该文件")
    }
}

fun openDirectory(context: Context, directory: File, onError: (String) -> Unit) {
    try {
        val uri = getFileUri(context, directory)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("无法打开目录")
    }
}

fun shareFile(context: Context, file: File, onError: (String) -> Unit) {
    try {
        val uri = getFileUri(context, file)
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("分享失败")
    }
}

fun shareFiles(context: Context, fileList: List<File>, onError: (String) -> Unit) {
    if (fileList.isEmpty()) return
    if (fileList.size == 1) {
        shareFile(context, fileList.first(), onError)
        return
    }
    try {
        val uris = ArrayList<Uri>()
        for (file in fileList) {
            uris.add(getFileUri(context, file))
        }
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("分享失败")
    }
}

fun scanMediaLibrary(context: Context, files: List<File>) {
    val targets = files.filter { it.exists() && it.isFile }
    if (targets.isEmpty()) return
    MediaScannerConnection.scanFile(
        context,
        targets.map { it.absolutePath }.toTypedArray(),
        null,
        null
    )
}

fun resolveOutputDirectory(basePath: String = AppPrefs.toolsOutputPath, child: String? = null): File {
    val base = File(basePath)
    if (!base.exists()) base.mkdirs()
    if (child.isNullOrBlank()) return base
    val target = File(base, child)
    if (!target.exists()) target.mkdirs()
    return target
}

fun getPathFromUri(uri: Uri): String? {
    val docId = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (_: Exception) {
        return uri.path
    }

    val split = docId.split(":")
    return when (split.size) {
        2 -> {
            val volume = split[0]
            val relPath = split[1]
            if (volume.equals("primary", ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/$relPath"
            } else {
                "/storage/$volume/$relPath"
            }
        }
        else -> uri.path
    }
}

fun Context.queryDisplayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}

fun Context.readTextFromUri(uri: Uri): String? {
    return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}