package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun WikiListGroupError(message: String, loading: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, enabled = !loading) { Text(if (loading) "正在重试…" else "重新加载") }
    }
}
