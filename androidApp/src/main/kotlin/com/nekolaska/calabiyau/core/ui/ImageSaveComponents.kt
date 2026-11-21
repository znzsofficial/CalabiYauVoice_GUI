package com.nekolaska.calabiyau.core.ui

import android.content.Context
import android.webkit.URLUtil
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.util.enqueueDownload
import java.io.File

/** Enqueue only: completion is owned by DownloadManager, never reported here as already saved. */
fun enqueueImageSave(context: Context, url: String, showMessage: (String) -> Unit) {
    try {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) { "不支持的图片地址" }
        val fileName = URLUtil.guessFileName(url, null, null).ifBlank { "image" }
        context.enqueueDownload(url = url, dir = File(AppPrefs.savePath), fileName = fileName, description = "正在保存图片…")
        showMessage("已开始保存：$fileName")
    } catch (e: Exception) {
        showMessage("保存失败：${e.message}")
    }
}

@Composable
internal fun ConfirmImageSaveDialog(label: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("保存图片") }, text = { Text(label) },
        confirmButton = { FilledTonalButton(onClick = onConfirm) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, shape = AppShapes.sheet)
}
