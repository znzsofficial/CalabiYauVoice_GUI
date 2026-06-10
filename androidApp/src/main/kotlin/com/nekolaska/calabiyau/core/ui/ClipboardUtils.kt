package com.nekolaska.calabiyau.core.ui

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

@Composable
fun rememberPlainTextClipboardCopier(onCopied: () -> Unit = {}): (label: String, text: String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope, onCopied) {
        { label, text ->
            if (text.isNotBlank()) {
                scope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
                    onCopied()
                }
            }
        }
    }
}
