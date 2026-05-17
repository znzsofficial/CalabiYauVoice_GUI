package com.nekolaska.calabiyau.core.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import java.io.File

// ════════════════════════════════════════════════════════
//  Shared image preview and save helpers
// ════════════════════════════════════════════════════════

/**
 * Lightweight model for screens that need to track the currently previewed image and title.
 */
data class PreviewImage(
    val url: String,
    val title: String
)

/**
 * Full-screen zoomable image preview.
 *
 * By default the save action handles [String] URLs and [Uri] models through [DownloadManager] and
 * writes into [AppPrefs.savePath]. Pass [onSave] only when a screen needs custom confirmation,
 * permissions, or a non-download based save flow.
 */
@Composable
fun ImagePreviewDialog(
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val saveImage = remember(context, model, contentDescription, onSave, showSnack) {
        onSave ?: { savePreviewImage(context, model, contentDescription, showSnack) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            ZoomableImage(
                model = model,
                contentDescription = contentDescription,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = onDismiss,
                onLongPress = saveImage
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
            IconButton(
                onClick = saveImage,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) { Icon(Icons.Outlined.SaveAlt, contentDescription = "保存") }
            bottomContent?.invoke(this)
        }
    }
}

/**
 * Default save path for generic previews.
 *
 * This intentionally only supports URI-like models. Bitmap/file-provider specific save flows should
 * stay in the feature screen that owns that data.
 */
private fun savePreviewImage(
    context: Context,
    model: Any?,
    contentDescription: String?,
    showSnack: (String) -> Unit
) {
    val uri = when (model) {
        is Uri -> model
        is String -> model.takeIf { it.isNotBlank() }?.toUri()
        else -> null
    } ?: run {
        showSnack("无法保存此图片")
        return
    }

    try {
        val fileName = URLUtil.guessFileName(uri.toString(), null, null)
            .takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: "image"
        val dir = File(AppPrefs.savePath)
        dir.mkdirs()
        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription("正在保存图片...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(File(dir, fileName)))
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        showSnack("已保存: $fileName")
    } catch (e: Exception) {
        showSnack("保存失败: ${e.message}")
    }
}
