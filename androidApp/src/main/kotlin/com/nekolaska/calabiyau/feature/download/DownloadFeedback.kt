package com.nekolaska.calabiyau.feature.download

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog

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
    ImagePreviewDialog(model = model, contentDescription = name, onDismiss = onDismiss)
}
