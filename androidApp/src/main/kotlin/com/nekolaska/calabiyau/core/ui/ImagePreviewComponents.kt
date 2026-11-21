package com.nekolaska.calabiyau.core.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.nekolaska.calabiyau.core.preferences.AppPrefs

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
 * writes into [AppPrefs.savePath]. A [saveConfirmationLabel] requests confirmation before enqueueing.
 */
@Composable
fun ImagePreviewDialog(
    modifier: Modifier = Modifier,
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
    saveConfirmationLabel: String? = null,
    bottomContent: (@Composable BoxScope.() -> Unit)? = null
) {
    FullscreenImageDialog(onDismiss) {
        val context = LocalContext.current
        val showSnack = rememberSnackbarLauncher()
        var ready by remember(model) { mutableStateOf(false) }
        var confirming by remember(model) { mutableStateOf(false) }
        val saveImage = {
            if (saveConfirmationLabel == null) savePreviewImage(context, model, showSnack) else confirming = true
        }
        ZoomableImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize().padding(16.dp),
            onClick = onDismiss,
            onLongPress = saveImage,
            onImageReady = { ready = it }
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
        IconButton(
            onClick = saveImage,
            enabled = ready,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) { Icon(Icons.Outlined.SaveAlt, contentDescription = "保存") }
        bottomContent?.invoke(this)
        if (confirming && saveConfirmationLabel != null) {
            ConfirmImageSaveDialog(label = saveConfirmationLabel, onDismiss = { confirming = false },
                onConfirm = { confirming = false; savePreviewImage(context, model, showSnack) })
        }
    }
}

/** Only the dialog window is styled. The backdrop covers the bars, controls stay in the safe area. */
@Composable
internal fun FullscreenImageDialog(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                @Suppress("DEPRECATION")
                it.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                it.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    it.isStatusBarContrastEnforced = false
                    it.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        val host = remember { SnackbarHostState() }
        CompositionLocalProvider(LocalSnackbarHostState provides host) {
            Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(), contentAlignment = Alignment.Center) {
                content()
                SnackbarHost(host, Modifier.align(Alignment.BottomCenter))
            }
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

    enqueueImageSave(context, uri.toString(), showSnack)
}
