package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val QualityBadgeSurface = Color(0xCC141720)
private val QualityBadgeRim = Color.White.copy(alpha = 0.14f)

fun catalogQualityLabel(level: Int): String = when (level) {
    1 -> "初始"
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    5 -> "传说"
    6 -> "私服"
    8 -> "臻藏"
    else -> "未知"
}

fun catalogQualityColor(level: Int): Color = when (level) {
    1 -> Color(0xFF94A3B8)
    2 -> Color(0xFF3B82F6)
    3 -> Color(0xFFA855F7)
    4 -> Color(0xFFF59E0B)
    5 -> Color(0xFFEF4444)
    6, 8 -> Color(0xFFFF6B2C)
    else -> Color(0xFF9CA3AF)
}

@Composable
fun BoxScope.CatalogQualityBadge(
    label: String?,
    level: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    padding: Dp = if (compact) 4.dp else 12.dp
) {
    if (label.isNullOrBlank() || level == null) return
    val accent = catalogQualityColor(level)
    Surface(
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(padding),
        shape = AppShapes.capsule,
        color = QualityBadgeSurface,
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, QualityBadgeRim)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Box(
                modifier = Modifier.size(if (compact) 9.dp else 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.55f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape)
                )
            }
            Text(
                label,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
