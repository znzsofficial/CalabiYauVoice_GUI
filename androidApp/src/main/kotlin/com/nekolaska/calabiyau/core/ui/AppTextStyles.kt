package com.nekolaska.calabiyau.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppTextStyles {
    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    val dialogTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    val topBarTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    val topBarSubtitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val chipLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)

    val statLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    val statValue: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    val bodyMuted: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    val captionMuted: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val listItemTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge

    val listItemMeta: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val settingsItemTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)

    val settingsItemSubtitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    val codeBlock: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    val monoLog: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
}

object AppTextColors {
    val muted
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    val subtle
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
}
