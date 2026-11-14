package com.nekolaska.calabiyau.feature.download

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LocalSnackbarHostState

@Composable
internal fun DownloadSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = AppShapes.chip,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary
        )
    }
}

/** Image saves belong to the Dialog window, above the page/sheet that opened it. */
@Composable
internal fun DownloadImagePreview(model: String, name: String, onDismiss: () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
        ImagePreviewDialog(
            model = model,
            contentDescription = name,
            onDismiss = onDismiss,
            bottomContent = {
                DownloadSnackbarHost(
                    hostState,
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                )
            }
        )
    }
}
