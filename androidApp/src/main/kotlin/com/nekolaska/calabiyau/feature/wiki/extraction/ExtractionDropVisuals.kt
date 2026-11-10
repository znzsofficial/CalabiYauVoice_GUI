package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

/** Extraction Q5 is 臻藏. Q8 is a source label, not the main game's collectible rarity. */
@Composable
internal fun extractionDropQualityColor(code: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (code) {
        1 -> if (dark) Color(0xFFB9C3D1) else Color(0xFF526174)
        2 -> if (dark) Color(0xFF9AC8FF) else Color(0xFF235A9C)
        3 -> if (dark) Color(0xFFD5B4FF) else Color(0xFF75409E)
        4 -> if (dark) Color(0xFFF5CC82) else Color(0xFF805313)
        5 -> if (dark) Color(0xFFFFB4AD) else Color(0xFFA13734)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun ExtractionDropQualityBadge(label: String, code: Int) {
    val color = extractionDropQualityColor(code)
    Surface(shape = smoothCornerShape(6.dp), color = color.copy(alpha = 0.10f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(color, smoothCornerShape(2.dp)))
            Text(label.ifBlank { if (code > 0) "Q$code" else "品质未标注" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun ExtractionDropThumbnail(
    name: String, qualityLabel: String, qualityCode: Int, imageUrl: String?, modifier: Modifier = Modifier
) {
    val shape = smoothCornerShape(10.dp)
    val color = extractionDropQualityColor(qualityCode)
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier.size(56.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, color.copy(alpha = 0.45f), shape)
        .semantics { contentDescription = listOf(name, qualityLabel).filter { it.isNotBlank() }.joinToString("，") },
        contentAlignment = Alignment.Center) {
        if (!loaded) Icon(Icons.Outlined.Inventory2, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        if (!imageUrl.isNullOrBlank()) AsyncImage(
            model = imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(5.dp),
            onLoading = { loaded = false }, onSuccess = { loaded = true }, onError = { loaded = false }
        )
    }
}
